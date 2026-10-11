---
id: TASK-38
title: 'Backend: reject non-words before calling Claude (runk became drunk)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 14:51'
updated_date: '2026-10-11 15:23'
labels:
  - backend
  - ios
dependencies: []
priority: high
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer test 2026-10-11: typing 'runk' showed a card for 'drunk' under the headword 'runk'. The word is not in the bundle, so the backend asked Claude Haiku, which 'corrected' the spelling instead of returning empty senses, and the wrong entry was cached on the server and the phone. LLM judgement alone cannot decide what is a word.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The backend checks the normalised word against a bundled open English word list (SCOWL / Hunspell en_US, license file included) plus its inflections; a word not on the list returns the existing not-a-word answer without calling Claude
- [ ] #2 WORD_PROMPT forbids spelling correction and describing a different word; entries whose senses define a different headword are rejected
- [x] #3 Word prompt version bumped (backend and iOS PromptVersions) so cached wrong entries like 'runk' are never served again
- [x] #4 Tests: runk -> not a word with no Claude call; serendipity -> Claude called; run -> allowed; the app shows Did you mean for runk
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. EnglishWords (SCOWL 70 + NGSL from the bundle) loaded at startup. 2. WordService rejects unlisted words before Claude, no save. 3. WORD_PROMPT rule. 4. Bump v7 backend + iOS. 5. Tests, docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/20. Test it: start backend; curl localhost:8081/api/words/runk -> {"senses":[]}, no Claude call; curl .../serendipity -> full entry; in the app 'runk' shows 'not an English word' + Did you mean; ru%20nk -> 400. Automated: ./mvnw -B test (31 pass), iOS unit tests (85 pass). Follow-up: iOS ResultCache still saves empty answers; 'different headword' check is prompt-only.

AC #2 left open: the prompt forbids spelling correction, but code cannot check which word a definition describes; the SCOWL word list (AC #1) is the real guard. Merged in PR #20 / F1 #14.
<!-- SECTION:NOTES:END -->
