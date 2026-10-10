---
id: TASK-1
title: 'Backend: POST /api/translations with cache'
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
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sentences go to the backend, which asks Claude and caches the result in Postgres (spec R1-R3).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Same key returns cached: true without a Claude call
- [x] #2 Each targetLang cached on its own
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Done 2026-10-09. Returns Explanation (meaning, words, translation). iOS app calls it via the Server provider. Report: docs/reports/2026-10-09-connect-ios-to-backend.md
<!-- SECTION:FINAL_SUMMARY:END -->
