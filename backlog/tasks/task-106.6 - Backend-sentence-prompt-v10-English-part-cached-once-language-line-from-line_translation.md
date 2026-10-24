---
id: TASK-106.6
title: >-
  Backend: sentence prompt v10, English part cached once, language line from
  line_translation
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-24 09:52'
updated_date: '2026-10-24 10:22'
labels: []
dependencies:
  - TASK-106.2
parent_task_id: TASK-106
ordinal: 118000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today the sentence cache key has the language, so the same English explanation is paid once per language. v10 writes only the English explanation; its cache key has no language. The response keeps explanation.translation so the apps do not change: the server fills it from line_translation (text = the normalized sentence), or makes one small line call when it is missing, or null for a guest miss. The Vietnamese translations already in the cached sentence rows seed line_translation with source seed, free.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 sentence-prompt-version is v10; the sentence cache key and match key have no language; the streaming and plain endpoints both work
- [ ] #2 A second language for a cached sentence costs one line call, not a full sentence call; tests prove it with the fake Claude
- [ ] #3 A Flyway migration copies existing Vietnamese sentence translations into line_translation (source seed) for the v10-normalized text
- [ ] #4 Prefill sentence items still work; tests pass; doc-4, doc-18 and doc-7 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR #173 into feature/local (includes #168 until merged). Backend tests: 549 pass. Test it: run backend (Flyway V29); POST /api/translations for Hello there with vi then ja; the second makes only a LINE call (claude_usage). cachedOnly with ko gives meaning and translation null. A guest asking fr for the cached text gets 200 and translation null. Automated: cd backend and run mvnw -B test.
<!-- SECTION:NOTES:END -->
