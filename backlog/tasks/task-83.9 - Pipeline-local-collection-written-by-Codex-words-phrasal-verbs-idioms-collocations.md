---
id: TASK-83.9
title: >-
  Pipeline: local collection written by Codex (words, phrasal verbs, idioms,
  collocations)
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-17 16:36'
updated_date: '2026-10-17 16:49'
labels:
  - pipeline
dependencies: []
parent_task_id: TASK-83
priority: high
ordinal: 90000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Build a local collection in pipeline.db of English entries Saynata can answer without a paid Claude call: common words not bundled (SCOWL-70 + wordfreq), the 944 kept topic words, the 59 needs_review NGSL words, WordNet phrasal verbs and idioms, and collocations. Codex CLI (codex exec, the developer's ChatGPT plan, no per-call cost) writes each entry in the SAME JSON as the backend word entry (ClaudeService.WORD_PROMPT, WordEntry), and the same checks run (valid JSON, SimpleWords, not-a-word rule). A push step copies checked entries to production only after the developer's go-ahead. Trial first: 20 entries per kind and a side-by-side page against Claude answers.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A collection table in pipeline.db (kind word|phrasal_verb|idiom|collocation, source, zipf, status) is filled by 'pipeline collection collect' from the existing collectors, the topic and needs_review words and a collocation source, and 'collection report' shows the counts per kind
- [ ] #2 'pipeline collection write' asks Codex for the backend WordEntry JSON, runs the backend checks (one retry with feedback, then needs_review), resumes, has --limit and --kind, and saves the model name
- [ ] #3 'pipeline collection compare' writes an HTML page with the Codex entry next to a Claude entry for the trial items
- [ ] #4 'pipeline collection push' copies done entries to production word_lookup (dry run by default, --yes to write) and never overwrites a Claude row
- [ ] #5 Tests pass (uv run pytest) and doc-6, doc-18 and the CLAUDE.md overview are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. collection table + collect (reuse prefill collectors, topic/needs_review words, WordNet phrasal verbs/idioms, bundled usage collocations). 2. Codex writer: backend WORD_PROMPT read from ClaudeService.java, codex exec --output-schema, batches of 5, SimpleWords port + shape checks, one retry, resume. 3. compare page (Claude from NGSL pipeline, server cache, or paid --claude). 4. push to word_lookup (dry run default, ON CONFLICT DO NOTHING). 5. tests + docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-17: built. collect gives 17,124 rows (word 7,243, phrasal_verb 1,818, idiom 2,344, collocation 5,719; 173 SCOWL names skipped). Smoke test: Codex wrote 'elevation' and 'give up' (2 calls, 18 s, both done). Collocation source: the bundled entries' usage items (TASK-24) without placeholders; other options for later: Pearson Academic Collocation List, COCA n-grams (ngrams.info). Push target B (bundled tables) is not built; phrases need a backend + app change (proposal in doc-18). Trial and full runs are the developer's to start. Tests: 259 pass with TEST_DATABASE_URL.
<!-- SECTION:NOTES:END -->
