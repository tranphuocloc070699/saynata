---
id: TASK-49.2
title: 'Admin web app: words and sentences review (React + Refine + shadcn/ui)'
status: To Do
assignee: []
created_date: '2026-10-14 10:54'
updated_date: '2026-10-14 10:58'
labels:
  - web
  - cms
dependencies:
  - TASK-49.1
parent_task_id: TASK-49
priority: high
type: task
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
New folder admin/ (React + Vite + TypeScript + Refine + shadcn/ui on Tailwind) for F5. Mockup chosen by the developer: https://claude.ai/artifact/2ahgYwgaaWimvWxvp8rXSW — layout A: sidebar (Words, Sentences, Media), list with search and filter chips (Needs review first, Bundled, From backend, CEFR), and the selected entry on the right as a plain table of fields (not the phone layout). Buttons on the entry are icon-only with accessible labels (CLAUDE.md UI rule): Mark reviewed, Edit (form with the same fields plus a JSON tab), Regenerate (confirm in the page). Login page against /api/admin/auth (Refine authProvider). Data via Refine's simple-rest dataProvider against /api/admin. Build to static files; Caddy serves them at admin.loffy.me (add the Caddyfile snippet to doc-11). Add 'admin/' to the CLAUDE.md overview table and to CI (npm ci && npm run build && npm test).
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-14: mockup approved (layout A, plain field view, shadcn/ui + Refine). AC #1 can be checked at review.
<!-- SECTION:NOTES:END -->
