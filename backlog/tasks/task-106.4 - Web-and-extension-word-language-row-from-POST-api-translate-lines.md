---
id: TASK-106.4
title: 'Web and extension: word language row from POST /api/translate-lines'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 09:51'
updated_date: '2026-10-24 10:10'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Shared lines.ts (cache, loadLines, rowContent) copied web/extension. 2. translateLines in both api.ts (guest = cachedOnly). 3. Web WordCard row; extension worker message + card row. 4. Vitest; docs 23 and 19.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/169 (includes #168). Test it: Goal: word card shows a language row for every language except English; opening it sends one POST /api/translate-lines. Steps: web npm run dev, pick Korean, look up run, open the row twice; extension build with VITE_API_BASE=http://localhost:8081, load dist, select run, open the row, sign in from the card; stop backend and open a new word's row. Expected: one request, second open none; guest = cachedOnly=true; Vietnamese keeps dictionary text on miss/error; others show sign-in line or short error. Automated: cd web && npm test && npm run build; cd extension && npm test && npm run build.
<!-- SECTION:NOTES:END -->
