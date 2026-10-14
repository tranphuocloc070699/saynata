---
id: TASK-32
title: F2 Explain a sentence
status: Done
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-12 03:08'
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
- [x] #1 Test it: a sentence shows the English explanation first and the Vietnamese line last, collapsed
- [x] #2 Test it: text over 500 characters is blocked in the app with a counter
- [x] #3 Test it: the Show translation setting changes how the Vietnamese line opens
- [x] #4 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-12: merged into main with F2 (PR #29). Developer tested F2 on the simulator and approved; 119 iOS unit tests pass (iPhone 16 Pro, iOS 18.5); CI backend, pipeline and doc links pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
F2 Explain a sentence: explain-first result, word cards, target language last and closed. Developer tested and approved; merged PR #29.
<!-- SECTION:FINAL_SUMMARY:END -->
