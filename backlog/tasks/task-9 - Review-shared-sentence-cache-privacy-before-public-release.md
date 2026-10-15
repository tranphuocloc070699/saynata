---
id: TASK-9
title: Review shared sentence cache privacy before public release
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 04:31'
updated_date: '2026-10-14 11:27'
labels:
  - backend
milestone: m-0
dependencies: []
priority: low
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sentences are shared across users in the backend cache and may contain personal text. Decision (doc-7, 2026-10-14, option 1): keep the cache shared, but do not store a sentence that looks personal: an email address, a phone number (7+ digits with spaces, dots, dashes or +), a link (http, www., or a domain like name.com), or any run of 6+ digits. Such a sentence is still explained by Claude and returned, just never saved or read from the cache. Implement in the backend translation flow (backend/src/main/java/com/englishtranslation/backend/translation/TranslationService.java), with a small PersonalTextDetector class and unit tests. Update the sentence feature page (backlog/docs/features/doc-4) and doc-1 Decisions.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Decision recorded: keep shared, add per-user scope, or opt-out
- [ ] #2 A sentence with an email, phone number, link or 6+ digit run is explained but not saved to or read from sentence_translation (tests)
- [ ] #3 Normal sentences are cached exactly as before (existing tests still pass)
- [ ] #4 doc-4 and doc-1 describe the rule
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add PersonalTextDetector (email, phone, link, 6+ digit run). 2. TranslationService: if personal, skip cache read and save, cached=false. 3. Unit tests for detector and service. 4. Update doc-4 and doc-1. 5. Run backend tests and check_docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented on branch fix/task-9: PersonalTextDetector + TranslationService skips cache read/save for personal text. Backend tests: 84 run, 0 failures, BUILD SUCCESS. Test it: Goal: a sentence with an email, phone, link or 6+ digits is explained but never stored. Steps: 1) run backend, POST /api/translations with text 'Email me at a@b.com' twice. 2) Same with 'I have 2 cats in 2026' twice. Expected: 1) cached false both times, no new row in sentence_translation. 2) second call cached true. Automated: cd backend && ./mvnw -B test (PersonalTextDetectorTest, TranslationServiceTest). PR link: opened by orchestrator.
<!-- SECTION:NOTES:END -->
