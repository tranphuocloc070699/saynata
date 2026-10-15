---
id: TASK-55
title: Show why a word needs review in the admin
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 06:21'
updated_date: '2026-10-15 06:27'
labels:
  - backend
  - admin
dependencies: []
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Words saved with needs_review = true (hard words still left after one retry, see WordService and SimpleWords.problems) show only a "Needs review" badge in the admin. The reasons are not stored, so the developer does not know what to fix. "working" and "apple" are flagged in production. Developer decision 2026-10-15: when an entry needs review, show the reason. The reasons are computed when the entry is read, not stored (no migration, works for words already flagged, stays right after an edit).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AdminWord (list, get, update, reviewed) has reviewReasons: string[], computed with SimpleWords.problems on the current entry; empty when the word is clean
- [ ] #2 Admin word detail shows the reasons as a list under the Status row when the word needs review
- [ ] #3 Admin edit form shows the same list at the top; when the reasons are empty it says "No problems found now; press ✓ to mark it reviewed"; the flag is never cleared automatically
- [ ] #4 Backend and admin Vitest tests cover it; doc-13 and doc-14 are updated; scripts/check_docs.py passes
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. AdminWord gets reviewReasons; AdminWordService injects SimpleWords and fills it (list rows, get, update, reviewed) only when needsReview is true.
2. Admin types: reviewReasons on WordItem. WordsPage: list under Status row; top of the edit form (also says 'No problems found now' when empty).
3. Tests: backend AdminBundledWordsTest style, Vitest in wordsPage.test.tsx.
4. Update doc-13, doc-14; run check_docs.py.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/55. Test it: after merge and deploy, open https://admin.loffy.me, press the Needs review chip, open "working" or "apple". Expect a reason list under Status (e.g. "sense 1: the definition uses hard words [abstain]") and the same list at the top of the edit form. After a fixing save the list is recomputed; if empty it says "No problems found now; press ✓ to mark it reviewed" and the badge stays. Automated: cd backend && ./mvnw -B test (171 pass); cd admin && npx vitest run (34 pass).
<!-- SECTION:NOTES:END -->
