---
id: TASK-33
title: F3 Listen
status: Done
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-14 11:18'
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
- [x] #1 Test it: the speakers read the word, each explanation and each example with an English voice, with the network off
- [x] #2 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified 2026-10-14: developer tested on a real iPhone 13 (iOS 18) with the main build after PR #37/#38 (word, sentence, speakers, share sheet, 'an apple', media all worked); iOS unit tests 146 passed (Codex run on the Mac, iPhone 16 Pro iOS 18.5 simulator); docs check 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
F3 Listen merged into main (PR #37) and approved after the developer's phone test.
<!-- SECTION:FINAL_SUMMARY:END -->
