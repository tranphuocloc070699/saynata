---
id: TASK-102.1
title: 'Web homepage: explain a word or sentence, with GSAP motion'
status: In Progress
assignee: []
created_date: '2026-10-23 10:05'
updated_date: '2026-10-23 10:26'
labels:
  - web
dependencies: []
parent_task_id: TASK-102
type: feature
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The first page of the web app (TASK-102): the tool itself. Direction B from the brainstorm (artifact https://claude.ai/artifact/P4yQXKHykS8zMhFkFKtHyA): one composer card with a pixel cat on its edge, suggestions while typing, and the result below in one reading column. The developer asked for GSAP for the motion. The backend has no CORS setup, so the web app reaches /api through a same-origin proxy. Guests get only cached and bundled answers (doc-17); a miss shows a sign-in message until the Sign in page exists.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Typing shows up to 8 suggestions from GET /api/suggest, 250 ms after the last key
- [x] #2 One word opens a word card (headword, IPA, Listen, senses with level chip and examples); the target-language row is last and closed
- [x] #3 A sentence streams its simple English summary from POST /api/translations/stream; the Vietnamese row is last and closed
- [x] #4 The query is in the address (/?q=...), so a result can be shared and reloaded
- [x] #5 GSAP animates the page entrance, the cat, the suggestions and the result cards; prefers-reduced-motion shows the final state with no motion
- [x] #6 Errors (sign in required, rate limited, network) show a clear line near the composer
- [x] #7 Buttons are icon-only with aria-labels and a 44px tap target; the page works at 375px with no side scroll; light and dark
- [x] #8 npm run build and npm test pass in web/
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Scaffold web/ with create-next-app (Next 16, Tailwind 4), add gsap, @gsap/react, Vitest.
2. Copy the tested API client, SSE parser and textKind from extension/src/shared; add suggest().
3. Search logic in lib/explain.ts (startState, fetchResult), tested without React.
4. Home: query in the address, state set during render, fetch in an effect; Composer with PixelCat; WordCard and SentenceCard like iOS.
5. GSAP through gsap.matchMedia (reduced motion = no motion): page enter, cat, result reveal, chips.
6. /api same-origin rewrite in next.config.ts; doc-23, doc-3, doc-7, CLAUDE.md, CI job.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Built in worktree branch web/task-102.1-homepage.
- Checked in the browser pane against https://api.loffy.me through the rewrite: suggestions for "giv", word "run" (4 senses, clip), cached sentence "Hold on a second." streamed, guest miss shows the sign-in line, 375 px and dark mode.
- The browser pane was hidden, so GSAP ran at 2 frames a second there; the final layout was checked with motion forced off. The developer should look at the motion in a real browser.
- Samples under the box are cached answers only (give up, run, Hold on a second., Looks good.), so guests can try them.
- The skill design-system search suggested indigo + Baloo/Comic Neue (a kids style); not used, the Sage and Teal tokens win.
- Follow-ups in TASK-102: word page, sign-in, other pages, Caddy route and domain for deploy.
<!-- SECTION:NOTES:END -->
