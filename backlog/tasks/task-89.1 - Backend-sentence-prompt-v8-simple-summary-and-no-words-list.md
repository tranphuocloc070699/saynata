---
id: TASK-89.1
title: 'Backend: sentence prompt v8, simple summary and no words list'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-17 16:43'
updated_date: '2026-10-17 16:44'
labels: []
dependencies: []
parent_task_id: TASK-89
priority: high
ordinal: 91000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A new sentence takes ~1.15 s to the first token, and the words list plus translation add ~0.7 s more (measured 2026-10-17). The new result (TASK-89, Option B) shows only the original and a simple summary; words are explained on tap from the dictionary, so Claude's words list is not needed. Old app versions still read meaning, words and translation, so they must keep working.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SENTENCE_PROMPT v8 asks for isEnglish, then summary (the sentence rewritten in simple B1 words, about the same length, the content itself, never a description of the sentence), then translation; no words list
- [ ] #2 app.claude.sentence-prompt-version is v8 and the iOS PromptVersions.sentence is v8
- [ ] #3 Old clients keep working: the response and the stream's meaning-delta / meaning events carry the summary as the meaning, and words is an empty list
- [ ] #4 Tests cover the new answer shape and the stream with a fake Claude; no test calls the real API
- [ ] #5 doc-4 is updated (prompt, fields, events, Last traced)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. SENTENCE_PROMPT v8 + schema (isEnglish, summary, translation), drop words. 2. Map summary to meaning, words empty in public JSON. 3. Stream reads summary. 4. Version bump backend + iOS line. 5. Tests. 6. doc-4.
<!-- SECTION:PLAN:END -->
