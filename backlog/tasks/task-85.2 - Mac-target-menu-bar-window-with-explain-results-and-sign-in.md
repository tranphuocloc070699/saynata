---
id: TASK-85.2
title: 'Mac target: menu-bar window with explain results and sign-in'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:44'
labels:
  - mac
dependencies:
  - TASK-85.1
parent_task_id: TASK-85
priority: high
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
doc-16 Part 1 step 2. New macOS 15 SwiftUI target SaynataMac in the same Xcode project, MenuBarExtra(.window) with no Dock icon (LSUIElement), layout from the mockup the developer picks. Input box on top, result below: simple English first, words next, target language collapsed (TranslationRowPreference). Speaker uses SpeechPlayer. Signs in with the same email-code flow as iOS (doc-17) because REQUIRE_SIGN_IN=true; tokens in the Keychain. Sign to Run Locally with a free Apple ID.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Menu-bar icon opens the window; no Dock icon
- [ ] #2 A word and a sentence show results in the product-rule order, with speech
- [ ] #3 Sign in with an email code works and a new sentence streams
- [ ] #4 Builds with Sign to Run Locally (no paid account)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AppKit NSStatusItem + NSPopover (doc-7) hosting MenuBarContentView: header (name, language picker, Settings), input (Return explains, grows to 4 lines), shared TranslateResultView. Sign-in and Settings are sheets on the popover (checked: they work). SignInSheet gets a Mac Close button (Esc) and a plain email field. CharacterCounter moved to DesignSystem/Components. Debug -explain/-snapshot arguments for checks. Checked with snapshots: apple word card with picture, sentence opens sign-in. iOS 313 tests pass. Feature page doc-20.
<!-- SECTION:NOTES:END -->
