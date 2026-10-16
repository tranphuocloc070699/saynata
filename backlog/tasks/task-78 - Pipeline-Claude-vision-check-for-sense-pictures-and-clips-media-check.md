---
id: TASK-78
title: 'Pipeline: Claude vision check for sense pictures and clips (media-check)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 16:18'
updated_date: '2026-10-15 19:24'
labels:
  - pipeline
  - media
dependencies: []
ordinal: 77000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decision (doc-7, 2026-10-15): check every Pixabay picture and clip with Claude first, mark the wrong ones needs_ai, then generate AI images only for those (TASK-53; generation method not chosen yet). New command 'uv run pipeline media-check' sends each bundled sense's picture (a clip: its middle frame) with the word, part of speech, simple explanation and easy example to Claude Haiku 5.5 and saves a verdict (good, wrong, unsure) with a reason in a new table media_check (Flyway V12). With --apply the wrong ones get media_status = needs_ai. An HTML report with thumbnails lets the developer review.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 media-check checks each bundled sense with media (skips removed, needs_ai and already-checked senses unless --recheck) and saves verdict, reason, model, tokens and cost in media_check (Flyway migration)
- [ ] #2 A clip is checked on one frame from the middle (ffmpeg); the prompt has the word, part of speech, simple explanation and easy example; the answer is strict JSON and a malformed answer is not saved
- [ ] #3 --apply sets needs_ai only on wrong senses (never unsure); about 4 calls in parallel, retry on 429/5xx, stop at --max-cost (default 2 USD)
- [ ] #4 Summary (counts, cost, wrong list) is printed and data/out/media-check.html shows thumbnails, word, meaning, verdict and reason
- [ ] #5 Tests with a fake Claude client and fake ffmpeg; pipeline, backend and check_docs pass; doc-6, doc-15 and doc-7 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V12: table media_check (sense id, media id, verdict, reason, model, checked_at, tokens, cost).
2. pipeline/src/pipeline/media_check.py: prompt, JSON parsing, middle frame with ffmpeg, Claude call, cost, runner with 4 workers that stops at --max-cost, Postgres store, --apply, HTML report.
3. CLI command media-check.
4. Tests with a fake Claude client and a fake ffmpeg; Postgres tests run when TEST_DATABASE_URL is set.
5. Docs: doc-6, doc-15, doc-7 row.
6. Admin Media row: skipped (needs a mockup and touches AdminWord, its service and the React app).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/70. Built: Flyway V12 media_check, pipeline/src/pipeline/media_check.py, CLI media-check, 27 tests, docs doc-6/doc-15/doc-7. Admin Media row skipped (needs mockup, touches AdminWord + React). Postgres tests skipped locally (no Postgres).
Test it: Goal: Claude checks every sense picture, you review, then wrong ones become needs_ai. Steps: 1) open SSH tunnel; 2) cd pipeline && uv run pipeline media-check --limit 50; 3) open data/out/media-check.html; 4) uv run pipeline media-check (full, about 0.35 USD, stops at --max-cost 2); 5) review report; 6) uv run pipeline media-check --apply; 7) check GET /api/admin/media?status=needs_ai. Expected: counts per verdict, cost, wrong list; apply marks wrong only, never unsure. Failure: missing file prints failed and is retried next run. Automated: cd pipeline && uv run pytest; cd backend && ./mvnw -B test.
<!-- SECTION:NOTES:END -->
