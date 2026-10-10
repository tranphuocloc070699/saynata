---
id: TASK-23
title: 'Pipeline: small images and action clips from Pexels'
status: To Do
assignee: []
created_date: '2026-10-10 07:27'
updated_date: '2026-10-10 08:25'
labels:
  - pipeline
dependencies:
  - TASK-3
references:
  - 'https://www.pexels.com/api/documentation/'
  - 'https://www.pexels.com/license/'
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Senses with image_type photo or icon have an image_query but no image (image_url is empty). The user wants free stock media, as small as possible so it shows fast. Pexels fits best: its license allows resizing and storing copies, and it has a Videos API for actions (verbs). Unsplash's API terms require hotlinking their URLs, so it can't be resized and stored the same way — only use it if hotlinking is fine. Real GIFs are large; a 2-3 s muted MP4 loop is usually 5-10x smaller for the same clip and plays natively on iOS. ~20% of senses need media (measured on the first 245 words), so roughly 1,500-2,500 files for NGSL.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Nouns and adjectives (photo): one Pexels photo per sense, resized to 320 px and encoded as WebP or HEIC, about 15-30 KB
- [ ] #2 Verbs (actions): one Pexels video per sense, cut to a 2-3 s loop, 320 px, no audio, H.264/HEVC MP4, about 50-150 KB (GIF only if the app truly needs it)
- [ ] #3 Photographer name and Pexels link stored per file for attribution
- [ ] #4 Resume-safe like the text step: progress in pipeline.db, skips files already done
- [ ] #5 Decision recorded: bundle in the app vs download on first use (size budget for the app)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
