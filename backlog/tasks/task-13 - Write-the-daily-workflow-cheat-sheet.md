---
id: TASK-13
title: Write feature docs and the daily workflow
status: Done
assignee: []
created_date: '2026-10-10 05:46'
updated_date: '2026-10-10 06:20'
labels:
  - docs
dependencies: []
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The user forgets the workflow after a few days away. One short page they can open from the Backlog web UI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 backlog doc 'Workflow' exists with the steps: resume, pick, start, build, test, review, commit, finish
- [x] #2 Each step shows the exact phrase to say to Claude or the command to run
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. docs/features/: one doc per flow (sentence translation, word lookup, dictionary pipeline) with Mermaid diagrams, file:line tables, key rules, error paths, tests, known gaps. Traced from source on 2026-10-10.
2. docs/WORKFLOW.md: daily loop.
3. Backlog definitionOfDone + CLAUDE.md rule so docs stay current.
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added docs/features/ (README with template, sentence-translation, word-lookup, dictionary-pipeline): Mermaid flow + sequence diagrams, file:line tables (links and line numbers checked), errors, tests, change checklists, known gaps. Added docs/WORKFLOW.md. Backlog definition_of_done now has 'Feature doc updated' + 'Tests pass'; CLAUDE.md has a feature-docs rule. All 10 diagrams render with Mermaid 11.4.1.
<!-- SECTION:FINAL_SUMMARY:END -->
