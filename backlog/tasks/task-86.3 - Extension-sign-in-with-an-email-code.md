---
id: TASK-86.3
title: 'Extension: sign in with an email code'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-16 09:00'
updated_date: '2026-10-16 09:54'
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
- [x] #1 Email-code sign-in in the popup (POST /api/auth/email-code, /api/auth/verify)
- [x] #2 Tokens in chrome.storage.local; Bearer sent on words, translations and the stream
- [x] #3 On 401 with a token: one shared refresh, then retry once; invalid_refresh_token signs out
- [x] #4 A guest 401 sign_in_required shows a sign-in state in the card that opens the popup
- [x] #5 Sign out
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. shared/auth.ts: AuthClient (email-code, verify, refresh, sign-out) with the iOS error texts; AuthStore keeps the session in chrome.storage.local, is the TokenSource for SaynataApi, shares one refresh (a refresh token works once), signs out on invalid_refresh_token, keeps the session on a network error.
2. Service worker: auth messages from the popup; openSignIn from the card opens the popup (or a tab).
3. Popup account section: email step with a round icon-only send button, code step (one-time-code field, checks at 6 digits, Send a new code), signed-in row with a sign-out icon button. Same steps as the approved iOS sheet (option A).
4. Card: a Sign in icon button on the guest card; after sign-in the same text runs again.
5. Vitest for AuthStore/AuthClient and the account view.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
86 Vitest tests pass (auth.test.ts: client bodies and errors, shared refresh, refused refresh signs out, network keeps the session, Bearer via the API; accountView.test.ts: steps). Browser harness: email step, wrong code message, right code shows the signed-in row; the card's Sign in button sends openSignIn, and a session change re-runs the same text. Fixed: the sign-in icon was invisible (.message svg rule hit the button's svg). Not checked against the real api.loffy.me: needs a real email code, the developer's test.

PR: https://github.com/tranphuocloc070699/saynata/pull/93 (into feature/f7-browser-extension; merge order #90, #91, #92, #93).
<!-- SECTION:NOTES:END -->
