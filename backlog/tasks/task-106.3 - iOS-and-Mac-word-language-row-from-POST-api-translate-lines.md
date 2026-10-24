---
id: TASK-106.3
title: 'iOS and Mac: word language row from POST /api/translate-lines'
status: To Do
assignee: []
created_date: '2026-10-24 09:51'
labels: []
dependencies:
  - TASK-106.2
parent_task_id: TASK-106
ordinal: 115000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The word card (WordCardView, WordCardCompactView, Views/Word/TargetLanguageRowContent.swift) and Quick explain (QuickExplainCard, iOS 18 and 26 intents) show the line from the backend instead of the dictionary Vietnamese or Apple Translation. Shared code, so the Mac app gets it too. The row stays collapsed by default; the request is sent only when a row opens (or at load with Show translation = Always open). UI layout does not change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Opening the language row on any sense sends one request with every sense simple explanation of that card; the other senses open with no new request
- [ ] #2 Signed in: normal call. Guest: cachedOnly=true. Lines are kept in a phone cache per (text, language)
- [ ] #3 Fallback when the line is missing or the call fails: Vietnamese shows the dictionary text, other languages use Apple Translation as today
- [ ] #4 Swift Testing tests with a fake backend cover signed-in, guest miss and error fallback; tests pass on iPhone 16 Pro iOS 18.5 and the SaynataMac scheme builds
- [ ] #5 doc-5 (and doc-21 for Quick explain) updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
