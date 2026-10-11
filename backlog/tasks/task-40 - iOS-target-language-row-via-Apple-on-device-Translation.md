---
id: TASK-40
title: 'iOS: target-language row via Apple on-device Translation'
status: To Do
assignee: []
created_date: '2026-10-11 15:01'
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
- [ ] #1 The row title follows the picked target language
- [ ] #2 For non-Vietnamese languages the row shows Apple Translation (iOS 18 Translation framework) of the simple explanation, translated on device; iOS handles the one-time language download
- [ ] #3 Vietnamese shows the dictionary meaning as today; translations are cached in memory per sense and language
- [ ] #4 Errors (pack not downloaded and declined, unsupported language) show a short message in the row
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
