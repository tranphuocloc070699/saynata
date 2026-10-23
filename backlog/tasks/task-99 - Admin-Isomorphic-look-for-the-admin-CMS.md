---
id: TASK-99
title: 'Admin: Isomorphic look for the admin CMS'
status: To Do
assignee: []
created_date: '2026-10-23 08:47'
labels:
  - admin
  - ui
dependencies: []
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants the admin CMS (admin.loffy.me) to look like the Isomorphic admin template they run locally (Next.js + RizzUI, a paid ThemeForest template). Decision 2026-10-23: copy the look onto our own stack (Vite, React 19, Refine, shadcn), never copy template files (the repo is open source). Words page = table + right drawer (mockup option A); colours = Saynata sage teal. Backend /api/admin/** does not change. Mockup: side-session-notes admin-isomorphic-mockup.html.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every admin page uses the new shell, tables, badges and forms
- [ ] #2 No Isomorphic source file is copied into the repo
- [ ] #3 cd admin && npm test and npm run build pass
- [ ] #4 doc-14 and doc-7 describe the new look
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
