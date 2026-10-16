---
id: TASK-81
title: 'Admin: grid view for words with full-size hover popover'
status: To Do
assignee: []
created_date: '2026-10-16 06:10'
labels: []
dependencies: []
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
In the admin Words list, add a list/grid toggle next to the search box. Grid option A (developer's pick, 2026-10-16): cards about 5 per row with thumbnail, word, Vietnamese and chips (Bundled, N to review). Hovering a thumbnail shows a popover with the full-size image (or the playing video) plus sense number, source and size. Clicking a card opens the word detail as a side drawer. Infinite scroll and filters work the same as the list. The choice is remembered in localStorage.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 List/grid toggle (icon-only) next to search; choice remembered
- [ ] #2 Grid cards: thumbnail, word, Vietnamese, chips; about 5 per row on a laptop
- [ ] #3 Hover a thumbnail: popover with full-size image or video, sense number and source
- [ ] #4 Click a card: word detail opens as a side drawer; filters and infinite scroll still work
- [ ] #5 admin tests pass; feature doc updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
