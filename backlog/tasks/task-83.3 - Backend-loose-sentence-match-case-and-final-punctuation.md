---
id: TASK-83.3
title: 'Backend: loose sentence match (case and final punctuation)'
status: To Do
assignee: []
created_date: '2026-10-16 08:19'
labels: []
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The sentence cache key is the exact text, so "how are you" misses a cached "How are you?". Pre-filled phrases only help if small typing differences still match. Decision 10-16: on an exact miss, look again with a loose key (lowercase, final . ? ! removed, spaces collapsed). Case can matter ("US" vs "us"), so the exact key always wins first.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Saved sentence rows get a loose match key (new Flyway column + index, filled for old rows)
- [ ] #2 POST /api/translations and /stream answer an exact hit first, then a loose hit, before calling Claude; the response says cached=true
- [ ] #3 Tests cover exact wins over loose, loose hit, personal text never matched; doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
