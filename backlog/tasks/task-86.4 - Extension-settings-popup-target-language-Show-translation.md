---
id: TASK-86.4
title: 'Extension: settings popup (target language, Show translation)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:54'
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
- [x] #1 Popup picks the target language from the backend list (vi, en, ja, ko, zh, th, fr, es, de)
- [x] #2 Show translation mode saved in chrome.storage.sync and used by the next card
- [x] #3 Remember last choice saves the last open or closed state the user set on a card
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. src/popup (Vite page): Language select (backend list) and Show translation with three checkmark options, like the iOS Settings screen; saved in chrome.storage.sync through shared/settings.
2. The next card uses the new values (it reads settings when it opens); Remember uses lastRowOpen saved by the card.
3. Tests for the popup logic with jsdom; check it in the browser harness.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Popup copies the iOS Settings screen (no new mockup: the same list and checkmarks the developer already approved for the app). Checked in the in-app browser with a fake chrome.storage; 5 jsdom tests (settingsView, card English rule). The card reads the settings when it opens (content/index.ts openCard); Remember uses lastRowOpen saved on every row toggle (TASK-86.2). English target has no row, like the app.

PR: https://github.com/tranphuocloc070699/saynata/pull/92 (into feature/f7-browser-extension; merge order #90, #91, #92, #93).
<!-- SECTION:NOTES:END -->
