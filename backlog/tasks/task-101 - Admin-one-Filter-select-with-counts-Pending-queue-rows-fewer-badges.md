---
id: TASK-101
title: 'Admin: one Filter select with counts, Pending queue rows, fewer badges'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 10:04'
updated_date: '2026-10-23 10:10'
labels:
  - admin
  - ui
dependencies:
  - TASK-100
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer's choice after the mockups (2026-10-23): replace the chips with one Filter select grouped by parent (Level: A1-A2, B1-B2, C1-C2; Source: Bundled - on the phone, Backend database; Status: Pending, Needs review) with a count on every choice, drop the picture review badges, and show Pending (words in the prefill queue, answered at night) next to Needs review, with a way to remove one. Counts and rows must update after every action.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Words and Sentences use one Filter select with groups and a count per choice; chosen choices show as removable tags
- [ ] #2 Pending lists the prefill queue rows (GET /api/admin/prefill/items) of that kind; each row has an icon-only remove button that asks first (DELETE)
- [ ] #3 Rows show only Needs review · N and Pending badges; Wrong media / to review / Reviewed / No media badges are gone
- [ ] #4 Counts and the list reload after mark reviewed, edit, regenerate, media changes and remove
- [ ] #5 npm test green; doc-14 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. FilterMenu: groups with check/round buttons, a Count per choice (list endpoint, pageSize 1), tags with X.
2. PendingQueue: prefill/items rows of this kind, infinite scroll, remove with inline confirm (DELETE, JSON Content-Type), reload the resource.
3. EntryBrowser: chips get group/description/pending; Pending is on alone; pages decide the badges.
4. Words: Level/Source/Status columns, NeedsReviewBadge; drop media chips and picture badges. Sentences: Status.
5. Tests (wordsPage, infiniteScroll, wordsGrid, sentencesPage updated; new pendingQueue.test.tsx); doc-14, doc-7.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Code is on branch admin/task-101 (PR #156 or next), stacked on admin/task-99.4. npm test 107 passed.
<!-- SECTION:NOTES:END -->
