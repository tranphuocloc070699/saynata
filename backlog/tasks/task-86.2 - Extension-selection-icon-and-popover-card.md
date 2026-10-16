---
id: TASK-86.2
title: 'Extension: selection icon and popover card'
status: To Do
assignee: []
created_date: '2026-10-16 09:00'
labels:
  - extension
dependencies:
  - TASK-86.1
parent_task_id: TASK-86
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The visible part: a small icon next to selected text, and the card in a Shadow DOM so page CSS cannot break it. Starts from the mockup the developer picks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Icon appears near a text selection and goes away when the selection is cleared or on Escape
- [ ] #2 Card order: quoted text, simple English meaning, words, target language row last and collapsed by default
- [ ] #3 Meaning grows word by word from meaning-delta events
- [ ] #4 Icon-only buttons with aria-labels; speaker uses the Web Speech API
- [ ] #5 Card lives in a closed Shadow DOM and works on sites with strict CSP
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
