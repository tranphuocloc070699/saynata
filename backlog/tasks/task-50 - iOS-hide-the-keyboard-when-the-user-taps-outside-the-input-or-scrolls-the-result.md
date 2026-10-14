---
id: TASK-50
title: >-
  iOS: hide the keyboard when the user taps outside the input or scrolls the
  result
status: To Do
assignee: []
created_date: '2026-10-14 11:18'
labels:
  - ios
dependencies: []
priority: medium
type: task
ordinal: 52000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer request (2026-10-14): when the user taps outside the text input, the keyboard should hide. Today nothing in the app handles focus (no @FocusState, no scrollDismissesKeyboard); the input is EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateInputView.swift and the screen that holds it is the Translate view. Add a @FocusState for the input; clear it on a tap on the background/result area (simultaneous gesture, so buttons, folded rows, speakers and text selection still work) and on send; add .scrollDismissesKeyboard(.interactively) to the result ScrollView. No layout change, so no mockup. Start after the device fix (PR #38) — already merged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Tapping outside the input (background or result area) hides the keyboard; buttons, folded rows and speakers still respond to the same tap
- [ ] #2 Sending a word or sentence hides the keyboard
- [ ] #3 Dragging the result scroll view hides the keyboard interactively
- [ ] #4 Text selection in results still works; tested on the iPhone 16 Pro (iOS 18.5) simulator and noted in the PR
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
