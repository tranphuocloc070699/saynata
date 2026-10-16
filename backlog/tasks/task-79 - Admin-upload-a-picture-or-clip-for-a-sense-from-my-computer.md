---
id: TASK-79
title: 'Admin: upload a picture or clip for a sense from my computer'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 01:58'
updated_date: '2026-10-16 01:58'
labels:
  - backend
  - admin
  - media
dependencies: []
ordinal: 78000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer decision 2026-10-16: the admin can upload their own file for a sense, for example a picture made in ChatGPT, as a cheap alternative to paid AI generation (TASK-53). Same conversion as Pixabay media (320 px WebP photo, 3 s muted MP4 clip). An uploaded file is a normal media row, so the pipeline export and the next app build keep it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/admin/words/{id}/senses/{position}/media/upload (multipart file, ADMIN only) works for bundled and backend words; real type checked from magic bytes; max size from config; 400 unsupported_media, 413 too big, 502 convert failure
- [ ] #2 The file becomes a media row with source upload and credit 'Uploaded by the admin' (no Pixabay author or page), linked to the sense; needs_ai and removed status cleared; the old file is deleted through MediaCleanup when unused
- [ ] #3 Admin Media row has an icon-only Upload button (file picker) and drag-and-drop, a spinner while uploading, the list and the lightbox show the new media with the caption 'Uploaded by the admin'
- [ ] #4 Pipeline export keeps an uploaded file (copies it, writes its image_url) - tested
- [ ] #5 doc-7 row, doc-13, doc-14, doc-15 and doc-11 (body limit) updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V13: media.source (pixabay|upload, default pixabay).
2. Backend: MediaUploadValidator (magic bytes) + MediaStore.saveUpload (convert, hash name, upload row) + AdminMediaService.upload + controller POST .../media/upload; multipart limits from env; 400/413/502.
3. SenseMedia gets source; admin UI reads it.
4. Admin: Upload icon button, file picker, drag and drop, spinner, caption 'Uploaded by the admin' in row and lightbox.
5. Pipeline export test for an uploaded row.
6. Docs: doc-7, 13, 14, 15, 11. Tests: mvnw, npm test/build, pytest, check_docs.
<!-- SECTION:PLAN:END -->
