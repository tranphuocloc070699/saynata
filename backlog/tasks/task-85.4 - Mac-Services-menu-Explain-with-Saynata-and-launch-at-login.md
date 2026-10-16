---
id: TASK-85.4
title: 'Mac: Services menu ''Explain with Saynata'' and launch at login'
status: To Do
assignee: []
created_date: '2026-10-16 09:00'
labels:
  - mac
dependencies:
  - TASK-85.3
parent_task_id: TASK-85
priority: medium
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
doc-16 Part 1 steps 4-5. NSServices item 'Explain with Saynata' (right-click → Services) that opens the window with the selected text explained; no Accessibility permission. A 'Launch at login' toggle in Settings with SMAppService.mainApp.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Selecting text in Safari/Notes and choosing Services → Explain with Saynata shows the result
- [ ] #2 Launch at login toggle works and survives a restart
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
