---
id: TASK-74
title: 'iOS: show the local dictionary word cards at once while a sentence loads'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 13:24'
updated_date: '2026-10-15 13:37'
labels:
  - ios
dependencies: []
documentation:
  - backlog/docs/features/doc-4 - Sentence-translation.md
priority: high
ordinal: 73000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer decision 2026-10-15. The sentence's bundled dictionary words (TASK-56, SentenceWordFinder) work offline, so they can show before Claude answers. Small visual change on the loading screen; no new component.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 On send, local words show right away below the meaning placeholder, with the same compact word cards and section
- [ ] #2 When Claude answers: Claude's words first, then local words, no duplicates; the local cards keep their order
- [ ] #3 Not English or a failed request removes the local cards and shows the error as today
- [ ] #4 State-flow tests; PR has a simulator screenshot
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Store finds local words with SentenceWordFinder before the call (pendingSentence). 2. SentenceWordsSection shared by loading, meaning and result views; MeaningCard spinner when empty. 3. Clear on loaded/failed. 4. Store tests, simulator screenshot.
<!-- SECTION:PLAN:END -->
