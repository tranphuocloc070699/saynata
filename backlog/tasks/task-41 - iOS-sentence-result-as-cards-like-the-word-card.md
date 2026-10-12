---
id: TASK-41
title: 'iOS: sentence result as cards (like the word card)'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 15:36'
updated_date: '2026-10-12 03:08'
labels:
  - ios
dependencies: []
priority: high
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer approved the 2026-10-11 mockup: the sentence result uses the word-card style. The sentence on top (serif) with icon-only Listen and Copy; a Meaning card with the simple explanation and a folded target-language row (closed); a Words section where each word is a small card with the word, a form pill (past tense of blur, plural of edge) and a level chip when the word is in the bundled dictionary, its short explanation, and a tap to open the full word card.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Sentence header, Meaning card with folded target-language row, and word cards match the approved mockup
- [x] #2 Form pill and level chip come from the bundled dictionary when the word is there; otherwise the backend base-form note is used and no chip is shown
- [x] #3 Tapping a word card opens its full word card
- [x] #4 Icon-only buttons with accessibility labels; Vietnamese/target row last and closed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SentenceWordInfo: pure type that picks pill and chip from the bundled entry, else the backend 'base form: X' note (no chip). Unit tests. 2. WordLooking.bundledEntry(for:) so the store builds the result. 3. Views SentenceHeader, MeaningCard, SentenceWordCard (tap opens WordCardView in a sheet); replace TranslateExplanationView and TranslateWordRow. 4. Update doc-4, run on iPhone 16 Pro, screenshots.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/26 (also TASK-22, TASK-14). Test it: backend on 8081, run app on iPhone 16 Pro. Type 'The mist blurred the edges of the world', tap Explain. Expected: quoted sentence with Listen and Copy icons, Meaning card with closed Vietnamese row, Words cards (edges shows 'form of edge' pill and 'noun · B1' chip from the bundle; blurred and mist have none). Tap edges: full word card sheet. Automated: xcodebuild test (112 tests passed); SentenceWordInfoTests, TranslationStoreTests.

2026-10-12: merged into main with F2 (PR #29). Developer tested F2 on the simulator and approved; 119 iOS unit tests pass (iPhone 16 Pro, iOS 18.5); CI backend, pipeline and doc links pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Sentence result shown as cards like the word card. Shipped with F2 (PR #29), developer-tested.
<!-- SECTION:FINAL_SUMMARY:END -->
