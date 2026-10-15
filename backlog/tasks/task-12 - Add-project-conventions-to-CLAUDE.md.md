---
id: TASK-12
title: Add project conventions to CLAUDE.md
status: Done
assignee: []
created_date: '2026-10-10 05:46'
updated_date: '2026-10-14 11:25'
labels:
  - docs
dependencies: []
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Style drifts between AI sessions (naming, folders, how to test). Write the rules down from the current code so every session follows them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 CLAUDE.md has a short Conventions section for iOS, backend, and pipeline
- [x] #2 Each part lists its test command
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Read the current iOS, backend and pipeline code and write a short Conventions section in CLAUDE.md with each part's test command.
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Conventions section in CLAUDE.md for iOS, backend, pipeline and branches/PRs, each with its test command; written from the current code; approved by the developer and merged in PR #40.
<!-- SECTION:FINAL_SUMMARY:END -->
