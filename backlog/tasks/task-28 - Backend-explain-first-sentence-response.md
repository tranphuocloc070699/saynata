---
id: TASK-28
title: 'Backend: explain-first sentence response'
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
labels:
  - backend
dependencies: []
priority: high
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CLAUDE.md product rule: Saynata teaches English, it is not a translator. The main result for a sentence is a simple English explanation (B1-B2); the target-language text is a small helper line. The prompt and response model should ask the LLM for the English explanation first and keep the translation as a secondary field, and nothing should be named translate X. Cached rows made by the old prompt must not be served as if they were new.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The sentence prompt asks for the simple English explanation first; the target-language text is a secondary field
- [ ] #2 The response JSON puts the explanation first and the target-language text after the words
- [ ] #3 The sentence prompt version is bumped so old cached rows are not reused
- [ ] #4 Backend tests cover the new response shape; the iOS BackendClient decodes it
- [ ] #5 doc-4 (Sentence translation) and doc-1 glossary updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
