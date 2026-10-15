---
id: TASK-64
title: 'Admin: reset the Media row when another word is picked'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:59'
updated_date: '2026-10-15 07:59'
labels:
  - admin
dependencies: []
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
After picking another word, the Media row (photo or video, an open Pick another panel, its results and error) still shows the previous word's content, because React reuses the component and the video element keeps its old src.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The Media row is keyed by word id and sense index, so its state resets for another word or sense
- [ ] #2 A video element reloads when its src changes
- [ ] #3 Vitest: select word 1, open the panel, select word 2: the panel is closed and shows word 2's media
- [ ] #4 doc-14 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. key MediaRow by word id and sense index; key video by src. 2. Vitest with two words. 3. doc-14.
<!-- SECTION:PLAN:END -->
