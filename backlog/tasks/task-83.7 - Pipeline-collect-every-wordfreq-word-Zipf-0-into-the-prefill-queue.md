---
id: TASK-83.7
title: 'Pipeline: collect every wordfreq word (Zipf > 0) into the prefill queue'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-16 08:59'
updated_date: '2026-10-16 09:03'
labels: []
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 87000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer, 2026-10-16: "collects all words from wordfreq, Zipf > 0". TASK-83.2 collects only Zipf >= 3.5 (6,304 words); --zipf 0 today also lets in words wordfreq has never seen. Words the backend would reject (not on its SCOWL-70 + NGSL list: names, numbers, junk) must never be queued. A count on 2026-10-16 (SCOWL-70 lemmas minus bundled and topic words, Zipf > 0) gave about 68,330 words, about $48 to answer at $0.0007 a word. The queue runs higher priority first, so common words are answered first.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 uv run pipeline prefill collect --source words --all-wordfreq (or an equivalent flag) queues every lemma with wordfreq Zipf > 0 that the backend word list accepts, skipping bundled, topic-keep and already queued words
- [ ] #2 Priority still follows frequency, so a nightly run answers common words first
- [ ] #3 The dry run prints the count, the estimated Claude cost and nightly time at the current cap; no paid call is made
- [ ] #4 Tests and doc-6 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Add above_zero to collect_words, --all-wordfreq flag (words only), night_lines in the dry run, tests, doc-6.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built: prefill collect --source words --all-wordfreq (Zipf > 0, SCOWL-70 + NGSL, same list as backend EnglishWords). Dry run prints count, cost and night estimate. Production dry run (read-only, 2026-10-16): 68,669 new words, about 48.07 USD, 714 words a night at the 0.50 cap, about 83 min a run, about 96 nights. Test it: 1) nc -z localhost 55432. 2) cd pipeline && uv run pipeline prefill collect --source words --all-wordfreq --dry-run shows count, cost, nights, writes nothing. 3) --source phrases --all-wordfreq stops with an error. Automated: cd pipeline && uv run pytest tests/test_prefill.py

PR: https://github.com/tranphuocloc070699/saynata/pull/83
<!-- SECTION:NOTES:END -->
