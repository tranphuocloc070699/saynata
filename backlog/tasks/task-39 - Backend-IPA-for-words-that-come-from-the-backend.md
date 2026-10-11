---
id: TASK-39
title: 'Backend: IPA for words that come from the backend'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 15:00'
updated_date: '2026-10-11 15:24'
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
- [x] #1 The backend looks up IPA in the bundled open-dict-data ipa-dict en_US list (MIT, license file included) and returns it in GET /api/words/{word}, also for rows already cached
- [x] #2 The app shows that IPA on the card; words missing from the list show no IPA line
- [x] #3 Tests cover a word with IPA (graveyard), a word without, and a cached row
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add ipa-dict en_US.txt + LICENSE to backend resources/words. 2. IpaDictionary component (map lowercase word -> first IPA). 3. WordResponse gets nullable ipa, filled in WordService at request time (cached rows too). No prompt change. 4. iOS WordResponse reads optional ipa. 5. Tests backend + iOS. 6. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/21. Test it: look up graveyard and apple in the app (backend on 8082): IPA shows; hello unchanged; aardwolf shows no IPA line. Old phone-cached words keep no IPA. Automated: ./mvnw -B test (35 pass); iOS -only-testing:EnglishTranslationTests passes. Not run end to end (no Postgres/Claude key in worktree).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
IPA for backend words from ipa-dict, also for cached rows (PR #21). Verified: backend and iOS decoding tests.
<!-- SECTION:FINAL_SUMMARY:END -->
