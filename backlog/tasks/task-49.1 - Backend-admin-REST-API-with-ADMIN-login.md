---
id: TASK-49.1
title: 'Backend: admin REST API with ADMIN login'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-14 10:54'
updated_date: '2026-10-14 11:30'
labels:
  - backend
  - cms
dependencies: []
parent_task_id: TASK-49
priority: high
type: task
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Spring Boot /api/admin/** for the admin web app (F5). Accounts: a Flyway migration adds a users table (id, email unique, password_hash bcrypt, display_name, created_at) and a user_roles table (user_id, role: ADMIN, later USER); app users will reuse it later (Sign in with Apple is a separate later task). A command or one-time startup step creates the first admin from .env (ADMIN_EMAIL, ADMIN_INITIAL_PASSWORD) only when no admin exists. Spring Security: POST /api/admin/auth/login returns a token (decide in the plan: short-lived JWT in an HttpOnly, Secure, SameSite=Strict cookie is preferred), GET /api/admin/auth/me, logout; only /api/admin/** needs ADMIN; the public /api/translations, /api/words and /actuator/health stay open; login is rate limited. CORS only for https://admin.loffy.me (and http://localhost:5173 in dev) with credentials. Endpoints shaped for Refine's simple-rest data provider (list with _start/_end/_sort/_order/q filters and X-Total-Count): words (word_lookup), sentences (sentence_translation): list/search, needs_review filter, get one, update entry JSON (validate it parses to the response model), mark reviewed, delete = regenerate on next request. Tests with MockMvc. Update the backend feature docs and doc-11 (new .env keys).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 /api/admin/** returns 401 without login and works with the ADMIN login; public endpoints are unchanged (tests)
- [ ] #2 Words and sentences: list with paging and search, needs_review filter, get, update, mark reviewed, delete (tests)
- [ ] #3 An invalid entry JSON on update is rejected with 400 and a clear message (test)
- [ ] #4 New .env keys are in backend/.env.example and doc-11; feature docs updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V5: users + user_roles. 2. Spring Security (stateless): JWT HS256 in HttpOnly cookie saynata_admin, cookie filter, only /api/admin/** needs ADMIN. 3. Login/me/logout + in-memory IP rate limit (429). 4. First admin at startup from ADMIN_EMAIL + ADMIN_INITIAL_PASSWORD. 5. Admin words + sentences REST (Refine simple-rest shape, X-Total-Count), validate entry JSON on update. 6. CORS for /api/admin/** only. 7. MockMvc tests incl. public endpoints stay open. 8. Docs: .env.example, compose prod, doc-11, new feature page, CLAUDE.md row.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built backend/admin API (branch f5/task-49.1). 97 backend tests pass. Test it: Goal: log in as ADMIN and manage words/sentences over REST. Steps: 1) set ADMIN_JWT_SECRET, ADMIN_EMAIL, ADMIN_INITIAL_PASSWORD in backend/.env and start the backend (first admin is created, log line shows email only). 2) curl -i -c jar -H 'Content-Type: application/json' -d '{"email":"...","password":"..."}' http://localhost:8081/api/admin/auth/login. 3) curl -b jar 'http://localhost:8081/api/admin/words?_start=0&_end=10' -i (array + X-Total-Count). 4) PUT a bad entry body {"entry":{"senses":[]}} (Content-Type json) -> 400 with a clear detail. Expected: no cookie gives 401; /api/words/run and /actuator/health stay open. Automated: cd backend && ./mvnw -B test. PR is opened by the orchestrator (gh not authenticated in the worker).
<!-- SECTION:NOTES:END -->
