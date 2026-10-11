---
id: TASK-37
title: 'iOS: did you mean, for misspelled words'
status: To Do
assignee: []
created_date: '2026-10-11 11:31'
labels:
  - ios
dependencies: []
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer request 2026-10-11: typing 'runj' should suggest 'run'. The bundled dictionary has 2,750 words and their forms, so suggestions can be found offline at no cost.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 When the backend says a word is not English, or the app is offline and the word is not bundled, up to 3 close bundled words are shown (edit distance 1-2, common words first)
- [ ] #2 Tapping a suggestion looks it up
- [ ] #3 Unit tests cover runj -> run, a far typo with no suggestion, and a real word
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
