---
id: TASK-49
title: F5 Admin CMS
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
labels:
  - feature
  - cms
dependencies: []
priority: high
type: feature
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A web admin to review and fix the content: words (bundled and backend), sentences, and sense media, with the server Postgres as the single source of truth (doc-7, 2026-10-14). Stack: React (Vite) + React-Admin as static files served by Caddy at admin.loffy.me; Spring Boot /api/admin REST API with Spring Security (ADMIN role). Prepares for app users later (same Spring Security). Feature branch: feature/f5-cms. One testable feature = one feature branch and one GitHub PR into main (workflow: TASK-26). Workers open task PRs into the feature branch; the orchestrator reviews and merges them. The feature PR carries a Test it card, and it merges only after the developer has tested it and said yes. Delete the feature branch only after that PR merges.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test it: log in at admin.loffy.me, find a word and a sentence, see them as the app shows them, edit one, regenerate one, and see the change in the app
- [ ] #2 Feature PR into main has a Test it card and is merged after the developer approves
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
