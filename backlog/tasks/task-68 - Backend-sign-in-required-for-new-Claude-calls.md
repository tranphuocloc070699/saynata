---
id: TASK-68
title: 'Backend: sign-in required for new Claude calls'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 09:49'
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
