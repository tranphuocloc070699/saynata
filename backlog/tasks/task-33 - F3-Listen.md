---
id: TASK-33
title: F3 Listen
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-14 06:28'
labels:
  - feature
  - ios
dependencies:
  - TASK-7
type: feature
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A learner taps an icon to hear the headword, each sense's simple explanation, and each example sentence, on device and offline (scope: TASK-7, doc-7 2026-10-14). Feature branch: feature/f3-listen. One testable feature = one feature branch and one GitHub PR into main (workflow: TASK-26). Workers open task PRs into the feature branch; the orchestrator reviews and merges them. The feature PR carries a Test it card, and it merges only after the developer has tested it and said yes. Delete the feature branch only after that PR merges.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test it: the speakers read the word, each explanation and each example with an English voice, with the network off
- [ ] #2 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
