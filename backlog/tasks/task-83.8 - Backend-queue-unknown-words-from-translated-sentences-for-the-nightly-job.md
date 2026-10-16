---
id: TASK-83.8
title: 'Backend: queue unknown words from translated sentences for the nightly job'
status: To Do
assignee: []
created_date: '2026-10-16 08:59'
labels: []
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 88000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer, 2026-10-16: "when user request to translate sentences, check if any words (correct english words) in the sentence does not store in backend, queue it and run at night." Learners tap words inside a sentence result; a word that is not bundled and not cached costs a Claude call (and needs sign-in). Queuing them means tomorrow they answer from the cache. Only single validated English words are stored, never the sentence text. Uses the TASK-83.1 prefill_item queue and nightly job.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 After POST /api/translations and /stream respond (cache hit or miss), the words of the sentence are checked off the request path: on the backend English word list, not bundled (lemma or form), not in word_lookup; those go into prefill_item (kind word, source sentence-words)
- [ ] #2 A word already queued is not duplicated; a seen counter goes up each time, and priority grows with it (new Flyway migration)
- [ ] #3 Personal text (PersonalTextDetector) is never scanned; translation response time does not change (the work runs after the response, errors only logged)
- [ ] #4 The existing nightly Spring job (PREFILL_CRON, ClaudeGate prefill cap) answers them; no second scheduler
- [ ] #5 Tests with a fake Claude; doc-18 Prefill and doc-4 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
