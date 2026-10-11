---
id: TASK-26
title: >-
  Set up the orchestrator workflow: Sonnet implementer subagent and feature
  waves
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-11 08:21'
updated_date: '2026-10-11 08:37'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Write .claude/agents/implementer.md (sonnet, effort medium, isolation worktree, permissionMode bypassPermissions). Prompt: read CLAUDE.md, task, practices/swift.md; branch off the feature branch; tests + check_docs; push; gh pr create with a Test it card; append notes; never Done, never merge, never push main.
2. Add .claude/settings.json deny rules for force-push and push to main, plus a PreToolUse hook script (.claude/hooks/guard-git-push.sh) because Bash deny patterns miss forms like 'git -C . push' (docs: permissions#bash-rule-limits).
3. Verify: docs say deny rules block in every mode incl. bypassPermissions (permission-modes#available-modes); run a real check with claude -p --permission-mode bypassPermissions and --dry-run pushes.
4. Backlog: create missing tasks (explain-first backend change, word card screen, Settings 'Show translation', share extension) and feature tasks F1-F4 with dependencies; fix TASK-19 AC #2 to usage patterns (decision-2).
5. Branch model: one long-lived feature branch per feature (feature/f1-word-lookup); workers open task PRs into it (orchestrator reviews and merges); one feature PR into main with the Test it card, merged after the developer says yes. The feature branch is deleted only after its main PR merges, so no child PR is closed.
6. Commit to main; developer pushes main so worktrees and PRs start from it.
7. Start F1 wave: TASK-4, TASK-5, word card screen, TASK-24 (max 4 workers). TASK-6 and TASK-19 in the next wave.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done so far (2026-10-11):
- .claude/agents/implementer.md: sonnet, effort medium, isolation worktree, permissionMode bypassPermissions; Test it card required in every PR.
- .claude/settings.json deny rules + .claude/hooks/guard-git-push.sh (PreToolUse). Hook unit-checked on 15 commands.
- Verified with claude -p --permission-mode bypassPermissions: deny rule alone (hooks off) blocked a force dry-run push; hook blocked 'git -C . push origin HEAD:main', a form the deny rules miss. Docs: 'Deny rules block in every mode, including bypassPermissions' (code.claude.com/docs/en/permission-modes).
- Finding: subagent permissionMode bypassPermissions only applies when the main chat is in bypassPermissions too (sub-agents docs). In auto mode the worker runs in auto.
- .worktreeinclude copies EnglishTranslation/Secrets.xcconfig into worktrees so iOS builds.
- New tasks: TASK-27 word card screen, TASK-28 explain-first backend, TASK-29 Settings, TASK-30 share extension. Features: TASK-31 F1, TASK-32 F2, TASK-33 F3, TASK-34 F4. TASK-19 AC now says usage patterns, depends on TASK-24 and TASK-27.
- Branch model: feature/fN-* branch per feature; task PRs into it; one feature PR into main.
<!-- SECTION:NOTES:END -->
