---
id: TASK-82
title: 'Pipeline: media-ai, 100% AI pictures (Claude scene, Codex draw, blind guess)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 07:22'
updated_date: '2026-10-16 07:40'
labels: []
dependencies: []
ordinal: 81000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace every sense picture with an AI picture made through the developer's ChatGPT plan (Codex CLI image_gen), using the rules locked on 2026-10-16 after the 12-sense trial: green cartoon kid, objects never have faces, no extra people for things and places, buildings cut open, stricter blind-guess check. Trial script: scratchpad trial.py (path in the PR brief).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 uv run pipeline media-ai with --words/--limit/--all/--dry-run/--workers/--max-cost, resumable
- [ ] #2 Claude scene per sense (method + scene + focus + avoid), Codex draws it, picture saved as webp in the media folder
- [ ] #3 Stricter blind guess: open guess first, then multiple choice with distractors from a separate call; retry with feedback up to 3 tries; failures listed for review
- [ ] #4 --link replaces any sense picture with the passed AI picture (old media kept), media_status ai_generated
- [ ] #5 Flyway migration for the new media_generation fields; tests with fake Claude and Codex pass
- [ ] #6 doc-15, doc-7, CLAUDE.md overview updated; HTML report option
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Migration V16 adds nullable AI fields to media_generation. 2. pipeline/media_ai.py: scene (Sonnet), Codex runner behind a protocol, webp conversion, blind guess (open then multiple choice, separate distractor call), retry up to 3 tries, Store protocol + PostgresStore, worker threads with Postgres on main thread, cost cap, link (replaces any picture, keeps old media, clears review), report. 3. cli media-ai. 4. Tests with fake Claude/Codex/store, Postgres tests incl. second connection. 5. Docs: doc-15, doc-7, CLAUDE.md. 6. Run pytest + backend tests, PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/76 (branch pipeline/media-ai, into main).
Tests: pipeline 202 passed with a local Postgres (164 passed, 38 skipped without TEST_DATABASE_URL); backend 334 passed with Flyway V16. No real Claude or codex call was made.
Note: V16 uses JSON, not JSONB (H2 in the backend tests has no JSONB).

Test it
Goal: see 20 real AI pictures made and checked, look at them in a report, and link the good ones so the admin shows them in "Media to review".
Steps (in pipeline/; needs the SSH tunnel, ANTHROPIC_API_KEY, and codex login once):
1. uv run pipeline media-ai --dry-run --limit 3   (3 scenes + Codex prompts + estimate, draws nothing, about $0.05)
2. uv run pipeline media-ai --limit 20   (about 20-30 min with 4 workers), then open data/out/media-ai.html
3. rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/
4. uv run pipeline media-ai --link
5. Admin "Media to review" shows the linked senses again. uv run pipeline export + app build to see them in the app.
6. Everything: uv run pipeline media-ai --all --max-cost 100 (default cap is $5; run again to go on).
Expected: step 1 prints 3 scenes and prompts, saves nothing; step 2 prints one line per sense and a summary (passed, failed, none, errors, seconds per picture, Claude cost, ETA); failed senses keep their best picture, are not linked, and are listed; step 4 links only files that answer 200 on the server. Failure case: --max-cost 0.05 stops early, a second run continues; no codex installed gives a clear message.
Time: about 1 min per picture per worker, 1.3 tries per sense, 3,364 senses = about 14-18 hours with 4 workers. Claude: about $0.02-0.03 per sense = about $50-100 in all (the 20-sense trial shows the real number; --scene-effort medium/low is cheaper).
Automated: cd pipeline && TEST_DATABASE_URL=... uv run pytest ; cd backend && ./mvnw -B test
<!-- SECTION:NOTES:END -->
