---
id: TASK-66
title: 'Backend: user accounts and email sign-in code'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 10:05'
labels:
  - backend
  - security
dependencies: []
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Learners need real accounts so Claude spending can be tied to a person. Sign-in is an email code (no passwords); mail goes through AWS SES on the developer's domain. Decision of 2026-10-15 (doc-7). The iOS sign-in screen waits for a mockup and is not part of this task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/auth/email-code always answers 204; the 6-digit code is stored only as a hash, expires in 10 minutes, allows 5 wrong tries, one code per email
- [ ] #2 POST /api/auth/verify creates the USER on first sign-in and returns accessToken, refreshToken, expiresIn, user
- [ ] #3 Refresh tokens are random, stored hashed, rotated on use and revocable; /api/auth/refresh and /api/auth/sign-out work
- [ ] #4 GET /api/me and DELETE /api/me (deletes the account and its tokens) work with a Bearer token
- [ ] #5 Email sender is an interface with an SES (SESv2) implementation and a logging fake; backend starts with no AWS variables
- [ ] #6 Tests cover code expiry, wrong tries, hash-only storage, refresh rotation and revocation, account deletion
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V10: email_login_code, refresh_token, users.password_hash nullable. 2. auth package: EmailSender (SES + logging), AccessTokenService (JWT, key derived from USER_JWT_SECRET or ADMIN_JWT_SECRET), AuthService, AuthController, MeController, BearerTokenFilter. 3. Tests with the fake sender.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR #63: https://github.com/tranphuocloc070699/saynata/pull/63 (one PR for TASK-66 to 69). 273 backend tests pass (63 new). Test it - Goal: a guest gets cached answers only, a signed-in user gets new explanations, limits answer with codes. Steps (API=https://api.loffy.me; the code is read from the server log until SES is set): 1 GET /api/words/run; 2 guest POST /api/translations with a new sentence -> 401 sign_in_required; 3 POST /api/auth/email-code -> 204; 4 read the code: docker compose logs backend | grep 'fake email'; 5 POST /api/auth/verify -> tokens; 6 new sentence with Bearer -> 200 cached=false, then as guest -> cached=true; 7 GET /api/me; 8 POST /api/auth/refresh, then reuse the old token -> 401; 9 sign-out; 10 4th email-code in 15 min -> 429 Retry-After; 11 CLAUDE_DAILY_BUDGET_USD=0.00 -> 503 daily_limit_reached; 12 GET /api/admin/usage?days=7; 13 DELETE /api/me -> 204. Failure case: wrong code -> 401 invalid_code. Automated: cd backend && ./mvnw -B test. Sign-in emails go out only after SES_FROM_EMAIL and the AWS variables are set (doc-11 section 10). The current iOS app sends no token, so new sentences and uncached words get 401 after the merge.
<!-- SECTION:NOTES:END -->
