---
id: TASK-25
title: 'iOS: design system (Sage and Teal tokens, HIG text styles, base components)'
status: Done
assignee:
  - '@claude'
created_date: '2026-10-11 07:35'
updated_date: '2026-10-11 07:42'
labels:
  - ios
  - design
dependencies: []
references:
  - docs/design/color-schemes-2026-10-11.md
  - 'https://developer.apple.com/design/human-interface-guidelines/'
priority: high
type: feature
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Saynata has no shared colors, fonts or components, so every new screen would pick its own look. The developer does not trust their own taste and wants to follow Apple HIG plus one small design system. Decided 2026-10-11: palette Option A "Sage and Teal" from docs/design/color-schemes-2026-10-11.md, light mode only (dark mode dropped for now; Color Sets make adding it later cheap). No Claude Design or Figma: the design system lives in code and is viewed in an Xcode preview gallery.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Asset catalog has one Color Set per palette role (11 roles) with the Option A light hex values, and AccentColor is the teal accent
- [x] #2 App is light mode only (UIUserInterfaceStyle = Light)
- [x] #3 Theme.swift gives colors, spacing and corner radius as named constants; text uses only Apple text styles (meaning text is .body serif)
- [x] #4 Base components exist as SwiftUI views: 44 pt round icon button with accessibility label, level chip, meaning block, folded row
- [x] #5 A DesignSystemGallery preview shows every color, text style and component on one screen
- [x] #6 docs/design/design-system.md lists the rules with HIG links, and CLAUDE.md points to it
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add 11 Color Sets under Assets.xcassets/Theme (light only) and set AccentColor to #2F6F62.
2. Set INFOPLIST_KEY_UIUserInterfaceStyle = Light (or Info.plist key).
3. Add DesignSystem/Theme.swift: Theme colors, Spacing, Radius.
4. Add DesignSystem/Components: IconButton, LevelChip, MeaningBlock, FoldedRow.
5. Add DesignSystem/DesignSystemGallery.swift with #Preview.
6. Build with xcodebuild for the simulator.
7. Write docs/design/design-system.md and link it from CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Color Sets live in a namespaced Assets.xcassets/Theme folder and are read as Color("Theme/<Role>") in Theme.swift, so generated asset symbols cannot clash with SwiftUI names such as .background. AccentColor set to #2F6F62 so system controls use the teal. Dark mode values stay in docs/design/color-schemes-2026-10-11.md for later.

Verified: xcodebuild build and test pass on iPhone 16 Pro simulator (iOS 18.5), all EnglishTranslationTests green. Gallery rendered on the simulator with a temporary snapshot test (removed after); screenshot saved as docs/design/design-system-gallery.png. Built Info.plist has UIUserInterfaceStyle = Light. check_docs.py: 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added the Sage and Teal design system in code: 11 light-only Color Sets, Theme.swift (colors, 4 pt spacing, radius, 44 pt tap target), IconButton, LevelChip, MeaningBlock, FoldedRow, and a DesignSystemGallery preview. Light mode forced in Info.plist. Rules with HIG links in docs/design/design-system.md, linked from CLAUDE.md. Verified by simulator build, the full unit test run, and a rendered gallery screenshot.
<!-- SECTION:FINAL_SUMMARY:END -->
