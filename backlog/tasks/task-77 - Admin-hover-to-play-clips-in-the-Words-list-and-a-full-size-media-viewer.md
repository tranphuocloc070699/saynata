---
id: TASK-77
title: 'Admin: hover to play clips in the Words list, and a full-size media viewer'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 14:37'
updated_date: '2026-10-15 14:44'
labels:
  - admin
  - web
dependencies: []
priority: medium
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer approved mockup 2026-10-15. (1) A Words list row with a clip plays it muted, looping, inline in the 56x42 thumbnail while the mouse is over the row; only the hovered row has a video element (created on hover, preload none). (2) Clicking the photo or clip in the word detail Media row, or a zoom icon on a Pick-another result tile, opens a full-size viewer (dark backdrop, caption, close button, Esc and backdrop click close, focus returns).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Hovering a clip row plays it muted and looping in the thumbnail, leaving pauses and resets; only one row has a video; photos and touch devices do not change
- [ ] #2 Media row photo or clip and a zoom icon on each Pick-another tile open a lightbox with the right source and a caption word - sense N - Photo or Clip by author on Pixabay
- [ ] #3 Lightbox closes with the icon-only Close button, Esc, or a backdrop click, and focus returns to the clicked element
- [ ] #4 Vitest covers hover, the lightbox and closing; doc-14 and doc-7 are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Hover to play (HoverClip in WordsPage) and MediaLightbox (Radix Dialog) built in the same PR as TASK-76. Admin tests pass.

PR: https://github.com/tranphuocloc070699/saynata/pull/69. Test it: hover a clip row in Words (plays muted, stops on leave); click a Media row photo or a Pick-another zoom icon (viewer, Esc/backdrop/X close).
<!-- SECTION:NOTES:END -->
