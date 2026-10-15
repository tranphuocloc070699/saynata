---
id: TASK-67
title: 'Backend: daily Claude budget cap in USD'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 10:05'
labels:
  - backend
  - security
dependencies: []
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Claude spend must have a hard daily ceiling. Cost is counted in money from real token usage and per-model prices in config. Decision of 2026-10-15 (doc-7).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every Claude call (sentence, word, streaming) records model, input and output tokens, feature and computed cost
- [ ] #2 When today's (UTC) spend reaches app.claude.daily-budget-usd (default 1.00), no Claude call is made and the API answers 503 with code daily_limit_reached; cached answers still work
- [ ] #3 GET /api/admin/usage?days=7 returns spend per day and per feature
- [ ] #4 Prices for claude-haiku-5-5 are in config with a comment to check them
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. V11 claude_usage table. 2. ClaudeUsageService (cost from app.claude.pricing.*), recorded in ClaudeService for sentence, stream and word. 3. ClaudeGate budget check, 503 daily_limit_reached. 4. GET /api/admin/usage. 5. Tests.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR #63: https://github.com/tranphuocloc070699/saynata/pull/63 (one PR for TASK-66 to 69). 273 backend tests pass (63 new). Test it - Goal: a guest gets cached answers only, a signed-in user gets new explanations, limits answer with codes. Steps (API=https://api.loffy.me; the code is read from the server log until SES is set): 1 GET /api/words/run; 2 guest POST /api/translations with a new sentence -> 401 sign_in_required; 3 POST /api/auth/email-code -> 204; 4 read the code: docker compose logs backend | grep 'fake email'; 5 POST /api/auth/verify -> tokens; 6 new sentence with Bearer -> 200 cached=false, then as guest -> cached=true; 7 GET /api/me; 8 POST /api/auth/refresh, then reuse the old token -> 401; 9 sign-out; 10 4th email-code in 15 min -> 429 Retry-After; 11 CLAUDE_DAILY_BUDGET_USD=0.00 -> 503 daily_limit_reached; 12 GET /api/admin/usage?days=7; 13 DELETE /api/me -> 204. Failure case: wrong code -> 401 invalid_code. Automated: cd backend && ./mvnw -B test. Sign-in emails go out only after SES_FROM_EMAIL and the AWS variables are set (doc-11 section 10). The current iOS app sends no token, so new sentences and uncached words get 401 after the merge.
<!-- SECTION:NOTES:END -->
