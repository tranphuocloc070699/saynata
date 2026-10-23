---
id: TASK-98
title: 'iOS: Settings as cozy cards with a cat profile card'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-23 08:09'
updated_date: '2026-10-23 08:34'
labels:
  - ios
  - ui
dependencies:
  - TASK-96
priority: medium
ordinal: 104000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer finds Settings too plain (a default grouped List). They chose mockup A with option B rows (2026-10-23): a profile card on top with a small animated Mochi cat in a circle (idle sprite, from the cat sprite view of TASK-96), "Sign in" / "To explain new sentences" when signed out, the email with Sign out and Delete account when signed in; a "Translation" section (shorter than "Vietnamese row", same meaning for any target language) with three rows Closed / Always open / Remember, each with a one-line hint and an accent checkmark; then an About card (About Saynata, Source code). The Mac app still adds its own sections through moreSections.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Signed out, the profile card shows the cat, "Sign in" and the hint, and opens the sign-in sheet; signed in it shows the email, Sign out and Delete account (with the existing confirmation)
- [ ] #2 The Translation section has three rows with hints and a checkmark on the picked mode; the stored setting and its behaviour do not change
- [ ] #3 The About card links to an About page or the repo; the Mac moreSections still appear
- [ ] #4 The cat stands still with Reduce Motion and when the assets are missing (fallback from TASK-96)
- [ ] #5 design-system.md is updated; tests still pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Rewrite SettingsView as ScrollView of cards: profile (cat), Translation rows with hints, About. 2. AboutView page. 3. Titles Closed/Always open/Remember (hints added). 4. Tests, docs, screenshots.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/149 (base ui/task-96; retarget to feature/local after #148). Test it: gear > Settings signed out: cat profile card opens sign-in; Translation card Closed/Always open/Remember with checkmark; About card (About page, Source code opens GitHub). Reduce Motion or missing assets: cat is still. Tests: 442 pass (iPhone 16 Pro, iOS 18.5); SaynataMac builds.
<!-- SECTION:NOTES:END -->
