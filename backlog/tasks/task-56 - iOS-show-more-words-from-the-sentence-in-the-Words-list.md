---
id: TASK-56
title: 'iOS: show more words from the sentence in the Words list'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 06:22'
updated_date: '2026-10-15 06:27'
labels:
  - ios
dependencies: []
priority: medium
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today the sentence result shows only the words Claude listed as difficult for B1, so 'I like apples' shows no words. Developer decision (2026-10-15, option a): the app also splits the sentence into words with NLTokenizer/NLTagger and looks each one up in the bundled dictionary, offline, with no backend or prompt change. Claude's words stay first with Claude's meaning. Then add other content words found in the bundled dictionary (inflected forms matched to the lemma by the forms table), skipping function words, helpers (be/have/do), numbers and duplicates, in sentence order. Same compact SentenceWordCard.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Claude's words come first and keep Claude's meaning; they are never repeated
- [ ] #2 Other content words in the bundled dictionary are added in sentence order; inflected forms match the lemma (apples -> apple, ran -> run)
- [ ] #3 Function words, be/have/do helpers and numbers are skipped; words not in the bundled dictionary are skipped
- [ ] #4 Unit tests cover 'I like apples' and 'She ran to the shops'; doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SentenceWordFinder: NLTagger (lexicalClass) tokens, keep noun/verb/adjective/adverb, skip be/have/do and modals.
2. Look each token up with bundledEntry (forms table gives lemma). Skip misses, duplicates and Claude's words.
3. SentenceWordInfo init from a dictionary entry; SentenceResult appends extras after Claude's words.
4. Unit tests with a fake lookup. Update doc-4.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Added SentenceWordFinder (NLTagger lexicalClass), SentenceWordInfo(sentenceWord:entry:), SentenceResult appends extras after Claude's words. 9 new tests. NaturalLanguage tagging dropped she/to/the on the simulator. Note: be/have/do are always skipped, even as main verbs (simple rule). doc-4 updated.

PR: https://github.com/tranphuocloc070699/saynata/pull/56 . Test it: Explain 'She ran to the shops'; Words show Claude's words first, then ran (past tense of run) and shops, no she/to/the.
<!-- SECTION:NOTES:END -->
