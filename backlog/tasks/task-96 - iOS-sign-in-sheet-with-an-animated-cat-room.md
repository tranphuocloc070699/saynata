---
id: TASK-96
title: 'iOS: sign-in sheet with an animated cat room'
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
ordinal: 102000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer finds the sign-in sheet (TASK-70/92) plain and unfinished: too much text, three button styles that do not match. They chose mockup A with a pixel-art cat room (2026-10-23, round 2 mockups): Mochi Example Room 2 at the top, the Mochi cat sleeping in its bed and a blue ball rolling, then "Welcome to Saynata" (serif), Sign in with Apple, Sign in with Google, and a small "Use email instead" link that shows the email/code steps. The cat art is a paid pack (CatMegaBundle, no license file, no redistribution) and this repo is public, so the PNGs must never be committed: the developer chose a git-ignored folder copied into the bundle at build time, with a still fallback when the files are missing. Animation must cost almost nothing: sprite sheet on a CALayer, discrete CAKeyframeAnimation on contentsRect, nearest filter, no per-frame SwiftUI state (see ~/Downloads/CatFormSprites/README.md). Mockup: side-session-notes saynata-mockups.html. A shared cat sprite view is reused by the Settings task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sign-in step 1 shows the cat room on top, the serif title, Apple and Google buttons as equal full-width pills, and a "Use email instead" link; the email code, link and code steps still work as before
- [ ] #2 The sleeping cat and the ball animate with Core Animation only (no SwiftUI redraw per frame), stop when the sheet closes, and stand still when Reduce Motion is on
- [ ] #3 Cat PNGs live in a git-ignored folder; a setup script copies the used files from the developer's CatMegaBundle folder; a clean checkout without them builds and shows a still fallback (SF Symbol cat on the sage background)
- [ ] #4 A reusable cat sprite view (iOS and macOS) is in DesignSystem/Components and in the gallery; design-system.md and doc-17 are updated
- [ ] #5 Tests cover the sprite frame math and the missing-asset fallback
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/148 (into feature/local). 440 iOS tests pass; app and SaynataMac build with and without PaidAssets/. Ball sheet is 5 frames of 24x16 (bounces).
<!-- SECTION:NOTES:END -->
