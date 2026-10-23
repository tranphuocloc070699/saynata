---
id: TASK-95
title: 'New Saynata logo and app icons (two cat heads, S and さ)'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 07:47'
updated_date: '2026-10-23 08:15'
labels:
  - design
  - ios
  - mac
  - extension
dependencies: []
priority: medium
ordinal: 101000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The old icon (thin serif S in a pale circle) did not say "language app" and faded at 16 px in the browser toolbar and the Mac menu bar; the Mac app had no app icon at all. On 2026-10-23 the developer picked a new logo from mockups (doc-7, Design, 10-23): two cat heads on a teal tile, a light cat with a serif Latin "S" and a sage cat with the Japanese "さ" (sa), no speech tail, only Theme colours. It is inspired by the CatMegaBundle pixel cats, but no sprite from that paid pack goes into the logo or the public repo. The developer also asked for the Google sign-in (OAuth consent screen) logo and the extension selection button to use it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The iOS app icon (`Assets.xcassets/AppIcon.appiconset/AppIcon.png`, 1024 px, no alpha) is the new logo, with the letters as outlines, not live text
- [ ] #2 The Mac app has an app icon: macOS sizes 16 to 512 @1x/@2x in the same appiconset, a tile with margin and shadow as the HIG asks, and `ASSETCATALOG_COMPILER_APPICON_NAME` set for the SaynataMac target
- [ ] #3 The Mac menu-bar item shows a one-colour template version of the logo (`MenuBarIcon` image set) instead of the SF Symbol `character.book.closed`
- [ ] #4 The browser extension icons 16, 32, 48 and 128 px are the new logo, and the selection button shows the two cats instead of the letter "S"
- [ ] #5 A 120 x 120 PNG for the Google Cloud OAuth consent screen is in `docs/design/logo/`, with the steps to upload it written down
- [ ] #6 One script (`scripts/logo/build_logo.py`) makes every file from the drawing, so a colour change is one run; `docs/design/design-system.md` has a Logo section
- [ ] #7 No CatMegaBundle file and no font file is added to the repo
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Script: outline "S" (DM Serif Display, OFL) and "さ" (Noto Serif JP Bold, OFL) with fontTools, build the SVGs (tile, iOS square, Mac tile, menu-bar template, cats only), draw PNGs with headless Chrome.
2. Asset catalog: mac idiom sizes in AppIcon, MenuBarIcon image set (SVG, template); APPICON_NAME for SaynataMac; AppDelegate uses MenuBarIcon.
3. Extension: new icons, selection button shows the generated cats SVG (`content/logo.ts`).
4. Google consent logo PNG + upload steps; docs (design system, doc-19, CLAUDE.md, doc-7).
5. Build Mac + iOS, extension build and tests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built in worktree branch design/task-95-logo.
- scripts/logo/build_logo.py makes every file; fonts DM Serif Display + Noto Serif JP (OFL) are passed as arguments, not committed.
- Checks: SaynataMac and EnglishTranslation (iPhone 16 Pro, iOS 18.5) build OK; the Mac app now has AppIcon.icns and MenuBarIcon in Assets.car; extension npm run build OK, npm test 118 passed; check_docs 0 problems.
- Not checked: the menu-bar icon and the new icons on a real device, and the Google consent logo (needs the developer to upload it in Google Cloud).
<!-- SECTION:NOTES:END -->
