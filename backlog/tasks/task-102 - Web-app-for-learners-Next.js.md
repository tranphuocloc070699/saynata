---
id: TASK-102
title: Web app for learners (Next.js)
status: To Do
assignee: []
created_date: '2026-10-23 10:05'
labels:
  - web
dependencies: []
type: feature
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Saynata has the iPhone app, a Mac menu-bar app and a browser extension, but no public website for learners. A website lets anyone try Saynata from a link, and word pages that Google can find bring new learners. Brainstorm and mockups: artifact https://claude.ai/artifact/P4yQXKHykS8zMhFkFKtHyA (8 MVP pages: Home, Word page, Introduction, Sign in, History & Liked, Settings, Privacy & Terms, 404). Chosen 2026-10-23: homepage direction B (one reading column with a cat, like the iOS home, TASK-97), Next.js App Router for server HTML on word pages and link previews, GSAP for motion. Same product rules as the app: simple English first, target language last and closed, icon-only buttons.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The web/ app builds with npm run build and has tests with npm test
- [ ] #2 All 8 MVP pages exist and follow the Sage and Teal design system
- [ ] #3 doc-23 (Web app) describes the flows
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
