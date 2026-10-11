---
id: TASK-24
title: Replace word partners and simpler words with usage patterns
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-10 08:21'
updated_date: '2026-10-11 09:58'
labels:
  - pipeline
  - backend
dependencies: []
priority: high
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User decision 2026-10-10: the word card drops the 'Word partners' (collocations) and 'Simpler words' (synonyms) chip rows and shows 'How to use it' instead. Reasons: chips show word groups without saying how to build a sentence, and 19% of 'simpler words' are harder than the headword (get -> obtain, can -> tin). Each sense gets 2-3 usage items: a sentence pattern plus one example, e.g. 'be reluctant to + verb' -> 'She was reluctant to leave.' The existing 2,578 entries keep their definitions: a separate usage pass adds usage per word (one call per word, no retries), about $8 on Sonnet 5.5, instead of regenerating everything (~$36).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sense model (pipeline models.py, backend WordEntry) has usage: [{pattern, example}] and no collocations or synonyms
- [ ] #2 Pipeline prompt and backend WORD_PROMPT ask for 2-3 usage items per sense; patterns use simple words and placeholders like + verb, + noun, sb, sth
- [ ] #3 A usage pass fills usage for existing done entries without changing their definitions; resume-safe
- [ ] #4 Export schema stores usage as JSON; dictionary.sqlite re-exported
- [ ] #5 Spec glossary (doc-1) and feature docs (doc-5, doc-6) updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. pipeline: Usage model (kind, pattern, example), Sense.usage, drop collocations/synonyms; shared USAGE_RULES in the prompt; usage checks in validate.py.
2. pipeline: usage pass (usage.py + 'pipeline usage' command): one call per word, no retries, only adds sense.usage, resume-safe; tested with a mocked LLM.
3. export: usage as JSON column; remove collocations/synonyms.
4. backend: WordEntry.Sense.usage, WORD_PROMPT with the agreed examples, word-prompt-version v3 -> v4; tests.
5. Docs: doc-1 glossary, doc-5, doc-6, pipeline README, CLAUDE.md check.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented. Test it: (1) cd pipeline && uv run pytest (26 pass); (2) uv run pipeline usage --dry-run --words get make depend (3 calls, ~$0.01, saves nothing); (3) cd backend && ./mvnw -B test (24 pass); (4) ./mvnw spring-boot:run then curl -s localhost:8081/api/words/depend -> senses[].usage[{kind,pattern,example}], no collocations/synonyms. Developer must run the real pass (2,750 done words, ~$8-9): cd pipeline && uv run pipeline usage; then uv run pipeline export. AC #2 is finished after that run. Word prompt version is v4: iOS PromptVersions.word (PR #8) must become v4 when it lands.

Follow-up (idioms + no duplicates): USAGE_RULES and WORD_PROMPT now ask for at least one fitting idiom (B2-C1 ok), no repeated phrase or meaning across all senses, placeholder must match the example. 3 new idiom few-shots (make ends meet, take with a pinch of salt, get the hang of). usage.py drops a pattern that equals/sits inside another kept pattern of the same word across senses (placeholders, punctuation, trailing s removed; one-word patterns do not count as contained). Hard-word check stays on the example only. Word prompt version v5 (backend + iOS PromptVersions.word + CacheTests hash).
<!-- SECTION:NOTES:END -->
