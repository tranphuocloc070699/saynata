---
id: TASK-50
title: >-
  iOS: hide the keyboard when the user taps outside the input or scrolls the
  result
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 11:18'
updated_date: '2026-10-15 06:27'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. @FocusState in TranslateView, passed to TranslateInputView.
2. Clear focus on send and on a simultaneous tap gesture on the ScrollView.
3. scrollDismissesKeyboard(.interactively).
4. Check on iPhone 16 Pro simulator: buttons, folded rows, speakers, selection.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done in PR (branch feature/keyboard-and-sentence-words). @FocusState in TranslateView; tap gesture is simultaneous and sits on the options and the result area, not on the text box (a first try on the whole ScrollView also hid the keyboard on a tap inside the box, found on the simulator). Send clears focus. scrollDismissesKeyboard(.interactively). Simulator (iPhone 16 Pro, hardware keyboard on): tap inside keeps focus, tap outside ends it. Result-area taps/selection need a check on a real iPhone.
<!-- SECTION:NOTES:END -->
