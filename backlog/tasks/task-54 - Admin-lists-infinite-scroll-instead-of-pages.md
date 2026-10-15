---
id: TASK-54
title: 'Admin lists: infinite scroll instead of pages'
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 06:08'
updated_date: '2026-10-15 06:11'
labels:
  - admin
  - web
dependencies: []
ordinal: 57000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The admin Words list has 2,750 bundled words, and paging 20 at a time with arrows is slow to browse (developer decision 2026-10-15). The shared list in EntryBrowser should load the next page when the user scrolls near the end. Note: the Media page is still a placeholder with no list, so it will get this when it uses EntryBrowser.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Scrolling near the end of the list loads the next page from the server (50 items per page); pages are not all loaded at once
- [ ] #2 Changing the search or a filter chip resets the list to the first page
- [ ] #3 Footer shows a count line like '120 of 2,750' and has no previous/next arrows
- [ ] #4 A small spinner row shows at the bottom while more is loading; no button text
- [ ] #5 The selected item stays selected and visible when more pages load; after edit or Mark reviewed the list row updates without going back to the top
- [ ] #6 Vitest tests cover loading the next page, reset on filter change, and the count line; npm test and npm run build pass
- [ ] #7 doc-14 is updated and python3 scripts/check_docs.py passes
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace useList with useInfiniteList (pageSize 50) in EntryBrowser; flatten pages.
2. Sentinel li + IntersectionObserver near the end calls fetchNextPage when hasNextPage.
3. Footer: '<loaded> of <total>' (toLocaleString), spinner row while fetching next page; remove arrows and page state.
4. Selection kept by id; refetch after edit keeps scroll (infinite query refetches loaded pages).
5. Vitest tests; update doc-14; check_docs; PR.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented in EntryBrowser (useInfiniteList, 50 per page, IntersectionObserver sentinel, N of total footer, spinner row). npm test 31 pass, npm run build ok, doc-14 updated. Test it: run admin (npm run dev), open Words. Footer says 50 of 2,750 with no arrows. Scroll to load more with a spinner row. Search or the Needs review chip resets to page 1. The selected word stays selected. Mark reviewed far down updates the row without jumping to top. Automated: cd admin && npm test.

PR: https://github.com/tranphuocloc070699/saynata/pull/52
<!-- SECTION:NOTES:END -->
