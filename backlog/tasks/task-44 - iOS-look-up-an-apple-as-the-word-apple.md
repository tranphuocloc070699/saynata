---
id: TASK-44
title: 'iOS: look up ''an apple'' as the word ''apple'''
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-12 03:06'
updated_date: '2026-10-14 11:18'
labels:
  - ios
dependencies: []
priority: high
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer typed 'an apple' and got a sentence explanation, not the word card for 'apple'. Today one token goes to the word lookup and two or more go to the sentence flow (TranslationStore.translate, CacheKeys.isSingleToken). Learners often type a word with its article. Edge punctuation is already trimmed by CacheKeys.trimmingWordPunctuation; check it covers ! . , ? ; : and quotes. Applies to word input only: real sentences keep their articles. The share extension and Translate button use the same TextRoute, so they must get the same rule.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 'a apple', 'an apple', 'the apple' (any case, extra spaces) open the word card for 'apple'
- [x] #2 Punctuation around the word is ignored: 'apple!', 'an apple.', '"apple",' open the word card for 'apple'
- [x] #3 Input that is only an article ('a', 'the') or has two or more words after the article (for example 'the red apple') keeps today's behaviour
- [x] #4 TextRoute (share extension, Translate button) uses the same rule as the app
- [x] #5 Unit tests cover the cases above
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add CacheKeys.wordQuery(_:) -> String? (one token, or article a/an/the + exactly one word; nil = sentence). 2. TranslationStore.translate routes with it and passes the bare word to lookUpWord and spelling help. 3. Tests in CacheTests and TranslationStoreTests. 4. Update doc-5. 5. PR into main.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented CacheKeys.wordQuery + TranslationStore routing; 128 tests pass (9 new). AC #4 open: TextRoute is on feature/f6-share-extension; it must call CacheKeys.wordQuery after the merge. Test it (iPhone 16 Pro, iOS 18.5): type 'an apple' -> word card 'apple'; 'The apple!' -> same; 'the red apple' -> sentence result; 'an aple' -> not-a-word with did-you-mean 'apple'. Automated: CacheTests, TranslationStoreTests (xcodebuild test, -only-testing:EnglishTranslationTests).

PR: https://github.com/tranphuocloc070699/saynata/pull/34

Verified 2026-10-14: developer tested on a real iPhone 13 (iOS 18) with the main build after PR #37/#38 (word, sentence, speakers, share sheet, 'an apple', media all worked); iOS unit tests 146 passed (Codex run on the Mac, iPhone 16 Pro iOS 18.5 simulator); docs check 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Article + single word ('an apple', punctuation) opens the word card, in the app and TextRoute; merged in PR #34; verified on the developer's iPhone and by unit tests.
<!-- SECTION:FINAL_SUMMARY:END -->
