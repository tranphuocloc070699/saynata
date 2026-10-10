---
id: TASK-24
title: Replace word partners and simpler words with usage patterns
status: To Do
assignee: []
created_date: '2026-10-10 08:21'
updated_date: '2026-10-10 08:25'
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
