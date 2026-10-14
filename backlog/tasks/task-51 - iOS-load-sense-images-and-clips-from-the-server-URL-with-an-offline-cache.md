---
id: TASK-51
title: 'iOS: load sense images and clips from the server URL, with an offline cache'
status: To Do
assignee: []
created_date: '2026-10-14 11:22'
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
