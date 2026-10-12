---
id: TASK-14
title: 'Fix result heading showing the picker language, not the result''s'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 06:22'
updated_date: '2026-10-11 15:51'
labels:
  - ios
dependencies: []
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TranslateView passes store.targetLanguage.title to TranslateResultView (TranslateView.swift:19). Translate into Japanese, switch the picker to French: the heading says French over Japanese text. See doc-4 Sentence translation, Known gaps.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The heading shows the language the result was made in, even after the picker changes
- [ ] #2 A unit test covers it
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. TranslationState.loaded carries a SentenceResult (sentence, explanation, language it was made in). The result view reads the language from it, not from the picker. 2. Unit test: change the picker after loading, the loaded language stays.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/26. Test it: explain a sentence in Vietnamese, then change the picker to Japanese without tapping Explain. Expected: result row still says Vietnamese. Automated: TranslationStoreTests loadedResultKeepsItsLanguageWhenThePickerChanges and pickerChangedWhileWaitingDoesNotChangeTheResultLanguage.
<!-- SECTION:NOTES:END -->
