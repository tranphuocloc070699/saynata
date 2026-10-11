---
id: TASK-24
title: Replace word partners and simpler words with usage patterns
status: To Do
assignee: []
created_date: '2026-10-10 08:21'
updated_date: '2026-10-11 09:24'
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
- [ ] #1 A usage pass fills usage for existing done entries without changing their definitions; resume-safe
- [ ] #2 Export schema stores usage as JSON; dictionary.sqlite re-exported
- [ ] #3 Spec glossary (doc-1) and feature docs (doc-5, doc-6) updated
- [ ] #4 Sense model (pipeline models.py, backend WordEntry, iOS Sense) has usage: [{kind, pattern, example}] with kind one of pattern, collocation, phrase (phrasal verb or fixed phrase), idiom; no collocations or synonyms fields
- [ ] #5 Pipeline prompt and backend WORD_PROMPT ask for up to 3 usage items per sense (fewer when the sense has no good ones); placeholders are someone, something, + noun, + verb, + -ing (no sb/sth)
- [ ] #6 Prompt rules: only common, natural items a native speaker uses often (no rare or old idioms); each item matches that sense's meaning; examples use simple words and pass the NGSL check
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-11 brainstorm with the developer: option A (pattern + one example per line), plus a kind tag so the card can show a small label (idiom, collocation) and the future mini game can filter by kind. Examples agreed: get + noun / get a job / get hold of something; get + adjective / get better / get used to + -ing; make a decision (not do a decision) / make up your mind; take a break / take it easy; depend on + something / It depends.
<!-- SECTION:NOTES:END -->
