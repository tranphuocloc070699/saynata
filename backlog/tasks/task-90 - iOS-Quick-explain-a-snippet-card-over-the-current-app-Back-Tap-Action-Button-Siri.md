---
id: TASK-90
title: >-
  iOS: Quick explain, a snippet card over the current app (Back Tap, Action
  Button, Siri)
status: In Progress
assignee: []
created_date: '2026-10-17 16:43'
updated_date: '2026-10-17 16:53'
labels:
  - ios
dependencies: []
ordinal: 93000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer watches YouTube in full screen and wants to look up a word without leaving the video. iOS has no overlay API. TASK-87 opened the app because a background App Intent cannot read the clipboard (Allow Paste is never asked). A required text parameter avoids the clipboard: iOS asks for the text in its own small box over the current app, and the answer shows as a snippet card. Developer picked the short card (2B) from the mockup on 2026-10-17. Snippet views cannot read asset-catalog colors, so the card uses literal theme colors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A "Quick explain" App Intent with a required text parameter runs without opening Saynata and is listed as an App Shortcut (Shortcuts, Spotlight, Siri, Action Button, Back Tap)
- [ ] #2 A word shows its first meaning (bundle, phone cache, then backend); a sentence shows its simple English meaning; the target-language line is last
- [ ] #3 The card is short (layout 2B), has only icon buttons, and an icon link opens the full result in Saynata
- [ ] #4 Guest without a cached answer, slow network (8 s), too long, empty and non-English text each show a short message
- [ ] #5 Swift Testing tests with fakes cover word, sentence and the error cases
- [ ] #6 doc-21 and the CLAUDE.md overview describe Quick explain
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Reuse the removed TASK-87 card code (99bbb94): QuickExplainer (no clipboard), QuickExplainCard layout 2B, Theme.SnippetColors.
2. QuickExplainIntent with a required text parameter and requestValueDialog; App Shortcut.
3. Tests with fakes; run on iPhone 16 Pro iOS 18.5; run the intent from the Shortcuts app.
4. doc-21, doc-7, doc-9, CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built. 350 tests pass on iPhone 16 Pro (iOS 18.5). Ran the intent from the Shortcuts app on the simulator: the text box shows at the top, 'happy' shows the 2B card (adjective · A1, meaning, Vietnamese last); a guest asking for 'reluctant' gets the sign-in card. Not yet tried over a full-screen YouTube video on the phone.
<!-- SECTION:NOTES:END -->
