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
    uv run pipeline import-to-postgres   # one time: copy the bundled dictionary.sqlite into Postgres (DATABASE_URL)
    uv run pipeline export               # build data/out/dictionary.sqlite (+ media/) from Postgres
    uv run pipeline link-media           # after the media pass: add its media to senses in Postgres (no text changes)
    uv run pipeline media-check --limit 50   # Claude Haiku looks at 50 pictures/clips; saves verdicts, writes data/out/media-check.html
    uv run pipeline media-check --apply  # also mark the "wrong" senses needs_ai (needs the SSH tunnel, DATABASE_URL)
    uv run pipeline media-generate --dry-run   # needs_ai senses: prompts and the cost estimate, calls nothing
    uv run pipeline media-generate --limit 20  # OpenAI makes 20 AI pictures (about $0.08); data/out/media-generate.html
    uv run pipeline media-generate --link      # after rsync: the new pictures replace the old ones in Postgres
    uv run pipeline media-ai --dry-run --limit 3   # TASK-82: 3 scenes and Codex prompts, draws nothing
    uv run pipeline media-ai --limit 20          # Claude writes, Codex draws, Claude guesses blind; data/out/media-ai.html
    uv run pipeline media-ai --link              # after rsync: the passed AI pictures replace the sense pictures
    uv run pipeline export-local         # old: build dictionary.sqlite from data/pipeline.db
    uv run pipeline topics candidates    # topic words from WordNet + wordfreq into topic_words (no LLM)
    uv run pipeline topics judge --dry-run --limit 50   # one Haiku call, prints the verdicts, saves nothing
    uv run pipeline topics judge         # Haiku keeps or drops every candidate (about 27 calls, under $0.10)
    uv run pipeline topics review        # apply the reviewed drop / move / keep lists on top of the judge (no LLM)
    uv run pipeline topics promote       # kept topic words join the words table as pending (no LLM), then generate
    uv run pipeline topics report        # counts and samples per topic
    uv run pipeline topics export-tsv    # data/out/topic_words.tsv for review
"""

import argparse
import json
import os
import sqlite3
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from dotenv import load_dotenv
from pydantic import ValidationError

from pipeline import export, postgres, sources, store
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


def add_media(limit: int, workers: int, words: list[str] | None, dry_run: bool) -> None:
    """The media pass: downloads and resizes Pixabay media for senses that need it. See media.py."""
    from pipeline import media

    db = store.connect()
    print(f"Pixabay Media Pass, {workers} workers{', dry run: nothing is saved' if dry_run else ''}")
    counts = media.run_pass(db, limit=limit, workers=workers, words=words, dry_run=dry_run)
    print(f"Done: {counts['saved']} saved, {counts['failed']} failed, {counts['media_found']} media files matched")


BUNDLED_SQLITE = Path("../EnglishTranslation/EnglishTranslation/Resources/dictionary.sqlite")


def import_to_postgres(args) -> None:
    sqlite_path = Path(args.sqlite)
    if not sqlite_path.is_file():
        raise SystemExit(f"No dictionary file at {sqlite_path}. Use --sqlite to point to it.")
    media_dir = Path(args.media_dir) if args.media_dir else sqlite_path.parent / "media"
    pipeline_db = None
    if store.DB_PATH.is_file():
        pipeline_db = sqlite3.connect(store.DB_PATH)
        print(f"Reading Pixabay attribution from {store.DB_PATH}")
    pg = postgres.connect(postgres.database_url(args.database_url))
    result = postgres.import_dictionary(
        sqlite3.connect(sqlite_path), pg, media_dir=media_dir, pipeline_db=pipeline_db, overwrite=args.overwrite
    )
    pg.close()
    print(f"Words written: {result.words_written}, skipped (already in Postgres): {result.words_skipped}")
    print(f"Senses: {result.senses}, forms: {result.forms}, media rows in the file: {result.media}")
    if result.words_skipped and not args.overwrite:
        print("Skipped words were not changed. Use --overwrite to replace them with the file.")


def link_media_to_postgres(args) -> None:
    sqlite_path = Path(args.sqlite)
    if not sqlite_path.is_file():
        raise SystemExit(f"No dictionary file at {sqlite_path}. Use --sqlite to point to it.")
    media_dir = Path(args.media_dir) if args.media_dir else sqlite_path.parent / "media"
    pipeline_db = sqlite3.connect(store.DB_PATH) if store.DB_PATH.is_file() else None
    pg = postgres.connect(postgres.database_url(args.database_url))
    linked = postgres.link_media(sqlite3.connect(sqlite_path), pg, media_dir=media_dir, pipeline_db=pipeline_db)
    pg.close()
    print(f"Senses linked to media: {linked} (senses that already had media were not changed)")


def export_from_postgres(args) -> None:
    pg = postgres.connect(postgres.database_url(args.database_url), read_only=True)
    result = export.export_from_postgres(pg, media_dir=Path(args.media_dir))
    pg.close()
    print(f"Exported {result.words} words, {result.senses} senses, {result.forms} forms to {result.path}")
    print(f"Media: {result.media_copied} files copied to {result.path.parent / 'media'}")
    if result.media_missing:
        print(f"Warning: {len(result.media_missing)} media files are not in {args.media_dir}, for example "
              f"{', '.join(result.media_missing[:3])}. Copy the server media folder there first (--media-dir).")


def check_media(args) -> None:
    """The Claude vision check of sense pictures and clips (TASK-78). See media_check.py."""
    from pipeline import media_check

    media_dir = Path(args.media_dir)
    pg = postgres.connect(postgres.database_url(args.database_url))
    store_ = media_check.PostgresStore(pg)
    summary = media_check.run(
        store_, media_dir, args.model, lambda model, content: media_check.fetch_from_claude(model, content),
        words=args.words, limit=args.limit, recheck=args.recheck, apply=args.apply, max_cost=args.max_cost,
        workers=args.workers,
    )
    media_check.print_summary(summary, applied=args.apply)
    rows = store_.latest_checks()
    media_check.write_report(rows, Path(args.report), media_dir)
    pg.close()
    print(f"\nReport with {len(rows)} checked senses: {args.report}")


def generate_media(args) -> None:
    """AI images for the senses marked needs_ai (TASK-53). See media_generate.py."""
    from pipeline import media_generate

    media_dir = Path(args.media_dir)
    pg = postgres.connect(postgres.database_url(args.database_url))
    store_ = media_generate.PostgresStore(pg)
    if args.link:
        base_url = args.media_base_url or os.environ.get("MEDIA_BASE_URL") or media_generate.DEFAULT_MEDIA_BASE_URL
        media_generate.link_generated(store_, media_dir, media_generate.is_on_server(base_url))
        pg.close()
        return
    if args.dry_run:  # no key needed: nothing is called
        generator = media_generate.OpenAIImageGenerator(args.model or media_generate.DEFAULT_MODEL, api_key="dry-run")
    else:
        try:
            generator = media_generate.make_generator(args.provider, args.model)
        except (media_generate.ProviderStop, ValueError) as error:
            pg.close()
            raise SystemExit(f"media-generate: {error}")
    summary = media_generate.run(
        store_, generator, media_dir, words=args.words, limit=args.limit, regenerate=args.regenerate,
        max_cost=args.max_cost, workers=args.workers, dry_run=args.dry_run,
    )
    if not args.dry_run:
        media_generate.print_summary(summary, args.max_cost)
        rows = store_.report_rows()
        media_generate.write_report(rows, Path(args.report), media_dir)
        print(f"\nReport with {len(rows)} AI pictures: {args.report}")
    pg.close()


def generate_media_ai(args) -> None:
    """AI pictures for every sense: Claude scene, Codex draws, Claude blind guess (TASK-82). See media_ai.py."""
    from pipeline import media_ai
    from pipeline.media_generate import ProviderStop, is_on_server, DEFAULT_MEDIA_BASE_URL

    words = [w.strip().lower() for chunk in (args.words or []) for w in chunk.split(",") if w.strip()]
    media_dir = Path(args.media_dir)
    if not args.link and not (words or args.limit or args.all):
        raise SystemExit("media-ai: say which senses: --words a,b  or  --limit N  or  --all")
    if not args.link and not os.environ.get("ANTHROPIC_API_KEY", "").strip():
        raise SystemExit("media-ai: ANTHROPIC_API_KEY is not set in pipeline/.env")
    pg = postgres.connect(postgres.database_url(args.database_url))
    store_ = media_ai.PostgresStore(pg)
    if args.link:
        base_url = args.media_base_url or os.environ.get("MEDIA_BASE_URL") or DEFAULT_MEDIA_BASE_URL
        media_ai.link_passed(store_, media_dir, is_on_server(base_url))
        pg.close()
        return
    try:
        drawer = None if args.dry_run else media_ai.CodexDrawer()  # a dry run draws nothing, so it needs no Codex
    except ProviderStop as error:
        pg.close()
        raise SystemExit(f"media-ai: {error}")
    services = media_ai.Services(media_ai.make_fetch(args.scene_effort), drawer, media_dir)
    summary = media_ai.run(
        store_, services, words=words or None, limit=args.limit or 100000, redo=args.redo,
        max_cost=args.max_cost, workers=args.workers, dry_run=args.dry_run,
    )
    if not args.dry_run:
        media_ai.print_summary(summary, args.workers, args.max_cost)
        rows = store_.report_rows()
        count = media_ai.write_report(rows, Path(args.report), media_dir)
        print(f"\nReport with {count} senses: {args.report}")
    pg.close()


def topic_words(args) -> None:
    """Topic words (TASK-61): candidates, the Haiku judge, and review. See topics.py."""
    from pipeline import topics

    db = store.connect()
    if args.topics_command == "candidates":
        candidates = topics.build_candidates(topics.ensure_wordnet(), topics.known_words(db), args.zipf)
        added = topics.save_candidates(db, candidates)
        print(f"{len(candidates)} candidates, {added} new in topic_words. Next: uv run pipeline topics judge")
    elif args.topics_command == "judge":
        print(f"Model: {topics.JUDGE_MODEL}, {topics.JUDGE_BATCH} words per call"
              f"{', dry run: nothing is saved' if args.dry_run else ''}")
        counts = topics.run_judge(db, topics.fetch_from_claude, limit=args.limit, dry_run=args.dry_run)
        print(f"Done: {counts['keep']} keep, {counts['drop']} drop, {counts['no_verdict']} without a verdict, "
              f"{counts['failed_batches']} failed calls (run again to retry those words)")
    elif args.topics_command == "review":
        counts = topics.apply_review(db)
        print(f"Reviewed overrides: {counts['drop']} dropped, {counts['move']} moved, {counts['keep']} restored")
    elif args.topics_command == "promote":
        added = topics.promote(db)
        print(f"{added} kept topic words added to the words table as pending. "
              f"Next: uv run pipeline generate --limit {max(added, 1)}")
    elif args.topics_command == "report":
        topics.report(db)
    elif args.topics_command == "export-tsv":
        path = export.OUT_DIR / "topic_words.tsv"
        print(f"Wrote {topics.export_tsv(db, path)} words to {path}")


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
    media_parser = commands.add_parser("media")
    media_parser.add_argument("--limit", type=int, default=100000)
    media_parser.add_argument("--workers", type=int, default=4, help="concurrent media downloads (default 4)")
    media_parser.add_argument("--words", nargs="+", help="only these words (to try it on a few)")
    media_parser.add_argument("--dry-run", action="store_true", help="query Pixabay and print matches, save nothing")
    check_parser = commands.add_parser("media-check", help="Claude looks at each sense picture/clip and says good, wrong or unsure")
    check_parser.add_argument("--limit", type=int, default=100000)
    check_parser.add_argument("--words", nargs="+", help="only these words (to try it on a few)")
    check_parser.add_argument("--apply", action="store_true", help="mark the wrong senses needs_ai (never unsure)")
    check_parser.add_argument("--recheck", action="store_true", help="also check senses that were checked before")
    check_parser.add_argument("--model", default="claude-haiku-5-5")
    check_parser.add_argument("--max-cost", type=float, default=2.0, help="stop starting new checks at this many US dollars (default 2)")
    check_parser.add_argument("--workers", type=int, default=4, help="Claude calls in parallel (default 4)")
    check_parser.add_argument("--media-dir", default="data/media", help="folder with the media files (default data/media)")
    check_parser.add_argument("--report", default="data/out/media-check.html")
    check_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    gen_parser = commands.add_parser("media-generate", help="AI pictures for the senses marked needs_ai (OpenAI)")
    gen_parser.add_argument("--limit", type=int, default=100000)
    gen_parser.add_argument("--words", nargs="+", help="only these words (to try it on a few)")
    gen_parser.add_argument("--provider", default="openai", choices=["openai"])
    gen_parser.add_argument("--model", help="default gpt-image-1-mini")
    gen_parser.add_argument("--max-cost", type=float, default=5.0,
                            help="budget cap in US dollars; never passed (default 5)")
    gen_parser.add_argument("--dry-run", action="store_true", help="print prompts and the estimate; call and save nothing")
    gen_parser.add_argument("--regenerate", action="store_true",
                            help="also senses that already have a picture waiting to be linked")
    gen_parser.add_argument("--link", action="store_true",
                            help="after rsync: link the new pictures whose files are on the server")
    gen_parser.add_argument("--workers", type=int, default=2, help="image calls in parallel (default 2)")
    gen_parser.add_argument("--media-dir", default="data/media", help="folder for the files (default data/media)")
    gen_parser.add_argument("--media-base-url", help="where the server serves the files (default MEDIA_BASE_URL "
                            "or https://api.loffy.me/media)")
    gen_parser.add_argument("--report", default="data/out/media-generate.html")
    gen_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    ai_parser = commands.add_parser("media-ai", help="AI pictures for every sense: Claude scene, Codex draws, blind guess")
    ai_parser.add_argument("--words", nargs="+", help="only these words, for example --words bank,old")
    ai_parser.add_argument("--limit", type=int, help="the N most common senses that still need a picture")
    ai_parser.add_argument("--all", action="store_true", help="every sense that still needs a picture")
    ai_parser.add_argument("--redo", action="store_true", help="also senses that already have a final result for this prompt version")
    ai_parser.add_argument("--dry-run", action="store_true", help="print 3 scenes and Codex prompts and the estimate; draw and save nothing")
    ai_parser.add_argument("--link", action="store_true", help="after rsync: link the passed pictures whose files are on the server")
    ai_parser.add_argument("--workers", type=int, default=4, help="senses in parallel, one Codex each (default 4)")
    ai_parser.add_argument("--max-cost", type=float, default=5.0, help="Claude budget cap in US dollars (default 5)")
    ai_parser.add_argument("--scene-effort", choices=["low", "medium", "high", "xhigh", "max"],
                           help="thinking effort of the scene call (default: the API default); lower is cheaper")
    ai_parser.add_argument("--media-dir", default="data/media", help="folder for the files (default data/media)")
    ai_parser.add_argument("--media-base-url", help="where the server serves the files (default MEDIA_BASE_URL "
                           "or https://api.loffy.me/media)")
    ai_parser.add_argument("--report", default="data/out/media-ai.html")
    ai_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    commands.add_parser("revalidate")
    commands.add_parser("report")
    commands.add_parser("export-local")
    import_parser = commands.add_parser("import-to-postgres")
    import_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    import_parser.add_argument("--sqlite", default=str(BUNDLED_SQLITE), help="the dictionary.sqlite to read")
    import_parser.add_argument("--media-dir", help="folder with the media files (default: media/ next to the sqlite file)")
    import_parser.add_argument("--overwrite", action="store_true", help="replace words that are already in Postgres")
    link_parser = commands.add_parser("link-media")
    link_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    link_parser.add_argument("--sqlite", default=str(export.OUT_DIR / "dictionary.sqlite"),
                             help="the dictionary.sqlite whose image_url values to link (default: data/out/, from export-local)")
    link_parser.add_argument("--media-dir", help="folder with the media files (default: media/ next to the sqlite file)")
    export_parser = commands.add_parser("export")
    export_parser.add_argument("--database-url", help="default: DATABASE_URL in pipeline/.env")
    export_parser.add_argument("--media-dir", default=str(export.MEDIA_DIR), help="folder with the media files to copy")
    topics_parser = commands.add_parser("topics", help="topic words: candidates, judge, report, export-tsv")
    topics_commands = topics_parser.add_subparsers(dest="topics_command", required=True)
    candidates_parser = topics_commands.add_parser("candidates")
    candidates_parser.add_argument("--zipf", type=float, default=3.5, help="minimum wordfreq Zipf (default 3.5)")
    judge_parser = topics_commands.add_parser("judge")
    judge_parser.add_argument("--limit", type=int, default=100000)
    judge_parser.add_argument("--dry-run", action="store_true", help="call Claude and print the verdicts, save nothing")
    topics_commands.add_parser("review", help="apply the reviewed drop / move / keep lists in topics.py")
    topics_commands.add_parser("promote", help="add the kept topic words to the words table for generate")
    topics_commands.add_parser("report")
    topics_commands.add_parser("export-tsv")
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
    elif args.command == "media":
        add_media(args.limit, args.workers, args.words, args.dry_run)
    elif args.command == "media-check":
        check_media(args)
    elif args.command == "media-generate":
        generate_media(args)
    elif args.command == "media-ai":
        generate_media_ai(args)
    elif args.command == "revalidate":
        revalidate()
    elif args.command == "report":
        report()
    elif args.command == "import-to-postgres":
        import_to_postgres(args)
    elif args.command == "link-media":
        link_media_to_postgres(args)
    elif args.command == "export":
        export_from_postgres(args)
    elif args.command == "topics":
        topic_words(args)
    elif args.command == "export-local":
        path, count, without_usage, without_levels = export.export_from_pipeline_db(store.connect())
        print(f"Exported {count} words to {path}")
        if without_usage:
            print(f"Warning: {without_usage} words have no usage yet. Run `uv run pipeline usage` first.")
        if without_levels:
            print(f"Warning: {without_levels} words have no example levels yet (old examples are exported as easy, medium). "
                  "Run `uv run pipeline examples` first.")
