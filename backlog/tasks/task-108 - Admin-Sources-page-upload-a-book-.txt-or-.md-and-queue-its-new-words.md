---
id: TASK-108
title: 'Admin: Sources page, upload a book (.txt or .md) and queue its new words'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 09:52'
updated_date: '2026-10-24 10:17'
labels: []
dependencies: []
priority: medium
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants to find new words every day from books and news. First step: an admin page (Sources) where the admin uploads a .txt or .md file with a title; the backend finds the words and phrases that are not in the database yet, with no LLM call, and the admin picks which ones go into the prefill queue (then the local Codex writes them). Mockup agreed in chat on 2026-10-24: drop zone + title field, four counts (words in file, known, new words, new phrases), filter chips (Useful, Rare, Names skipped), a table (pick, word, times, how common, a short snippet) and an Add N to queue button. Copyright: the book text is never stored; only words, counts and one short snippet per word for the review, removed after queueing.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/admin/sources (ADMIN only, max 5 MB, .txt or .md) returns counts and candidate words and phrases: base forms (via dictionary_form and the word list), names skipped, words already bundled or cached skipped, each with times seen, a how-common score and a snippet of at most 12 words
- [ ] #2 How-common scores and the phrasal verb and idiom list come from small resource files exported from the pipeline (wordfreq and WordNet), with the export command in doc-6
- [ ] #3 POST /api/admin/sources/{id}/queue adds the picked items to prefill_item with source book:<title>, skipping items already queued
- [ ] #4 The admin Sources page matches the mockup; admin tests (npm test) and backend tests cover upload limits, filtering and queueing; doc-13 and doc-18 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Pipeline: source-lists command writes zipf-en.tsv + phrases-en.tsv into backend resources (done, doc-6).
2. Flyway V28: source_upload table (title, counts, candidates JSON, no book text); widen prefill_item.source to 100.
3. Backend sources/: WordFrequency + PhraseList resources, SourceAnalyzer (tokens, names, lemma, known check, phrases, snippets), AdminSourcesController (POST upload, GET one, POST queue), allow multipart in JsonWritesOnlyFilter.
4. Tests (upload limits, filtering, queueing).
5. Admin SourcesPage + nav + tests.
6. Docs doc-13, doc-18, doc-6, CLAUDE.md; PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built: pipeline source-lists (free, ran locally), backend sources/ (Flyway V28), admin Sources page, docs 13/14/18/6 and CLAUDE.md. Tests: backend 539 pass, admin 118 pass + build, pipeline 247 pass. Test it: start backend + admin, open Sources, upload a .txt/.md with a title, check tiles and chips, pick words, Add N to queue, see them Pending (source book:<title>); failure: .pdf gives 'Upload a .txt or .md file.', over 5 MB gives 'That file is too big.'
<!-- SECTION:NOTES:END -->
