---
id: TASK-93.1
title: 'Backend: Liked words and sentences per account (/api/liked)'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-19 13:00'
updated_date: '2026-10-19 13:06'
labels:
  - backend
dependencies: []
parent_task_id: TASK-93
priority: high
ordinal: 98000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Liked items must follow a signed-in user across the iPhone, the Mac app and the browser extension, so they live in Postgres per user. Guests cannot like (the app shows the sign-in sheet). API contract shared with the iOS subtask (keep it exactly, or update both tasks): GET /api/liked -> {"items":[{"kind":"word"|"sentence","text","preview","createdAt"}]} newest first. PUT /api/liked with {"kind","text","preview"} -> the saved item; it is idempotent (liking the same item again only keeps one row and updates preview). DELETE /api/liked?kind=&text= -> 204, also when nothing was there. The item key is kind + normalized text (word: lowercased and trimmed, same normalization as the word lookup; sentence: trimmed, case kept). text is what the user saw (max 500 characters, like the sentence limit); preview is the short simple-English meaning for the list (max 300). No Claude call anywhere in this feature, so ClaudeGate is not involved.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET, PUT and DELETE /api/liked work for a signed-in user and only ever touch that user's rows
- [ ] #2 A guest gets 401 with code sign_in_required on all three endpoints
- [ ] #3 PUT is idempotent on kind + normalized text; bad kind, empty text, text over 500 or preview over 300 characters give 400 ProblemDetail; a user can keep at most 5000 liked items (400 liked_limit_reached past that)
- [ ] #4 Deleting the account (existing delete-account flow) deletes the user's liked rows
- [ ] #5 Schema only through a new Flyway file; JUnit tests cover the cases above; doc-17 or a new feature page documents the endpoints
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Flyway V23 liked_item (FK cascade to users). 2. Package liked/: entity, repository, service (validation, normalize, 5000 limit), controller using CallerContext (401 sign_in_required for guests). 3. LikedApiTest. 4. doc-17 section + CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/131 (into feature/local). Test it: run backend, get TOKEN from POST /api/auth/verify. PUT /api/liked {kind:word,text:Run,preview:..} -> 200; GET /api/liked -> items newest first; PUT again with ' run ' -> still one row; DELETE /api/liked?kind=word&text=RUN -> 204 (twice too); GET /api/liked without token -> 401 code sign_in_required; bad kind/empty text/text>500/preview>300 -> 400. Automated: LikedApiTest (10 tests); cd backend && ./mvnw -B test (488 pass). Contract unchanged.
<!-- SECTION:NOTES:END -->
