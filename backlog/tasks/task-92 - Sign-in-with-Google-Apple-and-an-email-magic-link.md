---
id: TASK-92
title: 'Sign in with Google, Apple and an email magic link'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-18 07:41'
updated_date: '2026-10-18 07:56'
labels:
  - backend
  - ios
  - security
dependencies: []
priority: high
ordinal: 96000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
App users can sign in with Google, Sign in with Apple, or a one-tap link emailed to them, next to the existing email code (TASK-70, doc-17). Backend verifies provider ID tokens (JWKS signature, aud, iss, exp, nonce), finds or creates the user, and returns the same access + refresh tokens. Magic link: single-use, short-lived, emailed through the existing sender; an HTTPS page on the backend opens the app with a custom URL scheme (free Apple ID) and becomes a universal link with the paid account and the new domain. Plain sign-in form for now; the developer redesigns it later (no mockup for this form). Every domain in config.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend: POST /api/auth/google and /api/auth/apple verify the ID token with the provider JWKS, aud, iss, exp and nonce, then return the same tokens as /verify
- [ ] #2 Backend: POST /api/auth/email-link emails a single-use short-lived link; POST /api/auth/email-link/verify exchanges it for tokens; GET link page opens the app
- [ ] #3 New tables only through a new Flyway file; new config keys in .env.example and doc-11; tests use fakes, no real provider calls
- [ ] #4 iOS: sign-in sheet has Google, Apple and Email me a link next to the email code; the link opens the app and signs in
- [ ] #5 Email-code sign-in still works; backend and iOS tests pass; doc-17 and CLAUDE.md updated; domain move checklist written
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend: IdTokenVerifier (JWKS, iss, aud, exp, nonce), SocialSignInService, SignInLinkService (hash + PKCE secret), link page, Flyway V22. 2. iOS: AuthClient/AccountStore calls, GoogleSignInFlow (no SDK), Apple button, Email me a link, onOpenURL. 3. Tests with fakes. 4. doc-17, doc-11 section 13, doc-7, CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built on branch feature/task-92-social-sign-in. Backend: 468 tests pass (24 new). iOS: 384 tests pass on iPhone 16 Pro iOS 18.5 (12 new); SaynataMac builds. The sheet was not checked visually (simulator taps did not reach the app). New options are iOS only; Mac keeps the email code.
<!-- SECTION:NOTES:END -->
