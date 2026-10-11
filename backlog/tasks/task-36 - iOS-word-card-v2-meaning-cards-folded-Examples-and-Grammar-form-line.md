---
id: TASK-36
title: 'iOS: word card v2 (meaning cards, folded Examples and Grammar, form line)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 11:31'
updated_date: '2026-10-11 11:43'
labels:
  - ios
dependencies: []
priority: high
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer picked option B from the 2026-10-11 mockups with changes: each meaning is its own card; the card title is the number plus the full simple explanation ('1  to move fast on your feet') with the level chip; Examples and Grammar (was 'How to use it') are folded rows; Grammar groups items by kind with bullets; a typed form shows 'past tense of run' under the headword.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each sense is a card titled with its number and simple explanation, plus the level chip
- [ ] #2 Examples is a folded row listing examples with easy / medium / harder labels
- [ ] #3 Grammar is a folded row grouping usage by kind (Patterns, Collocations, Phrases and idioms) as bullet lists
- [ ] #4 Typing a form ('ran') shows the form line ('past tense of run') under the word; Vietnamese stays last and collapsed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Model: add Example{level,text} (reads old strings), WordEntry.matchedForm (TASK-35 not merged yet; merge theirs when it lands). 2. Tokens: example level colors in Theme + asset catalog. 3. Views: SenseTitleRow, FormPill, WordExamplesSection, grouped WordUsageSection (Patterns/Collocations/Phrases and idioms); rebuild WordCardView and WordCardCompactView. 4. Samples + previews. 5. Unit tests (usage grouping, pill text, example decoding). 6. Run on iPhone 16 Pro, screenshots. 7. Update doc-5 and design-system.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/18. Test it: Goal: numbered meaning cards with closed Examples, Grammar, Vietnamese rows; a form shows 'past tense of run'. Steps: run on iPhone 16 Pro, type 'apple', Explain, open Examples then Grammar then Vietnamese; check the previews in WordCardView.swift (Form: ran, apple, get, no usage, old data). Expected: card per sense (number circle, serif explanation, level chip), no counters, Grammar grouped Patterns / Collocations / Phrases and idioms, example labels easy/medium/harder in previews; the pill for 'ran' shows in the preview now and in the app after TASK-35 (store does not fill matchedForm yet). Automated: 66 iOS unit tests pass (xcodebuild test ... -only-testing:EnglishTranslationTests -parallel-testing-enabled NO).
<!-- SECTION:NOTES:END -->
