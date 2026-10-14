---
id: TASK-49.3
title: >-
  Move the bundled dictionary and media into Postgres; pipeline exports
  dictionary.sqlite from it
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
updated_date: '2026-10-14 10:54'
labels:
  - backend
  - pipeline
  - cms
dependencies:
  - TASK-49.1
parent_task_id: TASK-49
priority: medium
type: task
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Make the server Postgres the source of truth (doc-7, 2026-10-14). Flyway migration for dictionary tables (words, senses, forms, media with Pixabay attribution) or a source column on word_lookup: decide in the plan and ask the developer if unsure. A one-time import of the current dictionary.sqlite and the media files (store media on the VPS disk, served by Caddy, or keep them in the repo: decide in the plan). Change pipeline/export.py to build dictionary.sqlite and the media folder from Postgres (read-only connection string from pipeline/.env). Admin API lists bundled words too. Do not run paid Claude passes. Update doc-6 (Dictionary pipeline) and CLAUDE.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All 2,750 bundled words and their senses, examples, usage and media are in Postgres after the import (count check test)
- [ ] #2 pipeline export builds a dictionary.sqlite from Postgres that the app reads unchanged (same schema; pytest)
- [ ] #3 An edit made in the admin for a bundled word appears in the next exported dictionary.sqlite
- [ ] #4 doc-6 and CLAUDE.md describe the new source of truth and commands
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
