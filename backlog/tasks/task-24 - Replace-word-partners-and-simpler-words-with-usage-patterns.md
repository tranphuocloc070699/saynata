---
id: TASK-24
title: Replace word partners and simpler words with usage patterns
status: Done
assignee:
  - '@implementer'
created_date: '2026-10-10 08:21'
updated_date: '2026-10-11 15:24'
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
- [x] #1 A usage pass fills usage for existing done entries without changing their definitions; resume-safe
- [x] #2 Export schema stores usage as JSON; dictionary.sqlite re-exported
- [x] #3 Spec glossary (doc-1) and feature docs (doc-5, doc-6) updated
- [x] #4 Sense model (pipeline models.py, backend WordEntry, iOS Sense) has usage: [{kind, pattern, example}] with kind one of pattern, collocation, phrase (phrasal verb or fixed phrase), idiom; no collocations or synonyms fields
- [x] #5 Pipeline prompt and backend WORD_PROMPT ask for up to 3 usage items per sense (fewer when the sense has no good ones); placeholders are someone, something, + noun, + verb, + -ing (no sb/sth)
- [x] #6 Prompt rules: only common, natural items a native speaker uses often (no rare or old idioms); each item matches that sense's meaning; examples use simple words and pass the NGSL check
- [x] #7 Idioms: when the sense has a known idiom (common or less common, B2-C1 is fine), include at least one; its example still uses simple words
- [x] #8 No duplicates: no two usage items in a word repeat the same phrase or meaning (e.g. 'depend on + something' and 'It depends on + noun'), across all its senses; the placeholder matches the example
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [x] #2 Tests pass
- [x] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
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
2026-10-11 brainstorm with the developer: option A (pattern + one example per line), plus a kind tag so the card can show a small label (idiom, collocation) and the future mini game can filter by kind. Examples agreed: get + noun / get a job / get hold of something; get + adjective / get better / get used to + -ing; make a decision (not do a decision) / make up your mind; take a break / take it easy; depend on + something / It depends.

Implemented. Test it: (1) cd pipeline && uv run pytest (26 pass); (2) uv run pipeline usage --dry-run --words get make depend (3 calls, ~$0.01, saves nothing); (3) cd backend && ./mvnw -B test (24 pass); (4) ./mvnw spring-boot:run then curl -s localhost:8081/api/words/depend -> senses[].usage[{kind,pattern,example}], no collocations/synonyms. Developer must run the real pass (2,750 done words, ~$8-9): cd pipeline && uv run pipeline usage; then uv run pipeline export. AC #2 is finished after that run. Word prompt version is v4: iOS PromptVersions.word (PR #8) must become v4 when it lands.

Follow-up (idioms + no duplicates): USAGE_RULES and WORD_PROMPT now ask for at least one fitting idiom (B2-C1 ok), no repeated phrase or meaning across all senses, placeholder must match the example. 3 new idiom few-shots (make ends meet, take with a pinch of salt, get the hang of). usage.py drops a pattern that equals/sits inside another kept pattern of the same word across senses (placeholders, punctuation, trailing s removed; one-word patterns do not count as contained). Hard-word check stays on the example only. Word prompt version v5 (backend + iOS PromptVersions.word + CacheTests hash).

PR #12 (idioms + no duplicates, prompt v5): https://github.com/tranphuocloc070699/saynata/pull/12. Test it: cd pipeline && uv run pytest (26 pass); optional dry run uv run pipeline usage --dry-run --words depend make take (~$0.01); then the real pass uv run pipeline usage (~$8-9) and uv run pipeline export. Backend 24 pass, iOS unit tests pass.
2026-10-11 dry run (get, make, depend, Sonnet, ~$0.01): 29 items, 0 dropped, natural. Developer asked for more idioms (not only common ones) and no duplication before the full run.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Usage lines (pattern, collocation, phrase, idiom) replace word partners and simpler words; idioms and no duplicates; full pass 16,043 lines (PRs #9, #12, #13). Verified: pipeline/backend/iOS tests, dry runs, sample review.
<!-- SECTION:FINAL_SUMMARY:END -->
