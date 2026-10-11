---
id: TASK-11
title: Add CI that runs backend and pipeline tests
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-10 05:46'
updated_date: '2026-10-10 08:51'
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
- [ ] #4 Workflow runs python3 scripts/check_docs.py and fails on broken doc links
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add .github/workflows/ci.yml: backend (Java 17, ./mvnw test), pipeline (uv, Python 3.12, uv run pytest), docs (python3 scripts/check_docs.py).
2. Add a CI badge to README.md.
3. Run the three checks locally.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Workflow .github/workflows/ci.yml: backend (Java 17, ./mvnw -B test), pipeline (Python 3.12, uv run pytest), docs (check_docs.py). Local checks green (mvn test, 9 pytest, 0 doc problems). README badge added. GitHub run on PR #4 was queued when I finished; ACs stay unchecked until it is green.
<!-- SECTION:NOTES:END -->
