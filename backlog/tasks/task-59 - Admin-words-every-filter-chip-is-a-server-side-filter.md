---
id: TASK-59
title: 'Admin words: every filter chip is a server-side filter'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:16'
updated_date: '2026-10-15 07:26'
labels:
  - backend
  - admin
dependencies: []
priority: high
ordinal: 61000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Bundled, From backend and CEFR chips only filter the rows already loaded on the client. With infinite scroll, "From backend" keeps fetching page after page and showed "Nothing found" while loading 850 of 2,753. The backend list endpoint already has source and cefr filters (TASK-49.3), so the chips must use them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Bundled, From backend, A1-A2 and B1-B2 chips send the source and cefr params to the backend; no client-side matching is left
- [ ] #2 One source at a time; CEFR levels are OR-ed; the count line shows the filtered total
- [ ] #3 Admin Vitest test: a chip sends the server param and loads no extra pages
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Source and CEFR chips become server filters (source eq, cefr as a list). Remove the client matching from EntryBrowser. 2. Vitest: a chip sends the param, no extra pages. 3. doc-14.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/58. Chips are {field, value, combine}; all server filters; source/media one at a time, levels OR-ed (cefr list). Level = first sense (backend column). Test it: admin.loffy.me, press From backend: one request, filtered total, no endless loading, no 'Nothing found'. Automated: admin npm test (infiniteScroll, wordsPage, dataProvider tests).
<!-- SECTION:NOTES:END -->
