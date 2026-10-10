---
id: TASK-22
title: Show the 500-character limit in the app and a clear 400 message
status: To Do
assignee: []
created_date: '2026-10-10 06:37'
updated_date: '2026-10-10 08:25'
labels:
  - ios
  - backend
dependencies: []
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The backend rejects text over 500 characters with detail 'Invalid request content.' The app has no limit or counter, so the user only sees that vague message after waiting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The input shows a character count and blocks Explain above 500
- [ ] #2 Backend 400 for long text says what is wrong (e.g. 'Text must be 1-500 characters.')
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
