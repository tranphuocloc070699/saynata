---
id: TASK-57
title: >-
  Admin: make sure the mark-reviewed button clears the flag, and show a reason
  count in the list
status: In Progress
assignee:
  - '@implementer'
created_date: '2026-10-15 07:16'
updated_date: '2026-10-15 07:26'
labels:
  - backend
  - admin
dependencies: []
priority: high
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
After the PR #55 deploy the developer still sees "Two words still need review" ("apple" and "working", both from the backend). We do not know if the check button really clears needs_review for backend words, and the list row only says "Needs review" with no hint of how many problems there are. The flag must never be cleared automatically: the developer decides.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A backend test marks a backend word reviewed and reads it back through the API and the repository: needs_review is false
- [ ] #2 If marking reviewed does not clear the flag for backend words, it is fixed
- [ ] #3 The list row of a flagged word shows a short reason count (and a tooltip with the reasons) next to the Needs review badge
- [ ] #4 The PR tells the developer what to do for apple and working (fix the hard words, or press the check button)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Backend test: mark a backend word reviewed with the request the admin sends (POST, JSON body), read it back (API, list filter, repository). Fix if it does not clear. 2. Show a reason count and tooltip next to the badge in the list row. 3. Vitest, docs 13 and 14.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PR: https://github.com/tranphuocloc070699/saynata/pull/58. Root cause: the backend clears the flag (new test). The admin app refreshed 'list' queries but the infinite list lives under 'infinite', so the badge stayed until reload; now invalidates 'all'. Test it: after deploy, reload admin.loffy.me, Needs review chip, see 'N problems' badges (tooltip = reasons), open working and apple: fix with the pencil or press the check button. Expected: badge disappears at once and stays gone after reload. Automated: cd backend && ./mvnw -B test (178 pass); cd admin && npm test && npm run build (41 pass).
<!-- SECTION:NOTES:END -->
