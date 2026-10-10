---
id: TASK-4
title: 'iOS: bundle dictionary.sqlite and look up words'
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
labels:
  - ios
milestone: m-0
dependencies:
  - TASK-3
priority: high
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Words should be answered offline from the bundled dictionary first (spec R1, R2 step 1). Normalise rules must match backend CacheKeys.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 dictionary.sqlite is bundled in the app
- [ ] #2 A single-token input is normalised and looked up in the bundle, including word forms
- [ ] #3 Unit tests cover hit and miss
<!-- AC:END -->
