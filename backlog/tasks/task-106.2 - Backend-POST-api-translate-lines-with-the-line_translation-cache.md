---
id: TASK-106.2
title: 'Backend: POST /api/translate-lines with the line_translation cache'
status: To Do
assignee: []
created_date: '2026-10-24 09:51'
labels: []
dependencies: []
parent_task_id: TASK-106
ordinal: 114000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The endpoint, table, prompt and limits in the parent contract. Every LLM call goes through limits/ClaudeGate; tests use the fake Claude key and never call the real API.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 POST /api/translate-lines answers the parent contract, keeping the order of texts; one LLM call covers all misses of a request
- [ ] #2 A second request for the same (text, language, prompt version) is answered from line_translation with cached true and no LLM call
- [ ] #3 ?cachedOnly=true never reaches ClaudeGate and returns null for misses; a guest miss without it gets 401 sign_in_required
- [ ] #4 Flyway migration adds line_translation; usage is saved as Feature LINE; line-prompt-version is in application.properties
- [ ] #5 Tests cover hit, miss, cachedOnly, guest, en rejected, too many texts; doc-5, doc-17 and doc-11 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
