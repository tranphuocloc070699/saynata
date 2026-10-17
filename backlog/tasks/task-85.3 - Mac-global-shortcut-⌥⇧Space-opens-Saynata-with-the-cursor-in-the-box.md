---
id: TASK-85.3
title: 'Mac: global shortcut ⌥⇧Space opens Saynata with the cursor in the box'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:50'
labels:
  - mac
dependencies:
  - TASK-85.2
parent_task_id: TASK-85
priority: high
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
doc-16 Part 1 step 3. Add the KeyboardShortcuts package (sindresorhus, MIT). Default ⌥⇧Space, changeable in the Mac Settings window. The shortcut opens (or closes) the menu-bar window from any app and focuses the input box with its text selected.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ⌥⇧Space opens the window from any app with the cursor in the input box
- [ ] #2 The user can change or clear the shortcut in Settings, and it is remembered
- [ ] #3 Pressing it again or Esc closes the window
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
KeyboardShortcuts 2.4.0 (SPM, Mac target only). Name .openSaynata, default ⌥⇧Space; onKeyUp toggles the popover; Esc closes (NSPopover). SettingsView gets a generic moreSections (EmptyView on iOS); the Mac adds a Shortcut section with the recorder. MenuBarFocus.isSettingsOpen. Checked: Settings snapshot shows the recorder with ⌥⇧Space. Hotkey press could not be automated (no Accessibility permission for osascript), so the developer tests it. iOS 313 tests pass.
<!-- SECTION:NOTES:END -->
