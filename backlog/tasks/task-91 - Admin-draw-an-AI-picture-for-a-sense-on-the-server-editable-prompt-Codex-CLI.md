---
id: TASK-91
title: >-
  Admin: draw an AI picture for a sense on the server (editable prompt, Codex
  CLI)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-18 03:22'
updated_date: '2026-10-18 04:05'
labels:
  - admin
  - backend
  - media
dependencies: []
ordinal: 95000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today the admin button Mark for AI only sets media_status = needs_ai and shows 'Waiting for an AI image'; nothing on the server draws a picture. Pictures come only from pipeline media-ai / media-generate on the developer's Mac, then rsync and --link. The developer wants to fix one sense from the admin: open a dialog with the picture prompt already filled in, edit it, press OK, and the server draws the picture and links it to the sense. Developer decisions (2026-10-18): draw with the Codex CLI on the VPS using the developer's ChatGPT login (auth.json copied to the server; no per-picture cost, about 1 minute) instead of the OpenAI image API (about $0.004 a picture, OpenAI recommends API keys for automation); layout A (a dialog, from the mockup); include the wand button (Claude Sonnet writes a new scene, about $0.02); a $1 a day cap. Prefill: the newest media-ai scene of the sense (media_generation.scene_json) as the Codex image prompt, else the media-generate template (word, meaning, easy example) with the media-ai style. No blind-guess check on the server: the new picture goes to 'Media to review'.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clicking the AI button on a sense's media row opens a dialog with the prompt already filled in (newest media-ai scene, else the template), the current picture and the meaning; buttons are icon-only with accessibility labels
- [ ] #2 The wand button asks Claude Sonnet 5.5 for a new scene through ClaudeGate, saved as admin usage, and stops at a $1 a day cap for these calls
- [ ] #3 OK starts a background job on the server that runs codex exec with the edited prompt; the admin shows drawing, done and failed states and keeps polling after the dialog closes
- [ ] #4 A finished picture is cropped to 3:2, saved as a hashed WebP in MEDIA_DIR, gets a media row with source ai, replaces the sense's picture for bundled and backend words (old file cleaned up like Upload), sets media_status ai_generated and clears the review
- [ ] #5 A daily picture count cap (configurable) and a missing or logged-out Codex give clear errors and keep the old picture
- [ ] #6 Codex runs with a clean environment (no DB password or API keys), a read-only sandbox by default and a timeout; jobs left running at restart are marked failed
- [ ] #7 Backend and admin tests cover the prompt, the job states, the caps and the link; no test calls Claude or Codex for real
- [ ] #8 doc-15, doc-13, doc-14, doc-11 (Codex on the VPS: Docker image, auth.json volume) and doc-7 are updated; PR into feature/local with a Test it card
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V21 ai_picture_job. 2. Backend: AiPictureProperties, prompt builder (constants from media_ai.py), scene via ClaudeService (Sonnet), caps, ImageDrawer + CodexImageDrawer, single-thread job runner, save+link like upload. 3. Admin endpoints. 4. Dockerfile/compose/env. 5. Admin dialog + polling. 6. Tests. 7. Docs (doc-7,9,11,13,14,15, CLAUDE.md). 8. PR into feature/local.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/118 (into feature/local).

Test it
Goal: press Sparkles on a sense in the admin, edit the prompt, OK, and see a new AI picture ("AI image (codex)") after about 1 minute.
Part 1 (Mac): codex login status; AI_PICTURES_ENABLED=true ./mvnw spring-boot:run in backend/; cd admin && npm run dev; open a word, Sparkles, edit, (optional wand, about $0.02), check icon. The row shows "Drawing an AI picture... you can leave this page" (it survives a reload), then the new picture in "Media to review".
Failure cases: AI_PICTURES_ENABLED off gives "AI pictures are not set up"; codex logout gives a red "The AI picture failed: Codex is not signed in on the server" and the old picture stays; an empty prompt gives "Write a prompt first."
Part 2 (server, doc-11 section 12): mkdir /var/lib/saynata/codex (chown 10001), copy ~/.codex/auth.json (chmod 600), AI_PICTURES_ENABLED=true in .env, redeploy, docker compose exec backend codex login status, draw one picture.
Cost: Codex uses the ChatGPT plan (no per-picture cost, 30 a day); the wand is about $0.02 a call ($1 a day cap).
Automated: cd backend && ./mvnw -B test (444 pass); cd admin && npm test (97 pass).
Not verified: a real Codex picture, Codex inside Docker with a login (the read-only sandbox may need workspace-write), the amd64 image build, the dialog in a real browser.
<!-- SECTION:NOTES:END -->
