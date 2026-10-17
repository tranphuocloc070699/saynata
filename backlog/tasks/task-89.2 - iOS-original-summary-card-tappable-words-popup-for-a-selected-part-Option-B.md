---
id: TASK-89.2
title: >-
  iOS: original + summary card, tappable words, popup for a selected part
  (Option B)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-17 16:43'
updated_date: '2026-10-17 17:02'
labels: []
dependencies:
  - TASK-89.1
parent_task_id: TASK-89
priority: high
ordinal: 92000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer picked mockup Option B (https://claude.ai/artifact/RxDfLAMiHKNFQdoC5kniTu): one card with the original sentence (larger) over the simple summary, split by a hairline; every word has a light dotted underline and opens the existing word card in a bottom sheet; selecting a part of either sentence opens a small popup under the selection at once (no menu step) with the part, its simple version, Listen, Copy and the target language folded. Used by the app, the Share and Translate sheets and the Mac app, which all compile the app folder.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The sentence result shows one card: original with Listen, hairline, summary with Listen and the small 'In simple words' label, then the target-language folded row last and closed (Show translation setting respected); Copy is an icon-only button
- [ ] #2 Every word in both sentences has a dotted underline; tapping it highlights it and opens the word card sheet (bundled dictionary first, then GET /api/words/{word}); the old Words list is gone
- [ ] #3 Selecting a part of either sentence opens a popup under the selection with that part summarized through POST /api/translations/stream; words inside the popup are tappable too; tapping outside closes it
- [ ] #4 The summary streams word by word as before; the original shows at once
- [ ] #5 Stores are @Observable, colours and spacing from Theme, icon-only buttons with accessibility labels; Swift Testing tests for word splitting, selection-to-text and the popup store; tests pass on iPhone 16 Pro iOS 18.5
- [ ] #6 PromptVersions.sentence is v8 (matching the backend); doc-4 and docs/design/design-system.md components table are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. PromptVersions.sentence = v8.
2. Plain Swift: SentenceTokenizer (words + lookup word), SentencePart (selection -> text, one word or a part).
3. SentencePartStore (@Observable): streams a part through the same stream path as a sentence.
4. iOS: SelectableSentenceText (UITextView wrapper: dotted words, tap, debounced selection). Mac/fallback: Text with links.
5. SentenceTextView (taps open SentenceWordSheet, selection opens popover), SentencePartPopup, SentenceCard; rewrite SentenceResultView / SentenceMeaningView. Remove Words list, SentenceHeader, MeaningCard, SentenceWordCard, SentenceWordsSection and the unused word finder.
6. Extensions pass loadWordEntry; gallery + design-system + doc-4 / doc-10 / doc-20 updates.
7. Swift Testing tests; run iOS tests, build SaynataMac and extensions; simulator screenshots.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built: SentenceCard (one card), SelectableSentenceText (UITextView, iOS; Text with links on Mac), SentencePartPopup + SentencePartStore, tokenizer and selection logic with tests. Removed Words list and SentenceWordFinder. Tests: 349 pass on iPhone 16 Pro iOS 18.5; SaynataMac and both extensions build. Screenshots in docs/design/screenshots/task-89.2-*.png.
<!-- SECTION:NOTES:END -->
