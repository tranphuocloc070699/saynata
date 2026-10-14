---
id: TASK-23
title: 'Pipeline: small images and action clips from Pixabay'
status: Done
assignee:
  - '@loc'
created_date: '2026-10-10 07:27'
updated_date: '2026-10-13 09:20'
labels:
  - pipeline
dependencies:
  - TASK-3
references:
  - 'https://pixabay.com/api/docs/'
  - 'https://pixabay.com/service/license-summary/'
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Senses with image_type photo or icon have an image_query but no image (image_url is empty). The user wants free stock media, as small as possible so it shows fast. Switched from Pexels to Pixabay because Pexels paused new API keys. Pixabay provides an instant API key, generous rate limits (100 req/min), both Photos and Videos endpoints, and permits local resizing, caching, and bundling. Real GIFs are large; a 2-3s muted MP4 loop is 5-10x smaller for the same clip and plays natively on iOS. Files are resized to 320px (WebP for photos, MP4 for video loops) and stored for offline bundling.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Nouns and adjectives (photo): one Pixabay photo per sense, resized to 320 px WebP, about 15-30 KB
- [x] #2 Verbs (actions): one Pixabay video per sense, cut to a 2-3 s loop, 320 px, no audio, H.264/HEVC MP4, about 50-150 KB
- [x] #3 Photographer name and Pixabay link stored per file for attribution
- [x] #4 Resume-safe like the text step: progress in pipeline.db, skips senses/files already done
- [x] #5 Media bundled in the app alongside dictionary.sqlite for offline 0ms load
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #2 Tests pass
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Build pipeline/src/pipeline/media.py with Pixabay Photos & Videos fetchers.
2. Implement local media processor: Pillow (320px WebP) and ffmpeg (320px muted MP4 loop).
3. Save media in data/media/ and track progress/attribution in pipeline.db.
4. Add CLI commands in cli.py: 'media' with --dry-run, --words, --limit, --workers.
5. Add unit tests in tests/test_media.py with fake HTTP fixtures.
6. Verify dry-run with real Pixabay API key on sample words.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Phase 1 complete: Added pillow>=10.0 to pyproject.toml, synced via uv (Pillow 12.3.0 with WebP support verified). Created data/media/ directory. Verified ffmpeg 7.1.1 is available. All 61 existing pytest tests pass.

Implemented media.py, CLI commands (pipeline media), export.py image_url population, and unit tests (test_media.py). Dry run on sample words (apple, run, bank) verified against Pixabay API with both photos and video action loops.

Phase 2 & iOS integration complete:
- Updated DictionaryStore to load sense.image_url and bind to WordEntry/Sense.
- Created SenseMediaView with AVPlayerLayer LoopingVideoView for MP4s and UIImage for WebP photos.
- Standardized thumbnail dimension to 240x135 with scaledToFill and block corner radius to prevent layout shift and eliminate empty border space.
- Verified on iPhone 16 Pro simulator (iOS 18.5) with real samples: 'dog' (WebP photo), 'tree' (WebP photo), and 'run' (3.0s looping MP4).
- All 66 pipeline tests and all 129 iOS tests pass.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented media pipeline with Pixabay photos and action loops, WebP/MP4 transcoding, export mapping, SenseMediaView in iOS app, and verified on iPhone 16 Pro simulator.
<!-- SECTION:FINAL_SUMMARY:END -->
