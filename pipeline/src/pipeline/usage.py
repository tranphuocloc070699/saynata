"""The usage pass (TASK-24): adds `usage` to entries that are already done.

Definitions, examples and Vietnamese are NOT touched. One LLM call per word, no retries:
usage items that fail the checks are dropped, so a word may end up with fewer than 3 (or none).
A word is finished when every sense has a `usage` key, so a stopped run resumes where it stopped.
"""

import json
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


def clean_usage(items: list[Usage], vocabulary: set[str]) -> tuple[list[dict], list[str]]:
    """Keeps up to 3 good, different items. Returns (items to save, reasons for the dropped ones)."""
    kept: list[dict] = []
    dropped: list[str] = []
    seen: set[str] = set()
    for item in items:
        problems = usage_problems(item, vocabulary)
        pattern_key = item.pattern.strip().lower()
        if pattern_key in seen:
            problems.append(f"'{item.pattern}' is a duplicate")
        if len(kept) >= MAX_USAGE_PER_SENSE:
            problems.append(f"'{item.pattern}' is over the limit of {MAX_USAGE_PER_SENSE}")
        if problems:
            dropped.extend(problems)
            continue
        seen.add(pattern_key)
        kept.append({"kind": item.kind.value, "pattern": item.pattern.strip(), "example": item.example.strip()})
    return kept, dropped


def add_usage(entry: dict, result: UsageResult, vocabulary: set[str]) -> tuple[dict, list[str]]:
    """Returns a copy of the entry with `usage` on every sense. Everything else stays as it was."""
    by_number = {s.sense_number: s.usage for s in result.senses}
    new_entry = {**entry, "senses": []}
    dropped: list[str] = []
    for number, sense in enumerate(entry["senses"], start=1):
        usage, sense_dropped = clean_usage(by_number.get(number, []), vocabulary)
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
