"""Prefill queue (TASK-83.2): the words and phrases learners will probably type, queued so the backend can answer
them in advance.

This module makes NO paid calls. It reads word lists and writes rows into the backend table `prefill_item`
(Flyway, made by TASK-83.1). The backend night job (TASK-83.1) later asks Claude for each pending row, with the same
prompt, cache key and checks as a real user, and sets the row to done, skipped or failed.

Two sources, both safe to run again (a row that is already queued is skipped, see `insert_items`):
- words: SCOWL-70 (the backend word list) minus the bundled dictionary (NGSL lemmas and forms, Postgres
  `dictionary_word` and `dictionary_form`) minus the kept topic words (TASK-61, they will be bundled).
  Forms are reduced to lemmas with lemminflect, and wordfreq keeps the common ones.
- phrases: multiword lemmas from Open English WordNet 2025 (phrasal verbs, idioms), ranked by wordfreq.
"""

import re
import sqlite3
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import psycopg

WORDS_DIR = Path(__file__).resolve().parents[3] / "backend/src/main/resources/words"

# What the backend job paid per answer on 2026-10-16 (production claude_usage, Claude Haiku 5.5).
COST_PER_WORD = 0.0007
COST_PER_SENTENCE = 0.0002

SOURCE_WORDS = "scowl-70"
SOURCE_PHRASES = "oewn-2025"
PHRASE_LANG = "vi"  # phrases are cached as sentences for the Vietnamese target language

# Default minimum wordfreq Zipf. Measured on 2026-10-16: 3.5 gives about 6.3 thousand word lemmas; 4.0 gives
# about 2.7 thousand phrases (3.5 would give 3.2 thousand, 3.0 gives 3.6 thousand).
DEFAULT_WORD_ZIPF = 3.5
DEFAULT_PHRASE_ZIPF = 4.0

# Night job defaults (backend application.properties): $0.50 cap per run, 3 s wait after each new word
# (PREFILL_DELAY), and about 4 s for each Claude call (measured in production).
NIGHT_CAP_USD = 0.50
SECONDS_PER_WORD = 3 + 4

# Only phrasal verbs and idioms. WordNet's noun phrases are mostly compound nouns and species names ("rat cheese",
# "common starling"): 49 thousand of them, and nearly none are something a learner types.
PHRASE_POS = {"v", "a", "s", "r"}  # verb, adjective (also "s", adjective satellite), adverb
# WordNet writes "turn one's stomach" and "look after somebody": nobody types these, so they are skipped.
PLACEHOLDERS = {"one's", "oneself", "someone", "somebody", "something"}

# Never queued: rude words (the topic list already has a similar set) and the ones it misses.
BLOCKED = {"ass", "crap", "cock", "dick", "pussy", "rape", "tit", "tits", "bitch", "piss", "fag", "boob", "boobs",
           "sperm", "shit", "fuck", "fucking", "fucked", "fucker", "cunt", "bastard", "whore", "slut", "nigger",
           "nigga", "asshole", "bullshit", "dildo", "porn", "cum", "wank", "twat", "prick", "motherfucker"}

# A phrase made only of these ("to that", "in for", "be on") is not worth an answer of its own.
FUNCTION_WORDS = {"a", "an", "the", "to", "in", "on", "at", "of", "for", "with", "by", "from", "up", "down", "out",
                  "off", "over", "as", "and", "or", "it", "its", "that", "this", "these", "those", "be", "is", "are",
                  "was", "have", "has", "had", "do", "does", "so", "not", "no", "all", "one", "there", "then",
                  "than", "but", "if", "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them"}

PHRASE_PATTERN = re.compile(r"[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){1,3}")  # 2 to 4 words


@dataclass(frozen=True)
class Item:
    kind: str  # "word" or "sentence"
    text: str
    target_lang: str
    source: str
    priority: int  # higher is answered first


def priority_for(zipf: float) -> int:
    return round(zipf * 100)


# ---------- word lists ----------


def read_word_list(name: str, directory: Path = WORDS_DIR) -> set[str]:
    """One word per line, lowercase. Lines that start with # are comments."""
    words = set()
    for line in (directory / name).read_text(encoding="utf-8").splitlines():
        line = line.strip().lower()
        if line and not line.startswith("#"):
            words.add(line)
    return words


def bundled_from_files(directory: Path = WORDS_DIR) -> set[str]:
    """NGSL lemmas and forms, plus the extra words the backend already treats as simple."""
    return read_word_list("ngsl.txt", directory) | read_word_list("simple-extra.txt", directory)


def bundled_from_postgres(pg: psycopg.Connection) -> set[str]:
    """Every lemma and form in the server dictionary. More exact than ngsl.txt: it also has the topic words that
    were bundled after ngsl.txt was made."""
    with pg.cursor() as cursor:
        cursor.execute("SELECT lemma FROM dictionary_word UNION SELECT form FROM dictionary_form")
        return {row[0].lower() for row in cursor.fetchall()}


def kept_topic_words(pipeline_db: sqlite3.Connection | None) -> set[str]:
    """Topic words the judge kept (TASK-61): they will be bundled, so they must not be queued."""
    if pipeline_db is None:
        return set()
    rows = pipeline_db.execute("SELECT lemma FROM topic_words WHERE status = 'keep'")
    return {row[0].lower() for row in rows}


# ---------- words ----------


def lemmas_of(form: str) -> set[str]:
    """Every lemma lemminflect knows for this form ("running" -> run; "leaves" -> leaf, leave)."""
    from lemminflect import getAllLemmas

    found = {lemma for lemmas in getAllLemmas(form).values() for lemma in lemmas}
    return found or {form}


def is_candidate_word(form: str) -> bool:
    return form.isascii() and form.isalpha() and form.islower() and len(form) > 2


def collect_words(
    scowl: set[str],
    excluded: set[str],
    min_zipf: float = DEFAULT_WORD_ZIPF,
    zipf_of: Callable[[str], float] | None = None,
    lemmas_for: Callable[[str], set[str]] = lemmas_of,
    above_zero: bool = False,
) -> list[Item]:
    """Common SCOWL-70 words that are not bundled, as lemmas, most frequent first.

    `excluded` holds the bundled forms and lemmas and the kept topic words. A form is dropped when it or any of its
    lemmas is excluded ("apples" is dropped because "apple" is bundled). Only lemmas still on SCOWL-70 are kept,
    because the backend rejects other words. A lemma gets the highest Zipf of the forms that lead to it.

    `above_zero` (TASK-83.7, `--all-wordfreq`) keeps every form wordfreq has seen (Zipf > 0) and ignores `min_zipf`.
    A plain `min_zipf` of 0 would also let in words wordfreq never saw (Zipf exactly 0).
    """
    if zipf_of is None:
        from wordfreq import zipf_frequency

        def zipf_of(word: str) -> float:
            return zipf_frequency(word, "en")

    best: dict[str, float] = {}
    for form in scowl:
        if not is_candidate_word(form) or form in excluded or form in BLOCKED:
            continue
        zipf = zipf_of(form)
        if zipf <= 0 if above_zero else zipf < min_zipf:
            continue
        lemmas = lemmas_for(form)
        if lemmas & excluded:
            continue
        for lemma in lemmas:
            if lemma in scowl and is_candidate_word(lemma) and lemma not in BLOCKED:
                best[lemma] = max(best.get(lemma, 0.0), zipf)
    items = [Item("word", lemma, "", SOURCE_WORDS, priority_for(zipf)) for lemma, zipf in best.items()]
    return sorted(items, key=lambda item: (-item.priority, item.text))


# ---------- phrases ----------


def normalize_phrase(lemma: str) -> str | None:
    """Lowercase, single spaces. None when it is not a plain 2-4 word phrase (proper nouns, digits, symbols)."""
    if lemma != lemma.lower():  # a capital letter means a proper noun in WordNet ("New York")
        return None
    text = " ".join(lemma.split())
    words = text.split()
    if not PHRASE_PATTERN.fullmatch(text) or any(word in BLOCKED or word in PLACEHOLDERS for word in words):
        return None
    if all(word in FUNCTION_WORDS for word in words):
        return None
    return text


def collect_phrases(
    lemmas: dict[str, set[str]],
    min_zipf: float = DEFAULT_PHRASE_ZIPF,
    zipf_of: Callable[[str], float] | None = None,
) -> list[Item]:
    """Multiword WordNet lemmas, ranked by the wordfreq Zipf of the whole phrase.

    `lemmas` maps a lemma to its parts of speech in WordNet. Phrases that are only nouns are skipped (PHRASE_POS).
    wordfreq cannot count a phrase, it combines the words, so a high Zipf mostly means "made of common words".
    """
    if zipf_of is None:
        from wordfreq import zipf_frequency

        def zipf_of(phrase: str) -> float:
            return zipf_frequency(phrase, "en")

    best: dict[str, float] = {}
    for lemma, parts_of_speech in lemmas.items():
        text = normalize_phrase(lemma)
        if text is None or text in best or not parts_of_speech & PHRASE_POS:
            continue
        zipf = zipf_of(text)
        if zipf >= min_zipf:
            best[text] = zipf
    items = [Item("sentence", text, PHRASE_LANG, SOURCE_PHRASES, priority_for(zipf)) for text, zipf in best.items()]
    return sorted(items, key=lambda item: (-item.priority, item.text))


def load_wordnet():
    """Open English WordNet 2025, the same copy the topic words use (downloaded once, 11 MB, then several minutes
    to add it to the local wn database)."""
    from pipeline import topics

    topics.WN_DIR.mkdir(parents=True, exist_ok=True)  # wn makes data/raw/wn but not data/raw
    return topics.ensure_wordnet()


def wordnet_lemmas(wordnet) -> dict[str, set[str]]:
    """Every WordNet lemma that has a space in it (the multiword ones), with its parts of speech."""
    found: dict[str, set[str]] = {}
    for word in wordnet.words():
        if " " in word.lemma():
            found.setdefault(word.lemma(), set()).add(word.pos)
    return found


# ---------- the queue ----------


def queue_exists(pg: psycopg.Connection) -> bool:
    """False until the backend has made the table (Flyway, TASK-83.1)."""
    with pg.cursor() as cursor:
        cursor.execute("SELECT to_regclass('prefill_item') IS NOT NULL")
        return cursor.fetchone()[0]


def queued_keys(pg: psycopg.Connection) -> set[tuple[str, str, str]]:
    with pg.cursor() as cursor:
        cursor.execute("SELECT kind, text, target_lang FROM prefill_item")
        return {(kind, text, lang) for kind, text, lang in cursor.fetchall()}


def insert_items(pg: psycopg.Connection, items: list[Item]) -> int:
    """Queues the items that are not queued yet and returns how many were new. Never changes a queued row,
    so running collect again is safe (the unique key is kind + text + target_lang)."""
    with pg.cursor() as cursor:
        cursor.execute("SELECT count(*) FROM prefill_item")
        before = cursor.fetchone()[0]
        cursor.executemany(
            "INSERT INTO prefill_item (kind, text, target_lang, source, priority) VALUES (%s, %s, %s, %s, %s) "
            "ON CONFLICT (kind, text, target_lang) DO NOTHING",
            [(i.kind, i.text, i.target_lang, i.source, i.priority) for i in items],
        )
        cursor.execute("SELECT count(*) FROM prefill_item")
        after = cursor.fetchone()[0]
    pg.commit()
    return after - before


# ---------- report ----------


def night_estimate(words: int, sentences: int = 0) -> tuple[float, int, float]:
    """(cost in dollars, answers per night, nights) at the default cap of the backend night job.
    Sentences cost less, so the count of answers per night uses the average price of this batch."""
    cost = estimated_cost(words, sentences)
    total = words + sentences
    per_answer = cost / total if total else COST_PER_WORD
    per_night = max(1, int(NIGHT_CAP_USD / per_answer))
    return cost, per_night, total / per_night


def night_lines(words: int, sentences: int = 0) -> list[str]:
    cost, per_night, nights = night_estimate(words, sentences)
    minutes = per_night * SECONDS_PER_WORD / 60
    return [f"Estimated Claude cost: about ${cost:,.2f} (${COST_PER_WORD} a word, ${COST_PER_SENTENCE} a sentence, "
            f"Claude Haiku 5.5). No call is made now.",
            f"Night job at the ${NIGHT_CAP_USD:.2f} cap: about {per_night} answers a night, one run about "
            f"{minutes:.0f} minutes ({SECONDS_PER_WORD} s each), so about {nights:.0f} nights for all of them."]


def estimated_cost(pending_words: int, pending_sentences: int) -> float:
    return pending_words * COST_PER_WORD + pending_sentences * COST_PER_SENTENCE


def report_lines(pg: psycopg.Connection) -> list[str]:
    with pg.cursor() as cursor:
        cursor.execute(
            "SELECT kind, status, source, count(*) FROM prefill_item GROUP BY kind, status, source "
            "ORDER BY kind, status, source")
        rows = cursor.fetchall()
    if not rows:
        return ["The prefill queue is empty. Run: uv run pipeline prefill collect --source words"]
    lines = [f"{'kind':<9} {'status':<8} {'source':<12} {'count':>6}"]
    lines += [f"{kind:<9} {status:<8} {source or '-':<12} {count:>6}" for kind, status, source, count in rows]
    pending = Counter()
    for kind, status, _, count in rows:
        if status == "pending":
            pending[kind] += count
    cost = estimated_cost(pending["word"], pending["sentence"])
    lines.append("")
    lines.append(f"Pending: {pending['word']} words x ${COST_PER_WORD} + {pending['sentence']} sentences "
                 f"x ${COST_PER_SENTENCE} = about ${cost:.2f} with Claude Haiku 5.5")
    return lines
