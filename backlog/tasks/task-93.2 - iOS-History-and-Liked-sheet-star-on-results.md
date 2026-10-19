---
id: TASK-93.2
title: 'iOS: History and Liked sheet, star on results'
status: To Do
assignee: []
created_date: '2026-10-19 13:00'
labels:
  - ios
dependencies:
  - TASK-93.1
parent_task_id: TASK-93
priority: high
ordinal: 99000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
See the parent task for the why and the decisions. UI follows the agreed mockup (2026-10-19, option A): a clock IconButton left of the Settings gear opens a sheet with a History | Liked segmented control, a search field, rows grouped by day (Today, Yesterday, date). A word row shows the word, a type/level chip when known (or "phrase") and the short meaning; a sentence row shows the sentence (serif) and its simple summary. Tapping a row closes the sheet and shows that result again (put the text in the input and run the normal translate, so a cached answer costs nothing). Swipe left deletes a row. A star IconButton (icon only, accessibility label "Like" / "Unlike") sits next to the speaker on the word header and on the sentence card. History: every word, phrase and sentence that reached a final valid result (.loadedWord or .loaded) is recorded on the device, newest first; a repeat moves to the top instead of a second row; keep the newest 1000; a clear-all icon button with a confirm. Store it in SQLite in the App Group folder (like ResultCache), so the Share and Translate extensions record into the same history. Liked: talks to the backend API defined in the backend subtask (GET/PUT/DELETE /api/liked, Bearer token through the existing BackendClient and AccountStore refresh). A guest who taps the star gets the existing sign-in sheet; after sign-in the item is liked. Keep a local copy of the liked list so the stars and the Liked segment show at once and offline; on sign-out clear it. The SaynataMac target compiles this folder: it must still build (Mac UI entry is not required in this task).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clock button opens the History | Liked sheet as in the mockup; search filters rows; tapping a row shows that result again; swipe deletes; clear-all asks first
- [ ] #2 Every final word, phrase and sentence result (cache, bundle or backend) is added to History once, repeats move to the top, the newest 1000 are kept, and the share and translation extensions add to the same History
- [ ] #3 The star on the word header and the sentence card likes and unlikes through /api/liked; its state is right for items liked on another device after the list loads
- [ ] #4 A guest tapping the star sees the sign-in sheet; after signing in the item is liked; sign-out clears the local liked list
- [ ] #5 Swift Testing tests cover the history store (dedupe, cap, order), the liked store (guest, like, unlike, offline copy) with fakes; the iOS tests pass on iPhone 16 Pro iOS 18.5 and SaynataMac builds
- [ ] #6 A feature page in backlog/docs/features/ describes both flows; the CLAUDE.md overview mentions History and Liked
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
