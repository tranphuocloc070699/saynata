---
id: TASK-2
title: 'Backend: GET /api/words/{word} with cache'
status: Done
assignee: []
created_date: '2026-10-10 04:30'
updated_date: '2026-10-10 04:31'
labels:
  - backend
milestone: m-0
dependencies: []
references:
  - backend/README.md
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Words missing from the bundled dictionary go to the backend, which asks Claude for an Entry in the pipeline JSON shape and caches it (spec R1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Returns { word, entry, cached }
- [x] #2 Second request is cached
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
word package + WordServiceTest exist.
<!-- SECTION:FINAL_SUMMARY:END -->
