## Test it
**Goal:** See that a word entry now has `usage` (kind, pattern, example) and no `collocations` or `synonyms`, in the pipeline, the export and the backend. Nothing changes in the app yet.

**Steps:**
1. Pipeline tests (no API key, no cost): `cd pipeline && uv run pytest`
2. Dry run on 3 real done words, saves nothing. Cost: 3 Claude calls, about $0.01 on Sonnet 5.5. Run it in the main checkout, where `data/pipeline.db` lives:
   `cd pipeline && uv run pipeline usage --dry-run --words get make depend`
3. Backend tests: `cd backend && ./mvnw -B test`
4. Call the backend (needs Docker and `CLAUDE_API_KEY` in `backend/.env`; one Haiku call, under $0.001):
   `cd backend && ./mvnw spring-boot:run`, then in another terminal `curl -s localhost:8081/api/words/depend`
5. Look it up again: `curl -s localhost:8081/api/words/depend`

**Expected:**
- Step 1: 26 passed. Step 3: 24 passed.
- Step 2: for each word, a list of usage items per sense (max 3), such as `collocation: "make a decision"`. Dropped items are printed under the word with the reason. The command ends with `Done: 0 saved ...`. The real database does not change.
- Step 4: JSON like `{"word":"depend","cached":false,"entry":{"senses":[{"pos":"verb","cefr":"...","simpleDefinition":"...","vietnamese":"...","examples":["...","..."],"usage":[{"kind":"pattern","pattern":"depend on + something","example":"It depends on the weather."}]}]}}`. There is no `collocations` or `synonyms` key. `kind` is one of `pattern`, `collocation`, `phrase`, `idiom`.
- Step 5: the same entry with `"cached":true`.
- Failure case: `curl -s "localhost:8081/api/words/two%20words"` returns 400. A failed Claude call in the usage pass prints `failed` for that word, leaves its row alone, and the next run tries it again.

**Automated:** `pipeline/tests/test_usage.py` (usage pass with a fake LLM: definitions untouched, resume, no retry, bad items dropped, max 3, dry run, export column), `pipeline/tests/test_validate.py`, `backend/.../ClaudeServiceTest.java` (prompt text, schema has `usage`, JSON round trip), `WordServiceTest.java`. Commands: `cd pipeline && uv run pytest` and `cd backend && ./mvnw -B test`.

## Developer action: finish AC #2 (re-export dictionary.sqlite)
I did NOT run the usage pass on the real API. The real data has **2,750** `done` entries (the task said 2,578), so about **$8-9** on Sonnet 5.5 (one call per word, no retries). Run these in the main checkout after merging this PR:

```bash
cd pipeline
CLAUDE_MODEL=claude-sonnet-5-5 uv run pipeline usage     # add usage to every done word that has none
uv run pipeline usage                                    # run again if some words printed "failed" (it only redoes those)
uv run pipeline export                                   # build data/out/dictionary.sqlite; warns if any word still has no usage
```

Tip: back up first (`cp data/pipeline.db data/pipeline.db.bak-usage`). AC #2 (re-exported `dictionary.sqlite`) is finished after your run.

## What changed
- Pipeline: `Sense.usage` (`kind`, `pattern`, `example`; max 3) replaces `collocations` and `synonyms`. `USAGE_RULES` (kinds, placeholders, "common only", "must fit this sense", the agreed few-shot examples) is in the generate prompt and the usage pass.
- Pipeline: new `usage.py` and `pipeline usage` command (`--words`, `--dry-run`, `--limit`, `--workers`). One call per word, no retries, resume-safe. It only adds `usage` and removes the old fields.
- Pipeline checks: usage examples must pass the NGSL check (contractions count as their base word; `me`, `my`, `oh` allowed). Patterns with `sb`/`sth` and more than 3 items are rejected. In the usage pass, bad items are dropped, not retried.
- `PROMPT_VERSION` is not bumped (would redo all entries, about $36). New `generate` runs include usage.
- Export: `senses.usage` JSON column, no `collocations` or `synonyms`. `export` warns when words have no usage.
- Backend: `WordEntry.Sense.usage` (`WordEntry.Usage`), new `WORD_PROMPT`, `app.claude.word-prompt-version` v3 to **v4**. `kind` is a plain String because the Anthropic schema writes enum names in capitals (found by a test).
- Docs: doc-1 glossary, doc-4 (line numbers), doc-5, doc-6, pipeline README.

## Notes for other workers
- iOS JSON contract: `usage: [{kind, pattern, example}]`, `kind` lowercase.
- PR #8 (TASK-5) is not in `feature/f1-word-lookup` yet, so `CacheKeys.swift` does not exist and I made no iOS change. **When it lands, `PromptVersions.word` must change from "v3" to "v4"** (or its test against `application.properties` will fail). If this PR merges after #8, whoever merges second should do it.

## Follow-up ideas
- Semantic fit ("get up" on the "buy" sense) can only be checked by the prompt, not by code. Read the dry run output and the first 50 words after the full run.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
