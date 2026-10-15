---
id: TASK-75
title: 'Backend: warm up the Claude connection after start-up'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 13:24'
updated_date: '2026-10-15 13:37'
labels:
  - backend
dependencies: []
documentation:
  - backlog/docs/features/doc-4 - Sentence-translation.md
priority: medium
ordinal: 74000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The first Claude call after a backend restart took 7-9 s (cold TLS and JVM). Developer decision 2026-10-15: one tiny background call at start-up.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 On ApplicationReadyEvent, one tiny Claude call (max_tokens about 1) runs in the background; it does not block start-up or health
- [ ] #2 Skipped in tests and when no Claude key is set; recorded in usage but without the daily cap check; warm-up time is logged
- [ ] #3 HTTP client keeps connections alive if the SDK allows it
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ClaudeService.warmUp (1 token, Feature.WARMUP, no budget check). 2. ClaudeWarmUp on ApplicationReadyEvent, background thread, off in tests/without key. 3. OkHttp maxIdleConnections 5, keepAlive 10 min. 4. Tests, docs.
<!-- SECTION:PLAN:END -->
