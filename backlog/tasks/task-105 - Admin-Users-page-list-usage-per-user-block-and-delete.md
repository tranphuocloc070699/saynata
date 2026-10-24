---
id: TASK-105
title: 'Admin Users page: list, usage per user, block and delete'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-24 06:07'
updated_date: '2026-10-24 11:07'
labels:
  - admin
  - backend
  - accounts
dependencies: []
ordinal: 111000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants to manage app accounts in the admin (mockup https://claude.ai/artifact/MGYGs1Eu9FuLdSpFNxSq1X, choices 2026-10-24: all A). Today the admin has no users page, claude_usage has no user, nothing records last seen, and an account cannot be blocked. Apple review also needs a working account deletion path.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flyway adds users.blocked_at, blocked_reason, last_seen_at and claude_usage.user_id; usage rows get the caller's user id from now on (old rows stay without one)
- [ ] #2 last_seen_at is saved on sign-in and on token refresh, at most once an hour per user
- [ ] #3 ClaudeGate refuses a blocked user's new Claude call with 403 account_blocked; cached and bundled answers still work
- [ ] #4 GET /api/admin/users (q, blocked, provider, paging, X-Total-Count), GET /{id} (usage today, 7 days, total, last 14 days, sign-in methods, liked count, devices), POST /{id}/block {reason}, POST /{id}/unblock, DELETE /{id} with the same effect as deleting the account in the app; admin accounts give 400 for block and delete
- [ ] #5 Admin Users page: totals on top, table, Filter with counts, drawer with usage and sign-in methods, block with a reason, delete only after typing the email
- [ ] #6 Backend and admin tests green; doc-13, doc-14, doc-17 updated; doc-7 row
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V27: users.blocked_at, blocked_reason, last_seen_at; claude_usage.user_id + index.
2. ClaudeUsageService.record saves the caller's user id (not for PREFILL). AuthService marks last seen on every token issue, at most hourly.
3. ClaudeGate: blocked user -> 403 account_blocked (AccountBlockedException).
4. AdminUserService/Controller: list, summary, detail, block, unblock, delete (admins refused).
5. Admin: People > Users page with totals, Filter menu, table, drawer, block with reason, delete after typing the email.
6. Tests: AdminUserTest, usersPage.test.tsx; docs doc-13, doc-14, doc-17, doc-7, CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Backend 530 tests pass, admin 114 pass, build ok. The admin infinite-scroll test failed once while Maven ran at the same time (CPU load); it passes alone 3 of 3.

PR #162 (admin/users into feature/local), Test it card in the PR.

Flyway file renamed V27 -> V31 when merging feature/local (V27-V29 are taken there, and #174 uses V30). Release #174 before or together with this PR: Flyway does not apply a lower version after a higher one.
<!-- SECTION:NOTES:END -->
