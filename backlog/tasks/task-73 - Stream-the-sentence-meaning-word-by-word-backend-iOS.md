---
id: TASK-73
title: Stream the sentence meaning word by word (backend + iOS)
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 13:24'
updated_date: '2026-10-15 13:37'
labels:
  - backend
  - ios
dependencies: []
references:
  - backlog/docs/doc-16 - Plan-Mac-menu-bar-app-and-browser-extension.md
documentation:
  - backlog/docs/features/doc-4 - Sentence-translation.md
priority: high
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The meaning event arrives at 1.3-1.8 s on production and then appears all at once. Developer decision 2026-10-15: send the meaning as it grows (meaning-delta events) so text starts to show earlier. Old app versions ignore unknown events.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend sends event meaning-delta {"text": new characters} while the meaning string grows, throttled to about 1 per 50 ms; escapes and surrogate pairs are decoded and never split
- [ ] #2 The full meaning event and done are unchanged (order: deltas, meaning, done); not-English answers send no deltas
- [ ] #3 iOS appends deltas in the same Meaning card; meaning replaces the text, done gives the full result; out-of-order or missing deltas never break the final result
- [ ] #4 Tests: delta reader cut at every position, event order, iOS store states loading, partial meaning, meaning, loaded; old clients ignoring deltas still work
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. JsonStringFieldReader.drainDelta (decoded text, surrogates held back). 2. MeaningStream: 50 ms throttle, flush before meaning. 3. Controller sends meaning-delta. 4. iOS: Translating + BackendClient onMeaningDelta; store appends, full meaning wins. 5. Tests both sides, docs.
<!-- SECTION:PLAN:END -->
