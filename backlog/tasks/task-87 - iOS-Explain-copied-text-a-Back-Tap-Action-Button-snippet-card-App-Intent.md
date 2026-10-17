---
id: TASK-87
title: >-
  iOS: Explain copied text, Back Tap or the Action Button opens Saynata on the
  copied text
status: In Progress
assignee: []
created_date: '2026-10-17 06:10'
updated_date: '2026-10-17 08:13'
labels:
  - ios
  - feature
dependencies: []
priority: medium
ordinal: 89000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The learner reads in other apps (Safari, Books, Messages) and wants a meaning without leaving the app. iOS cannot draw over other apps, so the quickest path is an App Shortcut: copy text, double-tap the back of the phone (Back Tap) or press the Action Button, and a Shortcuts snippet card shows the explanation on top of the current app, without opening Saynata. Works on older phones too (Back Tap is iPhone 8 and later). Ordered by the developer on 2026-10-17.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An App Intent and App Shortcut "Explain copied text" opens Saynata (openAppWhenRun = true) and can be picked in Back Tap and the Action Button with no Shortcuts setup
- [ ] #2 With text on the clipboard the app puts it in the input and explains it, the same as typing (word or sentence)
- [ ] #3 With nothing copied, only spaces, or the paste refused, the input and result are cleared and the input gets the keyboard
- [ ] #4 A sign-in sheet left from an earlier text does not cover the new start
- [ ] #5 Swift Testing tests cover the clipboard to store logic behind ClipboardReading
- [ ] #6 Feature doc, doc-7 row and CLAUDE.md overview updated; PR goes into feature/local
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Mockup of the snippet card (A compact vs B fuller), wait for the pick: developer picked B.
2. CopiedTextExplainer (clipboard behind ClipboardReading): clean, route word/sentence like the app, not-English check, 8 s time limit, error cards.
3. CopiedTextCard (layout B), ExplainCopiedTextIntent (openAppWhenRun false, snippet), SaynataShortcuts; Intents/ left out of the extensions and Mac.
4. Swift Testing tests; run in the iPhone 16 Pro (iOS 18.5) simulator from the Shortcuts app.
5. doc-21, doc-3, doc-7, doc-9, CLAUDE.md; PR into feature/local.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Simulator (iOS 18.5, Shortcuts app): App Shortcut listed under App Shortcuts > Saynata; word card for 'run' (bundled) renders; guest sentence shows the sign-in card with bundled words; the icon link opens Saynata on the text and the app shows its sign-in sheet. iOS asks Allow Paste every run unless Paste from Other Apps = Allow.
Found: the card first rendered blank because the app process could not read the asset catalog while Shortcuts drew it ('No color named Theme/TextPrimary'). Fixed with Theme.SnippetColors (numbers) + ThemeSnippetColorsTests.
Not tested: Back Tap and the Action Button themselves (no hardware in the simulator).
Pre-existing, not this task: CacheKeysTests sentenceKey* fail on feature/local since prompt v7 (#100) because the expected hashes still use v6.

2026-10-17 afternoon: changed by the developer. On the iPhone 13 (iOS 26.5) the Shortcuts card read nothing because a background intent cannot ask Allow Paste (no Paste from Other Apps row either). Developer: 'Back tap is good, so could you change the feature to open the app, check if the clipboard has data, paste it. Otherwise just make the input blank.' The card, CopiedTextExplainer and Theme.SnippetColors are removed. Tested on the iPhone 17e (iOS 26.3) simulator: paste question, text pasted and explained (guest: sign-in sheet), empty clipboard: empty input with focus after closing the old sheet.
<!-- SECTION:NOTES:END -->
