---
id: TASK-83.1
title: 'Backend: prefill queue and nightly job with its own budget'
status: To Do
assignee: []
created_date: '2026-10-16 08:18'
labels: []
dependencies: []
parent_task_id: TASK-83
priority: high
ordinal: 81000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fills the shared cache before users ask, through the same WordService and TranslationService code users hit, so the prompt, prompt version, cache key, SimpleWords retry and Pixabay media job are identical. Today ClaudeGate throws sign_in_required for a caller with no user, and every Claude call counts against the users $1 daily budget, so a night run would eat the day budget. Queue rows are written by the pipeline (TASK-83 collect task) and by admins.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A Flyway migration adds a prefill_item queue (kind word or sentence, text, target_lang, source, priority, status pending/done/skipped/failed, attempts, last_error, timestamps; unique per kind + normalized text + target_lang)
- [ ] #2 A run takes pending items by priority and answers each through WordService / TranslationService; an item already cached is marked done without a Claude call; non-words and personal text are marked skipped
- [ ] #3 Prefill Claude calls are saved in claude_usage under their own feature, do not count toward app.claude.daily-budget-usd, and stop a run once the run cap (app.prefill.max-usd-per-run, default 0.50) would be passed
- [ ] #4 A nightly schedule (app.prefill.cron, off when empty) and POST /api/admin/prefill/run?maxUsd=&limit= (ADMIN only) start a run; only one run at a time
- [ ] #5 GET /api/admin/prefill shows counts by status and source and the spend of the last runs
- [ ] #6 Tests with a fake Claude cover: cache hit costs nothing, cap stops the run, user budget not touched, failed item retried later; doc-17 / a new feature page and CLAUDE.md overview updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
