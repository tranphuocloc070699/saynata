---
id: TASK-93
title: 'History and Liked: find past words and sentences again'
status: To Do
assignee: []
created_date: '2026-10-19 13:00'
labels:
  - ios
  - backend
dependencies: []
priority: high
ordinal: 97000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The learner looks up many words and sentences a day but has no way to find one again, except typing it from memory. History lists every word and sentence that got a valid answer (cache, bundle or backend). Liked keeps the ones the learner stars, to study later. Decisions (2026-10-19, developer): a clock button next to Settings opens one sheet with History | Liked segments (mockup option A, not a tab bar); History lives on the device only (works for guests, App Group so the extensions add to it); Liked lives on the server per account (needs sign-in, follows the user to the Mac app and the browser extension).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Subtasks for the backend Liked API and the iOS History + Liked screens are done and merged into feature/local
- [ ] #2 The developer tested History and Liked on the phone
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
