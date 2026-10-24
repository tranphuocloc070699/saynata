---
id: TASK-108
title: 'Admin: Sources page, upload a book (.txt or .md) and queue its new words'
status: To Do
assignee: []
created_date: '2026-10-24 09:52'
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
