---
id: TASK-26
title: >-
  Set up the orchestrator workflow: Sonnet implementer subagent and feature
  waves
status: To Do
assignee: []
created_date: '2026-10-11 08:21'
labels:
  - workflow
dependencies: []
priority: high
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decided with the developer on 2026-10-11, so a fresh chat can start without this history.
- Orchestrator: the main chat, Opus 5.5 at medium effort, in the main checkout. It plans, starts workers, reviews PRs (Opus), and merges after the developer says yes. It does not write feature code.
- Workers: a project subagent on Sonnet 5.5, permissionMode bypassPermissions (developer choice), isolation: worktree. Each worker builds, runs tests, pushes a branch, opens a GitHub PR, and appends notes to its task. It never marks a task Done.
- One GitHub PR per testable FEATURE, not per task. Every PR and task gets a "Test it" card: goal, steps on simulator or device, expected result.
- Features: F1 Look up a word (TASK-4, 5, 6, 19 + word card on the new design system), F2 Explain a sentence (TASK-18, 14, 22, 20 done, 15 done, 21 + explain-first backend change), F3 Listen (TASK-7), F4 Speak and score (TASK-8). Backend chores (16) are checked by CI only.
- Run 3-4 workers per wave; the developer review is the limit.
- Chats: one chat per job; workers are subagents, not chats; handoffs go in Backlog notes, never /tmp. Merged PRs: avoid stacked PRs (deleting the base branch closes the child PR, as happened to #2).
- Docs checked: https://code.claude.com/docs/en/sub-agents (model, effort, isolation, permissionMode fields), https://code.claude.com/docs/en/model-config (CLAUDE_CODE_SUBAGENT_MODEL).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 .claude/agents/implementer.md exists with model sonnet, effort medium, isolation worktree, permissionMode bypassPermissions, and a prompt that requires a Test it card in every PR
- [ ] #2 Deny rules block force-push and pushing to main, and it is verified that they still apply in bypassPermissions mode
- [ ] #3 Backlog has feature tasks F1-F4 with their task lists, and the missing tasks exist: explain-first backend change, word card screen, Settings, share extension
- [ ] #4 TASK-19 acceptance criteria say usage patterns, not collocations and synonyms (decision-2)
- [ ] #5 First wave (F1) started with at most 4 workers
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
