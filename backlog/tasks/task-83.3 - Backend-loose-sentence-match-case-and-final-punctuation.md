---
id: TASK-83.3
title: 'Backend: loose sentence match (case and final punctuation)'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-16 08:19'
updated_date: '2026-10-23 07:23'
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
- [x] #1 Saved sentence rows get a loose match key (new Flyway column + index, filled for old rows)
- [x] #2 POST /api/translations and /stream answer an exact hit first, then a loose hit, before calling Claude; the response says cached=true
- [x] #3 Tests cover exact wins over loose, loose hit, personal text never matched; doc-4 updated
- [ ] #4 Loose key also ignores curly vs straight quotes and apostrophes, any whitespace, and a final … or trailing comma; Flyway V24 recomputes match_key for saved rows
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CacheKeys.normalizeSentenceLoose + sentenceMatchKey. 2. Flyway V17 match_key (Java migration so Java and DB keys are the same code; H2 tests and Postgres differ in hash functions) + index, backfill. 3. SentenceTranslation sets match_key; repository finds oldest by match_key. 4. TranslationService: exact hit, then loose hit, then Claude. Stream uses the same method. 5. Tests, doc-4.

6. (2026-10-22, follow-up) Loose key also ignores curly vs straight quotes/apostrophes, every kind of space, and a final … or comma; Flyway V24 (Java) recomputes match_key for saved rows; tests for 'Looks good!' vs 'looks good', 'Is it OK?' decision, cachedOnly and stream hits, prefill runner; doc-4, doc-18, doc-7, doc-9.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Admin sentence PUT only changes the explanation (source text and keys never change), so match_key stays right. No prompt version bump: lookup change only.

PR: https://github.com/tranphuocloc070699/saynata/pull/78. Test it: run a local backend (port 8082), curl POST /api/translations with 'How are you?' (cached=false), then 'how are you' (cached=true, no Claude call), then targetLang ja (cached=false), personal text twice (cached=false both). Automated: cd backend && ./mvnw -B test (345 pass). V17 is a Java migration (H2 vs Postgres hash functions differ).

Renamed migration to V18 (V16 is TASK-82, V17 is TASK-83.1).

2026-10-22 follow-up (branch prefill/task-83.3): CacheKeys.normalizeSentenceLoose now also makes ‘ ’ “ ” (and friends) straight, collapses every kind of space (\s and \p{Z}, so the no-break space too) and drops a final … or comma, besides case and . ? !. Inner punctuation is kept. V24__recompute_sentence_match_key.java (Java, same reason as V18: sha256 of Java-normalised text, H2 vs Postgres) recomputes every match_key and writes only the changed ones; source_text is never touched. The text shown to the user is still what they typed.

Decision: the final '?' does NOT matter ('Is it OK' finds 'Is it OK?'). Reason: people type questions without '?' on a phone, the same words almost always mean the same question, and keeping it would make most prefilled questions miss. Cost: 'You're coming.' and 'You're coming?' share one answer. Exact key still wins first. Row in doc-7 (Claude's call, not yet reviewed by the developer) and a question at the end of doc-7.

Prefill runner uses TranslationService.translate, so it gets the loose match for free (new PrefillRunnerTest: queued 'Looks good!' + 'looks good' = one Claude call, one row, second item cached). Gap: prefill_item is unique on the exact text, so both can still be queued (doc-18 known gaps). The phone cache still uses the exact key (doc-4 known gaps).

Tests: mvn -B test, 496 pass (./mvnw could not download Maven in the cloud box, the installed mvn was used).

PR (follow-up, 2026-10-22): https://github.com/tranphuocloc070699/saynata/pull/138 into feature/local. Test it card in the PR.

2026-10-23: merged into feature/local (PR #138, after a doc merge with TASK-83.11/83.12). Backend 500 tests pass. The developer tested it and said it works.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The loose sentence match also ignores curly quotes, a final … and trailing commas (V24 recomputes old keys). Verified: backend tests (500 pass, incl. CacheKeysTest, CachedOnlyTest, SentenceMatchKeyMigrationTest) and the developer's manual test.
<!-- SECTION:FINAL_SUMMARY:END -->
