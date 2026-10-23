---
id: TASK-99.3
title: Admin Sentences page as a table with a right drawer
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 08:48'
updated_date: '2026-10-23 08:59'
labels:
  - admin
  - ui
dependencies: []
parent_task_id: TASK-99
ordinal: 108000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Same pattern as the Words table so both pages look alike.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sentences list is a table (sentence, simple meaning, language, review) and a row opens a right drawer with the actions
- [ ] #2 Search, chips and infinite scroll keep working; tests green
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SentencesPage passes columns (Language, Review, Model) to EntryBrowser, so it gets the table and drawer from TASK-99.2.
2. New sentencesPage.test.tsx; doc-14.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
npm test 106 passed. Branch on top of admin/task-99.2.
<!-- SECTION:NOTES:END -->
