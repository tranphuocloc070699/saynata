---
id: TASK-70
title: 'iOS: sign in with an email code, Keychain tokens, refresh and retry'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 10:16'
updated_date: '2026-10-15 10:30'
labels:
  - ios
  - security
dependencies: []
priority: high
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
iOS side of user accounts (backend: PR #63, doc-17). A guest gets bundled and cached content only. When the backend answers 401 sign_in_required, a bottom sheet asks for an email, then the 6-digit code, then the original request runs again. Tokens live in the Keychain. Settings has an Account row (Sign in, or email + Sign out + Delete account). Extensions stay guests (free Apple ID has no shared Keychain access group). The app must keep working against a backend with REQUIRE_SIGN_IN=false.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sign-in sheet (email step, code step with one oneTimeCode field, Send a new code) opens over the result on 401 sign_in_required; after verify the original word or sentence request runs again
- [ ] #2 Access and refresh tokens are stored in the Keychain; Authorization Bearer is sent on translations, the stream and words; one refresh and retry on 401; failed refresh signs out locally
- [ ] #3 Settings Account row: Sign in, or email + Sign out + Delete account (confirm, DELETE /api/me, local sign out)
- [ ] #4 Errors: daily_limit_reached shows the server message, rate_limited shows 'Try again in N minutes', the sheet shows code errors
- [ ] #5 Tests for Keychain store, auth client, 401-sheet-verify-retry, refresh-then-retry, failed refresh sign-out, delete account, error messages; docs updated (doc-17 iOS section, doc-4, doc-5, design-system)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Keychain store (SecItem, behind a small protocol so tests use a fake) and AuthClient (/api/auth/*, /api/me) with a fake URLProtocol in tests.
2. AccountStore (@Observable): user, tokens in Keychain, single-flight refresh, sign out, delete account. Implements the token provider that BackendClient uses.
3. BackendClient: Authorization Bearer on translations, stream and words; BackendError gets signInRequired, dailyLimitReached, rateLimited (from code, or status); on 401 with a token: refresh once, retry once.
4. TranslationStore: on signInRequired remember the request and ask for the sheet; after sign-in run it again.
5. Views: SignInSheet (two steps) + SignInModel, TranslateView sheet, Settings Account section, extension hint (open Saynata).
6. Tests, simulator screenshots of both steps, docs (doc-17, doc-4, doc-5, design-system, doc-10), check_docs.py, PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/64. Built Account/ (Keychain store, AuthClient, AccountStore, SignInModel), Bearer + refresh-once-and-retry in BackendClient, coded errors, sign-in sheet (option A), Settings Account section, extensions stay guests. 284 tests pass on iPhone 16 Pro iOS 18.5. Test it: Goal: a guest asks for a new sentence, the sign-in sheet opens over the result, the email code is typed, the sheet closes and the same request runs again. Steps: 1) against a backend with REQUIRE_SIGN_IN=true, type a new sentence and tap send; 2) sheet shows 'Sign in to explain new sentences'; type an email, tap the arrow; 3) read the code on the VPS: docker compose -f docker-compose.prod.yml logs backend | grep 'fake email'; type it; 4) the explanation appears; 5) Settings > Account shows the email, Sign out, Delete account. Expected: wrong code shows 'That code is wrong or has expired...'; 5 wrong tries show 'Too many tries. Send a new code.'; daily limit shows the server message; rate limit shows 'Try again in N minutes'. With REQUIRE_SIGN_IN=false nothing changes. Automated: xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.5' -only-testing:EnglishTranslationTests -parallel-testing-enabled NO. Known gaps: extensions are guests (no shared Keychain group on a free Apple ID); word sheet inside a sentence shows only the message, no sign-in sheet.
<!-- SECTION:NOTES:END -->
