---
id: TASK-83.5
title: 'iOS: warm connection and cached-only prefetch while typing'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 08:19'
updated_date: '2026-10-20 02:30'
labels: []
dependencies:
  - TASK-83.4
parent_task_id: TASK-83
priority: medium
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The app sends nothing until the user submits, and the first request pays about 200 ms for a new TLS connection. No UI change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The app opens the backend connection at launch (one cheap request) so the first lookup reuses it
- [ ] #2 After a 400 ms typing pause the app asks for the cached-only answer of the current word or sentence and keeps it, so submit shows it without waiting; a miss changes nothing
- [ ] #3 Prefetch is cancelled when the text changes and never causes a Claude call or a sign-in sheet; tests with a fake client
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. BackendClient: warmUp() (GET /actuator/health) and prefetchCachedExplanation() (POST /api/translations?cachedOnly=true, a hit is saved in the phone cache, a miss or error does nothing). 2. Translating and WordLooking get a prefetch method with a no-op default; WordLookup reuses backend.cachedEntry(forPhrase:) and skips bundled words. 3. TranslationStore: inputText didSet starts a 400 ms debounced prefetch Task, cancelled on every change and when translate() starts; submit uses the normal path so History is written only there. 4. App and Mac app call warmUp at launch. 5. Tests with fakes, build all targets, update doc-4, doc-5, CLAUDE.md.
<!-- SECTION:PLAN:END -->
