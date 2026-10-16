---
id: TASK-85
title: F6 Mac menu-bar app (Saynata for macOS)
status: In Progress
assignee: []
created_date: '2026-10-16 08:59'
updated_date: '2026-10-16 09:28'
labels:
  - mac
dependencies: []
documentation:
  - backlog/docs/doc-16 - Plan-Mac-menu-bar-app-and-browser-extension.md
priority: high
type: feature
ordinal: 82000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer asked: "Implement desktop app, I want to open with shortcut." Build doc-16 Part 1 option C: a native SwiftUI macOS target in the same Xcode project, a MenuBarExtra window with no Dock icon, a global shortcut (default ⌥⇧Space, changeable) that opens the window with the cursor in the input box, the same results as iOS (explain first, words, target language collapsed), speech, then a Services item and launch at login. Free Apple ID: Sign to Run Locally.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Mac app builds and runs locally with a free Apple ID
- [ ] #2 ⌥⇧Space opens the window with the cursor in the input box, from any app
- [ ] #3 Results follow the product rule: explanation first, words, target language collapsed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
