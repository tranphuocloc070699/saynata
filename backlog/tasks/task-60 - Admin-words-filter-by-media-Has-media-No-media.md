---
id: TASK-60
title: 'Admin words: filter by media (Has media, No media)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:16'
updated_date: '2026-10-15 07:16'
labels:
  - backend
  - admin
dependencies: []
priority: medium
ordinal: 62000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants to see which words have a picture or clip and which do not, while a Pixabay pass adds media to bundled words. A word has media if any of its senses has a linked media row.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/admin/words accepts media=with|without (anything else is 400); with = any sense has media (dictionary_sense.media_id for bundled words, word_lookup_media.media_id for backend words)
- [ ] #2 Admin chips "Has media" and "No media" (one at a time) send the param to the server
- [ ] #3 Backend and admin tests cover it; doc-13, doc-14 and doc-15 are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: media=with|without in AdminWordService.list with EXISTS subqueries (no view change). 2. Chips Has media and No media, one at a time. 3. Tests both sides; docs 13, 14, 15.
<!-- SECTION:PLAN:END -->
