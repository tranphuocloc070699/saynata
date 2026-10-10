---
id: TASK-19
title: 'iOS: single-word input shows a dictionary entry, not a sentence explanation'
status: To Do
assignee: []
created_date: '2026-10-10 06:36'
updated_date: '2026-10-10 08:25'
labels:
  - ios
dependencies:
  - TASK-4
  - TASK-6
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today every input goes to POST /api/translations, so one word like 'Graveyard' gets a sentence explanation where Meaning and Words say the same thing. Spec R1: one token after normalising is a Word and uses the dictionary path (bundle, local cache, GET /api/words). It needs its own result view built from WordEntry senses.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Input with one token goes to the word lookup path; more than one token goes to /api/translations
- [ ] #2 Word result shows each sense: pos, CEFR, simple definition, Vietnamese, examples, collocations, synonyms
- [ ] #3 Token rule matches backend CacheKeys normalising (unit tests)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
