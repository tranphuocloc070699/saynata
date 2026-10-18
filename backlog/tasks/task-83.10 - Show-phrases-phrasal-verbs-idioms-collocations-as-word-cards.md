---
id: TASK-83.10
title: 'Show phrases (phrasal verbs, idioms, collocations) as word cards'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-18 01:58'
updated_date: '2026-10-18 02:05'
labels:
  - backend
  - ios
dependencies: []
parent_task_id: TASK-83
ordinal: 94000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-83.9 wrote about 7,200 phrases with the backend word prompt, but nothing serves them: the app sends any input with a space to the sentence path, and WordService rejects a text with a space before the cache. Pushing them to word_lookup only helps once both sides read phrases from the cache.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /api/words/{phrase} (2-6 words) answers only from word_lookup: a hit is the word card, a miss is 404 not_cached, Claude and ClaudeGate are never reached
- [x] #2 A phrase's IPA is its words' IPA joined, or none when a word is missing from the IPA list
- [x] #3 The app asks GET /api/words/{phrase}?cachedOnly=true for 2-6 words and shows the word card on a hit; 404 not_cached falls back to the sentence explanation
- [x] #4 Every word card header shows the IPA on the same line as the headword, wrapping under it when too long (developer's choice, mockup A)
- [x] #5 Backend and iOS tests pass; doc-5, doc-18 and the CLAUDE.md overview are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: WordService phrase branch, controller pattern, IpaDictionary join, PhraseLookupTest. 2. iOS: CacheKeys.phraseQuery, TranslationStore tries cachedOnly word lookup, BackendClient cachedOnly. 3. WordHeaderView: IPA beside the headword. 4. Docs, PR into feature/local. Pushing the phrases is the developer's go-ahead.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-18: built. Mockup: developer chose A (today's word card) and asked for the IPA on the headword line on every card. Backend: phrase branch in WordService (cache only, 404 not_cached), controller pattern allows up to 6 words, IpaDictionary joins word IPA; PhraseLookupTest; 401 backend tests pass. iOS: CacheKeys.phraseQuery, WordLooking.phraseEntry, BackendClient.cachedEntry(forPhrase:) with cachedOnly=true, TranslationStore and QuickExplainer try it before the sentence path; WordHeaderView puts the IPA beside the headword (ViewThatFits). 372 iOS tests pass, SaynataMac builds. Also fixed feature/local's broken iOS build: TASK-89.2 deleted SentenceWordFinder but TASK-90's QuickExplainer still used it; the offline bundled-word list is now QuickExplainer.bundledWords. Not done: pushing the phrases (developer's go-ahead, after deploy).
<!-- SECTION:NOTES:END -->
