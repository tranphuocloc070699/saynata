---
id: TASK-51
title: 'iOS: load sense images and clips from the server URL, with an offline cache'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 11:22'
updated_date: '2026-10-15 03:45'
labels:
  - ios
  - media
dependencies:
  - TASK-49.4
priority: medium
type: task
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decision (doc-7, 2026-10-14, option B). SenseMediaView (EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift) today loads only bundled files. Make it load https URLs from Sense.imageUrl: photos with a small disk cache (URLCache with a size limit, or a cache folder in Caches), clips streamed by AVPlayer and cached after the first play; keep bundled files working for old data. Same fixed 240x135 frame, no layout change (no mockup). While loading, show the empty frame area hidden (no jump); on failure show nothing. Runs on the developer's Mac (needs Xcode).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A sense with an https imageUrl shows the photo or plays the muted clip loop
- [ ] #2 After the first view the photo shows with the network off (cache)
- [ ] #3 Bundled media paths from old data still work; a failed download shows no media and no error
- [ ] #4 Unit tests for URL handling (bundled path vs https URL) and the cache
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SenseMediaSource: a path is bundled or an https URL (other schemes ignored). Server format: absolute https://api.loffy.me/media/<word>_<n>_<hash>.webp|.mp4 (from PR #46, WordEntryView + MediaProperties.urlFor).
2. MediaFileCache (actor): files in Caches/SenseMedia, name = sha256(url)+extension, size limit 50 MB (oldest file removed first), one download per URL at a time, injected downloader for tests.
3. SenseMediaView: remote photo = cache then download; hidden (space kept) while loading, nothing on failure. Remote clip = AVPlayer streams the URL, cache fills after first play, next play uses the file.
4. Tests for source parsing and cache. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR https://github.com/tranphuocloc070699/saynata/pull/48 (TASK-47 and TASK-51 together). Tests: 164 passed on iPhone 16 Pro iOS 18.5.
Test it. Goal: bundled media still works, clips play only when visible, server https media shows and works offline.
Steps now: look up run (clip plays; scroll away pauses; Home pauses), look up dog (photo), a word without media (no box).
Steps after PR #46 is deployed: curl https://api.loffy.me/api/words/<new word> shows imageUrl https://api.loffy.me/media/<file>; look it up in the app; Airplane mode + restart: photo still shows; failed download shows nothing.
Automated: SenseMediaTests (xcodebuild test ... -only-testing:EnglishTranslationTests).
<!-- SECTION:NOTES:END -->
