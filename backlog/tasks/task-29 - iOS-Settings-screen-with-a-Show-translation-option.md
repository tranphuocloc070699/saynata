---
id: TASK-29
title: 'iOS: Settings screen with a Show translation option'
status: To Do
assignee: []
created_date: '2026-10-11 08:36'
labels:
  - ios
dependencies: []
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CLAUDE.md product rule: the target-language line is collapsed by default, and Settings lets the learner change that. Some learners want the Vietnamese open all the time, others want the app to remember their last choice.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Settings opens from an icon-only button on the main screen
- [ ] #2 Show translation has three options: Collapsed (default), Always open, Remember last choice; the choice is saved across launches
- [ ] #3 Word cards and sentence results follow the setting
- [ ] #4 Unit tests cover the Remember last choice logic
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
