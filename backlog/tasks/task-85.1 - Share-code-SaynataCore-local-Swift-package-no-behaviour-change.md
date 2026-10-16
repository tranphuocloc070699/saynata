---
id: TASK-85.1
title: 'Share code: the Mac target compiles the app folder (no iOS change)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:34'
labels:
  - mac
  - ios
dependencies: []
parent_task_id: TASK-85
priority: high
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
doc-16 Part 1 step 1, changed by doc-7 (10-16): instead of a SaynataCore package, add a macOS 15 SwiftUI app target SaynataMac that compiles the EnglishTranslation app folder the same way ShareExtension and TranslationExtension do (file-system synchronized group + membership exceptions for iOS-only files). Wrap the few UIKit parts in #if os(iOS) with a macOS twin where needed. The Mac app in this PR is a bare menu-bar stub; the real window is TASK-85.2.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SaynataMac builds for macOS with Sign to Run Locally, compiling the shared app folder
- [ ] #2 iOS app and both extensions build; all EnglishTranslationTests pass on iPhone 16 Pro (iOS 18.5)
- [ ] #3 No visible change in the iOS app
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SaynataMac target added (macOS 15, sandbox + network client, LSUIElement). It compiles the EnglishTranslation folder with the same exceptions as the extensions. Platform/PlatformSupport.swift holds PlatformImage, Pasteboard, SystemSpellChecker, emailFieldStyle, inlineNavigationTitle, .sheetTrailing. SenseMediaView player works on AppKit too; ResultCache uses Application Support on the Mac (no App Group prompt); AVAudioSession is iOS only. Mac Debug build: BUILD SUCCEEDED. iOS: 313 tests passed on iPhone 16 Pro (iOS 18.5).
<!-- SECTION:NOTES:END -->
