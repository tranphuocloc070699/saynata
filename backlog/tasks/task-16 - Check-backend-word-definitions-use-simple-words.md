---
id: TASK-16
title: Check backend word definitions use simple words
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-10 06:22'
updated_date: '2026-10-12 02:26'
labels:
  - backend
dependencies: []
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The pipeline rejects definitions with words outside NGSL (pipeline/src/pipeline/validate.py). The backend saves Claude's word entries without that check, so backend definitions can be harder than bundled ones. See doc-5 Word lookup, Known gaps.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Backend entries are checked against the same simple-word rules (or the decision not to is recorded)
- [x] #2 Entries that fail are retried with feedback or flagged
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SimpleWords class: ngsl.txt + simple-extra.txt, rules copied from validate.py. 2. WordService: check, one retry with feedback (describeWordAgain), else save with needs_review (V4). 3. Mocked tests. 4. Update doc-5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented SimpleWords + one retry + needs_review flag (V4). Backend tests: 41 pass. Test it: run ./mvnw -B test; flagged rows: SELECT word FROM word_lookup WHERE needs_review. See PR.

PR: https://github.com/tranphuocloc070699/saynata/pull/31

2026-10-12: merged in main (PR #31). Re-verified on main: ./mvnw test, 43 tests, 0 failures (WordServiceTest covers retry + needsReview).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Backend word entries now pass the same NGSL simple-word check as the pipeline; one retry with feedback, else saved with needs_review (V4). Verified with ./mvnw test on main (43 pass).
<!-- SECTION:FINAL_SUMMARY:END -->
