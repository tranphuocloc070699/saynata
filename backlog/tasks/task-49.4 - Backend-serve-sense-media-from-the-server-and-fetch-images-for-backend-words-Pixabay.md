---
id: TASK-49.4
title: >-
  Backend: serve sense media from the server and fetch images for backend words
  (Pixabay)
status: To Do
assignee: []
created_date: '2026-10-14 11:22'
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
