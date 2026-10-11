---
id: TASK-30
title: 'iOS: share extension to explain selected text'
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 08:36'
labels:
  - ios
dependencies:
  - TASK-19
  - TASK-28
priority: low
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the UX report (docs/design/translate-ux-2026-10-10.md, item 11): selecting text in Safari or Messages and sharing it to Saynata takes about 3 taps with no typing and no app switch, which beats opening the app. The report says to do it after the core word and sentence flows are stable.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Saynata appears in the share sheet for selected text
- [ ] #2 The result shows in a sheet, using the same word or sentence path as the app
- [ ] #3 Text over the 500-character limit shows a clear message
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
