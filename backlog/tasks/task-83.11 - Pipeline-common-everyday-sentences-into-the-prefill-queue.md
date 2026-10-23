---
id: TASK-83.11
title: 'Pipeline: common everyday sentences into the prefill queue'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-22T09:54:00Z'
updated_date: '2026-10-23T02:00:00Z'
labels:
  - pipeline
  - prefill
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Words are well covered in the shared cache (about 34k), but the short everyday lines learners meet all the time ("Looks good", "No worries", "I'll get back to you") are not, so each one costs a slow first Claude call, and a guest gets no answer at all. The developer wants these pre-answered by the night job (TASK-83.1). The list is written by hand (no paid call, no copied list such as Tatoeba or a textbook) and queued as kind sentence, target vi, with common replies first. TASK-83.3 makes the cache match ignore case and final punctuation, so one row covers "Looks good." and "looks good".
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 pipeline/data/common_sentences.tsv has about 2,000-3,000 rows (text, situation, kind), 2-15 words each, no names, personal data, rude lines, or duplicates after lowercasing and removing final punctuation
- [ ] #2 uv run pipeline prefill collect --source sentences [--file] [--dry-run] queues new rows into prefill_item as kind sentence with a priority (common replies first) and a source label, skipping rows already queued and lines the backend PersonalTextDetector would skip
- [ ] #3 --dry-run writes nothing and prints the count and a cost estimate at the deepseek-v4.1-flash prices in application.properties
- [ ] #4 Tests cover parsing, duplicates, the dry run and the insert (Postgres tests skipped without TEST_DATABASE_URL); doc-18, doc-6 and the CLAUDE.md pipeline row are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Write the list by hand in kind/situation groups, build pipeline/data/common_sentences.tsv (text, situation, kind) with a check for length, duplicates (loose key: lowercase, no final . ? !), and the PersonalTextDetector patterns.
2. prefill.py: read_sentence_file, loose_key, looks_personal (mirror of the backend), priority by kind and length, deepseek price from application.properties, sentence cost estimate.
3. cli.py: --source sentences with --file; skip rows whose loose key is already queued as a vi sentence; dry run prints count and cost.
4. Tests in test_prefill.py; docs doc-18, doc-6, CLAUDE.md; check_docs.py; PR into feature/local.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built on prefill/common-sentences (PR into feature/local). 2,488 hand-written lines in pipeline/data/common_sentences.tsv (19 kinds; pipeline/.gitignore now keeps this one file from data/). `prefill collect --source sentences [--file] [--dry-run] [--limit]`: loose-key dedupe (CacheKeys.normalizeSentenceLoose copy), PersonalTextDetector copy, priority by kind (reply 900 first) + 4 per word under 15, source common-sentences, kind sentence, target vi. Dry run: 2,488 lines about $1.34 at deepseek-v4.1-flash prices (1,000 in / 200 out tokens a line is an estimate), about 3 nights at the $0.50 cap. Tests: 11 new in test_prefill.py; all 34 pass against a throwaway local Postgres 16, full suite 233 passed. Real insert checked only against that local DB, never production.
<!-- SECTION:NOTES:END -->
