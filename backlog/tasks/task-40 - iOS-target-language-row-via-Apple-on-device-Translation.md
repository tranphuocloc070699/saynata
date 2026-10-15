---
id: TASK-40
title: 'iOS: target-language row via Apple on-device Translation'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 15:01'
updated_date: '2026-10-15 03:38'
labels:
  - ios
dependencies: []
priority: high
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer chose option A (doc-7, 2026-10-11) and approved the mockup: when the picker is not Vietnamese, the last folded row of each meaning card is titled with the picked language (e.g. Korean) and shows an on-device translation of the simple English explanation, with a small note 'Translated on this iPhone'. Vietnamese keeps the dictionary meaning. No backend cost.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The row title follows the picked target language
- [x] #2 For non-Vietnamese languages the row shows Apple Translation (iOS 18 Translation framework) of the simple explanation, translated on device; iOS handles the one-time language download
- [x] #3 Vietnamese shows the dictionary meaning as today; translations are cached in memory per sense and language
- [x] #4 Errors (pack not downloaded and declined, unsupported language) show a short message in the row
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. TranslationCache (in-memory, per English text + language) and small TargetLanguage/Sense helpers. 2. TargetLanguageRowContent view: Vietnamese = dictionary text; others = .translationTask, lazy (view exists only when row is open). 3. Pass targetLanguage to WordCardView and compact card; row title = language title; no row for English. 4. Unit tests, simulator run, update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented. PR Test it card: pick Korean, look up 'ran', open the 'Korean' row on a real iPhone (iOS 18): spinner, then Korean text + 'Translated on this iPhone'; reopen is instant; Vietnamese still shows dictionary text; declining the download shows 'Could not translate...'. Simulator (iPhone 16 Pro 18.5) reports en-ko unsupported, so only the error message path was seen there. Tests: 92 pass.

PR: https://github.com/tranphuocloc070699/saynata/pull/23

AC #2 needs a real iPhone: the simulator reports the English-Korean pair as unsupported. Merged in PR #23 / F1 #14.

2026-10-15: developer confirmed on a real iPhone that Apple Translation works in the row (AC #2).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Target-language row follows the picked language; Vietnamese shows dictionary text, others use on-device Apple Translation with an in-memory cache and short error messages. Verified by 92 unit tests (PR #23) and the developer's real-iPhone test.
<!-- SECTION:FINAL_SUMMARY:END -->
