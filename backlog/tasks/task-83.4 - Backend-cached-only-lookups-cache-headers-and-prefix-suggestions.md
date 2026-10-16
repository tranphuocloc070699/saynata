---
id: TASK-83.4
title: 'Backend: cached-only lookups, cache headers and prefix suggestions'
status: To Do
assignee: []
created_date: '2026-10-16 08:19'
labels: []
dependencies:
  - TASK-83.1
parent_task_id: TASK-83
priority: medium
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
So the app can ask for answers while the user types without ever causing a Claude call, and repeat lookups need no network.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/words/{word}?cachedOnly=true and the sentence endpoints with cachedOnly return a cached or bundled answer or 404 not_cached, never call Claude and never go through ClaudeGate
- [ ] #2 Cached and bundled word answers send Cache-Control (public, max-age agreed in the task plan)
- [ ] #3 GET /api/suggest?q=<prefix> returns up to 8 bundled words, cached words and cached phrases that start with the prefix, in under 50 ms on the server, rate limited like other public reads
- [ ] #4 Tests and doc-5 / doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
