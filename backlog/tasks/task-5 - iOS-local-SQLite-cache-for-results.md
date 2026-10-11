---
id: TASK-5
title: 'iOS: local SQLite cache for results'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-11 09:32'
labels:
  - ios
milestone: m-0
dependencies: []
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Repeat lookups should not hit the network (spec R2 step 2). Cache key must equal the backend's sha256 key.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Translations and word entries are saved locally after a backend answer
- [ ] #2 Same key is served from the local cache with no network call
- [ ] #3 Cache key matches backend CacheKeys for the same input
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CacheKeys.swift: normalise + sha256 (CryptoKit) like backend CacheKeys.java; PromptVersions constant (v4 sentence, v3 word) pointing to application.properties.
2. ResultCache.swift: actor on system SQLite3; table result_cache(cache_key PK, kind, data BLOB, created_at); get/save by key.
3. BackendClient takes optional cache; explain() checks cache first, saves 200 body after a backend answer. Factory passes a shared on-disk cache.
4. Tests: key vectors, cache round trip, no-network on repeat via URLProtocol stub.
5. Update docs (doc-4, doc-5), check_docs, PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Test it
**Goal:** Asking for the same sentence twice makes only one network call; the second answer comes from the phone.
**Steps:**
1. Start the backend (`cd backend && ./mvnw spring-boot:run`) and run the app in the simulator with the "Server" provider.
2. Explain "I was reluctant to leave." (Vietnamese). Wait for the result.
3. Stop the backend. Tap Explain again with the same text (extra spaces at the ends are fine).
4. Change the language to Japanese and tap Explain.
**Expected:**
- Step 2 shows the explanation.
- Step 3 shows the same explanation at once, even with the backend off.. Implemented: CacheKeys.swift, ResultCache.swift, BackendClient cache wiring, tests (29 pass), docs doc-4/doc-5.
<!-- SECTION:NOTES:END -->
