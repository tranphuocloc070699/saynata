---
id: TASK-3
title: 'Pipeline: generate the full NGSL dictionary'
status: In Progress
assignee: []
created_date: '2026-10-10 04:30'
updated_date: '2026-10-10 08:25'
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
- [ ] #2 Failed words retried; remaining failures and needs_review fixed or accepted
- [x] #3 uv run pipeline export produces data/out/dictionary.sqlite
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-10: switched generate.py from Gemini/DeepSeek (OpenAI SDK) to the Anthropic SDK with structured output (messages.parse, output_format=WordEntry, effort low). Model from CLAUDE_MODEL (default claude-haiku-5-5). PROMPT_VERSION 1 -> 2, so the 245 Gemini entries are redone for a consistent dictionary.
6-word comparison (reluctant, bank, however, container, ability, order): Haiku 2/6 pass first try, ~2,080 in / 360 out tokens, $0.0004/call, 3.3 s/call; Sonnet 3/6, $0.008/call, 3.8 s/call, clearly better definitions and Vietnamese. Common failure: 'goods'.
Next step: user picks model, adds ANTHROPIC_API_KEY to pipeline/.env, then run generate.

2026-10-10 run done (Sonnet 5.5, 8 workers, ~45 min): 2,578 done, 231 needs_review, 0 failed; 4,558 LLM calls (~$36). Fixed KeyError('text') in senses._first_example (word 'stock'). Exported data/out/dictionary.sqlite: 2.5 MB. needs_review: 189 fail only on 'hard words', most of them basic (container 18, rude 10, unhappy 8, cloth 8, queen, polite, insect, tv, internet, uk). Next step: user decides allowlist vs retry vs accept.
<!-- SECTION:NOTES:END -->
