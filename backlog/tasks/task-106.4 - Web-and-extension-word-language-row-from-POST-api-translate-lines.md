---
id: TASK-106.4
title: 'Web and extension: word language row from POST /api/translate-lines'
status: To Do
assignee: []
created_date: '2026-10-24 09:51'
labels: []
dependencies:
  - TASK-106.2
parent_task_id: TASK-106
ordinal: 116000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
web/src/components/home/WordCard.tsx and extension/src/content/card.ts show only the dictionary Vietnamese today, and nothing for other languages (doc-23 and doc-19 known gaps). They should show the backend line for every language except English. The extension calls through its service worker like its other calls.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Opening the row sends one request with all sense explanations of the card; all languages except English get a row
- [ ] #2 Guest uses cachedOnly=true; on a miss Vietnamese shows the dictionary text and other languages show a short line with a sign-in link (web) or the sign-in hint the extension already uses
- [ ] #3 Lines are kept in memory per (text, language) so reopening costs nothing
- [ ] #4 Vitest tests cover signed-in, guest miss and error; web and extension builds pass; doc-23 and doc-19 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
