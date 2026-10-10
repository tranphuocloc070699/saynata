---
id: TASK-14
title: 'Fix result heading showing the picker language, not the result''s'
status: To Do
assignee: []
created_date: '2026-10-10 06:22'
updated_date: '2026-10-10 06:23'
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
<!-- DOD:END -->
