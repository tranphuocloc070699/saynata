---
id: TASK-83.13
title: 'Backend: loose sentence match drops quotes around the whole sentence'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-23 07:34'
updated_date: '2026-10-23 07:42'
labels:
  - backend
  - cache
dependencies: []
parent_task_id: TASK-83
priority: medium
ordinal: 102000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why
After TASK-83.3 the loose match makes curly quotes straight but keeps them. A sentence copied with quotes around it misses the cache: on 2026-10-23 the live API answered `Looks good.` from the cache, but `"Looks good."` and `“Looks good.”` gave 404 with cachedOnly=true. Copied text often has these quotes.

## What
In `CacheKeys.normalizeSentenceLoose`, after the straight-quote step and trimming, remove ONE matching pair of quotes (" or ') that wraps the whole text, also when the final . ? ! … sits inside or outside the closing quote. Inner quotes stay (She said "hi" to me). A new Flyway Java migration recomputes match_key for saved rows (like V24); never edit V18/V24.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 "Looks good.", “Looks good.”, 'Looks good.' and "Looks good". give the same loose key as Looks good.
- [ ] #2 Quotes inside a sentence are kept; an apostrophe word ('cause) is not broken
- [ ] #3 A new Flyway migration recomputes match_key for saved sentences; tests cover it
- [ ] #4 doc-4 and doc-18 updated
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CacheKeys.withoutWrappingQuotes in normalizeSentenceLoose. 2. Flyway Java V25 recompute. 3. Tests. 4. doc-4, doc-18.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/145. Test it: after deploy, POST https://api.loffy.me/api/translations?cachedOnly=true with {"text":"“Looks good.”","targetLang":"vi"} gives 200 (also \"Looks good.\" and \"Looks good\".); an uncached text still gives 404 not_cached. Automated: cd backend && ./mvnw -B test (504 pass).
<!-- SECTION:NOTES:END -->
