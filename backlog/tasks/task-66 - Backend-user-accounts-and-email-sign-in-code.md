---
id: TASK-66
title: 'Backend: user accounts and email sign-in code'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 09:49'
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
