---
id: TASK-83
title: 'Pre-filled answers: common words and phrases ready before users type them'
status: To Do
assignee: []
created_date: '2026-10-16 08:18'
labels: []
dependencies: []
priority: high
type: feature
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Developer goal: "keep collecting words and sentences and bundle them on the backend, so results show up as fast as possible while users type." A cached answer comes back in 25-50 ms, a new Claude call takes about 2 s, and guests (REQUIRE_SIGN_IN=true) get no new Claude answers at all. On 2026-10-16 production had only 9 backend words and 28 sentences cached. Decisions (doc-7, 10-16): pre-fill through a backend job using the same prompt, cache key and checks as users; words with wordfreq Zipf >= 3.5 not in the bundled dictionary or the TASK-61 topic list; phrases (phrasal verbs, idioms); loose sentence match; guest misses NOT recorded; the job runs every night with its own $0.50 cap, separate from the users $1 daily budget. Plan notes: side-session notes prefill-plan.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Common words and phrases are answered from the cache, also for guests
- [ ] #2 The night job runs by itself on the VPS and never spends more than its own cap
- [ ] #3 The app gets cached answers while the user types, without new Claude calls
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
