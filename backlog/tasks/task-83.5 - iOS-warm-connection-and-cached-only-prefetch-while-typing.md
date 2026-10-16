---
id: TASK-83.5
title: 'iOS: warm connection and cached-only prefetch while typing'
status: To Do
assignee: []
created_date: '2026-10-16 08:19'
labels: []
dependencies:
  - TASK-83.4
parent_task_id: TASK-83
priority: medium
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The app sends nothing until the user submits, and the first request pays about 200 ms for a new TLS connection. No UI change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The app opens the backend connection at launch (one cheap request) so the first lookup reuses it
- [ ] #2 After a 400 ms typing pause the app asks for the cached-only answer of the current word or sentence and keeps it, so submit shows it without waiting; a miss changes nothing
- [ ] #3 Prefetch is cancelled when the text changes and never causes a Claude call or a sign-in sheet; tests with a fake client
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
