---
id: TASK-39
title: 'Backend: IPA for words that come from the backend'
status: To Do
assignee: []
created_date: '2026-10-11 15:00'
labels:
  - backend
  - ios
dependencies: []
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer test 2026-10-11: graveyard and apple (not in the bundled NGSL dictionary) show no IPA, while hello (bundled) does. The backend response has no ipa field. A free IPA word list gives it without an LLM call.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The backend looks up IPA in the bundled open-dict-data ipa-dict en_US list (MIT, license file included) and returns it in GET /api/words/{word}, also for rows already cached
- [ ] #2 The app shows that IPA on the card; words missing from the list show no IPA line
- [ ] #3 Tests cover a word with IPA (graveyard), a word without, and a cached row
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
