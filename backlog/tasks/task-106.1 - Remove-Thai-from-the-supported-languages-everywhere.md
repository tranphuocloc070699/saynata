---
id: TASK-106.1
title: Remove Thai from the supported languages everywhere
status: To Do
assignee: []
created_date: '2026-10-24 09:51'
labels: []
dependencies: []
parent_task_id: TASK-106
ordinal: 113000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer keeps only English (no translation row), Vietnamese, Japanese, Korean, Simplified Chinese, French, Spanish and German. Thai goes from the backend, iOS/Mac, web and extension pickers. Old rows with target_lang th stay in the database; a client that still has th saved falls back to Vietnamese.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TargetLanguage.java, TargetLanguage.swift, web/src/lib/languages.ts and extension/src/shared/languages.ts have no Thai; a request with targetLang th gets 400
- [ ] #2 A saved th choice on iOS/Mac, web and extension reads as Vietnamese, with a test on each
- [ ] #3 Backend, iOS, web and extension tests pass; docs that list the languages are updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
