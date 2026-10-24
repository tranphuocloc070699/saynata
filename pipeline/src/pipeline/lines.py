"""Language lines (TASK-106.5): one short natural line per (English text, language), written on this Mac by the Codex CLI
and pushed to the server table `line_translation`, so the app can show the language row without a paid Claude call.

Everything lives in one table, `lines`, in pipeline.db (one row per text, language and prompt version):

    collect   -> reads Postgres (read-only): the simple definition of every bundled sense, of every backend word,
                 and the text of every cached sentence -> rows with status pending (one per language)
    write     -> Codex writes up to 50 lines of ONE language per call; every line is checked
                 -> done | pending again (tried again next run, up to 3 times) | needs_review | failed
    push      -> done rows into `line_translation` (dry run unless --yes), source codex, never over an existing row
    report    -> counts per language and status, and the time to write the rest

The prompt is read from the backend (ClaudeService.LINE_PROMPT) and the prompt version from application.properties
(app.claude.line-prompt-version), so a pushed line is the one the backend would have written and is found by the same
key. The key is the sha256 of the normalized text (LineTranslationService.normalize and hash), see `normalize` below.
Codex runs through the developer's ChatGPT plan (`codex login` once): no per-call cost, but it uses the plan's limits,
so agents never start long runs. The commands and the run time are in doc-6 and doc-18.
"""

import hashlib
import json
import re
import sqlite3
import time
import unicodedata
from collections import Counter
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path

from pipeline import collection
from pipeline.collection import ProviderStop, WriteError

BACKEND = collection.BACKEND
APPLICATION_PROPERTIES = collection.APPLICATION_PROPERTIES
CLAUDE_SERVICE = collection.CLAUDE_SERVICE

# The languages that have a line (TargetLanguage.java without English and Thai), with the name the prompt shows.
LANGUAGES = {"vi": "Vietnamese", "ja": "Japanese", "ko": "Korean", "zh": "Simplified Chinese",
             "fr": "French", "es": "Spanish", "de": "German"}

SOURCE_BUNDLED = "bundled"  # simple definition of a bundled sense (dictionary_sense)
SOURCE_BACKEND = "backend-word"  # simple definition of a sense of a word Claude or Codex wrote (word_lookup)
SOURCE_SENTENCE = "sentence"  # the text of a cached sentence (sentence_translation.source_text)
SOURCES = (SOURCE_BUNDLED, SOURCE_BACKEND, SOURCE_SENTENCE)
PRIORITY_BASE = {SOURCE_BUNDLED: 3_000_000, SOURCE_BACKEND: 2_000_000, SOURCE_SENTENCE: 1_000_000}

STATUSES = ("pending", "done", "needs_review", "failed")
MAX_ATTEMPTS = 3  # a line that fails the checks or a call that fails is tried again by later runs until this many tries
MAX_TEXT = 500  # LineTranslationService.MAX_TEXT, counted in UTF-16 units like Java's String.length()

BATCH_SIZE = 50
DEFAULT_WORKERS = 4
SECONDS_PER_CALL = 20  # a batch of 50 short definitions at effort low; measured 14-17 s on 2026-10-24
SERVER_SOURCE = "codex"

SCHEMA = """
CREATE TABLE IF NOT EXISTS lines (
    id             INTEGER PRIMARY KEY,
    text_hash      TEXT NOT NULL,              -- sha256 hex of the normalized text, as the server computes it
    text           TEXT NOT NULL,              -- the normalized English text
    target_lang    TEXT NOT NULL,              -- vi | ja | ko | zh | fr | es | de
    prompt_version TEXT NOT NULL,              -- app.claude.line-prompt-version, for example v1
    source         TEXT NOT NULL,              -- bundled | backend-word | sentence
    priority       INTEGER NOT NULL DEFAULT 0, -- higher is written first: bundled senses, then backend words, then sentences
    status         TEXT NOT NULL DEFAULT 'pending',
    attempts       INTEGER NOT NULL DEFAULT 0, -- tries so far (a line that failed a check, or a failed call)
    translation    TEXT,
    problems       TEXT,                       -- JSON list of failed checks, or why the call failed
    model          TEXT,                       -- for example codex:gpt-5.6-terra
    pushed_at      TEXT,                       -- when push wrote it to the server
    updated_at     TEXT DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (text_hash, target_lang, prompt_version)
);
CREATE INDEX IF NOT EXISTS idx_lines_next ON lines (target_lang, status, priority DESC, id);
"""


def connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.executescript(SCHEMA)
    return connection


# ---------- the key: the same text gives the same hash as the backend ----------


# Java's Character.isWhitespace, which String.strip() uses: no-break spaces (U+00A0, U+2007, U+202F) are NOT included.
JAVA_WHITESPACE = (set(range(0x09, 0x0E)) | set(range(0x1C, 0x21)) | {0x1680} | set(range(0x2000, 0x2007))
                   | set(range(0x2008, 0x200B)) | {0x2028, 0x2029, 0x205F, 0x3000})
_JAVA_STRIP = "".join(chr(code) for code in sorted(JAVA_WHITESPACE))
# Java's regex \s (no flags) is only ASCII: space, tab, newline, vertical tab, form feed, carriage return.
_INNER_SPACE = re.compile(r"[ \t\n\x0b\f\r]+")


def normalize(text: str | None) -> str:
    """LineTranslationService.normalize: strip(), then every run of \\s (ASCII only) becomes one space. Case is kept.
    Python's str.split() would also collapse U+00A0 or U+3000, and the hash would not match the server's."""
    return _INNER_SPACE.sub(" ", (text or "").strip(_JAVA_STRIP))


def text_hash(normalized_text: str) -> str:
    """LineTranslationService.hash = CacheKeys.key(text): sha256 of the one part, as 64 hex characters."""
    return hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()


def java_length(text: str) -> int:
    """String.length() in Java: UTF-16 units (an emoji counts 2). The server accepts 1-500 of them."""
    return len(text.encode("utf-16-le")) // 2


def usable_text(text: str | None) -> str | None:
    """The normalized text, or None when the server would refuse it (empty or longer than 500)."""
    clean = normalize(text)
    return clean if clean and java_length(clean) <= MAX_TEXT else None


# ---------- the backend prompt ----------


def backend_line_prompt(path: Path = CLAUDE_SERVICE) -> str:
    """ClaudeService.LINE_PROMPT, read from the Java file, so Codex gets the very same instructions as the backend."""
    return collection.java_text_block(path.read_text(encoding="utf-8"), "LINE_PROMPT")


def backend_line_prompt_version(path: Path = APPLICATION_PROPERTIES) -> str:
    """app.claude.line-prompt-version: part of the key, so a pushed row is found by the backend."""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("app.claude.line-prompt-version="):
            return line.split("=", 1)[1].strip()
    raise ValueError("app.claude.line-prompt-version is missing")


BATCH_NOTE = """Answer from your own knowledge. Do not run commands, read files or search the web.
Return JSON like {"lines": ["...", "..."]} with exactly one line for each text, in the same order.
"""


def build_prompt(lang: str, texts: list[str], line_prompt: str) -> str:
    """One Codex message. Codex has no system prompt, so the backend prompt comes first. The message after it is
    ClaudeService.linesMessage: the language name, then every text with its id, tags escaped."""
    parts = [line_prompt, BATCH_NOTE, f"<target_language>{LANGUAGES[lang]}</target_language>", "<texts>"]
    parts += [f'<text id="{n}">{collection.escape_tags(text)}</text>' for n, text in enumerate(texts, 1)]
    parts.append("</texts>")
    return "\n".join(parts)


# ---------- the checks ----------


KANA = re.compile(r"[぀-ヿㇰ-ㇿｦ-ﾟ]")
HAN = re.compile(r"[㐀-䶿一-鿿豈-﫿]")
HANGUL = re.compile(r"[ᄀ-ᇿ㄰-㆏가-힯]")
MIN_SCRIPT_SHARE = 0.3  # of the letters, so a name or **bold** English word inside the line is allowed


def _share(pattern: re.Pattern, line: str) -> float:
    letters = [c for c in line if c.isalpha()]
    return len(pattern.findall(line)) / len(letters) if letters else 0.0


def _has_vietnamese_letter(line: str) -> bool:
    """Any letter with a tone mark or a hat or horn (ă â ê ô ơ ư), or đ. Every real Vietnamese line has one."""
    return any(c in "đĐ" or (c.isalpha() and any(unicodedata.combining(m) for m in unicodedata.normalize("NFD", c)))
               for c in line)


def line_problems(text: str, line: str | None, lang: str) -> list[str]:
    """Why a line is not good enough. An empty list means it passes."""
    if line is None or not line.strip():
        return ["empty line"]
    line = line.strip()
    problems = []
    if "\n" in line or "\r" in line:
        problems.append("more than one line")
    if "<text" in line or "</" in line:
        problems.append("contains prompt tags")
    if line.casefold() == text.casefold():
        problems.append("same as the English text")
    length = java_length(line)
    cjk = lang in ("ja", "ko", "zh")
    if length > max(40 if cjk else 60, (2 if cjk else 3) * java_length(text)):
        problems.append(f"too long ({length} characters for {java_length(text)})")
    if not cjk and length < max(1, java_length(text) // 5):
        problems.append(f"too short ({length} characters for {java_length(text)})")
    if lang == "ja":
        if HANGUL.search(line):
            problems.append("has Korean letters")
        if _share(re.compile(KANA.pattern + "|" + HAN.pattern), line) < MIN_SCRIPT_SHARE:
            problems.append("does not look like Japanese")
    elif lang == "ko":
        if KANA.search(line):
            problems.append("has Japanese letters")
        if _share(HANGUL, line) < MIN_SCRIPT_SHARE:
            problems.append("does not look like Korean")
    elif lang == "zh":
        if KANA.search(line) or HANGUL.search(line):
            problems.append("has Japanese or Korean letters")
        if _share(HAN, line) < MIN_SCRIPT_SHARE:
            problems.append("does not look like Chinese")
    else:
        if KANA.search(line) or HAN.search(line) or HANGUL.search(line):
            problems.append("has Asian letters")
        if lang == "vi" and not _has_vietnamese_letter(line):
            problems.append("does not look like Vietnamese (no tone marks)")
    return problems


# ---------- collect (Postgres, read-only) ----------


@dataclass
class Collected:
    """What collect found, per source: texts seen, rows added, rows that were there, rows already on the server."""

    texts: Counter = field(default_factory=Counter)
    too_long: int = 0  # empty or longer than 500: the server would refuse them
    added: Counter = field(default_factory=Counter)  # per language
    existing: int = 0  # already in pipeline.db
    on_server: int = 0  # already in line_translation on the server (any source): not written again


def bundled_texts(pg) -> list[str]:
    with pg.cursor() as cursor:
        cursor.execute("SELECT s.simple_definition FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id "
                       "ORDER BY w.word_rank, s.position")
        return [row[0] for row in cursor.fetchall()]


def backend_word_texts(pg) -> list[str]:
    """simpleDefinition of every sense in word_lookup.entry_json (words the backend answered, also Codex ones)."""
    texts = []
    with pg.cursor() as cursor:
        cursor.execute("SELECT entry_json FROM word_lookup ORDER BY id")
        for (entry_json,) in cursor.fetchall():
            try:
                senses = json.loads(entry_json).get("senses") or []
            except json.JSONDecodeError:
                continue
            texts += [sense.get("simpleDefinition") or "" for sense in senses if isinstance(sense, dict)]
    return texts


def sentence_texts(pg) -> list[str]:
    """The text of every cached sentence. TASK-106.6 (prompt v10) keeps the same column, so this keeps working."""
    with pg.cursor() as cursor:
        cursor.execute("SELECT source_text FROM sentence_translation ORDER BY id")
        return [row[0] for row in cursor.fetchall()]


TEXT_SOURCES = {SOURCE_BUNDLED: bundled_texts, SOURCE_BACKEND: backend_word_texts, SOURCE_SENTENCE: sentence_texts}


def server_lines(pg, prompt_version: str) -> set[tuple[str, str]]:
    """(text_hash, language) of every line the server has for this prompt version. Empty before the Flyway file V27
    ran (the table is not there yet)."""
    with pg.cursor() as cursor:
        cursor.execute("SELECT to_regclass('line_translation') IS NOT NULL")
        if not cursor.fetchone()[0]:
            return set()
        cursor.execute("SELECT text_hash, target_lang FROM line_translation WHERE prompt_version = %s",
                       (prompt_version,))
        return {(row[0], row[1]) for row in cursor.fetchall()}


def collect(connection: sqlite3.Connection, pg, prompt_version: str, sources: list[str] | None = None,
            languages: list[str] | None = None, dry_run: bool = False) -> Collected:
    """Adds one pending row per (text, language) that is not in pipeline.db and not on the server yet. The first
    source that has a text sets its priority; the same text from another source is not added twice."""
    result = Collected()
    languages = languages or list(LANGUAGES)
    on_server = server_lines(pg, prompt_version)
    seen: set[str] = set()
    for source in sources or SOURCES:
        rows = []
        for sequence, raw in enumerate(TEXT_SOURCES[source](pg)):
            text = usable_text(raw)
            if text is None:
                result.too_long += 1
                continue
            digest = text_hash(text)
            if digest in seen:
                continue
            seen.add(digest)
            result.texts[source] += 1
            for lang in languages:
                if (digest, lang) in on_server:
                    result.on_server += 1
                else:
                    rows.append((digest, text, lang, prompt_version, source, PRIORITY_BASE[source] - sequence))
        have = {(r[0], r[1]) for r in connection.execute(
            "SELECT text_hash, target_lang FROM lines WHERE prompt_version = ?", (prompt_version,))}
        new = [row for row in rows if (row[0], row[2]) not in have]
        if not dry_run:
            connection.executemany(
                "INSERT OR IGNORE INTO lines (text_hash, text, target_lang, prompt_version, source, priority) "
                "VALUES (?, ?, ?, ?, ?, ?)", new)
            connection.commit()
        result.existing += len(rows) - len(new)
        for row in new:
            result.added[row[2]] += 1
    return result


# ---------- write (Codex) ----------


LINES_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["lines"],
                "properties": {"lines": {"type": "array", "items": {"type": "string"}}}}


class Writer:
    """What `write` needs from Codex. CodexWriter is the real one; the tests use a fake."""

    name: str

    def write(self, prompt: str) -> list[str]: ...


class CodexWriter(collection.CodexWriter):
    """collection.CodexWriter (same `codex exec` call, same stop and error rules) with the lines JSON schema."""

    schema = LINES_SCHEMA
    answer_key = "lines"


@dataclass
class Result:
    row_id: int
    text: str
    status: str  # done | pending | needs_review | failed
    translation: str | None = None
    problems: list[str] = field(default_factory=list)


def _final_status(attempts: int, check_failed: bool) -> str:
    if attempts < MAX_ATTEMPTS:
        return "pending"
    return "needs_review" if check_failed else "failed"


def write_batch(rows: list[sqlite3.Row], lang: str, writer: Writer, line_prompt: str) -> tuple[list[Result], int]:
    """One Codex call for the rows (all one language). If the answer has the wrong number of lines the batch is split
    in two and asked again, down to one row; a row that still fails is marked and tried again by a later run.
    Returns the results and the number of calls."""
    texts = [row["text"] for row in rows]
    calls = 1
    try:
        lines = writer.write(build_prompt(lang, texts, line_prompt))
    except ProviderStop:
        raise
    except WriteError as error:
        return [Result(r["id"], r["text"], _final_status(r["attempts"] + 1, False), None, [str(error)[:200]])
                for r in rows], calls
    if len(lines) != len(rows):
        if len(rows) == 1:
            return [Result(rows[0]["id"], rows[0]["text"], _final_status(rows[0]["attempts"] + 1, False), None,
                           [f"{len(lines)} lines for 1 text"])], calls
        middle = len(rows) // 2
        results = []
        for half in (rows[:middle], rows[middle:]):
            more, more_calls = write_batch(half, lang, writer, line_prompt)
            results += more
            calls += more_calls
        return results, calls
    results = []
    for row, line in zip(rows, lines):
        problems = line_problems(row["text"], line, lang)
        if problems:
            results.append(Result(row["id"], row["text"], _final_status(row["attempts"] + 1, True),
                                  (line or "").strip() or None, problems))
        else:
            results.append(Result(row["id"], row["text"], "done", line.strip()))
    return results, calls


def rows_to_write(connection: sqlite3.Connection, prompt_version: str, languages: list[str] | None = None,
                  sources: list[str] | None = None, limit: int | None = None, retry_failed: bool = False
                  ) -> dict[str, list[sqlite3.Row]]:
    """The pending rows per language, most important first. `limit` is per language. retry_failed first puts the
    failed and needs_review rows back to pending with no tries."""
    languages = languages or list(LANGUAGES)
    if retry_failed:
        connection.execute("UPDATE lines SET status = 'pending', attempts = 0 WHERE status IN ('failed', 'needs_review') "
                           "AND pushed_at IS NULL AND prompt_version = ?", (prompt_version,))
        connection.commit()
    out = {}
    for lang in languages:
        query = "SELECT * FROM lines WHERE status = 'pending' AND target_lang = ? AND prompt_version = ?"
        params: list = [lang, prompt_version]
        if sources:
            query += f" AND source IN ({','.join('?' * len(sources))})"
            params += sources
        query += " ORDER BY priority DESC, id"
        if limit:
            query += " LIMIT ?"
            params.append(limit)
        rows = connection.execute(query, params).fetchall()
        if rows:
            out[lang] = rows
    return out


def save(connection: sqlite3.Connection, result: Result, model: str) -> None:
    connection.execute(
        "UPDATE lines SET status = ?, attempts = attempts + 1, translation = ?, problems = ?, model = ?, "
        "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (result.status, result.translation, json.dumps(result.problems, ensure_ascii=False) if result.problems else None,
         model, result.row_id))
    connection.commit()


@dataclass
class RunSummary:
    counts: Counter = field(default_factory=Counter)
    calls: int = 0
    stopped: str | None = None
    seconds: float = 0.0


def estimate_hours(rows: int, batch: int = BATCH_SIZE, workers: int = DEFAULT_WORKERS) -> float:
    calls = -(-rows // batch)
    return calls * SECONDS_PER_CALL / workers / 3600


def chunks(rows: list, size: int) -> list[list]:
    return [rows[i:i + size] for i in range(0, len(rows), size)]


def write_rows(connection: sqlite3.Connection, by_language: dict[str, list[sqlite3.Row]], writer: Writer, *,
               batch_size: int = BATCH_SIZE, workers: int = DEFAULT_WORKERS, line_prompt: str | None = None,
               progress: Callable[[str, list[Result]], None] | None = None) -> RunSummary:
    """Writes the rows with `workers` Codex calls at once, one language per call. Saving happens on this thread (sqlite
    is not thread-safe). Stops at the first ProviderStop and keeps everything done so far."""
    line_prompt = line_prompt or backend_line_prompt()
    summary, started = RunSummary(), time.time()
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futures = {pool.submit(write_batch, group, lang, writer, line_prompt): lang
                   for lang, rows in by_language.items() for group in chunks(rows, batch_size)}
        for future in as_completed(futures):
            if future.cancelled():
                continue
            try:
                results, calls = future.result()
            except ProviderStop as stop:
                summary.stopped = str(stop)
                for other in futures:
                    other.cancel()
                continue
            summary.calls += calls
            for result in results:
                save(connection, result, writer.name)
                summary.counts[result.status] += 1
            if progress:
                progress(futures[future], results)
    summary.seconds = time.time() - started
    return summary


# ---------- report ----------


def report_lines(connection: sqlite3.Connection) -> list[str]:
    rows = connection.execute("SELECT target_lang, status, pushed_at IS NOT NULL AS pushed, count(*) "
                              "FROM lines GROUP BY target_lang, status, pushed").fetchall()
    if not rows:
        return ["There are no lines yet. Run: uv run pipeline lines collect"]
    table: dict[str, Counter] = {}
    for lang, status, pushed, count in rows:
        table.setdefault(lang, Counter())[status] += count
        if pushed:
            table[lang]["pushed"] += count
    columns = (*STATUSES, "pushed")
    head = f"{'language':<10}" + "".join(f"{c:>14}" for c in columns) + f"{'total':>9}"
    lines = [head, "-" * len(head)]
    totals: Counter = Counter()
    for lang in [lang for lang in LANGUAGES if lang in table] + sorted(set(table) - set(LANGUAGES)):
        counts = table[lang]
        totals.update(counts)
        lines.append(f"{lang:<10}" + "".join(f"{counts[c]:>14}" for c in columns)
                     + f"{sum(counts[s] for s in STATUSES):>9}")
    lines.append("-" * len(head))
    lines.append(f"{'all':<10}" + "".join(f"{totals[c]:>14}" for c in columns)
                 + f"{sum(totals[s] for s in STATUSES):>9}")
    by_source = Counter()
    for source, count in connection.execute("SELECT source, count(*) FROM lines GROUP BY source"):
        by_source[source] = count
    lines.append("")
    lines.append("Per source: " + ", ".join(f"{s} {by_source[s]}" for s in SOURCES))
    pending = totals["pending"]
    lines.append(f"To write: {pending} lines. At {BATCH_SIZE} per Codex call, about {SECONDS_PER_CALL} s a call and "
                 f"{DEFAULT_WORKERS} calls at once: about {estimate_hours(pending):.1f} hours.")
    ready = connection.execute("SELECT count(*) FROM lines WHERE status = 'done' AND pushed_at IS NULL").fetchone()[0]
    lines.append(f"Ready to push: {ready} lines.")
    return lines


# ---------- push ----------


@dataclass
class PushPlan:
    rows: list[sqlite3.Row]
    exists: list[sqlite3.Row]  # already in line_translation (any source): left as it is
    to_insert: list[sqlite3.Row]
    stale: int = 0  # done with an older prompt version: not pushed


def push_rows(connection: sqlite3.Connection, prompt_version: str, languages: list[str] | None = None,
              limit: int | None = None) -> tuple[list[sqlite3.Row], int]:
    """Done rows not pushed yet, for the current prompt version. Also the number of done rows with an older one."""
    query = ("SELECT * FROM lines WHERE status = 'done' AND pushed_at IS NULL AND translation IS NOT NULL "
             "AND prompt_version = ?")
    params: list = [prompt_version]
    if languages:
        query += f" AND target_lang IN ({','.join('?' * len(languages))})"
        params += languages
    rows = connection.execute(query + " ORDER BY target_lang, priority DESC, id", params).fetchall()
    stale = connection.execute("SELECT count(*) FROM lines WHERE status = 'done' AND pushed_at IS NULL "
                               "AND prompt_version != ?", (prompt_version,)).fetchone()[0]
    return (rows[:limit] if limit else rows), stale


def plan_push(pg, rows: list[sqlite3.Row], prompt_version: str, stale: int = 0) -> PushPlan:
    hashes = list({row["text_hash"] for row in rows})
    found: set[tuple[str, str]] = set()
    with pg.cursor() as cursor:
        for start in range(0, len(hashes), 5000):
            cursor.execute("SELECT text_hash, target_lang FROM line_translation WHERE prompt_version = %s "
                           "AND text_hash = ANY(%s)", (prompt_version, hashes[start:start + 5000]))
            found.update((row[0], row[1]) for row in cursor.fetchall())
    plan = PushPlan(rows, [], [], stale)
    for row in rows:
        (plan.exists if (row["text_hash"], row["target_lang"]) in found else plan.to_insert).append(row)
    return plan


def push(connection: sqlite3.Connection, pg, plan: PushPlan, prompt_version: str) -> int:
    """Writes the planned rows into line_translation in one transaction. ON CONFLICT DO NOTHING: a row that appeared
    meanwhile (a user asked Claude) wins. Marks the rows pushed_at, also the ones that were already there."""
    with pg.cursor() as cursor:
        cursor.executemany(
            "INSERT INTO line_translation (text_hash, text, target_lang, prompt_version, translation, source, "
            "created_at) VALUES (%s, %s, %s, %s, %s, %s, now()) "
            "ON CONFLICT (text_hash, target_lang, prompt_version) DO NOTHING",
            [(row["text_hash"], row["text"], row["target_lang"], prompt_version, row["translation"], SERVER_SOURCE)
             for row in plan.to_insert])
        inserted = cursor.rowcount  # psycopg 3: the sum over all rows of executemany
    pg.commit()
    connection.executemany("UPDATE lines SET pushed_at = CURRENT_TIMESTAMP WHERE id = ?",
                           [(row["id"],) for row in plan.to_insert + plan.exists])
    connection.commit()
    return inserted
