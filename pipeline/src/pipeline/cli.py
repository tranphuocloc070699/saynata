"""Command line entry point.

    uv run pipeline preview reluctant    # see what we send to the LLM (no API key needed)
    uv run pipeline try reluctant        # call the LLM for one word, print result, save nothing
    uv run pipeline generate --limit 200 # generate the 200 most common NGSL words
    uv run pipeline generate --retry-review  # also redo words that failed the checks
    uv run pipeline generate --workers 8     # LLM calls in parallel (default 8)
    uv run pipeline usage --dry-run --words get make   # try the usage pass on 2 done words, save nothing
    uv run pipeline examples --dry-run --words run apple make   # try the examples pass on 3 done words
    uv run pipeline examples             # give every done word 3 levelled examples (about $0.002 a word)
    uv run pipeline usage                # add usage to every done word that has none (about $8 for 2,578 words)
    uv run pipeline revalidate         # re-check needs_review words after the checks change (no LLM)
    uv run pipeline report               # counts + entries that need review
    uv run pipeline export               # build data/out/dictionary.sqlite
"""

import argparse
import json
from concurrent.futures import ThreadPoolExecutor, as_completed

from dotenv import load_dotenv
from pydantic import ValidationError

from pipeline import export, sources, store
from pipeline.senses import build_source
from pipeline.validate import build_vocabulary, find_problems

MAX_ATTEMPTS = 3


def preview(word: str) -> None:
    from pipeline.generate import build_prompt

    source = build_source(sources.load_kaikki(word))
    print(f"IPA: {source.ipa}\n")
    print(build_prompt(word, source, feedback=[]))


def try_word(word: str) -> None:
    from pipeline.generate import generate_entry, model_name

    source = build_source(sources.load_kaikki(word))
    entry = generate_entry(word, source, feedback=[])
    print(f"Model: {model_name()}\n")
    print(entry.model_dump_json(indent=2))
    problems = find_problems(word, entry, build_vocabulary(sources.load_ngsl()))
    print("\nChecks:", "passed" if not problems else "\n- " + "\n- ".join(problems))


def _generate_word(lemma: str, rank: int, vocabulary: set[str]) -> dict:
    """Runs in a worker thread: network and LLM only, no DB access. Returns the fields for save_result."""
    from pipeline.generate import generate_entry

    try:
        source = build_source(sources.load_kaikki(lemma))
        feedback: list[str] = []
        entry = None
        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                entry = generate_entry(lemma, source, feedback)
            except ValidationError as error:  # reply was not the JSON shape we asked for
                feedback = [f"your JSON did not match the schema: {error.errors()[:3]}"]
                continue
            feedback = find_problems(lemma, entry, vocabulary, rank=rank)
            if not feedback:
                break
        return {
            "status": "needs_review" if feedback else "done", "attempts": attempt, "ipa": source.ipa,
            "entry": entry.model_dump_json() if entry else None, "problems": feedback,
        }
    except Exception as error:  # keep going; failed words are retried on the next run
        return {"status": "failed", "attempts": 1, "problems": [repr(error)]}


def generate(limit: int, retry_review: bool, workers: int) -> None:
    from pipeline.generate import PROMPT_VERSION, model_name

    ngsl = sources.load_ngsl()
    vocabulary = build_vocabulary(ngsl)
    db = store.connect()
    store.add_words(db, ngsl)

    rows = store.words_to_generate(db, limit, PROMPT_VERSION, retry_review)
    print(f"Model: {model_name()}, {workers} workers")
    # Worker threads wait on the network in parallel. Only this thread writes to SQLite.
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_generate_word, row["lemma"], row["rank"], vocabulary): row["lemma"] for row in rows}
        for number, future in enumerate(as_completed(futures), start=1):
            lemma, result = futures[future], future.result()
            store.save_result(db, lemma, prompt_version=PROMPT_VERSION, **result)
            print(f"[{number}/{len(rows)}] {lemma}: {result['status']}", flush=True)


def revalidate() -> None:
    """Re-runs the checks on stored needs_review entries. Words that now pass become done; no LLM calls."""
    from pipeline.models import WordEntry

    vocabulary = build_vocabulary(sources.load_ngsl())
    db = store.connect()
    rows = db.execute("SELECT lemma, rank, entry FROM words WHERE status = 'needs_review' AND entry IS NOT NULL").fetchall()
    passed = 0
    for row in rows:
        problems = find_problems(row["lemma"], WordEntry.from_stored(row["entry"]), vocabulary, rank=row["rank"])
        if problems:
            db.execute("UPDATE words SET problems = ? WHERE lemma = ?", (json.dumps(problems), row["lemma"]))
        else:
            db.execute("UPDATE words SET status = 'done', problems = '[]', updated_at = CURRENT_TIMESTAMP WHERE lemma = ?", (row["lemma"],))
            passed += 1
    db.commit()
    print(f"{passed} of {len(rows)} needs_review words now pass")


def add_usage(limit: int, workers: int, words: list[str] | None, dry_run: bool) -> None:
    """The usage pass: one Claude call per done word that has no usage yet. See usage.py."""
    from pipeline import usage
    from pipeline.generate import model_name

    vocabulary = build_vocabulary(sources.load_ngsl())
    db = store.connect()
    print(f"Model: {model_name()}, {workers} workers{', dry run: nothing is saved' if dry_run else ''}")
    counts = usage.run_pass(db, vocabulary, usage.fetch_from_claude, limit=limit, workers=workers, words=words, dry_run=dry_run)
    print(f"Done: {counts['saved']} saved, {counts['failed']} failed (run again to retry), "
          f"{counts['items']} usage items kept, {counts['dropped']} dropped by the checks")


def add_examples(limit: int, workers: int, words: list[str] | None, dry_run: bool) -> None:
    """The examples pass: one Claude call per done word whose examples have no level yet. See examples.py."""
    from pipeline import examples
    from pipeline.generate import model_name

    vocabulary = build_vocabulary(sources.load_ngsl())
    db = store.connect()
    print(f"Model: {model_name()}, {workers} workers{', dry run: nothing is saved' if dry_run else ''}")
    counts = examples.run_pass(db, vocabulary, examples.fetch_from_claude, limit=limit, workers=workers, words=words, dry_run=dry_run)
    print(f"Done: {counts['saved']} saved, {counts['failed']} failed (run again to retry), "
          f"{counts['items']} examples kept, {counts['dropped']} dropped by the checks")


def report() -> None:
    db = store.connect()
    for row in db.execute("SELECT status, COUNT(*) AS n, SUM(attempts) AS calls FROM words GROUP BY status"):
        print(f"{row['status']:>13}: {row['n']} words, {row['calls']} LLM calls")
    print("\nNeeds review / failed:")
    for row in db.execute("SELECT lemma, problems FROM words WHERE status IN ('needs_review', 'failed') ORDER BY rank"):
        print(f"- {row['lemma']}: {'; '.join(json.loads(row['problems']))}")


def main() -> None:
    load_dotenv()
    parser = argparse.ArgumentParser(prog="pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("preview").add_argument("word")
    commands.add_parser("try").add_argument("word")
    generate_parser = commands.add_parser("generate")
    generate_parser.add_argument("--limit", type=int, default=200)
    generate_parser.add_argument("--retry-review", action="store_true", help="also redo needs_review words")
    generate_parser.add_argument("--workers", type=int, default=8, help="LLM calls in parallel")
    usage_parser = commands.add_parser("usage")
    usage_parser.add_argument("--limit", type=int, default=100000)
    usage_parser.add_argument("--workers", type=int, default=8, help="LLM calls in parallel")
    usage_parser.add_argument("--words", nargs="+", help="only these words (to try it on a few)")
    usage_parser.add_argument("--dry-run", action="store_true", help="call Claude and print the result, save nothing")
    examples_parser = commands.add_parser("examples")
    examples_parser.add_argument("--limit", type=int, default=100000)
    examples_parser.add_argument("--workers", type=int, default=8, help="LLM calls in parallel")
    examples_parser.add_argument("--words", nargs="+", help="only these words (to try it on a few)")
    examples_parser.add_argument("--dry-run", action="store_true", help="call Claude and print the result, save nothing")
    commands.add_parser("revalidate")
    commands.add_parser("report")
    commands.add_parser("export")
    args = parser.parse_args()

    if args.command == "preview":
        preview(args.word)
    elif args.command == "try":
        try_word(args.word)
    elif args.command == "generate":
        generate(args.limit, args.retry_review, args.workers)
    elif args.command == "usage":
        add_usage(args.limit, args.workers, args.words, args.dry_run)
    elif args.command == "examples":
        add_examples(args.limit, args.workers, args.words, args.dry_run)
    elif args.command == "revalidate":
        revalidate()
    elif args.command == "report":
        report()
    elif args.command == "export":
        path, count, without_usage, without_levels = export.export(store.connect())
        print(f"Exported {count} words to {path}")
        if without_usage:
            print(f"Warning: {without_usage} words have no usage yet. Run `uv run pipeline usage` first.")
        if without_levels:
            print(f"Warning: {without_levels} words have no example levels yet (old examples are exported as easy, medium). "
                  "Run `uv run pipeline examples` first.")
