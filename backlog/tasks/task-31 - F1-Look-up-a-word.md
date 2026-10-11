---
id: TASK-31
title: F1 Look up a word
status: Done
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 15:24'
labels:
  - feature
  - ios
dependencies:
  - TASK-4
  - TASK-5
  - TASK-6
  - TASK-24
  - TASK-27
  - TASK-19
  - TASK-35
  - TASK-36
  - TASK-37
priority: high
type: feature
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A learner types one word and gets a simple English word card: offline from the bundled dictionary first, then the local cache, then GET /api/words. Feature branch: feature/f1-word-lookup. One testable feature = one feature branch and one GitHub PR into main (workflow: TASK-26). Workers open task PRs into the feature branch; the orchestrator reviews and merges them. The feature PR carries a Test it card, and it merges only after the developer has tested it and said yes. Delete the feature branch only after that PR merges.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Test it on the simulator: a bundled word shows its card with the network off
- [x] #2 Test it: a word missing from the bundle loads from the backend, and the second lookup works offline
- [x] #3 Test it: an error (offline, unknown word) shows a clear message
- [x] #4 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
F1 Look up a word merged into main (PR #14) after the developer's approval. 94 iOS tests, CI green. Open follow-ups: TASK-38 AC #2, TASK-40 device check.
<!-- SECTION:FINAL_SUMMARY:END -->
