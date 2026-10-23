---
id: TASK-100
title: >-
  Backend: queue the unknown words of guest sentences, and admin list/delete for
  the prefill queue
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-23 10:00'
updated_date: '2026-10-23 10:04'
labels:
  - backend
  - prefill
dependencies: []
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants 'Pending' in the admin: words learners met that are not in the database yet, answered by the night prefill job. Today SentenceWordQueue (TASK-83.8) queues the unknown words of a sentence only after the sentence was answered. A signed-out user's new sentence is refused (sign_in_required) before that, so its words are never queued. The admin also has only counts of the queue (GET /api/admin/prefill), no rows, and no way to remove one.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A sentence refused by the gate (sign_in_required, rate_limited, daily_limit_reached) still offers its words to SentenceWordQueue, on both sentence endpoints; cachedOnly requests still queue nothing
- [ ] #2 GET /api/admin/prefill/items?kind=&status=&q=&_start=&_end= lists queue rows (newest first, X-Total-Count), ADMIN only
- [ ] #3 DELETE /api/admin/prefill/items/{id} takes a row out of the queue (status skipped, so the same word is not queued again); 404 for an unknown id
- [ ] #4 Tests cover all of it; doc-18 and doc-13 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. TranslationController: on SignInRequired/RateLimited/DailyLimitReached, offer the sentence to SentenceWordQueue (both endpoints, not cachedOnly).
2. AdminPrefillController: GET /items (kind, status, q, paging, X-Total-Count), DELETE /items/{id} -> status skipped.
3. Tests: GuestSentenceWordsTest, AdminPrefillTest; docs doc-13, doc-18 (+ line refs in doc-4, doc-17).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Remove = status skipped (not a real delete), so a learner meeting the word again does not queue it again. Full backend suite: 511 passed.
<!-- SECTION:NOTES:END -->
