---
id: TASK-99.2
title: Admin Words page as a table with a right drawer
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 08:47'
updated_date: '2026-10-23 08:58'
labels:
  - admin
  - ui
dependencies: []
parent_task_id: TASK-99
ordinal: 107000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Mockup option A (developer's choice 2026-10-23): Isomorphic product-list style. Today the Words page is a split view (list left, detail right).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Words list is a table in a card: word with thumbnail and Vietnamese, Source, Level, Review, Media columns; soft dot badges
- [ ] #2 Search and filter chips sit in a bar above the table; the four media chips move into a Media filters menu
- [ ] #3 Clicking a row opens the word in a right drawer with the action buttons and the Media rows (same features as today)
- [ ] #4 Grid view, infinite scroll, hover play and hover preview keep working; tests updated and green
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. EntryBrowser: optional columns prop -> table view; a row opens the entry in the existing Sheet drawer.
2. Chip groups: chips with group sit in a menu (Media).
3. WordsPage: Source, Level, Review, Media columns; media chips grouped.
4. Update tests (table queries, drawer is modal: close before the next word), add table/drawer/menu tests; doc-14.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
npm test 104 passed, build ok. Branch admin/task-99.2 is on top of admin/task-99.1 (PR #150).
<!-- SECTION:NOTES:END -->
