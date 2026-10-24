---
id: TASK-110
title: 'Better way to say it: a fix for an unnatural or wrong sentence'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-24 10:11'
updated_date: '2026-10-24 10:11'
labels:
  - backend
  - ios
  - web
dependencies: []
ordinal: 112000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer writes English to practise and wants Saynata to show a corrected, more natural version of their own sentence ("I already do it" -> "I've already done it"), not only the simple summary. Developer choice (2026-10-24): the same sentence call returns it (no extra call), shown as a folded row "Better way to say it" (closed, like the language row) between the summary and the target-language row, changed words in bold, a one-line reason, Listen and Copy icons. Text from books or news must not be "fixed": the prompt defaults to no fix, texts over 40 words never get one, the backend drops a fix that changes nothing or rewrites too much, and clients show the row only for text the user typed (not Share, Translate menu, copied text, Quick explain, extension selection). Ships as sentence prompt v10; TASK-106.6 then becomes v11. Proposal: side-session notes better-version-proposal.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sentence prompt v10 returns better and betterWhy; empty when the text is natural; the API sends explanation.better and explanation.betterWhy as null then
- [ ] #2 Backend drops a fix that is the same text after normalizing or changes more than 40% of the words, and never asks for a fix on text over 40 words
- [ ] #3 iOS and Mac SentenceCard show a closed folded row "Better way to say it" with changed words bold, the reason, Listen and Copy icons, only for typed text
- [ ] #4 Tests: backend with fake Claude, iOS word diff, web vitest; doc-4 and doc-7 updated
- [ ] #5 Web SentenceCard shows the same row for typed text; the browser extension never shows it (its text comes from web pages)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
