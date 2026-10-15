---
id: TASK-76
title: 'Admin: remove media for real (delete unused files, Remove all media per word)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 14:33'
updated_date: '2026-10-15 14:43'
labels:
  - backend
  - admin
dependencies: []
priority: medium
ordinal: 75000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer decision 2026-10-15: add a word-level Remove all media in the admin, and make No image really hide the picture in the app by deleting the file when nothing else uses it. Reason: some words cannot have a good picture. Today No image unlinks the media in Postgres but the file stays on disk, so the app (bundled dictionary.sqlite image_url) keeps loading https://api.loffy.me/media/<file>.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 When media is unlinked (No image, Pick another, Remove all media) and no other sense uses the media row, the row and the file in MEDIA_DIR are deleted after the DB change commits; a shared file is kept; a missing file is logged and ignored
- [ ] #2 DELETE /api/admin/words/{id}/media (ADMIN only) unlinks the media of every sense of a bundled or backend word, marks each sense removed so it is not filled again, deletes unused files, and returns the AdminWord
- [ ] #3 Pipeline link-media skips senses with image_type none or media_status removed, and export writes no image_url for them (pipeline tests)
- [ ] #4 Admin word header has an icon-only Remove all media button with a confirmation; the Media rows show - and the list thumbnail refreshes afterwards (Vitest)
- [ ] #5 doc-7 row, doc-13, doc-14, doc-15 (and doc-6) are updated, with the phone-cache note and a follow-up idea for the iOS HTTP cache
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: MediaCleanup (delete media row only if no sense uses it, then delete the file after commit; missing file is fine). Call it from clear, replace and the new removeAllMedia.
2. Backend: DELETE /api/admin/words/{id}/media. One transaction unlinks every sense (bundled: image_type none + media_status removed; backend: ensure claim row + media_status removed). Then clean up files. Returns AdminWord.
3. Pipeline: link_media skips senses with image_type none / media_status removed (before it makes media rows). export already writes image_url only from media_id; add tests.
4. Admin: Remove all media icon button (ImageOff) in the word header with confirm; refresh like PR #58. Vitest.
5. Docs: doc-7 row, doc-13, doc-14, doc-15, doc-6, CLAUDE.md check. Tests: mvnw, pytest, npm test/build, check_docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Backend: MediaCleanup + DELETE /api/admin/words/{id}/media; pipeline link-media/export skip removed senses; admin button. Tests: backend 306 pass, pipeline 102 pass (local Postgres 15), admin 58 pass.
<!-- SECTION:NOTES:END -->
