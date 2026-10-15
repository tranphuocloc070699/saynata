---
id: TASK-52
title: >-
  Admin: replace a sense image (pick another Pixabay result, no image, or mark
  for AI)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 14:44'
updated_date: '2026-10-15 03:42'
labels:
  - backend
  - web
  - media
dependencies:
  - TASK-49
priority: medium
type: task
ordinal: 55000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decision (doc-7, 2026-10-14, images option B): Pixabay first; the developer fixes bad images in the admin. Builds on F5 (TASK-49.1-49.4: media table, admin API media[], admin web app Media row). In the admin detail Media row add icon-only buttons (aria-labels, CLAUDE.md UI rule): 'Pick another' opens a small panel with up to 6 other Pixabay results for that sense (search with the sense's image_query or the headword, editable); choosing one downloads, converts (same 320 px WebP / 3 s MP4 rules as TASK-49.4), saves a new media row and links it; 'No image' unlinks the media; 'Mark for AI' sets media_status = needs_ai on that sense (used by the AI image task). Works for bundled (dictionary_sense.media_id) and backend words (word_lookup_media). Mockup first for the panel (CLAUDE.md), then build. Admin API: GET /api/admin/media/search?sense=... (proxied, rate limited, ADMIN only), PUT/DELETE on a sense's media, POST mark-for-ai. Tests: backend (fake Pixabay), admin (Vitest). Update doc-13, doc-14.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Mockup for the pick-another panel approved by the developer before UI code
- [ ] #2 Pick another: shows up to 6 Pixabay results for the sense; choosing one replaces the image (file + media row) for bundled and backend words
- [ ] #3 No image: the sense has no media afterwards; Mark for AI: the sense is listed by GET /api/admin/media?status=needs_ai
- [ ] #4 Only ADMIN can call these endpoints; Pixabay calls are rate limited; tests cover each path
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V8: media_status column on dictionary_sense and word_lookup_media.
2. PixabayClient.search (up to 6 photo+clip results with preview); MediaStore (download, convert, save media row) extracted from BackendWordMediaService and reused.
3. Admin API: GET /api/admin/media/search?sense=b-12:0&q=, PUT/DELETE /api/admin/words/{id}/senses/{n}/media, POST .../media/mark-for-ai, GET /api/admin/media?status=needs_ai. ADMIN only (existing rule), search rate limited (30 per 5 min per admin).
4. Admin web app: inline panel under the Media row (approved option A), icon-only buttons with aria-labels.
5. Tests: backend with fake Pixabay + auth checks; Vitest for the panel.
6. Update doc-13, doc-14; check_docs; PR into feature/f5-cms.
<!-- SECTION:PLAN:END -->
