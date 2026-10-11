---
id: TASK-29
title: 'iOS: Settings screen with a Show translation option'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-11 08:36'
updated_date: '2026-10-11 16:02'
labels:
  - ios
dependencies: []
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CLAUDE.md product rule: the target-language line is collapsed by default, and Settings lets the learner change that. Some learners want the Vietnamese open all the time, others want the app to remember their last choice.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Settings opens from an icon-only button on the main screen
- [ ] #2 Show translation has three options: Collapsed (default), Always open, Remember last choice; the choice is saved across launches
- [ ] #3 Word cards and sentence results follow the setting
- [ ] #4 Unit tests cover the Remember last choice logic
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. TranslationRowPreference (mode + last-open, UserDefaults) with unit tests. 2. SettingsView sheet, gear IconButton beside language picker. 3. MeaningCard, WordCardView, WordCardCompactView read startsOpen and record changes. 4. Update doc-4, doc-5, design-system.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/28. Test it: run app, tap gear (top right), choose Always open, look up 'depend' -> Vietnamese row open on each sense; Remember last choice -> next result follows last row state; setting survives relaunch. Tests: TranslationRowPreferenceTests, full run 119 passed. Sentence screenshot not taken (needs paid backend call).
<!-- SECTION:NOTES:END -->
