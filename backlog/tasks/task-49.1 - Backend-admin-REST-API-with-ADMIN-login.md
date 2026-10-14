---
id: TASK-49.1
title: 'Backend: admin REST API with ADMIN login'
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
updated_date: '2026-10-14 10:58'
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
