---
id: TASK-8
title: 'iOS: speech to text with a score'
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
updated_date: '2026-10-10 08:25'
labels:
  - ios
milestone: m-0
dependencies: []
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Learner practises saying the meaning and gets feedback (spec R5). Fully on device.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Transcribes on device (requiresOnDeviceRecognition = true)
- [ ] #2 Score = correct words / expected words using word-level Levenshtein on normalised tokens
- [ ] #3 Shows missing and wrong words
- [ ] #4 Score function has unit tests
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
