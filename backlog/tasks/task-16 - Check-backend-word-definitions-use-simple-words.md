---
id: TASK-16
title: Check backend word definitions use simple words
status: To Do
assignee: []
created_date: '2026-10-10 06:22'
updated_date: '2026-10-10 08:25'
labels:
  - backend
dependencies: []
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The pipeline rejects definitions with words outside NGSL (pipeline/src/pipeline/validate.py). The backend saves Claude's word entries without that check, so backend definitions can be harder than bundled ones. See doc-5 Word lookup, Known gaps.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend entries are checked against the same simple-word rules (or the decision not to is recorded)
- [ ] #2 Entries that fail are retried with feedback or flagged
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
