---
id: TASK-91
title: >-
  Admin: draw an AI picture for a sense on the server (editable prompt, Codex
  CLI)
status: To Do
assignee: []
created_date: '2026-10-18 03:22'
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
