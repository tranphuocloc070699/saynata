---
id: TASK-6
title: 'iOS: word lookup falls back to backend'
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
labels:
  - ios
milestone: m-0
dependencies:
  - TASK-4
  - TASK-5
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Full lookup order: bundle -> local cache -> GET /api/words/{word} -> save locally (spec R2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Word missing from bundle and local cache calls /api/words and shows the entry
- [ ] #2 Result is saved to the local cache
- [ ] #3 Errors (422, 502, offline) show a clear message
<!-- AC:END -->
