---
id: TASK-47
title: >-
  iOS: sense media clips play only when visible, loop without stutter, with
  tests and docs
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 10:47'
updated_date: '2026-10-15 03:45'
labels:
  - ios
  - bug
dependencies:
  - TASK-23
priority: medium
type: bug
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-ups from the TASK-23 review (2026-10-14), file EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift. 1) LoopingPlayerUIView builds AVQueuePlayer(playerItem: item) and AVPlayerLooper(templateItem: item) with the same item, which can stutter on the first loop; Apple's pattern is an empty AVQueuePlayer(). 2) Every clip plays all the time, also off screen, which wastes battery on long word cards; play only while visible (iOS 18 onScrollVisibilityChange) and pause in the background. 3) No iOS tests for Sense.imageUrl (keys imageUrl and image_url) or DictionaryStore's optional senses.image_url column. 4) The Word lookup feature page (backlog/docs/features/doc-5) does not describe the media row. No layout change, so no mockup.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 LoopingPlayerUIView uses an empty AVQueuePlayer() with AVPlayerLooper
- [ ] #2 A clip plays only while at least about a third of it is visible on screen, and pauses when scrolled away or when the app goes to the background
- [ ] #3 Unit tests: Sense decodes imageUrl and image_url, encodes imageUrl, and is nil when missing; DictionaryStore reads image_url when the column exists and returns nil for an old file without it
- [ ] #4 doc-5 describes the media row (files, line numbers, offline behaviour) with an updated Last traced date
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. LoopingPlayerUIView: empty AVQueuePlayer() + AVPlayerLooper(templateItem:).
2. Play only while about a third visible (onScrollVisibilityChange threshold 0.33) and scenePhase is active; pause otherwise.
3. Tests: Sense imageUrl/image_url decode, encode imageUrl, nil when missing; DictionaryStore image_url column present/absent.
4. Describe the media row in doc-5, update Last traced.
Done together with TASK-51 in branch fix/task-47-51-media (same file).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR https://github.com/tranphuocloc070699/saynata/pull/48 (TASK-47 and TASK-51 together). Tests: 164 passed on iPhone 16 Pro iOS 18.5.
Test it. Goal: bundled media still works, clips play only when visible, server https media shows and works offline.
Steps now: look up run (clip plays; scroll away pauses; Home pauses), look up dog (photo), a word without media (no box).
Steps after PR #46 is deployed: curl https://api.loffy.me/api/words/<new word> shows imageUrl https://api.loffy.me/media/<file>; look it up in the app; Airplane mode + restart: photo still shows; failed download shows nothing.
Automated: SenseMediaTests (xcodebuild test ... -only-testing:EnglishTranslationTests).
<!-- SECTION:NOTES:END -->
