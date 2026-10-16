---
id: TASK-83.6
title: 'iOS: suggestion list while typing (mockup first)'
status: To Do
assignee: []
created_date: '2026-10-16 08:19'
labels: []
dependencies:
  - TASK-83.4
parent_task_id: TASK-83
priority: low
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Show bundled words first (offline, instant) and then server suggestions from /api/suggest under the input, so the learner taps a ready answer. Changes how the Translate screen looks, so it starts with a mockup and waits for the developer to choose.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A mockup is approved by the developer before SwiftUI code
- [ ] #2 Suggestions come from the bundled dictionary at once and from /api/suggest after a pause; tapping one shows its answer
- [ ] #3 Icon-only buttons rule and design system followed; tests with a fake client
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
