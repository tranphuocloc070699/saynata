---
id: TASK-38
title: 'Backend: reject non-words before calling Claude (runk became drunk)'
status: To Do
assignee: []
created_date: '2026-10-11 14:51'
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
- [ ] #1 The backend checks the normalised word against a bundled open English word list (SCOWL / Hunspell en_US, license file included) plus its inflections; a word not on the list returns the existing not-a-word answer without calling Claude
- [ ] #2 WORD_PROMPT forbids spelling correction and describing a different word; entries whose senses define a different headword are rejected
- [ ] #3 Word prompt version bumped (backend and iOS PromptVersions) so cached wrong entries like 'runk' are never served again
- [ ] #4 Tests: runk -> not a word with no Claude call; serendipity -> Claude called; run -> allowed; the app shows Did you mean for runk
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
