"""Local collection (TASK-83.9): English entries written on this Mac by the Codex CLI, checked, and pushed to the
server later, so Saynata can answer them without a paid Claude call.

Four kinds: word, phrasal_verb, idiom, collocation. Everything lives in one table, `collection`, in pipeline.db:

    collect   -> rows with status pending (or skipped: a word the backend would call "not a word")
    write     -> Codex writes the backend WordEntry JSON, the backend checks run, one retry with feedback
                 -> done | needs_review | skipped (Codex returned no senses) | failed (tried again next run)
    compare   -> an HTML page with the Codex entry next to a Claude entry, for the developer to judge
    push      -> done rows into the production cache `word_lookup` (dry run unless --yes), never over a Claude row

The prompt is read from the backend (ClaudeService.WORD_PROMPT) and the checks are a copy of the backend's
SimpleWords, so a pushed entry looks exactly like one the backend made itself. Codex runs through the developer's
ChatGPT plan (`codex login` once): no per-call cost, but it uses the plan's limits, so agents never start long runs.
"""

import hashlib
import html
import json
import os
import re
import shutil
import sqlite3
import subprocess
import tempfile
import time
import tomllib
from collections import Counter
from collections.abc import Callable, Iterable
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path

from pydantic import BaseModel, ValidationError

from pipeline import prefill

BACKEND = Path(__file__).resolve().parents[3] / "backend"
CLAUDE_SERVICE = BACKEND / "src/main/java/com/englishtranslation/backend/claude/ClaudeService.java"
APPLICATION_PROPERTIES = BACKEND / "src/main/resources/application.properties"

KINDS = ("word", "phrasal_verb", "idiom", "collocation")
STATUSES = ("pending", "done", "needs_review", "skipped", "failed")
MAX_ATTEMPTS = 3  # a failed row (Codex error, bad JSON) is tried again by later runs until this many tries

SOURCE_SCOWL = prefill.SOURCE_WORDS  # common words not bundled (SCOWL-70 + wordfreq)
SOURCE_TOPIC = "topic"  # kept topic words (TASK-61) still pending in the words table
SOURCE_NGSL_REVIEW = "ngsl-review"  # NGSL words the Claude pipeline left needs_review
SOURCE_WORDNET = prefill.SOURCE_PHRASES  # WordNet phrasal verbs and idioms
SOURCE_BUNDLED_USAGE = "bundled-usage"  # collocation and idiom usage items of the bundled entries

SCHEMA = """
CREATE TABLE IF NOT EXISTS collection (
    id             INTEGER PRIMARY KEY,
    kind           TEXT NOT NULL,              -- word | phrasal_verb | idiom | collocation
    text           TEXT NOT NULL UNIQUE,       -- lowercase, single spaces (CacheKeys.normalizeWord)
    source         TEXT NOT NULL,              -- scowl-70 | topic | ngsl-review | oewn-2025 | bundled-usage
    zipf           REAL NOT NULL DEFAULT 0,    -- wordfreq Zipf (for a phrase: of its words combined)
    priority       INTEGER NOT NULL DEFAULT 0, -- higher is written first: round(zipf * 100)
    status         TEXT NOT NULL DEFAULT 'pending',
    attempts       INTEGER NOT NULL DEFAULT 0, -- Codex calls made for this row
    model          TEXT,                       -- for example codex:gpt-5.6-terra
    prompt_version TEXT,                       -- the backend word prompt version, for example v7
    entry          TEXT,                       -- the backend WordEntry JSON (camelCase)
    problems       TEXT,                       -- JSON list of failed checks, or the reason for skipped/failed
    claude_entry   TEXT,                       -- a Claude entry to compare with (compare --claude), same JSON
    claude_model   TEXT,
    pushed_at      TEXT,                       -- when push wrote it to the server
    updated_at     TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_collection_next ON collection (status, priority DESC, id);
"""


def connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.executescript(SCHEMA)
    return connection


# ---------- the backend prompt ----------


def java_text_block(source: str, name: str) -> str:
    """The value of `static final String <name> = \"\"\" ... \"\"\";` as Java builds it: the common indent (the
    closing quotes count too) and trailing spaces removed, and the escapes \\" \\n \\\\ read."""
    match = re.search(rf'\b{name}\s*=\s*"""[ \t]*\n(.*?)\n([ \t]*)""";', source, re.S)
    if not match:
        raise ValueError(f"No text block named {name}")
    lines = match.group(1).split("\n")
    indents = [len(line) - len(line.lstrip()) for line in lines if line.strip()] + [len(match.group(2))]
    cut = min(indents)
    text = "\n".join(line[cut:].rstrip() for line in lines) + "\n"
    return re.sub(r'\\(["n\\])', lambda m: {'"': '"', "n": "\n", "\\": "\\"}[m.group(1)], text)


def backend_word_prompt(path: Path = CLAUDE_SERVICE) -> str:
    """ClaudeService.WORD_PROMPT, read from the Java file, so Codex gets the very same instructions as Claude."""
    return java_text_block(path.read_text(encoding="utf-8"), "WORD_PROMPT")


def backend_word_prompt_version(path: Path = APPLICATION_PROPERTIES) -> str:
    """app.claude.word-prompt-version: part of the cache key, so a pushed row is found by the backend."""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("app.claude.word-prompt-version="):
            return line.split("=", 1)[1].strip()
    raise ValueError("app.claude.word-prompt-version is missing")


def cache_key(word: str, prompt_version: str) -> str:
    """CacheKeys.key("word", promptVersion, normalizeWord(word)): sha256 of the parts joined with "|"."""
    return hashlib.sha256(f"word|{prompt_version}|{normalize(word)}".encode()).hexdigest()


def normalize(text: str) -> str:
    """CacheKeys.normalizeWord: trim, single spaces, lowercase."""
    return " ".join(text.split()).lower()


# Added to the backend prompt only when an item is a phrase. The backend has no phrase prompt yet: today a phrase
# goes to the sentence explanation (doc-4). See doc-18, "Local collection", for how the app could show these.
PHRASE_NOTE = """Some items are not single words but a phrasal verb, an idiom or a collocation (words that often go
together). Describe such an item as ONE dictionary entry for the whole phrase:
- pos: "phrasal verb", "idiom" or "phrase"
- simpleDefinition explains the whole phrase, never with the phrase itself.
- every example uses the whole phrase (a form of it is fine: "gave up" for "give up"; a phrasal verb may be split:
  "turn it off").
- usage: 0-3 items about the phrase itself, never about one of its words alone.
If an item is not a real, common English phrase exactly as written, return an empty list of senses for it.
"""

BATCH_NOTE = """Answer from your own knowledge. Do not run commands, read files or search the web.
Write one entry for EACH item below, in the same order. Copy the item text, exactly as given, into "word".
"""


def escape_tags(text: str) -> str:
    """ClaudeService.escapeTags: & first, so a typed "&lt;" stays text."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def build_prompt(items: list[tuple[str, str]], word_prompt: str, feedback: dict[str, list[str]] | None = None) -> str:
    """One Codex message for a batch of (kind, text). Codex has no system prompt, so the backend prompt comes first.
    `feedback` maps a text to the problems of its last answer (the retry, like WordService.retryWithFeedback)."""
    parts = [word_prompt]
    if any(kind != "word" for kind, _ in items):
        parts.append(PHRASE_NOTE)
    parts.append(BATCH_NOTE)
    parts.extend(f"<word>{escape_tags(text)}</word>" for _, text in items)
    for text, problems in (feedback or {}).items():
        parts.append(f"Your last answer for <word>{escape_tags(text)}</word> used words that are too hard or broke "
                     "the rules. Write the whole entry again. Use only very common words in definitions and in easy "
                     "examples. Problems: " + "; ".join(problems))
    return "\n".join(parts)


# ---------- the answer shape (backend word/WordEntry.java) ----------


class Example(BaseModel):
    level: str
    text: str


class Usage(BaseModel):
    kind: str
    pattern: str
    example: str


class Sense(BaseModel):
    pos: str
    cefr: str
    simpleDefinition: str
    vietnamese: str
    examples: list[Example]
    usage: list[Usage]


class Entry(BaseModel):
    senses: list[Sense]


def _strict(properties: dict, required: list[str] | None = None) -> dict:
    return {"type": "object", "additionalProperties": False, "required": required or list(properties),
            "properties": properties}


SENSE_SCHEMA = _strict({
    "pos": {"type": "string"},
    "cefr": {"type": "string", "enum": ["A1", "A2", "B1", "B2", "C1", "C2"]},
    "simpleDefinition": {"type": "string"},
    "vietnamese": {"type": "string"},
    "examples": {"type": "array", "description": "Exactly 3 examples of this sense, ordered easy, medium, harder.",
                 "items": _strict({"level": {"type": "string", "enum": ["easy", "medium", "harder"]},
                                   "text": {"type": "string"}})},
    "usage": {"type": "array",
              "description": "0-3 common, natural ways to use this sense. Fewer, or none, when there are no good ones.",
              "items": _strict({"kind": {"type": "string", "enum": ["pattern", "collocation", "phrase", "idiom"]},
                                "pattern": {"type": "string"}, "example": {"type": "string"}})},
})
# Codex answers a batch: one WordEntry per item, with the item text to match it back.
BATCH_SCHEMA = _strict({"entries": {"type": "array", "items": _strict({
    "word": {"type": "string"}, "senses": {"type": "array", "items": SENSE_SCHEMA}})}})


# ---------- the checks (backend word/SimpleWords.java) ----------

WORD = re.compile(r"[a-zA-Z]+(?:'[a-z]+)?")
EXAMPLE_EXTRA = {"me", "my", "oh"}
IRREGULAR_NEGATIVES = {"can't": "can", "won't": "will", "shan't": "shall"}
CONTRACTION_ENDINGS = ("'s", "'m", "'re", "'ve", "'ll", "'d", "n't")
MAX_OUTSIDE_IN_EXAMPLE = {"easy": 0, "medium": 1, "harder": 2}
LEVELS = ["easy", "medium", "harder"]
CEFR = {"A1", "A2", "B1", "B2", "C1", "C2"}
USAGE_KINDS = {"pattern", "collocation", "phrase", "idiom"}
MAX_USAGE = 3


def simple_vocabulary(directory: Path = prefill.WORDS_DIR) -> set[str]:
    """The backend's simple words: words/ngsl.txt plus words/simple-extra.txt."""
    return prefill.read_word_list("ngsl.txt", directory) | prefill.read_word_list("simple-extra.txt", directory)


def _words(text: str) -> list[str]:
    return [w.lower() for w in WORD.findall(text.replace("’", "'"))]


def _plain(word: str) -> str:
    if word in IRREGULAR_NEGATIVES:
        return IRREGULAR_NEGATIVES[word]
    for ending in CONTRACTION_ENDINGS:
        if word.endswith(ending):
            return word[: -len(ending)]
    return word


def hard_in_definition(definition: str, vocabulary: set[str]) -> list[str]:
    """SimpleWords.hardWordsInDefinition: every word must be simple; a possessive 's is not a new word."""
    return sorted({w.removesuffix("'s") for w in _words(definition)} - vocabulary)


def hard_in_example(text: str, headword: str, vocabulary: set[str]) -> list[str]:
    """SimpleWords.hardWordsInExample: the headword and any word that starts with it are never hard.

    One addition for phrases, which the backend has no rule for: every word of the phrase and its forms count as
    the headword ("give up": "gave", "giving" and "up" are never hard). A single word keeps the backend rule exactly
    ("giving" is still hard for "give", as in the backend)."""
    heads = normalize(headword).split()
    forms: set[str] = set()
    if len(heads) > 1:
        from pipeline.forms import word_forms

        for head in heads:
            forms |= word_forms(head)
    hard = set()
    for word in _words(text):
        plain = _plain(word)
        if plain in forms or any(plain.startswith(head) for head in heads):
            continue
        if plain not in vocabulary and plain not in EXAMPLE_EXTRA:
            hard.add(plain)
    return sorted(hard)


def _java_list(words: list[str]) -> str:
    return "[" + ", ".join(words) + "]"  # Java's Set.toString, so the lines read like the backend log


def simple_word_problems(entry: Entry, headword: str, vocabulary: set[str]) -> list[str]:
    """SimpleWords.problems, line for line: what makes the backend retry and then save needsReview."""
    problems = []
    for number, sense in enumerate(entry.senses, start=1):
        hard = hard_in_definition(sense.simpleDefinition, vocabulary)
        if hard:
            problems.append(f"sense {number}: the definition uses hard words {_java_list(hard)}")
        for example in sense.examples:
            allowed = MAX_OUTSIDE_IN_EXAMPLE.get(example.level.lower())
            hard = hard_in_example(example.text, headword, vocabulary)
            if allowed is not None and len(hard) > allowed:
                problems.append(f"sense {number}: the {example.level} example '{example.text}' uses hard words "
                                f"{_java_list(hard)}")
    return problems


def shape_problems(entry: Entry) -> list[str]:
    """What Claude's structured output guarantees and Codex's schema cannot (exact counts and order)."""
    problems = []
    if len(entry.senses) > 4:
        problems.append(f"{len(entry.senses)} senses (max 4)")
    for number, sense in enumerate(entry.senses, start=1):
        if [e.level for e in sense.examples] != LEVELS:
            problems.append(f"sense {number}: needs exactly 3 examples in the order easy, medium, harder")
        if any(not e.text.strip() for e in sense.examples):
            problems.append(f"sense {number}: an example is empty")
        if sense.cefr not in CEFR:
            problems.append(f"sense {number}: cefr '{sense.cefr}' is not A1-C2")
        if not sense.simpleDefinition.strip() or not sense.vietnamese.strip() or not sense.pos.strip():
            problems.append(f"sense {number}: pos, definition or Vietnamese is empty")
        if len(sense.usage) > MAX_USAGE:
            problems.append(f"sense {number}: {len(sense.usage)} usage items (max {MAX_USAGE})")
        for item in sense.usage:
            if item.kind not in USAGE_KINDS:
                problems.append(f"sense {number}: usage kind '{item.kind}' is not one of {sorted(USAGE_KINDS)}")
            if re.search(r"\b(sb|sth)\b", item.pattern):
                problems.append(f"sense {number}: usage '{item.pattern}' uses sb/sth (write someone, something)")
    return problems


def entry_problems(entry: Entry, headword: str, vocabulary: set[str]) -> list[str]:
    return shape_problems(entry) + simple_word_problems(entry, headword, vocabulary)


# ---------- collect ----------

PARTICLES = {"up", "down", "out", "off", "in", "on", "over", "away", "back", "through", "around", "round", "along",
             "about", "across", "by", "after", "into", "for", "with", "apart", "ahead", "forward", "together",
             "aside", "behind", "under", "onto", "upon", "without", "against", "at", "to", "from", "of"}

# A collocation or idiom from the bundled usage must be something a learner could type: no placeholders.
PLACEHOLDER_WORDS = prefill.PLACEHOLDERS | {"sb", "sth", "noun", "verb", "adjective", "clause"}
# Phrases never collected: rude or unsafe, and found in the sources above.
BLOCKED_PHRASES = {"bugger off", "get it on", "kill yourself", "piss off", "screw up", "screw around", "jerk off",
                   "knock up", "go to hell", "get laid", "hump"}
BLOCKED_WORDS = prefill.BLOCKED | {"bugger", "bloody", "damn", "goddamn", "suck"}
NUMBER_WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
                "twelve", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred",
                "thousand", "million", "billion", "and"}
TYPED_PHRASE = re.compile(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,5}")  # 2 to 6 words


@dataclass(frozen=True)
class Item:
    kind: str
    text: str
    source: str
    zipf: float
    status: str = "pending"
    reason: str | None = None  # why a row starts as skipped

    @property
    def priority(self) -> int:
        return prefill.priority_for(self.zipf)


def phrase_kind(text: str, parts_of_speech: set[str]) -> str:
    """phrasal_verb: a WordNet verb whose words after the first are all particles ("give up", "put up with").
    Every other multiword verb, adjective or adverb is an idiom ("take care", "in time", "break the ice")."""
    words = text.split()
    if "v" in parts_of_speech and 2 <= len(words) <= 3 and all(w in PARTICLES for w in words[1:]):
        return "phrasal_verb"
    return "idiom"


def allowed_phrase(text: str) -> bool:
    words = text.split()
    return (text not in BLOCKED_PHRASES and not any(w in BLOCKED_WORDS for w in words)
            and not all(w in NUMBER_WORDS for w in words))


def wordnet_items(lemmas: dict[str, set[str]], min_zipf: float, zipf_of: Callable[[str], float]) -> list[Item]:
    """The prefill phrase collector (same filters), sorted into phrasal verbs and idioms."""
    found = [i for i in prefill.collect_phrases(lemmas, min_zipf, zipf_of) if allowed_phrase(i.text)]
    pos_of: dict[str, set[str]] = {}
    for lemma, parts in lemmas.items():
        text = prefill.normalize_phrase(lemma)
        if text:
            pos_of.setdefault(text, set()).update(parts)
    return [Item(phrase_kind(i.text, pos_of.get(i.text, set())), i.text, SOURCE_WORDNET, i.priority / 100)
            for i in found]


def typed_phrase(pattern: str) -> str | None:
    """A usage pattern as a learner would type it, or None ("make a decision" yes; "depend on + something" no)."""
    text = normalize(pattern).rstrip(".!?")
    words = text.split()
    if not TYPED_PHRASE.fullmatch(text) or any(w in PLACEHOLDER_WORDS or w in prefill.BLOCKED for w in words):
        return None
    if all(w in prefill.FUNCTION_WORDS for w in words) or not allowed_phrase(text):
        return None
    return text


def name_only_words(wordnet_single_lemmas: Iterable[tuple[str, str]]) -> set[str]:
    """Words WordNet knows only with a capital letter and only as a noun ("David", "London", "Christmas"): names,
    not dictionary words. SCOWL-70 is lowercase, so they look like words there. Capitalised adjectives stay
    ("American", "Chinese": a learner looks them up), and so does a word WordNet does not know at all ("hey").
    Takes (lemma, part of speech) pairs."""
    lower, capital_noun, capital_other = set(), set(), set()
    for lemma, pos in wordnet_single_lemmas:
        if lemma == lemma.lower():
            lower.add(lemma)
        else:
            (capital_noun if pos == "n" else capital_other).add(lemma.lower())
    return capital_noun - capital_other - lower


def word_items(found: list, names: set[str]) -> list[Item]:
    """The prefill word collector's lemmas as rows. A name starts as skipped, so the report shows it."""
    return [Item("word", i.text, SOURCE_SCOWL, i.priority / 100) if i.text not in names and i.text not in BLOCKED_WORDS
            else Item("word", i.text, SOURCE_SCOWL, i.priority / 100, "skipped",
                      "a name: WordNet has it only with a capital letter" if i.text in names else "blocked word")
            for i in found]


def bundled_usage_items(entries: Iterable[str], zipf_of: Callable[[str], float]) -> list[Item]:
    """Collocations and idioms that the bundled entries already use (TASK-24 usage pass, about 7,100 collocations
    and 1,200 idioms). They were written for a sense of a common word, so they are things a learner meets."""
    best: dict[str, str] = {}
    for entry_json in entries:
        for sense in json.loads(entry_json).get("senses", []):
            for usage in sense.get("usage", []):
                kind = usage.get("kind")
                text = typed_phrase(usage.get("pattern", "")) if kind in ("collocation", "idiom") else None
                if text and text not in best:
                    best[text] = kind
    return [Item(kind, text, SOURCE_BUNDLED_USAGE, zipf_of(text)) for text, kind in best.items()]


def pipeline_word_items(pipeline_db: sqlite3.Connection, accepted: set[str],
                        zipf_of: Callable[[str], float]) -> list[Item]:
    """The kept topic words still pending (TASK-61) and the NGSL words left needs_review (TASK-3).
    A word the backend would call "not a word" (not on SCOWL-70 or NGSL) starts as skipped: the backend answers
    such a word with "no senses" before it looks in the cache, so an entry for it would never be shown."""
    rows = pipeline_db.execute(
        "SELECT lemma, topic FROM words WHERE (topic IS NOT NULL AND status != 'done') "
        "OR (topic IS NULL AND status = 'needs_review')")
    items = []
    for lemma, topic in rows:
        text = normalize(lemma)
        source = SOURCE_TOPIC if topic else SOURCE_NGSL_REVIEW
        if text in accepted:
            items.append(Item("word", text, source, zipf_of(text)))
        else:
            items.append(Item("word", text, source, zipf_of(text), "skipped", "not on the backend word list"))
    return items


def all_pipeline_lemmas(pipeline_db: sqlite3.Connection | None) -> set[str]:
    if pipeline_db is None:
        return set()
    return {normalize(row[0]) for row in pipeline_db.execute("SELECT lemma FROM words")}


def insert_items(connection: sqlite3.Connection, items: Iterable[Item]) -> Counter:
    """Adds the rows that are new (by text) and returns how many per kind. Never changes a row that exists, so
    collect is safe to run again. The first source that brings a text keeps it."""
    added: Counter = Counter()
    for item in items:
        cursor = connection.execute(
            "INSERT OR IGNORE INTO collection (kind, text, source, zipf, priority, status, problems) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            (item.kind, item.text, item.source, round(item.zipf, 3), item.priority, item.status,
             json.dumps([item.reason]) if item.reason else None))
        added[item.kind] += cursor.rowcount
    connection.commit()
    return added


def report_lines(connection: sqlite3.Connection) -> list[str]:
    rows = connection.execute(
        "SELECT kind, source, status, count(*) FROM collection GROUP BY kind, source, status").fetchall()
    if not rows:
        return ["The collection is empty. Run: uv run pipeline collection collect"]
    table: dict[tuple[str, str], Counter] = {}
    for kind, source, status, count in rows:
        table.setdefault((kind, source), Counter())[status] += count
    head = f"{'kind':<13} {'source':<14}" + "".join(f"{s:>13}" for s in STATUSES) + f"{'total':>8}"
    lines = [head, "-" * len(head)]
    totals: Counter = Counter()
    order = {kind: n for n, kind in enumerate(KINDS)}
    for (kind, source), counts in sorted(table.items(), key=lambda kv: (order.get(kv[0][0], 9), kv[0][1])):
        totals.update(counts)
        lines.append(f"{kind:<13} {source:<14}" + "".join(f"{counts[s]:>13}" for s in STATUSES)
                     + f"{sum(counts.values()):>8}")
    lines.append("-" * len(head))
    lines.append(f"{'all':<13} {'':<14}" + "".join(f"{totals[s]:>13}" for s in STATUSES)
                 + f"{sum(totals.values()):>8}")
    per_kind = Counter()
    for (kind, _), counts in table.items():
        per_kind[kind] += sum(counts.values())
    lines.append("")
    lines.append("Per kind: " + ", ".join(f"{kind} {per_kind[kind]}" for kind in KINDS))
    pending = totals["pending"] + totals["failed"]
    lines.append(f"To write: {pending} rows. At {BATCH_SIZE} per Codex call, about {SECONDS_PER_CALL} s a call and "
                 f"{DEFAULT_WORKERS} calls at once: about {estimate_hours(pending):.1f} hours.")
    return lines


# ---------- write (Codex) ----------

BATCH_SIZE = 5
DEFAULT_WORKERS = 4
SECONDS_PER_CALL = 40  # a batch of 5 words at effort low; measured 9 s for one word on 2026-10-17
CODEX_TIMEOUT = 600
DEFAULT_EFFORT = "low"  # the backend asks Claude with effort LOW too


def estimate_hours(rows: int, batch: int = BATCH_SIZE, workers: int = DEFAULT_WORKERS) -> float:
    calls = -(-rows // batch)
    return calls * SECONDS_PER_CALL / workers / 3600


class ProviderStop(Exception):
    """Codex cannot go on (not logged in, plan limit reached): stop the whole run, keep what is done."""


class WriteError(Exception):
    """This call failed (time out, no or bad JSON); the rows stay failed and are tried again next run."""


def default_codex_model(home: Path | None = None) -> str | None:
    """The model in ~/.codex/config.toml, so the row says which model wrote it (the call ignores the rest of
    that config, see CodexWriter)."""
    home = home or Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex")
    try:
        return tomllib.loads((home / "config.toml").read_text(encoding="utf-8")).get("model")
    except (OSError, tomllib.TOMLDecodeError):
        return None


class CodexWriter:
    """`codex exec` with a JSON schema, in an empty folder, read-only sandbox, no saved session.

    --ignore-user-config skips the user's notify hooks and projects (a hook would fire after every call), so the
    model and the effort are passed here. stdin is DEVNULL, because without it Codex waits for input forever.
    """

    STOP_WORDS = ("usage limit", "rate limit", "not logged in", "please log in", "codex login")

    def __init__(self, model: str | None, effort: str = DEFAULT_EFFORT, timeout: int = CODEX_TIMEOUT,
                 run: Callable = subprocess.run):
        if run is subprocess.run and shutil.which("codex") is None:
            raise ProviderStop("codex is not installed (npm i -g @openai/codex), then run: codex login")
        self.model, self.effort, self.timeout, self.run = model, effort, timeout, run

    @property
    def name(self) -> str:
        return f"codex:{self.model or 'default'}"

    def write(self, prompt: str) -> list[dict]:
        with tempfile.TemporaryDirectory() as folder:
            schema, answer = Path(folder) / "schema.json", Path(folder) / "answer.json"
            schema.write_text(json.dumps(BATCH_SCHEMA), encoding="utf-8")
            command = ["codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "--ephemeral",
                       "--ignore-user-config", "-c", f"model_reasoning_effort={self.effort}",
                       "--output-schema", str(schema), "-o", str(answer)]
            if self.model:
                command += ["-m", self.model]
            try:
                proc = self.run(command + [prompt], capture_output=True, text=True, timeout=self.timeout,
                                cwd=folder, stdin=subprocess.DEVNULL)
            except subprocess.TimeoutExpired as error:
                raise WriteError(f"codex did not finish in {self.timeout} s") from error
            text = answer.read_text(encoding="utf-8") if answer.is_file() else ""
        output = (proc.stdout or "") + (proc.stderr or "")
        if not text.strip():
            if any(word in output.lower() for word in self.STOP_WORDS):
                raise ProviderStop(f"codex: {output.strip()[-300:]}")
            raise WriteError(f"codex gave no answer (exit {proc.returncode}): {output.strip()[-300:]}")
        try:
            return json.loads(text)["entries"]
        except (json.JSONDecodeError, KeyError, TypeError) as error:
            raise WriteError(f"codex answer is not the batch JSON: {text[:200]}") from error


class Writer:
    """What `write` needs from Codex. CodexWriter is the real one; the tests use a fake."""

    name: str

    def write(self, prompt: str) -> list[dict]: ...


@dataclass
class Result:
    row_id: int
    text: str
    status: str
    calls: int
    entry: Entry | None = None
    problems: list[str] = field(default_factory=list)


def _match(answers: list[dict], text: str, position: int, batch_size: int) -> dict | None:
    """The answer for this item: by its "word", else by position when Codex answered every item in order."""
    for answer in answers:
        if isinstance(answer, dict) and normalize(str(answer.get("word", ""))) == text:
            return answer
    if len(answers) == batch_size and isinstance(answers[position], dict):
        return answers[position]
    return None


def _judge(answer: dict | None, text: str, vocabulary: set[str]) -> tuple[Entry | None, list[str], str]:
    """(entry, problems, status) for one answer. status: done, retry, skipped or failed."""
    if answer is None:
        return None, ["codex gave no answer for this item"], "failed"
    try:
        entry = Entry.model_validate({"senses": answer.get("senses")})
    except ValidationError as error:
        return None, [f"not the WordEntry JSON: {error.errors()[0]['msg']}"], "failed"
    if not entry.senses:
        return entry, ["codex says it is not a real English word or phrase (no senses)"], "skipped"
    problems = entry_problems(entry, text, vocabulary)
    return entry, problems, "retry" if problems else "done"


def write_batch(rows: list[sqlite3.Row], writer: Writer, word_prompt: str, vocabulary: set[str]) -> list[Result]:
    """One Codex call for the batch, then one retry call per item that failed the checks (like the backend:
    one retry with feedback, never more). Raises ProviderStop; a WriteError makes every row of the batch failed."""
    items = [(row["kind"], row["text"]) for row in rows]
    try:
        answers = writer.write(build_prompt(items, word_prompt))
    except WriteError as error:
        return [Result(row["id"], row["text"], "failed", 1, problems=[str(error)]) for row in rows]
    results = []
    for position, row in enumerate(rows):
        entry, problems, status = _judge(_match(answers, row["text"], position, len(rows)), row["text"], vocabulary)
        if status != "retry":
            results.append(Result(row["id"], row["text"], status, 1, entry, problems))
            continue
        try:
            again = writer.write(build_prompt([(row["kind"], row["text"])], word_prompt, {row["text"]: problems}))
            entry2, problems2, status2 = _judge(_match(again, row["text"], 0, 1), row["text"], vocabulary)
        except WriteError as error:
            entry2, problems2, status2 = None, [str(error)], "failed"
        if status2 in ("done", "retry"):
            # Like WordService: the retry's entry is kept even when it still has problems (needs_review).
            results.append(Result(row["id"], row["text"], "done" if status2 == "done" else "needs_review", 2,
                                  entry2, problems2))
        else:  # the retry broke: keep the first entry, marked for review
            results.append(Result(row["id"], row["text"], "needs_review", 2, entry, problems))
    return results


def rows_to_write(connection: sqlite3.Connection, kinds: list[str] | None, texts: list[str] | None,
                  limit: int | None, per_kind: int | None, redo: bool = False) -> list[sqlite3.Row]:
    """Pending rows, and failed ones with tries left, most frequent first. `per_kind` takes the first N of each
    kind (the trial). `texts` picks rows by text in any state but skipped-by-collect; with `redo` they are written
    again even when done."""
    where, params = ["(status = 'pending' OR (status = 'failed' AND attempts < ?))"], [MAX_ATTEMPTS]
    if texts:
        wanted = [normalize(t) for t in texts]
        where = [f"text IN ({','.join('?' * len(wanted))})"] + ([] if redo else
                                                               ["status IN ('pending', 'failed')"])
        params = wanted
    if kinds:
        where.append(f"kind IN ({','.join('?' * len(kinds))})")
        params += kinds
    rows = connection.execute(f"SELECT * FROM collection WHERE {' AND '.join(where)} ORDER BY priority DESC, id",
                              params).fetchall()
    if per_kind:
        seen: Counter = Counter()
        picked = []
        for row in rows:
            if seen[row["kind"]] < per_kind:
                seen[row["kind"]] += 1
                picked.append(row)
        rows = picked
    return rows[:limit] if limit else rows


def save(connection: sqlite3.Connection, result: Result, model: str, prompt_version: str) -> None:
    connection.execute(
        "UPDATE collection SET status = ?, attempts = attempts + ?, model = ?, prompt_version = ?, "
        "entry = COALESCE(?, entry), problems = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (result.status, result.calls, model, prompt_version,
         result.entry.model_dump_json() if result.entry and result.entry.senses else None,
         json.dumps(result.problems), result.row_id))
    connection.commit()


@dataclass
class RunSummary:
    counts: Counter = field(default_factory=Counter)
    calls: int = 0
    stopped: str | None = None
    seconds: float = 0.0


def batches(rows: list[sqlite3.Row], size: int) -> list[list[sqlite3.Row]]:
    """Words and phrases in separate batches: a word batch does not need the phrase note."""
    groups: list[list[sqlite3.Row]] = []
    for is_word in (True, False):
        same = [row for row in rows if (row["kind"] == "word") == is_word]
        groups += [same[i:i + size] for i in range(0, len(same), size)]
    return groups


def write_rows(connection: sqlite3.Connection, rows: list[sqlite3.Row], writer: Writer, *, batch_size: int = BATCH_SIZE,
               workers: int = DEFAULT_WORKERS, word_prompt: str | None = None, prompt_version: str | None = None,
               vocabulary: set[str] | None = None, progress: Callable[[Result], None] | None = None) -> RunSummary:
    """Writes the rows with `workers` Codex calls at once. Saving happens on this thread (sqlite is not
    thread-safe). Stops at the first ProviderStop and keeps everything done so far."""
    word_prompt = word_prompt or backend_word_prompt()
    prompt_version = prompt_version or backend_word_prompt_version()
    vocabulary = vocabulary if vocabulary is not None else simple_vocabulary()
    summary, started = RunSummary(), time.time()
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futures = [pool.submit(write_batch, group, writer, word_prompt, vocabulary)
                   for group in batches(rows, batch_size)]
        for future in as_completed(futures):
            if future.cancelled():
                continue
            try:
                results = future.result()
            except ProviderStop as stop:
                summary.stopped = str(stop)
                for other in futures:
                    other.cancel()
                continue
            for result in results:
                save(connection, result, writer.name, prompt_version)
                summary.counts[result.status] += 1
                summary.calls += result.calls
                if progress:
                    progress(result)
    summary.seconds = time.time() - started
    return summary


# ---------- compare ----------


def entry_from_pipeline(entry_json: str) -> Entry:
    """A pipeline.db entry (snake_case, models.WordEntry) in the backend shape, as the export writes it."""
    data = json.loads(entry_json)
    senses = []
    for sense in data.get("senses", []):
        examples = [e if isinstance(e, dict) else {"level": LEVELS[min(i, 1)], "text": e}
                    for i, e in enumerate(sense.get("examples", []))]
        senses.append({"pos": sense.get("pos", ""), "cefr": sense.get("cefr", ""),
                       "simpleDefinition": sense.get("simple_definition", ""),
                       "vietnamese": sense.get("vietnamese", ""), "examples": examples,
                       "usage": sense.get("usage", [])})
    return Entry.model_validate({"senses": senses})


def claude_from_pipeline(connection: sqlite3.Connection, pipeline_db: sqlite3.Connection | None) -> int:
    """Copies the Claude entry the NGSL pipeline already wrote (the needs_review words) into claude_entry. Free."""
    if pipeline_db is None:
        return 0
    copied = 0
    rows = connection.execute("SELECT id, text FROM collection WHERE kind = 'word' AND claude_entry IS NULL")
    for row_id, text in rows.fetchall():
        found = pipeline_db.execute("SELECT entry FROM words WHERE lemma = ? AND entry IS NOT NULL",
                                    (text,)).fetchone()
        if found:
            try:
                entry = entry_from_pipeline(found[0])
            except (ValidationError, json.JSONDecodeError, KeyError):
                continue
            connection.execute("UPDATE collection SET claude_entry = ?, claude_model = ? WHERE id = ?",
                               (entry.model_dump_json(), "claude (NGSL pipeline)", row_id))
            copied += 1
    connection.commit()
    return copied


def claude_from_server(connection: sqlite3.Connection, pg, prompt_version: str) -> int:
    """Copies Claude's answer from the production cache (word_lookup, read-only) when the word is already there."""
    rows = connection.execute("SELECT id, text FROM collection WHERE claude_entry IS NULL").fetchall()
    if not rows:
        return 0
    keys = {cache_key(text, prompt_version): row_id for row_id, text in rows}
    with pg.cursor() as cursor:
        cursor.execute("SELECT cache_key, entry_json, model FROM word_lookup WHERE cache_key = ANY(%s) "
                       "AND model NOT LIKE 'codex%%'", (list(keys),))
        found = cursor.fetchall()
    for key, entry_json, model in found:
        connection.execute("UPDATE collection SET claude_entry = ?, claude_model = ? WHERE id = ?",
                           (entry_json, model, keys[key]))
    connection.commit()
    return len(found)


CLAUDE_MODEL = "claude-haiku-5-5"  # app.claude.model
CLAUDE_COST_PER_ITEM = prefill.COST_PER_WORD  # production average, Claude Haiku 5.5, word prompt


def claude_entry(text: str, kind: str, word_prompt: str) -> tuple[Entry, float]:
    """Asks Claude exactly like ClaudeService.describeWord (same system prompt, same user message, effort low).
    A phrase gets the phrase note too. Returns the entry and its real cost."""
    import anthropic

    system = word_prompt + ("\n" + PHRASE_NOTE if kind != "word" else "")
    response = anthropic.Anthropic().messages.parse(
        model=CLAUDE_MODEL, max_tokens=16000, system=system,
        messages=[{"role": "user", "content": f"<word>{escape_tags(text)}</word>"}],
        output_format=Entry, output_config={"effort": "low"})
    if response.parsed_output is None:
        raise RuntimeError(f"Claude gave no entry (stop_reason={response.stop_reason})")
    # Claude Haiku 5.5: $1 per million input tokens, $5 per million output tokens.
    cost = response.usage.input_tokens / 1e6 * 1 + response.usage.output_tokens / 1e6 * 5
    return response.parsed_output, cost


def claude_from_api(connection: sqlite3.Connection, rows: list[sqlite3.Row], max_cost: float,
                    ask: Callable[[str, str, str], tuple[Entry, float]] = claude_entry,
                    word_prompt: str | None = None) -> tuple[int, float]:
    """Paid: one Claude call per row that has no Claude entry yet, until max_cost. Returns (answers, dollars)."""
    word_prompt = word_prompt or backend_word_prompt()
    spent, answered = 0.0, 0
    for row in rows:
        if row["claude_entry"]:
            continue
        if spent + CLAUDE_COST_PER_ITEM * 2 > max_cost:
            break
        entry, cost = ask(row["text"], row["kind"], word_prompt)
        spent += cost
        connection.execute("UPDATE collection SET claude_entry = ?, claude_model = ? WHERE id = ?",
                           (entry.model_dump_json(), CLAUDE_MODEL, row["id"]))
        connection.commit()
        answered += 1
    return answered, spent


def compare_rows(connection: sqlite3.Connection, per_kind: int, texts: list[str] | None = None) -> list[sqlite3.Row]:
    """The first N written rows of each kind (the trial), or the given texts."""
    if texts:
        wanted = [normalize(t) for t in texts]
        return connection.execute(f"SELECT * FROM collection WHERE text IN ({','.join('?' * len(wanted))}) "
                                  "ORDER BY kind, priority DESC", wanted).fetchall()
    rows = connection.execute("SELECT * FROM collection WHERE status IN ('done', 'needs_review', 'skipped') "
                              "AND model IS NOT NULL ORDER BY priority DESC, id").fetchall()
    seen: Counter = Counter()
    picked = []
    for row in rows:
        if seen[row["kind"]] < per_kind:
            seen[row["kind"]] += 1
            picked.append(row)
    order = {kind: n for n, kind in enumerate(KINDS)}
    return sorted(picked, key=lambda row: order.get(row["kind"], 9))


def _entry_html(entry_json: str | None, empty: str) -> str:
    if not entry_json:
        return f'<p class="none">{html.escape(empty)}</p>'
    entry = Entry.model_validate_json(entry_json)
    if not entry.senses:
        return '<p class="none">No senses ("not a word").</p>'
    parts = []
    for sense in entry.senses:
        examples = "".join(f'<li><span class="lvl">{html.escape(e.level)}</span> {html.escape(e.text)}</li>'
                           for e in sense.examples)
        usage = "".join(f'<li><span class="lvl">{html.escape(u.kind)}</span> <b>{html.escape(u.pattern)}</b> '
                        f'&rarr; {html.escape(u.example)}</li>' for u in sense.usage)
        parts.append(
            f'<div class="sense"><div class="meta">{html.escape(sense.pos)} &middot; {html.escape(sense.cefr)}</div>'
            f'<p class="def">{html.escape(sense.simpleDefinition)}</p><ul>{examples}</ul>'
            + (f'<ul class="usage">{usage}</ul>' if usage else "")
            + f'<p class="vi">{html.escape(sense.vietnamese)}</p></div>')
    return "".join(parts)


def compare_page(rows: list[sqlite3.Row]) -> str:
    """A light, self-contained page: one row per item, Codex left, Claude right, problems under the title."""
    cards = []
    for row in rows:
        problems = json.loads(row["problems"] or "[]")
        problem_html = "".join(f"<li>{html.escape(p)}</li>" for p in problems)
        cards.append(
            f'<section class="item"><h2>{html.escape(row["text"])} <small>{html.escape(row["kind"])} &middot; '
            f'{html.escape(row["source"])} &middot; <span class="st {row["status"]}">{row["status"]}</span>'
            f'</small></h2>'
            + (f'<ul class="problems">{problem_html}</ul>' if problem_html else "")
            + f'<div class="pair"><div class="col"><h3>{html.escape(row["model"] or "Codex")}</h3>'
            f'{_entry_html(row["entry"], "Not written yet.")}</div>'
            f'<div class="col"><h3>{html.escape(row["claude_model"] or "Claude")}</h3>'
            f'{_entry_html(row["claude_entry"], "No Claude entry (run compare --claude).")}</div></div>'
            f'<p class="vote">Better: <label><input type="radio" name="v{row["id"]}"> Codex</label> '
            f'<label><input type="radio" name="v{row["id"]}"> Claude</label> '
            f'<label><input type="radio" name="v{row["id"]}"> Same</label></p></section>')
    counts = Counter(row["kind"] for row in rows)
    summary = ", ".join(f"{kind} {counts[kind]}" for kind in KINDS if counts[kind])
    return f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Codex vs Claude</title>
<style>
:root{{--bg:#fafaf8;--card:#fff;--ink:#1d1d1b;--soft:#6b6b66;--line:#e4e2dc;--teal:#0f766e;--warn:#b45309;--bad:#b91c1c}}
@media (prefers-color-scheme: dark){{:root{{--bg:#151514;--card:#1f1f1d;--ink:#ecebe6;--soft:#a3a29c;--line:#34332f;
--teal:#2dd4bf;--warn:#fbbf24;--bad:#f87171}}}}
body{{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 -apple-system,system-ui,sans-serif}}
main{{max-width:1100px;margin:0 auto;padding:24px 16px}} h1{{font-size:22px;margin:0 0 4px}}
.intro{{color:var(--soft);margin:0 0 20px}}
.item{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px;margin:0 0 16px}}
.item h2{{font-size:19px;margin:0 0 8px}} .item h2 small{{font-size:13px;font-weight:400;color:var(--soft)}}
.st.done{{color:var(--teal)}} .st.needs_review{{color:var(--warn)}} .st.skipped,.st.failed{{color:var(--bad)}}
.pair{{display:grid;grid-template-columns:1fr 1fr;gap:16px}} @media (max-width:720px){{.pair{{grid-template-columns:1fr}}}}
.col h3{{font-size:12px;text-transform:uppercase;letter-spacing:.04em;color:var(--soft);margin:0 0 6px}}
.sense{{border-top:1px solid var(--line);padding:8px 0}} .meta{{font-size:12px;color:var(--soft)}}
.def{{margin:2px 0 4px;font-weight:600}} ul{{margin:0;padding-left:18px}} .usage{{margin-top:4px}}
.lvl{{font-size:11px;color:var(--soft);text-transform:uppercase}} .vi{{color:var(--soft);margin:4px 0 0}}
.problems{{color:var(--warn);font-size:13px;margin:0 0 8px}} .none{{color:var(--soft);font-style:italic}}
.vote{{font-size:13px;color:var(--soft);margin:8px 0 0}}
</style></head><body><main><h1>Codex vs Claude: the trial</h1>
<p class="intro">{len(rows)} items ({summary}). Same backend word prompt and the same checks for both.
Left: Codex (the collection). Right: Claude. The radio buttons are only for you while reading; nothing is saved.</p>
{''.join(cards)}</main></body></html>"""


# ---------- push ----------


@dataclass
class PushPlan:
    rows: list[sqlite3.Row]
    bundled: list[str]  # bundled on the server: the backend answers these from the dictionary, never the cache
    exists: list[str]  # already in word_lookup (a Claude answer, or pushed before): left as it is
    to_insert: list[sqlite3.Row]


def push_rows(connection: sqlite3.Connection, kinds: list[str], include_review: bool, limit: int | None) -> list:
    statuses = ("done", "needs_review") if include_review else ("done",)
    rows = connection.execute(
        f"SELECT * FROM collection WHERE status IN ({','.join('?' * len(statuses))}) "
        f"AND kind IN ({','.join('?' * len(kinds))}) AND pushed_at IS NULL AND entry IS NOT NULL "
        "ORDER BY priority DESC, id", (*statuses, *kinds)).fetchall()
    return rows[:limit] if limit else rows


def plan_push(pg, rows: list[sqlite3.Row], prompt_version: str) -> PushPlan:
    texts = [row["text"] for row in rows]
    keys = [cache_key(text, prompt_version) for text in texts]
    with pg.cursor() as cursor:
        cursor.execute("SELECT lower(lemma) FROM dictionary_word WHERE lower(lemma) = ANY(%s) "
                       "UNION SELECT lower(form) FROM dictionary_form WHERE lower(form) = ANY(%s)", (texts, texts))
        bundled = {row[0] for row in cursor.fetchall()}
        cursor.execute("SELECT cache_key FROM word_lookup WHERE cache_key = ANY(%s)", (keys,))
        cached = {row[0] for row in cursor.fetchall()}
    plan = PushPlan(rows, [], [], [])
    for row, key in zip(rows, keys):
        if row["text"] in bundled:
            plan.bundled.append(row["text"])
        elif key in cached:
            plan.exists.append(row["text"])
        else:
            plan.to_insert.append(row)
    return plan


def first_cefr(entry: Entry) -> str | None:
    return entry.senses[0].cefr if entry.senses else None


def push(connection: sqlite3.Connection, pg, plan: PushPlan, prompt_version: str) -> int:
    """Writes the planned rows into word_lookup, in one transaction. ON CONFLICT DO NOTHING: a row that appeared
    meanwhile (a user asked Claude) wins. Marks the rows pushed_at, also the ones that were already there."""
    inserted = 0
    with pg.cursor() as cursor:
        for row in plan.to_insert:
            entry = Entry.model_validate_json(row["entry"])
            cursor.execute(
                "INSERT INTO word_lookup (cache_key, word, entry_json, model, prompt_version, needs_review, cefr, "
                "created_at) VALUES (%s, %s, %s, %s, %s, %s, %s, now()) ON CONFLICT (cache_key) DO NOTHING",
                (cache_key(row["text"], prompt_version), row["text"], entry.model_dump_json(), row["model"][:64],
                 prompt_version, row["status"] == "needs_review", first_cefr(entry)))
            inserted += cursor.rowcount
    pg.commit()
    done = [row["id"] for row in plan.to_insert] + [row["id"] for row in plan.rows if row["text"] in plan.exists]
    connection.executemany("UPDATE collection SET pushed_at = CURRENT_TIMESTAMP WHERE id = ?", [(i,) for i in done])
    connection.commit()
    return inserted
