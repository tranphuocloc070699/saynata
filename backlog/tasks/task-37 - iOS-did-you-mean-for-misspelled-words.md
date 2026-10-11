---
id: TASK-37
title: 'iOS: did you mean, for misspelled words'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 11:31'
updated_date: '2026-10-11 15:24'
labels:
  - ios
dependencies: []
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer request 2026-10-11: typing 'runj' should suggest 'run'. The bundled dictionary has 2,750 words and their forms, so suggestions can be found offline at no cost.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 When the backend says a word is not English, or the app is offline and the word is not bundled, up to 3 close bundled words are shown (edit distance 1-2, common words first)
- [x] #2 Tapping a suggestion looks it up
- [x] #3 Unit tests cover runj -> run, a far typo with no suggestion, and a real word
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SpellingSuggester (Damerau-Levenshtein, lazy list, rank order, max 3) + DictionaryStore.spellingCandidates(). 2. WordLooking.suggestions(for:); store adds spelling help to .failed only for notAnEnglishWord or URLError. 3. SpellingSuggestionsView (chips, LevelChip style). 4. Tests + doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in a PR into feature/f1-word-lookup. 75 unit tests pass. Test it: type runj, Explain -> 'No word runj. Did you mean:' with chips run, ring; tap run -> word card. zzzzqq -> no chips. serendipity (backend on) -> card, no chips. Chip tap not checked on screen (another worker shared the simulator); covered by test tappingASuggestionLooksItUp.

PR: https://github.com/tranphuocloc070699/saynata/pull/17
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Did you mean suggestions for misspelled words, offline (PR #17). Verified: SpellingSuggesterTests, simulator runj -> run.
<!-- SECTION:FINAL_SUMMARY:END -->
