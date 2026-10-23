---
id: TASK-97
title: 'iOS: home input as one composer card, hamburger and history buttons'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-23 08:09'
updated_date: '2026-10-23 08:26'
labels:
  - ios
  - ui
dependencies: []
priority: medium
ordinal: 103000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer dislikes the home input: a plain grey 8 pt box that is always 140 pt tall, the counter and send button outside it, and the language/gear row on top. The home background is also plain white, not Theme.Colors.background. They chose mockup A (2026-10-23): one white composer card (Theme.Radius.card, hairline border) that grows with the text, the target-language picker as a chip inside at bottom-left, the round accent send button inside at bottom-right; on top only two round icon buttons, a hamburger on the left that opens the existing Settings sheet (gear goes away) and History on the right. No logo or title.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The composer is one card with the placeholder "Type a word or a sentence", the language chip and the send button inside; it starts about two lines tall and grows to a max height, then scrolls
- [ ] #2 The character counter is hidden until the text is near the 500 limit, and over the limit it still says "Make it shorter."
- [ ] #3 The top row has only a hamburger IconButton (label "Settings", opens SettingsView) on the left and the History IconButton on the right
- [ ] #4 The screen background is Theme.Colors.background; tap-outside and drag to hide the keyboard still work; the Mac popover still builds
- [ ] #5 design-system.md and doc-4 are updated; tests still pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/147 (into feature/local). 430 iOS tests pass, SaynataMac builds.

PR: https://github.com/tranphuocloc070699/saynata/pull/147 (into feature/local). 430 iOS tests pass, SaynataMac builds.
<!-- SECTION:NOTES:END -->
