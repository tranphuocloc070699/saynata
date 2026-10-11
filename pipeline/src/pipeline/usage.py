"""The usage pass (TASK-24): adds `usage` to entries that are already done.

Definitions, examples and Vietnamese are NOT touched. One LLM call per word, no retries:
usage items that fail the checks are dropped, so a word may end up with fewer than 3 (or none).
A word is finished when every sense has a `usage` key, so a stopped run resumes where it stopped.
"""

import json
import re
import sqlite3
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed

from pydantic import BaseModel, Field

from pipeline import store
from pipeline.models import MAX_USAGE_PER_SENSE, Usage
from pipeline.validate import usage_problems


class SenseUsage(BaseModel):
    sense_number: int = Field(description="Which sense this is for: 1 for the first sense, 2 for the second, ...")
    usage: list[Usage]


class UsageResult(BaseModel):
    senses: list[SenseUsage]


# fetch(lemma, entry) -> UsageResult. The real one calls Claude; tests pass a fake.
Fetch = Callable[[str, dict], UsageResult]


def needs_usage(entry: dict) -> bool:
    return any("usage" not in sense for sense in entry["senses"])


def build_usage_prompt(lemma: str, entry: dict) -> str:
    senses = [
        {"sense_number": number, "pos": s["pos"], "definition": s["simple_definition"], "examples": s["examples"]}
        for number, s in enumerate(entry["senses"], start=1)
    ]
    return f"Headword: {lemma}\nSenses:\n{json.dumps(senses, ensure_ascii=False, indent=1)}\n"


def fetch_from_claude(lemma: str, entry: dict) -> UsageResult:
    """One real Claude call. Raises RuntimeError when Claude refuses or the reply is cut off."""
    from pipeline.generate import USAGE_RULES, _client, _wait_for_rate_limit, model_name

    system = (
        "You add 'how to use it' items to a learner's dictionary entry for Vietnamese students of English.\n"
        "For each sense listed, return its usage items. Do not change the senses.\n\n"
        + USAGE_RULES
    )
    _wait_for_rate_limit()
    response = _client().messages.parse(
        model=model_name(),
        max_tokens=8000,
        system=system,
        messages=[{"role": "user", "content": build_usage_prompt(lemma, entry)}],
        output_format=UsageResult,
        output_config={"effort": "low"},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    if response.parsed_output is None:
        raise RuntimeError(f"No usage returned (stop_reason={response.stop_reason})")
    return response.parsed_output


# Placeholders become tokens, so "depend on + someone" and "depend on + something" stay different.
_TOKEN = re.compile(r"\+\s*(noun|verb|-ing|adjective|clause)|[a-z']+")
_PLACEHOLDER_TOKENS = {
    "someone": "SOMEONE", "somebody": "SOMEONE", "sb": "SOMEONE",
    "something": "SOMETHING", "sth": "SOMETHING", "noun": "SOMETHING",
    "verb": "VERB", "-ing": "ING", "adjective": "ADJECTIVE", "clause": "CLAUSE",
}


def pattern_words(pattern: str) -> tuple[str, ...]:
    """The tokens of a pattern, for finding duplicates. Punctuation and case are ignored.

    "depend on + something" -> ("depend", "on", "SOMETHING"). "It depends on + noun." matches it
    (contained). "depend on + someone" is a different pattern. A trailing "s" is cut from plain words
    so "depends" and "depend" match.
    """
    tokens: list[str] = []
    for match in _TOKEN.finditer(pattern.lower()):
        word = match.group(1) or match.group(0)
        if word in _PLACEHOLDER_TOKENS:
            tokens.append(_PLACEHOLDER_TOKENS[word])
        else:
            tokens.append(word.rstrip("s"))
    return tuple(tokens)


def _contains(longer: tuple[str, ...], shorter: tuple[str, ...]) -> bool:
    size = len(shorter)
    return any(longer[i:i + size] == shorter for i in range(len(longer) - size + 1))


def is_duplicate(words: tuple[str, ...], kept_words: list[tuple[str, ...]]) -> bool:
    """True when the pattern equals, or sits inside, or contains, a pattern already kept.

    A single token ("get") is too short to count as "contained", or it would hide "get a job".
    """
    for other in kept_words:
        if words == other:
            return True
        if len(words) >= 2 and _contains(other, words):
            return True
        if len(other) >= 2 and _contains(words, other):
            return True
    return False


def clean_usage(
    items: list[Usage], vocabulary: set[str], kept_words: list[tuple[str, ...]] | None = None,
) -> tuple[list[dict], list[str]]:
    """Keeps up to 3 good, different items. Returns (items to save, reasons for the dropped ones).

    `kept_words` holds the patterns already kept for the other senses of the same word, so a repeat
    in another sense is dropped too. New kept patterns are added to it.
    """
    kept: list[dict] = []
    dropped: list[str] = []
    if kept_words is None:
        kept_words = []
    for item in items:
        problems = usage_problems(item, vocabulary)
        words = pattern_words(item.pattern)
        if words and is_duplicate(words, kept_words):
            problems.append(f"'{item.pattern}' is a duplicate")
        if len(kept) >= MAX_USAGE_PER_SENSE:
            problems.append(f"'{item.pattern}' is over the limit of {MAX_USAGE_PER_SENSE}")
        if problems:
            dropped.extend(problems)
            continue
        kept_words.append(words)
        kept.append({"kind": item.kind.value, "pattern": item.pattern.strip(), "example": item.example.strip()})
    return kept, dropped


def add_usage(entry: dict, result: UsageResult, vocabulary: set[str]) -> tuple[dict, list[str]]:
    """Returns a copy of the entry with `usage` on every sense. Everything else stays as it was."""
    by_number = {s.sense_number: s.usage for s in result.senses}
    new_entry = {**entry, "senses": []}
    dropped: list[str] = []
    kept_words: list[tuple[str, ...]] = []  # shared by all senses: no repeats across senses
    for number, sense in enumerate(entry["senses"], start=1):
        usage, sense_dropped = clean_usage(by_number.get(number, []), vocabulary, kept_words)
        dropped.extend(f"sense {number}: {reason}" for reason in sense_dropped)
        # collocations/synonyms are gone from the model (decision-2), so leave them out of the saved entry.
        rest = {k: v for k, v in sense.items() if k not in ("collocations", "synonyms")}
        new_entry["senses"].append({**rest, "usage": usage})
    return new_entry, dropped


def _usage_for_word(lemma: str, entry: dict, vocabulary: set[str], fetch: Fetch) -> dict:
    """Runs in a worker thread: one call, no retries, no DB access."""
    try:
        new_entry, dropped = add_usage(entry, fetch(lemma, entry), vocabulary)
        return {"entry": new_entry, "dropped": dropped}
    except Exception as error:  # leave the row as it is; the next run picks it up again
        return {"error": repr(error)}


def run_pass(
    db: sqlite3.Connection, vocabulary: set[str], fetch: Fetch, *,
    limit: int, workers: int, words: list[str] | None = None, dry_run: bool = False,
) -> dict[str, int]:
    """Adds usage to done entries that have none yet. Returns counts: saved, failed, items, dropped."""
    rows = db.execute("SELECT lemma, entry FROM words WHERE status = 'done' ORDER BY rank").fetchall()
    todo = [(r["lemma"], json.loads(r["entry"])) for r in rows]
    todo = [(lemma, entry) for lemma, entry in todo if needs_usage(entry) and (not words or lemma in words)][:limit]

    counts = {"saved": 0, "failed": 0, "items": 0, "dropped": 0}
    # Worker threads wait on the network in parallel. Only this thread writes to SQLite.
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_usage_for_word, lemma, entry, vocabulary, fetch): lemma for lemma, entry in todo}
        for number, future in enumerate(as_completed(futures), start=1):
            lemma, result = futures[future], future.result()
            if "error" in result:
                counts["failed"] += 1
                print(f"[{number}/{len(todo)}] {lemma}: failed, {result['error']}", flush=True)
                continue
            items = sum(len(s["usage"]) for s in result["entry"]["senses"])
            counts["items"] += items
            counts["dropped"] += len(result["dropped"])
            if dry_run:
                usage = [s["usage"] for s in result["entry"]["senses"]]
                print(f"[{number}/{len(todo)}] {lemma}:\n{json.dumps(usage, ensure_ascii=False, indent=1)}", flush=True)
            else:
                store.save_entry(db, lemma, json.dumps(result["entry"], ensure_ascii=False))
                counts["saved"] += 1
                print(f"[{number}/{len(todo)}] {lemma}: {items} usage items", flush=True)
            for reason in result["dropped"]:
                print(f"    dropped: {reason}")
    return counts
