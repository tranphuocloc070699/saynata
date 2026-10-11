---
id: TASK-34
title: F4 Speak and score
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
labels:
  - feature
  - ios
dependencies:
  - TASK-8
type: feature
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A learner says the meaning aloud and gets a score with missing and wrong words, fully on device. Feature branch: feature/f4-speak-score. One testable feature = one feature branch and one GitHub PR into main (workflow: TASK-26). Workers open task PRs into the feature branch; the orchestrator reviews and merges them. The feature PR carries a Test it card, and it merges only after the developer has tested it and said yes. Delete the feature branch only after that PR merges.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test it on a device: speaking the expected text gives a high score, and skipping words lists them as missing
- [ ] #2 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
