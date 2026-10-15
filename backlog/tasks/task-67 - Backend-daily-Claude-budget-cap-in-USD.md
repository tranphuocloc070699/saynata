---
id: TASK-67
title: 'Backend: daily Claude budget cap in USD'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 09:49'
updated_date: '2026-10-15 09:49'
labels:
  - backend
  - security
dependencies: []
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Claude spend must have a hard daily ceiling. Cost is counted in money from real token usage and per-model prices in config. Decision of 2026-10-15 (doc-7).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every Claude call (sentence, word, streaming) records model, input and output tokens, feature and computed cost
- [ ] #2 When today's (UTC) spend reaches app.claude.daily-budget-usd (default 1.00), no Claude call is made and the API answers 503 with code daily_limit_reached; cached answers still work
- [ ] #3 GET /api/admin/usage?days=7 returns spend per day and per feature
- [ ] #4 Prices for claude-haiku-5-5 are in config with a comment to check them
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. V11 claude_usage table. 2. ClaudeUsageService (cost from app.claude.pricing.*), recorded in ClaudeService for sentence, stream and word. 3. ClaudeGate budget check, 503 daily_limit_reached. 4. GET /api/admin/usage. 5. Tests.
<!-- SECTION:PLAN:END -->
