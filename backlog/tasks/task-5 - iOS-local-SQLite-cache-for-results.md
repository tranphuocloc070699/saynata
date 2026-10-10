---
id: TASK-5
title: 'iOS: local SQLite cache for results'
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
updated_date: '2026-10-10 08:25'
labels:
  - ios
milestone: m-0
dependencies: []
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Repeat lookups should not hit the network (spec R2 step 2). Cache key must equal the backend's sha256 key.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Translations and word entries are saved locally after a backend answer
- [ ] #2 Same key is served from the local cache with no network call
- [ ] #3 Cache key matches backend CacheKeys for the same input
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
