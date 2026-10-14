---
id: TASK-49.4
title: >-
  Backend: serve sense media from the server and fetch images for backend words
  (Pixabay)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 11:22'
updated_date: '2026-10-14 12:19'
labels:
  - backend
  - pipeline
  - media
dependencies:
  - TASK-49.3
parent_task_id: TASK-49
priority: medium
type: task
ordinal: 53000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decision (doc-7, 2026-10-14, option B): media is served from the server, not bundled. Builds on TASK-49.3 (media rows in Postgres). 1) Store media files on the VPS (a Docker volume, for example /var/lib/saynata/media) and serve them read-only by Caddy at https://api.loffy.me/media/<file> with long cache headers; document the Caddyfile snippet and the volume in doc-11. 2) Word entries in the public API get an absolute media URL per sense (field imageUrl, unchanged name) when a media row exists. 3) When the backend creates a new word entry (word not in the bundle), it asks Pixabay (PIXABAY_API_KEY in .env) for one photo for nouns/adjectives or one short clip for action verbs, resizes like pipeline/src/pipeline/media.py (320 px WebP photos, 3 s muted H.264 MP4 clips, ffmpeg in the image), saves the file and the row with photographer attribution. It must never block the word response: do it after the response or on a background executor, and serve the image on the next lookup. 4) The pipeline media pass can upload its results to the server (or write into the same Postgres + volume). Do not run a full paid or large media pass; give the command. Tests with a fake Pixabay client.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/words/{word} returns imageUrl as an absolute https://api.loffy.me/media/... URL when the sense has media (test)
- [ ] #2 A new backend word gets a Pixabay photo or clip saved in the background without slowing the response (test with a fake client)
- [ ] #3 Caddy serves /media/ files; Caddyfile snippet, Docker volume and PIXABAY_API_KEY documented in doc-11 and .env.example
- [ ] #4 Attribution (photographer, Pixabay page) stored per file and returned by the admin API
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V7 word_lookup_media (claim row per backend sense). 2. media package: Pixabay client (rate limited), ffmpeg converter, BackendWordMediaService on a one-thread @Async executor. 3. WordService starts the job after saving a new word; GET /api/words adds imageUrl per sense (WordEntryView). 4. Admin API: media per sense with the credit; admin web app shows it. 5. Docker: ffmpeg + media volume, Caddy snippet, env docs (doc-11, .env.example). 6. Pipeline link-media command to put media rows on the server. 7. Tests (fake Pixabay, H2 and real PostgreSQL 16).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built on branch f5/task-49.4 (PR into feature/f5-cms is opened by the orchestrator). Flyway V7 word_lookup_media. Media job: one thread, claim-row per sense (Pixabay asked once per sense), ffmpeg for WebP and MP4, files named word_N_hash8.ext in MEDIA_DIR. imageUrl in GET /api/words via WordEntryView (omitted when none). Admin word items have media[] per sense with the credit; the admin Media row shows photo or clip + credit. Pipeline: new 'link-media' command (import-to-postgres skips known words, so it could not bring media in). Tests: backend 126 on H2, media/admin/word tests also on PostgreSQL 16; admin npm test 17; pipeline pytest 83 on PostgreSQL 16. Test it - Goal: a new word gets a picture on the second lookup, served by the server. Steps: 1) set PIXABAY_API_KEY and MEDIA_DIR in backend/.env, start the backend; 2) curl localhost:8081/api/words/<a new noun, for example harvest> (no imageUrl yet); 3) wait ~10 s and curl it again. Expected: second answer has entry.senses[0].imageUrl = MEDIA_BASE_URL/harvest_1_xxxxxxxx.webp and the file is in MEDIA_DIR; without a key no file and no imageUrl. Automated: cd backend && ./mvnw -B test; cd admin && npm test; cd pipeline && TEST_DATABASE_URL=... uv run pytest.
<!-- SECTION:NOTES:END -->
