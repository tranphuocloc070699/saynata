---
id: TASK-85.2
title: 'Mac target: menu-bar window with explain results and sign-in'
status: To Do
assignee: []
created_date: '2026-10-16 09:00'
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
