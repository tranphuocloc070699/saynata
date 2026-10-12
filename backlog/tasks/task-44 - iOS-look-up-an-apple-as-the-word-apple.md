---
id: TASK-44
title: 'iOS: look up ''an apple'' as the word ''apple'''
status: To Do
assignee: []
created_date: '2026-10-12 03:06'
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
- [ ] #1 'a apple', 'an apple', 'the apple' (any case, extra spaces) open the word card for 'apple'
- [ ] #2 Punctuation around the word is ignored: 'apple!', 'an apple.', '"apple",' open the word card for 'apple'
- [ ] #3 Input that is only an article ('a', 'the') or has two or more words after the article (for example 'the red apple') keeps today's behaviour
- [ ] #4 TextRoute (share extension, Translate button) uses the same rule as the app
- [ ] #5 Unit tests cover the cases above
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
