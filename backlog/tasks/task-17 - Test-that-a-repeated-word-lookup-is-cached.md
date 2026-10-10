---
id: TASK-17
title: Test that a repeated word lookup is cached
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-10 06:22'
updated_date: '2026-10-10 08:50'
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
- [x] #1 WordServiceTest checks the second lookup returns cached: true and calls Claude once
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add WordServiceTest.secondLookupComesFromCacheWithoutCallingClaude, mirroring the translation test.
2. Run mvn test.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Added WordServiceTest.secondLookupComesFromCacheWithoutCallingClaude (second lookup with different case/spaces: cached true, describeWord called once). mvn test green (system mvn). Removed the matching known gap from doc-5.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added a WordServiceTest that the second lookup is cached: true and Claude is called once. Verified with the full backend suite (green). doc-5 updated.
<!-- SECTION:FINAL_SUMMARY:END -->
