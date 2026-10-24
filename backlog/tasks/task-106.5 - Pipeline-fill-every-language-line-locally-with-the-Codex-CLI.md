---
id: TASK-106.5
title: 'Pipeline: fill every language line locally with the Codex CLI'
status: To Do
assignee: []
created_date: '2026-10-24 09:52'
labels: []
dependencies:
  - TASK-106.2
parent_task_id: TASK-106
ordinal: 117000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants all words and sentences translated into all supported languages before users ask, at no per-call cost, using the local Codex CLI on the ChatGPT plan (like collection write). About 7,779 bundled senses x 7 languages plus backend words and cached sentences. Agents write the code and run only a tiny sample; the developer starts the full run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 lines collect reads (read-only) every bundled sense and backend-word sense simple explanation and every cached sentence text from Postgres into a lines table in pipeline.db, one row per (text, language)
- [ ] #2 lines write asks codex exec for many texts of one language per call, checks each answer (not empty, not English, sensible length) and saves progress so a stopped run resumes
- [ ] #3 lines push --yes inserts done rows into the server line_translation with source codex and the current line prompt version, never over an existing row
- [ ] #4 lines report counts done and pending per language; pytest covers collect, the answer checks and push (no real Codex call in tests)
- [ ] #5 doc-6 and doc-18 describe the commands; the final message gives the developer the exact commands and the expected run time
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
