---
id: TASK-86.4
title: 'Extension: settings popup (target language, Show translation)'
status: To Do
assignee: []
created_date: '2026-10-16 09:00'
labels:
  - extension
dependencies:
  - TASK-86.1
parent_task_id: TASK-86
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The same two settings as the iOS app: the target language and the Show translation mode (Collapsed default, Always open, Remember last choice).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Popup picks the target language from the backend list (vi, en, ja, ko, zh, th, fr, es, de)
- [ ] #2 Show translation mode saved in chrome.storage.sync and used by the next card
- [ ] #3 Remember last choice saves the last open or closed state the user set on a card
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
