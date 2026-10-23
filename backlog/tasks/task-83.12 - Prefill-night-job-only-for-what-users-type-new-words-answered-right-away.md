---
id: TASK-83.12
title: 'Prefill: night job only for what users type; new words answered right away'
status: In Progress
assignee: []
created_date: '2026-10-23 02:39'
updated_date: '2026-10-23 02:46'
labels:
  - backend
  - pipeline
  - prefill
dependencies: []
parent_task_id: TASK-83
priority: high
ordinal: 101000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
The developer (2026-10-23): PREFILL_CRON is for what users type. When the developer adds new words (or phrases, sentences) to the server, they must exist in the server cache at once, not after one or more nights.

Today one queue (prefill_item) holds both: the words of users' sentences (source sentence-words, TASK-83.8) and the developer's collected items (words, phrases, common-sentences). The night job and POST /api/admin/prefill/run take the whole queue by priority, with no source filter.

## What
1. Backend: the scheduled run (PREFILL_CRON) takes only the user sources (config app.prefill.cron-sources, env PREFILL_CRON_SOURCES, default sentence-words).
2. Backend: POST /api/admin/prefill/run gets an optional source (repeatable) filter; no source = every source, as today.
3. Pipeline: prefill collect gets --now: after queueing, it signs in as ADMIN (ADMIN_EMAIL / ADMIN_PASSWORD or the existing admin login in pipeline/.env, new keys in .env.example) and calls POST /api/admin/prefill/run?source=<that source>&maxUsd=<--max-usd, default 0.50>, then prints how to watch progress (prefill report). The 2,488 common-sentences already queued can be started the same way.
4. Docs: doc-18 (flow, commands, config), doc-11 env list, backend/.env.example, CLAUDE.md overview prefill line.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The scheduled run answers only the sources in app.prefill.cron-sources (default sentence-words); other pending items stay pending
- [ ] #2 POST /api/admin/prefill/run?source=X runs only source X; without source it runs every source as before
- [ ] #3 uv run pipeline prefill collect --source <s> --now queues and starts a server run for that source; --dry-run never starts a run
- [ ] #4 Backend and pipeline tests cover the source filter and --now (fake HTTP); doc-18, doc-11 and .env.example updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-23: PR https://github.com/tranphuocloc070699/saynata/pull/141 (night job only for PREFILL_CRON_SOURCES, default sentence-words; admin run ?source=; pipeline prefill collect --now and prefill run --source). Test it card in the PR.
<!-- SECTION:NOTES:END -->
