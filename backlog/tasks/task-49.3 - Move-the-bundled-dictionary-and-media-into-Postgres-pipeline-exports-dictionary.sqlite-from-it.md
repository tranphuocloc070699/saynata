---
id: TASK-49.3
title: >-
  Move the bundled dictionary and media into Postgres; pipeline exports
  dictionary.sqlite from it
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 10:54'
updated_date: '2026-10-14 11:56'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V6: tables dictionary_word, dictionary_sense (examples/usage as JSON text, media_id), dictionary_form, media (file name, kind photo|clip, size, Pixabay attribution); word_lookup.cefr column; view admin_word_list (bundled + backend rows, ids b-N / w-N).
2. Backend: entities DictionaryWord/DictionarySense, AdminWordController lists both sources (JdbcClient over the view) with q, needs_review, source, cefr filters; GET/PUT/reviewed work for both id kinds; PUT on bundled updates dictionary tables with the same EntryParser validation. Backfill word_lookup.cefr at start-up.
3. Pipeline: psycopg; import-to-postgres (idempotent, skip existing lemmas, --overwrite) from dictionary.sqlite (+ pipeline.db media metadata, media files for size/width/height); export builds dictionary.sqlite + jsonl + media folder from Postgres (read-only); old pipeline.db export kept as export-local.
4. Tests: backend H2 tests; pipeline pytest against a real Postgres (TEST_DATABASE_URL, schema from the Flyway files); full real run import -> export on PG16 and Spring Boot against PG16.
5. Docs: doc-6, doc-13, doc-11, CLAUDE.md, pipeline README/.env.example.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built: Flyway V6 (dictionary_word, dictionary_sense, dictionary_form, media, word_lookup.cefr, view admin_word_list); admin words API lists bundled + backend words (ids b-N / w-N, filters source and cefr, PUT on bundled updates dictionary tables); pipeline import-to-postgres and export (read-only) from Postgres; export-local keeps the old pipeline.db export. Tests: backend 109 passed (H2); pipeline 81 passed with TEST_DATABASE_URL (68 passed, 13 skipped without); real PG16: Flyway V1-V6 applied, full import 2750/7779/5542, admin filters, PUT on b-191 then export: only that sense row differs from the bundled file. Test it: run pipeline tests with TEST_DATABASE_URL, then import-to-postgres and export against a local Postgres where the backend has run once. PR: opened by the orchestrator into feature/f5-cms.

TEST IT. Goal: the bundled dictionary lives in Postgres, the admin lists and edits it, and export builds the same dictionary.sqlite from it. Steps: 1) start a local Postgres, run the backend once (cd backend && SPRING_DATASOURCE_URL=... ./mvnw spring-boot:run) so Flyway V6 runs; 2) cd pipeline, set DATABASE_URL in .env, uv run pipeline import-to-postgres (expect: Words written: 2750, Senses: 7779, forms: 5542), run it again (expect: skipped 2750); 3) log in to the admin API, GET /api/admin/words?source=bundled&cefr=A1,A2 (X-Total-Count 1218), PUT /api/admin/words/b-191 with a changed definition; 4) uv run pipeline export --media-dir ../EnglishTranslation/EnglishTranslation/Resources/media (expect only that sense differs from the bundled file). Failure case: DELETE /api/admin/words/b-191 gives 400; export without DATABASE_URL says to set it. Automated: cd backend && ./mvnw -B test; cd pipeline && TEST_DATABASE_URL=postgresql://user@host/db uv run pytest. PR link: given by the orchestrator (gh not available here); branch f5/task-49.3.
<!-- SECTION:NOTES:END -->
