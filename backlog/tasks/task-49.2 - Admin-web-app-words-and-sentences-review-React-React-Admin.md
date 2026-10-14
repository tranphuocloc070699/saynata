---
id: TASK-49.2
title: 'Admin web app: words and sentences review (React + React-Admin)'
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
labels:
  - web
  - cms
dependencies:
  - TASK-49
parent_task_id: TASK-49
priority: high
type: task
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
New folder admin/ (React + Vite + TypeScript + React-Admin) for F5. MOCKUP FIRST: wait for the developer's choice before building screens (CLAUDE.md). Screens: login; Words list (search, needs_review first, CEFR, source bundled/backend); Word detail shown the way the app shows it (explanation first, senses, examples by level, usage, Vietnamese last, photo or clip); edit form; Regenerate and Mark reviewed buttons (icon-only with labels for screen readers, per the UI rule); Sentences list and detail the same way. Build to static files; Caddy serves them at admin.loffy.me (add the Caddyfile snippet to doc-11). Add 'admin/' to the CLAUDE.md overview table and CI (npm ci && npm run build && npm test).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Mockup approved by the developer before screen code
- [ ] #2 Words and sentences: list, search, filter, detail as the app shows it, edit, regenerate, mark reviewed
- [ ] #3 Builds to static files; Caddy config for admin.loffy.me in doc-11; CI builds it
- [ ] #4 CLAUDE.md overview lists the admin/ part and its commands
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
