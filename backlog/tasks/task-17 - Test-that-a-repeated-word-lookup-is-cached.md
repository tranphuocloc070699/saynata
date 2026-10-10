---
id: TASK-17
title: Test that a repeated word lookup is cached
status: To Do
assignee: []
created_date: '2026-10-10 06:22'
updated_date: '2026-10-10 08:25'
labels:
  - backend
dependencies: []
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TranslationServiceTest has secondRequestComesFromCacheWithoutCallingClaude; WordServiceTest only checks read-back. A cache bug for words would not be caught.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 WordServiceTest checks the second lookup returns cached: true and calls Claude once
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
