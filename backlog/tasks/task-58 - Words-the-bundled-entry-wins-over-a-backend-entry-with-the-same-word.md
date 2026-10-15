---
id: TASK-58
title: 'Words: the bundled entry wins over a backend entry with the same word'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:16'
updated_date: '2026-10-15 07:16'
labels:
  - backend
dependencies: []
priority: high
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The admin list shows some words twice, once as Bundled and once as Backend (example: "run"). The backend row in word_lookup was made by Claude before the bundled dictionary was imported into Postgres. Rule decided by the developer: the bundled entry wins.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/words/{word} returns the bundled entry (with its media) from Postgres when the word is in dictionary_word, never calls Claude and never creates a word_lookup row; the response shape is the same
- [ ] #2 A Flyway migration deletes word_lookup rows whose word is in dictionary_word, with their word_lookup_media rows
- [ ] #3 Backend tests cover the bundled lookup and the migration SQL
- [ ] #4 The PR lists which production rows the migration deletes
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. WordService.lookUp checks dictionary_word by lemma first and returns the bundled entry with media: no Claude, no save. 2. Flyway V9 deletes word_lookup rows whose word is a dictionary_word lemma (media links first). 3. Tests for the service and the migration SQL. 4. Docs 5, 13, 15.
<!-- SECTION:PLAN:END -->
