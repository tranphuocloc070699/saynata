---
id: TASK-11
title: Add CI that runs backend and pipeline tests
status: To Do
assignee: []
created_date: '2026-10-10 05:46'
updated_date: '2026-10-10 08:25'
labels:
  - devops
dependencies:
  - TASK-10
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Nothing forces tests to run, so a change can silently break an old feature. Use GitHub Actions.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Workflow runs ./mvnw test in backend/ on push and PR
- [ ] #2 Workflow runs uv run pytest in pipeline/ on push and PR
- [ ] #3 Badge or status visible on GitHub
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
