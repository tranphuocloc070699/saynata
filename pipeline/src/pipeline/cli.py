"""Command line entry point.

    uv run pipeline preview reluctant    # see what we send to the LLM (no API key needed)
    uv run pipeline try reluctant        # call the LLM for one word, print result, save nothing
    uv run pipeline generate --limit 200 # generate the 200 most common NGSL words
    uv run pipeline generate --retry-review  # also redo words that failed the checks
    uv run pipeline report               # counts + entries that need review
    uv run pipeline export               # build data/out/dictionary.sqlite
"""

import argparse
import json

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


def generate(limit: int, retry_review: bool) -> None:
    from pipeline.generate import PROMPT_VERSION, generate_entry, model_name

    ngsl = sources.load_ngsl()
    vocabulary = build_vocabulary(ngsl)
    db = store.connect()
    store.add_words(db, ngsl)

    rows = store.words_to_generate(db, limit, PROMPT_VERSION, retry_review)
    print(f"Model: {model_name()}")
    for number, row in enumerate(rows, start=1):
        lemma = row["lemma"]
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
                feedback = find_problems(lemma, entry, vocabulary, rank=row["rank"])
                if not feedback:
                    break
            status = "needs_review" if feedback else "done"
            store.save_result(
                db, lemma, status=status, prompt_version=PROMPT_VERSION, attempts=attempt,
                ipa=source.ipa, entry=entry.model_dump_json() if entry else None, problems=feedback,
            )
        except Exception as error:  # keep going; failed words are retried on the next run
            status = "failed"
            store.save_result(db, lemma, status=status, prompt_version=PROMPT_VERSION, attempts=1, problems=[repr(error)])
        print(f"[{number}/{len(rows)}] {lemma}: {status}")


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
    commands.add_parser("report")
    commands.add_parser("export")
    args = parser.parse_args()

    if args.command == "preview":
        preview(args.word)
    elif args.command == "try":
        try_word(args.word)
    elif args.command == "generate":
        generate(args.limit, args.retry_review)
    elif args.command == "report":
        report()
    elif args.command == "export":
        path, count = export.export(store.connect())
        print(f"Exported {count} words to {path}")
