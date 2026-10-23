---
id: TASK-99.4
title: Admin edit form in the Isomorphic form layout
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 08:48'
updated_date: '2026-10-23 09:01'
labels:
  - admin
  - ui
dependencies: []
parent_task_id: TASK-99
ordinal: 109000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Isomorphic forms put a title and hint on the left and fields on the right, with Cancel and Save at the bottom.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Edit form has sections (Word, each Sense) with a title and hint on the left, fields on the right
- [ ] #2 Form and JSON tabs stay; Cancel and Save are icon-only at the bottom
- [ ] #3 AI picture dialog and lightbox use the new tokens; tests green
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. EntryEditor: underlined tabs, Cancel + Save at the bottom in a sticky bar, new FormSection (title and hint left, fields right).
2. WordForm: one FormSection per sense; SentenceForm: Meaning, Words, Translation sections.
3. Inputs rounded-lg, drawer max-w-3xl, dialogs bg-card.
4. Test for sections and button order; doc-14.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
npm test 107 passed, build ok. Branch on top of admin/task-99.3.
<!-- SECTION:NOTES:END -->
