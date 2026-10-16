---
id: TASK-83.7
title: 'Pipeline: collect every wordfreq word (Zipf > 0) into the prefill queue'
status: To Do
assignee: []
created_date: '2026-10-16 08:59'
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
