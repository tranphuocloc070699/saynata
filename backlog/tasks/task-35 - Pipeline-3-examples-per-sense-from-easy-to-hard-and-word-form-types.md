---
id: TASK-35
title: 'Pipeline: 3 examples per sense from easy to hard, and word form types'
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-11 11:31'
updated_date: '2026-10-11 15:24'
labels:
  - pipeline
  - backend
dependencies:
  - TASK-24
priority: high
ordinal: 35000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer feedback on the F1 word card (2026-10-11): each meaning should show 2-3 examples ordered easy -> medium -> harder, and 'ran' should say 'past tense of run' instead of silently showing run. Today each sense has exactly 2 unordered examples, and the forms table only maps form -> word with no type.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each sense has 3 examples with a level (easy, medium, harder), ordered easy first; easy and medium use NGSL words only, harder may use B2 words; definitions are not changed
- [x] #2 A resume-safe examples pass adds them to done entries (one call per word, no retries), tested with a mocked LLM; backend WORD_PROMPT asks for the same shape and its prompt version is bumped
- [x] #3 Forms get a type (past tense, past participle, -ing form, plural, third person) from the free lemminflect library; ambiguous forms get 'form'; exported in dictionary.sqlite
- [x] #4 Export and iOS model read examples with levels and form types; old files without them still load
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Pipeline: Example model (levels, old strings load as easy/medium), EXAMPLE_RULES shared prompt, validate.example_problems, examples.py pass modelled on usage.py, 'pipeline examples' command.
2. forms.py with lemminflect; export forms.type and levelled examples; warnings in export command.
3. Backend: WordEntry.Example, WORD_PROMPT, word-prompt-version v6; iOS PromptVersions.word v6 + CacheTests hash.
4. iOS (data only): Example, WordForm, Sense decoding, DictionaryStore reads forms.type, old files still load.
5. Tests in all three parts, docs (doc-1, doc-5, doc-6, pipeline README), one 3-word dry run.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/16
Test it. Goal: pipeline gives each sense 3 levelled examples and labels word forms; app still works with old data. Steps: (1) cd pipeline && uv run pytest. (2) CLAUDE_MODEL=claude-sonnet-5-5 uv run pipeline examples --dry-run --words run make get (~$0.01, saves nothing). (3) optional full run: uv run pipeline examples (~$8, estimate) then uv run pipeline export; check forms.type for ran/went/apples/running. (4) In the app search ran and run: same cards as before. Expected: examples easy, medium, harder; ran and went = past tense, apples = plural, running = -ing form; the old bundled dictionary.sqlite (no forms.type) still loads. Automated: pipeline 57 passed, backend ./mvnw -B test 26 passed, iOS -only-testing:EnglishTranslationTests 62 passed (iPhone 17e).
Not done on purpose: paid full pass; bundled dictionary.sqlite not rebuilt.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
3 levelled examples per sense and form types via lemminflect; full pass 22,388 examples (PRs #16, #19). Verified: tests, dry runs, simulator ran -> past tense of run.
<!-- SECTION:FINAL_SUMMARY:END -->
