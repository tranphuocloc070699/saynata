---
id: TASK-31
title: F1 Look up a word
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 11:31'
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
- [ ] #1 Test it on the simulator: a bundled word shows its card with the network off
- [ ] #2 Test it: a word missing from the bundle loads from the backend, and the second lookup works offline
- [ ] #3 Test it: an error (offline, unknown word) shows a clear message
- [ ] #4 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
