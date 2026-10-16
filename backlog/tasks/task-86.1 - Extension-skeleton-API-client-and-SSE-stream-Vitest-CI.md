---
id: TASK-86.1
title: 'Extension: skeleton, API client and SSE stream, Vitest, CI'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:37'
labels:
  - extension
dependencies: []
parent_task_id: TASK-86
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The base every other extension task builds on: MV3 manifest, service worker that does all network calls (host_permissions for api.loffy.me, so no backend CORS change), and the non-UI logic (word vs sentence split, SSE parser, error codes from doc-17) under unit tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 extension/ builds with Vite to a loadable unpacked folder (manifest v3, service worker, content script, context menu, keyboard command)
- [x] #2 Word vs sentence split matches the app (one token, or a/an/the + one word)
- [x] #3 SSE parser handles meaning-delta, meaning, done and error events split across chunks
- [x] #4 Error codes sign_in_required, rate_limited, daily_limit_reached map to clear messages
- [x] #5 Vitest tests pass; a GitHub Actions job runs build + test for extension/ changes
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. extension/ with Vite + TypeScript; build script builds the service worker (ES module), content script (IIFE) and popup, and writes manifest.json.
2. shared/: languages, textKind (word vs sentence), SSE parser, API types, errors (ProblemDetail code, Retry-After), backend client (word, stream with fallback, Bearer via a token provider, one refresh + retry).
3. background: context menu, keyboard command, port 'explain' that streams events to the content script.
4. Vitest tests for shared/; CI job 'Extension' (npm ci, test, build).
5. Feature doc page, CLAUDE.md row, doc-7 row.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
npm test: 38 Vitest tests pass (textKind, sse, api, explain). npm run build: dist/ with manifest v3, background.js (ES module, no imports), content.js (IIFE). AC1 (load unpacked in Brave) is for the developer's test. The content script is a stub here that logs events to the page console; TASK-86.2 replaces it. npm 10 crashes resolving vitest peers, so extension/.npmrc sets legacy-peer-deps.
<!-- SECTION:NOTES:END -->
