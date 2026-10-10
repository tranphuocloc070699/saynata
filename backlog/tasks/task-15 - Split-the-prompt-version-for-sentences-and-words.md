---
id: TASK-15
title: Split the prompt version for sentences and words
status: To Do
assignee: []
created_date: '2026-10-10 06:22'
updated_date: '2026-10-10 08:25'
labels:
  - backend
dependencies: []
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
One app.claude.prompt-version is part of both cache keys, so a sentence-prompt change throws away every cached word too (and the other way round). See doc-4 Sentence translation, Known gaps.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sentence and word prompts each have their own version in config
- [ ] #2 Bumping one version does not change the other cache's keys (test)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
