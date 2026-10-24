---
id: TASK-109
title: 'iOS and Mac: remember the picked target language'
status: To Do
assignee: []
created_date: '2026-10-24 10:01'
labels: []
dependencies: []
priority: medium
ordinal: 121000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The iPhone and Mac apps keep the picked language only in memory, so it goes back to Vietnamese every time the app starts (found in TASK-106.1). The web app and the extension already remember it. TargetLanguage(savedCode:) from TASK-106.1 turns th or any unknown code into Vietnamese and is ready to use.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The picked language is saved (AppStorage / UserDefaults, the App Group suite if the extensions read it) and is restored when the app, the Mac app and the extensions start
- [ ] #2 A saved th or unknown code restores as Vietnamese through TargetLanguage(savedCode:)
- [ ] #3 Swift Testing tests cover save and restore; tests pass on iPhone 16 Pro iOS 18.5 and the SaynataMac scheme builds
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
