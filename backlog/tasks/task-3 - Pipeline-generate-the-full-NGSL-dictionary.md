---
id: TASK-3
title: 'Pipeline: generate the full NGSL dictionary'
status: To Do
assignee: []
created_date: '2026-10-10 04:30'
updated_date: '2026-10-10 04:44'
labels:
  - pipeline
milestone: m-0
dependencies: []
references:
  - pipeline/README.md
priority: high
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The app bundles dictionary.sqlite, so iOS word lookup needs it. On 2026-10-10: 245 done, 134 failed (mostly network errors: ConnectError, ReadTimeout), 4 need review (hard words in definitions), 2426 pending.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 No pending words left in uv run pipeline report
- [ ] #2 Failed words retried; remaining failures and needs_review fixed or accepted
- [ ] #3 uv run pipeline export produces data/out/dictionary.sqlite
<!-- AC:END -->
