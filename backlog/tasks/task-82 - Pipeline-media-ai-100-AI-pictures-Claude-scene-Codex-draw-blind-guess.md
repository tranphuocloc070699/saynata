---
id: TASK-82
title: 'Pipeline: media-ai, 100% AI pictures (Claude scene, Codex draw, blind guess)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 07:22'
updated_date: '2026-10-16 07:24'
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
