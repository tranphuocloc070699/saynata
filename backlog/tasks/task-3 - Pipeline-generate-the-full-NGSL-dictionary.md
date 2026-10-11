---
id: TASK-3
title: 'Pipeline: generate the full NGSL dictionary'
status: Done
assignee: []
created_date: '2026-10-10 04:30'
updated_date: '2026-10-11 06:19'
labels:
  - pipeline
milestone: m-0
dependencies: []
references:
  - pipeline/README.md
priority: high
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The app bundles dictionary.sqlite, so iOS word lookup needs it. On 2026-10-10: 245 done, 134 failed (mostly network errors: ConnectError, ReadTimeout), 4 need review (hard words in definitions), 2426 pending.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 No pending words left in uv run pipeline report
- [x] #2 Failed words retried; remaining failures and needs_review fixed or accepted
- [x] #3 uv run pipeline export produces data/out/dictionary.sqlite
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Tests pass
- [x] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. validate.py: strip possessive 's before the vocabulary check; add ~58 basic words (container, cloth, rude, unhappy, queen, tv, internet, ...) to the allowed list.
2. New CLI command 'revalidate': re-run find_problems on stored needs_review entries, mark passing ones done (no LLM calls).
3. Tests for both.
4. generate --retry-review for the ~131 left (Sonnet, ~$3.5).
5. report, export, update doc-6, check_docs.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-10: switched generate.py from Gemini/DeepSeek (OpenAI SDK) to the Anthropic SDK with structured output (messages.parse, output_format=WordEntry, effort low). Model from CLAUDE_MODEL (default claude-haiku-5-5). PROMPT_VERSION 1 -> 2, so the 245 Gemini entries are redone for a consistent dictionary.
6-word comparison (reluctant, bank, however, container, ability, order): Haiku 2/6 pass first try, ~2,080 in / 360 out tokens, $0.0004/call, 3.3 s/call; Sonnet 3/6, $0.008/call, 3.8 s/call, clearly better definitions and Vietnamese. Common failure: 'goods'.
Next step: user picks model, adds ANTHROPIC_API_KEY to pipeline/.env, then run generate.

2026-10-10 run done (Sonnet 5.5, 8 workers, ~45 min): 2,578 done, 231 needs_review, 0 failed; 4,558 LLM calls (~$36). Fixed KeyError('text') in senses._first_example (word 'stock'). Exported data/out/dictionary.sqlite: 2.5 MB. needs_review: 189 fail only on 'hard words', most of them basic (container 18, rude 10, unhappy 8, cloth 8, queen, polite, insect, tv, internet, uk). Next step: user decides allowlist vs retry vs accept.

2026-10-11: validate.py strips possessive 's and allows 58 BASIC_WORDS; new 'revalidate' command (no LLM) moved 101 of 231 needs_review to done. Then generate --retry-review on 134 (130 + 4 old v1 entries), Sonnet, ~348 calls (~$2.8): 75 more done. Result: 2,750 done, 59 needs_review accepted (69 hard-word, 18 headword, 3 too-long problems); backend fills them on demand. Exported dictionary.sqlite: 2,750 words, 2.7 MB. DB backup: data/pipeline.db.bak-2026-10-11. pytest 11 passed, check_docs 0 problems.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Finished the NGSL dictionary: 2,750 of 2,809 words exported. Added a basic-word allowlist and possessive fix to the checker, a free 'revalidate' command, and retried the rest with Sonnet; 59 hard cases accepted. Verified with pipeline report/export, pytest (11 passed), check_docs. doc-6 and pipeline README updated.
<!-- SECTION:FINAL_SUMMARY:END -->
