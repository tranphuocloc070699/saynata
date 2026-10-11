---
id: TASK-32
title: F2 Explain a sentence
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
labels:
  - feature
  - ios
  - backend
dependencies:
  - TASK-18
  - TASK-28
  - TASK-14
  - TASK-22
  - TASK-21
  - TASK-29
priority: high
type: feature
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A learner types a sentence and gets a simple English explanation first, then the words (compact word cards), then a collapsed Vietnamese line. The app talks only to our backend. TASK-15 and TASK-20 are already done. Feature branch: feature/f2-explain-sentence. One testable feature = one feature branch and one GitHub PR into main (workflow: TASK-26). Workers open task PRs into the feature branch; the orchestrator reviews and merges them. The feature PR carries a Test it card, and it merges only after the developer has tested it and said yes. Delete the feature branch only after that PR merges.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test it: a sentence shows the English explanation first and the Vietnamese line last, collapsed
- [ ] #2 Test it: text over 500 characters is blocked in the app with a counter
- [ ] #3 Test it: the Show translation setting changes how the Vietnamese line opens
- [ ] #4 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
