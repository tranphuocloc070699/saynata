---
id: TASK-62
title: 'Admin: thumbnail rows in the Words list'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:59'
updated_date: '2026-10-15 08:02'
labels:
  - admin
dependencies: []
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Words list rows were two plain text lines (word, English explanation). The developer chose mockup option A (2026-10-15): a small thumbnail on the left shows whether a word has media, and the Vietnamese meaning replaces the English line, because the detail already has the explanation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each word row has a rounded thumbnail (about 56x42): first sense photo, a play icon for a clip (no autoplay), or a quiet dash with no media
- [ ] #2 Line 1 is the bold word with the Bundled/Backend, Needs review and N problems badges; line 2 is the first sense Vietnamese meaning in secondary text; the English explanation is not in the row
- [ ] #3 Thumbnail images use loading=lazy; infinite scroll and the server chips still work; the Sentences list is unchanged
- [ ] #4 Vitest covers the three thumbnail states and the two text lines
- [ ] #5 doc-14 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a thumbnail + 2-line row to EntryBrowser via an optional itemThumb render prop. 2. WordsPage: thumb (photo/clip icon/dash), Vietnamese subtitle. 3. Tests. 4. doc-14. List API already returns entry and media, so no backend change.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in PR (see TASK-63 notes for link). List API already returns entry + media, so no backend change. Vitest wordRows.test.tsx covers 3 thumbnail states.
<!-- SECTION:NOTES:END -->
