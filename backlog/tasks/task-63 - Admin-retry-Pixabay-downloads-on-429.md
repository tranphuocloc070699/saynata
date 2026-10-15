---
id: TASK-63
title: 'Admin: retry Pixabay downloads on 429'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:59'
updated_date: '2026-10-15 07:59'
labels:
  - admin
  - backend
dependencies: []
ordinal: 64000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Picking another image failed with 'Could not download or convert that file'. The production log shows 'Pixabay download answered 429': the Pixabay rate limit is shared by one key (admin search, background jobs, a big media pass), so a pick can hit it. The admin needs a retry and a clear message instead of the convert-failure text.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 On HTTP 429 the Pixabay client retries (3 attempts in total) with backoff, respects Retry-After, and waits about 10 s at most in total
- [ ] #2 If it still fails, the admin pick answers with the message 'Pixabay is busy right now. Try again in a minute.'; real convert failures keep the generic message
- [ ] #3 Tests with a fake HTTP client: 429 then 200 succeeds; 429 three times gives the busy message
- [ ] #4 doc-13 and doc-15 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. HttpPixabayClient: retry on 429 (3 attempts, Retry-After, 10 s budget) in a shared send; PixabayBusyException. 2. AdminMediaService maps it to a busy message. 3. Tests with mocked HttpClient. 4. doc-13, doc-15.
<!-- SECTION:PLAN:END -->
