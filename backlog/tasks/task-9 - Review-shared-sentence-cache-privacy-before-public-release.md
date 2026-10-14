---
id: TASK-9
title: Review shared sentence cache privacy before public release
status: To Do
assignee: []
created_date: '2026-10-10 04:31'
updated_date: '2026-10-14 11:22'
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
- [ ] #2 Decision recorded: keep shared, add per-user scope, or opt-out
- [ ] #3 A sentence with an email, phone number, link or 6+ digit run is explained but not saved to or read from sentence_translation (tests)
- [ ] #4 Normal sentences are cached exactly as before (existing tests still pass)
- [ ] #5 doc-4 and doc-1 describe the rule
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
