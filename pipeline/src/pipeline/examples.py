"""The examples pass (TASK-35): gives every sense 3 examples, ordered easy, medium, harder.

Definitions, Vietnamese and usage are NOT touched. One LLM call per word, no retries.
Examples that fail the checks are dropped, so a sense may end up with fewer than 3. When none of the
new examples is good, the sense keeps its old ones (easy, medium), so it never ends up with nothing.
A word is finished when every example is an object {level, text}, so a stopped run resumes where it stopped.
"""

import json
import sqlite3
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed

from pydantic import BaseModel, Field

from pipeline import store
from pipeline.forms import word_forms
from pipeline.models import EXAMPLE_LEVELS, Example
from pipeline.validate import WORD_RE, _plain_word, example_problems


class SenseExamples(BaseModel):
    sense_number: int = Field(description="Which sense this is for: 1 for the first sense, 2 for the second, ...")
    examples: list[Example]


class ExamplesResult(BaseModel):
    senses: list[SenseExamples]


# fetch(lemma, entry) -> ExamplesResult. The real one calls Claude; tests pass a fake.
Fetch = Callable[[str, dict], ExamplesResult]


def needs_examples(entry: dict) -> bool:
    """True while any example is still an old plain string."""
    return any(not isinstance(example, dict) for sense in entry["senses"] for example in sense["examples"])


def old_example_texts(sense: dict) -> list[str]:
    return [e if isinstance(e, str) else e["text"] for e in sense["examples"]]


def build_examples_prompt(lemma: str, entry: dict) -> str:
    senses = [
        {
            "sense_number": number, "pos": s["pos"], "definition": s["simple_definition"],
            "old_examples": old_example_texts(s),  # the model may reuse a good one
        }
        for number, s in enumerate(entry["senses"], start=1)
    ]
    return f"Headword: {lemma}\nSenses:\n{json.dumps(senses, ensure_ascii=False, indent=1)}\n"


def fetch_from_claude(lemma: str, entry: dict) -> ExamplesResult:
    """One real Claude call. Raises RuntimeError when Claude refuses or the reply is cut off."""
    from pipeline.generate import EXAMPLE_RULES, _client, _wait_for_rate_limit, model_name

    system = (
        "You write example sentences for a learner's dictionary entry for Vietnamese students of English.\n"
        "For each sense listed, return its 3 examples. Do not change the senses.\n"
        "Each sense has old_examples. Reuse one of them when it is good (give it the right level), "
        "and write new ones for the rest.\n\n"
        + EXAMPLE_RULES
    )
    _wait_for_rate_limit()
    response = _client().messages.parse(
        model=model_name(),
        max_tokens=8000,
        system=system,
        messages=[{"role": "user", "content": build_examples_prompt(lemma, entry)}],
        output_format=ExamplesResult,
        output_config={"effort": "low"},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    if response.parsed_output is None:
        raise RuntimeError(f"No examples returned (stop_reason={response.stop_reason})")
    return response.parsed_output


# Words that do not make two sentences "the same": grammar words and very common helpers.
STOP_WORDS = {
    "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them", "my", "your", "his", "its",
    "our", "their", "a", "an", "the", "to", "of", "in", "on", "at", "for", "with", "and", "or", "but", "so",
    "is", "am", "are", "was", "were", "be", "been", "do", "does", "did", "have", "has", "had", "will", "would",
    "can", "could", "this", "that", "there", "not", "no", "if", "when", "very", "just",
}
MAX_SHARED_WORDS = 0.6  # more than 60% shared with another example of the sense: drop it


def content_words(text: str, headword_forms: set[str]) -> set[str]:
    """The words that carry the meaning of a sentence: no headword (or its forms) and no stop words."""
    words = {_plain_word(w) for w in WORD_RE.findall(text.replace("’", "'"))}
    return words - headword_forms - STOP_WORDS


def shared_share(first: set[str], second: set[str]) -> float:
    """How much of the shorter sentence's content words the other sentence also has (0 to 1)."""
    smaller = min(len(first), len(second))
    return len(first & second) / smaller if smaller else 0.0


def clean_examples(
    items: list[Example], headword: str, headword_forms: set[str], vocabulary: set[str],
) -> tuple[list[dict], list[str]]:
    """Keeps at most one good example per level, in the order easy, medium, harder.

    Returns (examples to save, reasons for the dropped ones).
    """
    dropped: list[str] = []
    best: dict[str, dict] = {}
    for item in items:
        problems = example_problems(item, headword, headword_forms, vocabulary)
        if item.level.value in best:
            problems.append(f"a second {item.level.value} example '{item.text}'")
        if problems:
            dropped.extend(problems)
        else:
            best[item.level.value] = {"level": item.level.value, "text": item.text.strip()}
    kept: list[dict] = []
    kept_words: list[set[str]] = []
    for level in EXAMPLE_LEVELS:  # easy is kept first; a later one that repeats an earlier one is dropped
        if level.value not in best:
            continue
        words = content_words(best[level.value]["text"], headword_forms | {headword.lower()})
        if any(shared_share(words, other) > MAX_SHARED_WORDS for other in kept_words):
            dropped.append(f"{level.value} example '{best[level.value]['text']}' repeats another example of the sense")
            continue
        kept.append(best[level.value])
        kept_words.append(words)
    return kept, dropped


def old_examples_as_levels(sense: dict) -> list[dict]:
    """The sense's current examples as {level, text}. Old plain strings become easy, medium."""
    return [
        e if isinstance(e, dict) else {"level": EXAMPLE_LEVELS[min(index, 1)].value, "text": e}
        for index, e in enumerate(sense["examples"])
    ]


def add_examples(
    lemma: str, entry: dict, result: ExamplesResult, vocabulary: set[str], known_forms: list[str],
) -> tuple[dict, list[str]]:
    """Returns a copy of the entry with new `examples` on every sense. Everything else stays as it was."""
    by_number = {s.sense_number: s.examples for s in result.senses}
    headword_forms = word_forms(lemma) | {form.lower() for form in known_forms}
    new_entry = {**entry, "senses": []}
    dropped: list[str] = []
    for number, sense in enumerate(entry["senses"], start=1):
        examples, sense_dropped = clean_examples(by_number.get(number, []), lemma, headword_forms, vocabulary)
        dropped.extend(f"sense {number}: {reason}" for reason in sense_dropped)
        if not examples:
            dropped.append(f"sense {number}: no good example, kept the old ones")
            examples = old_examples_as_levels(sense)
        new_entry["senses"].append({**sense, "examples": examples})
    return new_entry, dropped


def _examples_for_word(
    lemma: str, entry: dict, known_forms: list[str], vocabulary: set[str], fetch: Fetch,
) -> dict:
    """Runs in a worker thread: one call, no retries, no DB access."""
    try:
        new_entry, dropped = add_examples(lemma, entry, fetch(lemma, entry), vocabulary, known_forms)
        return {"entry": new_entry, "dropped": dropped}
    except Exception as error:  # leave the row as it is; the next run picks it up again
        return {"error": repr(error)}


def run_pass(
    db: sqlite3.Connection, vocabulary: set[str], fetch: Fetch, *,
    limit: int, workers: int, words: list[str] | None = None, dry_run: bool = False,
) -> dict[str, int]:
    """Adds levelled examples to done entries that still have plain ones. Returns counts: saved, failed, items, dropped."""
    rows = db.execute("SELECT lemma, forms, entry FROM words WHERE status = 'done' ORDER BY rank").fetchall()
    todo = [(r["lemma"], json.loads(r["forms"]), json.loads(r["entry"])) for r in rows]
    todo = [t for t in todo if needs_examples(t[2]) and (not words or t[0] in words)][:limit]

    counts = {"saved": 0, "failed": 0, "items": 0, "dropped": 0}
    # Worker threads wait on the network in parallel. Only this thread writes to SQLite.
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(_examples_for_word, lemma, entry, forms, vocabulary, fetch): lemma
            for lemma, forms, entry in todo
        }
        for number, future in enumerate(as_completed(futures), start=1):
            lemma, result = futures[future], future.result()
            if "error" in result:
                counts["failed"] += 1
                print(f"[{number}/{len(todo)}] {lemma}: failed, {result['error']}", flush=True)
                continue
            items = sum(len(s["examples"]) for s in result["entry"]["senses"])
            counts["items"] += items
            counts["dropped"] += len(result["dropped"])
            if dry_run:
                shown = [s["examples"] for s in result["entry"]["senses"]]
                print(f"[{number}/{len(todo)}] {lemma}:\n{json.dumps(shown, ensure_ascii=False, indent=1)}", flush=True)
            else:
                store.save_entry(db, lemma, json.dumps(result["entry"], ensure_ascii=False))
                counts["saved"] += 1
                print(f"[{number}/{len(todo)}] {lemma}: {items} examples", flush=True)
            for reason in result["dropped"]:
                print(f"    dropped: {reason}")
    return counts
