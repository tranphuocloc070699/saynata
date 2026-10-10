---
id: TASK-21
title: 'Sentence prompt v4: fix bad output for code, gibberish and base forms'
status: To Do
assignee: []
created_date: '2026-10-10 06:37'
labels:
  - backend
dependencies:
  - TASK-15
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Probe 2026-10-10 found: (1) code ('for (int i = 0; ...') and gibberish ('asdkj qwe zzxq') get a Vietnamese *explanation* in the translation field instead of a translation, and it is cached forever; code tokens get wrong parts of speech ('for' = noun). (2) 'base form: X' is added to words that are not past or -ing forms ('Ignore', 'previous (adjective)', 'reluctant'). (3) For a single word, the translation sometimes translates the meaning paragraph ('Graveyard' -> 'Nghĩa địa là nơi chôn cất người chết. Đó là một khu đất có nhiều mộ.').
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Decision recorded for non-English or not-a-sentence input (copy text unchanged, a flag in the response, or 422)
- [ ] #2 translation is always a translation of the input, never an explanation
- [ ] #3 Base form only appears for inflected words
- [ ] #4 The probe cases in backlog/docs/features (doc-4, Probe results) pass
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #2 Tests pass
<!-- DOD:END -->
