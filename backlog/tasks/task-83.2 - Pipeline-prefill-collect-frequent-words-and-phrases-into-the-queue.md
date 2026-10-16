---
id: TASK-83.2
title: 'Pipeline: prefill collect (frequent words and phrases into the queue)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 08:18'
updated_date: '2026-10-16 08:34'
labels: []
dependencies:
  - TASK-83.1
parent_task_id: TASK-83
priority: high
ordinal: 82000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fills the backend prefill_item queue with the words and phrases learners are most likely to type. No paid calls: it only reads word lists and writes queue rows. Words: SCOWL-70 (backend/src/main/resources/words/scowl-70.txt) minus ngsl.txt minus kept topic words (pipeline.db topic_words status keep, TASK-61, these will be bundled), reduced to lemmas (lemminflect), wordfreq Zipf >= 3.5 (about 7.4k forms, maybe 4.5k lemmas, about $3-4 to answer). Phrases: multiword lemmas from Open English WordNet 2025 (phrasal verbs, idioms) ranked by wordfreq. Must be re-runnable so new lists can be added over time.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 uv run pipeline prefill collect --source words|phrases [--zipf 3.5] [--limit N] [--dry-run] inserts queue rows with source and priority (higher frequency first) and skips rows already in the queue
- [ ] #2 Bundled lemmas and forms, topic keep words and words not on the backend word list are never queued
- [ ] #3 uv run pipeline prefill report prints counts by status/source and an estimated cost of the pending items
- [ ] #4 Tests (Postgres tests skip without TEST_DATABASE_URL); doc-6 updated with the commands and their cost
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. prefill.py: word + phrase collectors, insert ON CONFLICT DO NOTHING, report. 2. cli wiring. 3. tests. 4. doc-6 + CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/80. Test it: (1) cd pipeline; uv run pipeline prefill collect --source words --dry-run, then --source phrases --dry-run (about 6,304 words, 2,714 phrases, nothing written). (2) after TASK-83.1 is deployed: collect --source words --limit 20 twice (second run: 20 already queued). (3) collect words and phrases fully. (4) uv run pipeline prefill report (about $4.96 pending). Automated: cd pipeline && uv run pytest (222 passed with a local Postgres). Note: Flyway V16 is taken by media_generation, TASK-83.1 needs V17+.
<!-- SECTION:NOTES:END -->
