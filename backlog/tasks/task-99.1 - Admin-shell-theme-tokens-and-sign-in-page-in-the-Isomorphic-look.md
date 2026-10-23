---
id: TASK-99.1
title: 'Admin shell, theme tokens and sign-in page in the Isomorphic look'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 08:47'
updated_date: '2026-10-23 08:52'
labels:
  - admin
  - ui
dependencies: []
parent_task_id: TASK-99
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
First step of TASK-99: the frame every page sits in. Today the sidebar is a narrow plain list and the sign-in is a small box.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 White sidebar (about 240px) with the Saynata logo, an uppercase CONTENT label, Words / Sentences / Media links and an active bar on the left
- [ ] #2 Top bar with a search field and an avatar menu that holds Sign out (icon-only buttons keep aria-labels)
- [ ] #3 Pages have a title, a breadcrumb and actions on the right
- [ ] #4 Inter for text, Lexend Deca for headings; sage teal tokens in index.css
- [ ] #5 Sign-in page is split: form on the left, brand panel on the right; login tests still pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Fonts (Inter, Lexend Deca) and sage tokens with soft badge colours in index.css/tailwind.
2. Layout: white sidebar with CONTENT label, top bar (search to /words?q=, account menu with Sign out), PageHeader with breadcrumb, page in a white card.
3. EntryBrowser starts from ?q=.
4. Split sign-in page.
5. Tests: shell (title, breadcrumb, search, sign out), ?q= on Words; docs doc-14, doc-7.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built against a fake API for screenshots (no backend change). New tests: src/test/layout.test.tsx and a ?q= case in wordsPage.test.tsx. npm test 101 passed.
<!-- SECTION:NOTES:END -->
