---
id: TASK-83.3
title: 'Backend: loose sentence match (case and final punctuation)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 08:19'
updated_date: '2026-10-16 08:31'
labels: []
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The sentence cache key is the exact text, so "how are you" misses a cached "How are you?". Pre-filled phrases only help if small typing differences still match. Decision 10-16: on an exact miss, look again with a loose key (lowercase, final . ? ! removed, spaces collapsed). Case can matter ("US" vs "us"), so the exact key always wins first.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Saved sentence rows get a loose match key (new Flyway column + index, filled for old rows)
- [ ] #2 POST /api/translations and /stream answer an exact hit first, then a loose hit, before calling Claude; the response says cached=true
- [ ] #3 Tests cover exact wins over loose, loose hit, personal text never matched; doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CacheKeys.normalizeSentenceLoose + sentenceMatchKey. 2. Flyway V17 match_key (Java migration so Java and DB keys are the same code; H2 tests and Postgres differ in hash functions) + index, backfill. 3. SentenceTranslation sets match_key; repository finds oldest by match_key. 4. TranslationService: exact hit, then loose hit, then Claude. Stream uses the same method. 5. Tests, doc-4.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Admin sentence PUT only changes the explanation (source text and keys never change), so match_key stays right. No prompt version bump: lookup change only.

PR: https://github.com/tranphuocloc070699/saynata/pull/78. Test it: run a local backend (port 8082), curl POST /api/translations with 'How are you?' (cached=false), then 'how are you' (cached=true, no Claude call), then targetLang ja (cached=false), personal text twice (cached=false both). Automated: cd backend && ./mvnw -B test (345 pass). V17 is a Java migration (H2 vs Postgres hash functions differ).

Renamed migration to V18 (V16 is TASK-82, V17 is TASK-83.1).
<!-- SECTION:NOTES:END -->
