---
id: TASK-86.3
title: 'Extension: sign in with an email code'
status: To Do
assignee: []
created_date: '2026-10-16 09:00'
labels:
  - extension
dependencies:
  - TASK-86.1
parent_task_id: TASK-86
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
REQUIRE_SIGN_IN=true on the server, so a guest only gets cached and bundled answers (doc-17). The extension needs the same email-code sign-in as the iOS app, with tokens kept in chrome.storage and one shared refresh (a refresh token works once).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Email-code sign-in in the popup (POST /api/auth/email-code, /api/auth/verify)
- [ ] #2 Tokens in chrome.storage.local; Bearer sent on words, translations and the stream
- [ ] #3 On 401 with a token: one shared refresh, then retry once; invalid_refresh_token signs out
- [ ] #4 A guest 401 sign_in_required shows a sign-in state in the card that opens the popup
- [ ] #5 Sign out
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
