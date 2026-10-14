---
id: TASK-49.1
title: 'Backend: admin REST API with ADMIN login'
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
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
Spring Boot /api/admin/** for the admin web app (F5). Spring Security: only /api/admin/** needs login; the public /api/translations, /api/words and /actuator/health stay open. Admin login: decide in the plan between (a) one admin user from .env (ADMIN_USERNAME, ADMIN_PASSWORD_HASH as bcrypt) with a session cookie or (b) a users table with roles, which app users reuse later; ask the developer if unsure. CORS only for https://admin.loffy.me (and http://localhost:5173 in dev). Endpoints: list/search/filter words (word_lookup) and sentences (sentence_translation) with paging, needs_review filter, get one, update entry JSON (validate it parses to the same response model), mark reviewed, delete = regenerate on next request. Tests with MockMvc. Update the backend feature docs and doc-11 (new .env keys).
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
