---
id: TASK-15
title: Split the prompt version for sentences and words
status: Done
assignee:
  - '@claude'
created_date: '2026-10-10 06:22'
updated_date: '2026-10-11 08:10'
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
- [x] #1 Sentence and word prompts each have their own version in config
- [x] #2 Bumping one version does not change the other cache's keys (test)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. ClaudeProperties: replace promptVersion with sentencePromptVersion and wordPromptVersion.
2. application.properties: app.claude.sentence-prompt-version=v4, app.claude.word-prompt-version=v3 (word prompt is unchanged since v3, so cached words stay valid).
3. TranslationService uses the sentence version, WordService the word version.
4. Test: bump one version, the other cache still hits.
5. Update doc-4, doc-5, doc-1/database-design mentions; check_docs.
Stacked on TASK-20's branch (PR #1) because both edit the prompt-version line.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ClaudeProperties now has sentencePromptVersion (v4) and wordPromptVersion (v3: WORD_PROMPT is unchanged, so cached words stay valid). New PromptVersionCacheTest builds the services with different versions and shows a bump of one leaves the other cache hit. mvn test green (system mvn). Stacked on TASK-20 branch (PR #1).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Split app.claude.prompt-version into sentence-prompt-version and word-prompt-version. Verified by PromptVersionCacheTest (3 tests) and the full backend suite, green. doc-4 and doc-5 updated. Depends on PR #1 (TASK-20).
<!-- SECTION:FINAL_SUMMARY:END -->
