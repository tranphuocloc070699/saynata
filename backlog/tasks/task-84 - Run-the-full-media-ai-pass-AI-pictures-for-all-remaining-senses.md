---
id: TASK-84
title: Run the full media-ai pass (AI pictures for all remaining senses)
status: To Do
assignee: []
created_date: '2026-10-16 08:51'
labels:
  - media
  - pipeline
dependencies: []
ordinal: 82000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run media-ai (TASK-82, PR #76 + #77) on every remaining sense, then copy the pictures to the server and link them. Stopped on 2026-10-16 by the developer to run later.

State on 2026-10-16: 60 senses linked (20-sense trial + next 50, minus 10 failed). A full run was started and stopped after 8 more senses (7 passed, saved in media_generation, NOT linked yet; Claude $0.06). About 3,286 senses are left.

Estimate: about 26 hours with 4 workers (about 1 min per Codex picture, about 1.5 tries per sense), Claude about $34 (Sonnet 5.5 scenes + Haiku 5.5 checks, about $0.01 per sense). Codex pictures are covered by the ChatGPT plan. Keep the Mac awake and the lid open.

Commands (in pipeline/):
1. caffeinate -i uv run pipeline media-ai --all --workers 4 --max-cost 40 --report data/out/media-ai.html
   (stops cleanly at the cap or a plan limit; running it again continues)
2. While it runs, delete Codex's temporary PNGs now and then: find ~/.codex/generated_images -name '*.png' -mmin +60 -delete
3. rsync -a --ignore-existing data/media/ vps_sieutoc:/var/lib/saynata/media/ && ssh vps_sieutoc 'chown -R 10001:10001 /var/lib/saynata/media'
4. uv run pipeline media-ai --link
5. Review failed senses in data/out/media-ai.html, and the linked ones in the admin's Media to review.
6. uv run pipeline export, then an app build, so the iOS app gets the new pictures.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Full media-ai run finished (or stopped at the cap/plan limit and resumed until done)
- [ ] #2 Pictures copied to the server and passed senses linked
- [ ] #3 Failed senses reviewed (upload, remove or set image_type none)
- [ ] #4 pipeline export and an app build ship the new pictures
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
