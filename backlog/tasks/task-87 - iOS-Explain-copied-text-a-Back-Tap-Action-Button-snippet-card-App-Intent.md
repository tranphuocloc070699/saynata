---
id: TASK-87
title: 'iOS: Explain copied text, a Back Tap / Action Button snippet card (App Intent)'
status: In Progress
assignee: []
created_date: '2026-10-17 06:10'
updated_date: '2026-10-17 06:41'
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
- [ ] #1 An App Intent "Explain copied text" reads the clipboard and shows a snippet card without opening the app (openAppWhenRun = false)
- [ ] #2 One word uses the word lookup (bundle, local cache, backend); more than one word uses the sentence API (local cache first)
- [ ] #3 The card shows the simple English meaning first and the target-language line last, with an icon-only "Open in Saynata" button for the full result
- [ ] #4 It is an App Shortcut, so it can be picked in Back Tap and the Action Button with no Shortcuts setup
- [ ] #5 Empty or non-English clipboard shows a short message in the card; a guest asking for an answer that is not cached sees a clear "sign in in Saynata" line; a slow network shows a clear message instead of hanging
- [ ] #6 Swift Testing tests cover the clipboard to request logic behind a protocol
- [ ] #7 Feature doc, doc-7 row and CLAUDE.md overview updated; PR goes into feature/local
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
<!-- SECTION:NOTES:END -->
