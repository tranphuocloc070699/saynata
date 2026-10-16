---
id: TASK-86
title: 'Browser extension: explain selected text on any web page (Chromium MV3)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:29'
labels:
  - extension
dependencies: []
references:
  - backlog/docs/doc-16 - Plan-Mac-menu-bar-app-and-browser-extension.md
ordinal: 82000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer reads English on the web in Brave and wants a Google Translate / DeepL style popover for selected words and sentences. doc-16 Part 2 option A: one Manifest V3 extension in TypeScript (extension/, Vite), loaded unpacked in Brave or Chrome. It calls the same backend as the app (api.loffy.me) and follows the CLAUDE.md product rule (explain first, translate last) and the icon-only button rule. Safari comes later, inside the Mac app.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Selecting text on a page shows a small Saynata icon; clicking it, the context menu or a shortcut opens the card
- [ ] #2 One word uses GET /api/words/{word}; more uses POST /api/translations/stream with a growing meaning
- [ ] #3 A guest sees cached answers and a clear sign-in state; a signed-in user gets new answers
- [ ] #4 Settings popup for target language and Show translation mode
- [ ] #5 Vitest tests and a CI job for build + test
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
