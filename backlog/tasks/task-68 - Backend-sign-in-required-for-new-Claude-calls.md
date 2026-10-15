---
id: TASK-68
title: 'Backend: sign-in required for new Claude calls'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 10:09'
labels:
  - backend
  - security
dependencies:
  - TASK-66
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Guests may use only bundled words and cached answers. A request that needs a new Claude call (new sentence, or a word that is neither bundled nor cached) must come from a signed-in user. Decision of 2026-10-15 (doc-7).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Without a valid Bearer token, cache and bundled hits are served as before
- [ ] #2 On a miss that needs Claude, a guest gets 401 with code sign_in_required (stream: an error event with the same code)
- [ ] #3 With a valid USER token the Claude call is made as today; the admin cookie auth is unchanged
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CallerContext (thread-local user id) set by BearerTokenFilter, copied into the stream worker. 2. ClaudeGate.beforeNewCall in TranslationService and WordService before each Claude call. 3. 401 sign_in_required (HTTP and stream error event). 4. Tests: guest hit, guest miss, user miss.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR #63: https://github.com/tranphuocloc070699/saynata/pull/63 (one PR for TASK-66 to 69). 273 backend tests pass (63 new). Test it - Goal: a guest gets cached answers only, a signed-in user gets new explanations, limits answer with codes. Steps (API=https://api.loffy.me; the code is read from the server log until SES is set): 1 GET /api/words/run; 2 guest POST /api/translations with a new sentence -> 401 sign_in_required; 3 POST /api/auth/email-code -> 204; 4 read the code: docker compose logs backend | grep 'fake email'; 5 POST /api/auth/verify -> tokens; 6 new sentence with Bearer -> 200 cached=false, then as guest -> cached=true; 7 GET /api/me; 8 POST /api/auth/refresh, then reuse the old token -> 401; 9 sign-out; 10 4th email-code in 15 min -> 429 Retry-After; 11 CLAUDE_DAILY_BUDGET_USD=0.00 -> 503 daily_limit_reached; 12 GET /api/admin/usage?days=7; 13 DELETE /api/me -> 204. Failure case: wrong code -> 401 invalid_code. Automated: cd backend && ./mvnw -B test. Sign-in emails go out only after SES_FROM_EMAIL and the AWS variables are set (doc-11 section 10). The current iOS app sends no token, so new sentences and uncached words get 401 after the merge.

Added REQUIRE_SIGN_IN env switch (default true). With false, guest misses still pass ClaudeGate: daily budget plus a per-IP guest limit (30 per hour, LIMIT_CLAUDE_GUEST_IP_MAX). GuestModeTest added; 277 tests pass.
<!-- SECTION:NOTES:END -->
