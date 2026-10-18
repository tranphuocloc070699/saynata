---
id: doc-9
title: Progress timeline
type: guide
created_date: '2026-10-11 08:52'
updated_date: '2026-10-18 04:03'
---
# Progress timeline

A dated story of the project, for the video. Add a line at the end of each working day.

| Date | What happened | Moment for the video |
|---|---|---|
| 2026-10-03 | Idea: a simple-English dictionary with a Vietnamese line, "like Cambridge". Chose the NGSL word list (~2,800 words) instead of "all vocabulary". | The idea is too big, then made small enough to ship. |
| 2026-10-06 | Plan: an iOS app to replace DeepL for daily use. Studied the IceCubes app to learn Swift patterns. | Learning from a real open-source app. |
| 2026-10-07 | First Swift work. Lesson: never show an API key in a screenshot (it happened twice). | An honest mistake. |
| 2026-10-09 | Spec written. Java Spring Boot backend with a shared cache. Found out temperature 0 is not possible, so the cache became the "same output" guarantee. Dropped the English-level picker to keep one cache. | The "aha": the cache solves two problems at once. |
| 2026-10-10 | First commit. Backlog.md, feature docs with diagrams, doc checker. Pipeline moved to Claude Sonnet with 8 threads. Picked the name **Saynata**. Product rule: **explain first, translate last**. decision-2: usage patterns. ui-researcher agent with Mobbin. Repo made public. | The pivot from translator to teacher. Naming with whois. |
| 2026-10-11 | Dictionary done: 2,750 words ("revalidate" fixed 101 failures for $0). CI on GitHub Actions. First PRs merged (#1, #3, #4, #5, #6). Design system: Sage and Teal, HIG text styles. Icon-only button rule. Orchestrator workflow set up: Opus plans, Sonnet workers in worktrees, a guard hook, and features F1-F4. PR #2 lost to a stacked branch. | Going from one chat to a small AI team. |
| 2026-10-11 (evening) | First full feature shipped: **F1 Look up a word** merged into main (PR #14) after 17 task PRs from Sonnet workers. Added usage lines (16,043, 1,170 idioms) and levelled examples (22,388) with two paid passes (~$17). The developer's own tests found real bugs (old branch, runk -> drunk, missing IPA, flicker) and changed the design three times through mockups. New rules: feature status with every test request, mockup before UI code, always iPhone 16 Pro. | Testing finds what reviews miss: three bugs only showed up on the simulator. |
| 2026-10-14 | Hosting decided: ChatGPT said to rewrite the backend in TypeScript for Cloudflare Workers; chose a VPS and kept Spring Boot instead. Added auto deploy (GitHub Actions over SSH after CI). Backend deployed with its .env. **F3 Listen** built by Codex from a Claude-written prompt (speakers on the word, each explanation, each example), reviewed here, and merged with the share extension, sense photos and clips (Pixabay), and the new app icon. All features merged into main before the phone test, by the developer's choice. Old chats archived down to one. | "Do I need to rewrite?" No: pick the host that fits the code you have. |
| 2026-10-14 (evening) | First run on a real iPhone 13: HTTPS backend at api.loffy.me (Caddy + Let's Encrypt), the app pointed at it (PR #38, found by Codex from device logs: the app was calling localhost). F3 Listen, the share sheet and "an apple" approved after the phone test; the Translate-button integration is parked until a paid Apple account. Decisions: stop the 50,000-word plan ("it costs too much money"), an admin CMS (React + Refine + shadcn/ui, users with roles), the server database as the source of truth, images served from the server, no caching of personal-looking sentences. Conventions written into CLAUDE.md. Three cloud workers started (admin API, admin web app, cache privacy) while the developer was away. | A phone test beats every review: one log line showed the whole bug. |
| 2026-10-15 | Backend accounts and cost protection built: sign in with an email code (AWS SES), guests get only cached and bundled answers, a $1 per day Claude cap counted from real token usage, and rate limits. The iOS sign-in screen waits for a mockup. | Protecting the wallet before the app has real users. |
| 2026-10-15 (evening) | The mockup was approved (option A) and the iOS side of accounts was built (TASK-70): a sign-in sheet that opens when the backend says "sign in", the code in six boxes, tokens in the Keychain, refresh and retry, and Sign out and Delete account in Settings. After a right code the first request runs again by itself. | The app asks for an account only at the moment it is needed. |
| 2026-10-15 (evening) | Sentence answers made faster, three changes in one PR: the meaning now streams word by word (`meaning-delta`), the offline dictionary word cards show at once while Claude works, and the backend makes one tiny Claude call after start-up to warm the connection. | New sentences took 2-2.5 s and the first call after a restart 7-9 s; most of it is Claude's time to first token. |
| 2026-10-15 (night) | Media removal made real (TASK-76): No image, Pick another and a new Remove all media button now delete the picture file when nothing else uses it, so the app stops showing it; removed senses are not filled again by the backend job or the pipeline. The admin list also plays a clip on hover and has a full-size viewer (TASK-77). |
| 2026-10-16 | The admin can upload their own picture or clip for a sense (TASK-79): a new Upload button and drag and drop on the Media row, a server check of the real file type, the same small WebP or MP4 as Pixabay media, and the credit "Uploaded by the admin". The pipeline export keeps the file. | A cheap alternative to paid AI images for the pictures Claude marks as wrong. |
| 2026-10-16 | AI pictures for the senses Claude marked wrong (TASK-53): the developer picked OpenAI GPT Image mini, and a new pipeline command `media-generate` makes a flat Sage-and-Teal picture per sense (about $0.0035 each, a $5 cap that is never passed), shows old and new side by side in a report, and links the new picture only after its file is on the server. The 20-picture trial (about $0.08) is the developer's to run. |
| 2026-10-16 (evening) | **F6 Saynata on the Mac** (TASK-85): "I want to open with shortcut." The developer picked the popover under the menu-bar icon from a mockup. Built in four stacked PRs: a macOS target that compiles the iPhone app's code (no package, no change on iOS, 313 tests still pass), the popover with the same results, speech and email sign-in, a global ⌥⇧Space shortcut, and a Services item "Explain with Saynata" plus open at login. All of it runs with a free Apple ID. | Reusing the iPhone code made a whole Mac app a few hundred lines. |
| 2026-10-16 | A browser extension for Brave and Chrome (TASK-86): select English on any page, a small teal "S" appears (icon A from the mockup), and a card shows the simple English meaning as it streams, the words, and the Vietnamese row last and closed. Right-click and Alt+Shift+S work too. A settings popup has the language and Show translation, and sign-in uses the same email code as the app. No backend change: the service worker makes every call. | "Implement chrome extension, show popover on selected words, sentences like gg translate, deepL." |
| 2026-10-17 | **Explain copied text** (TASK-87): copy text anywhere, double-tap the back of the phone or press the Action Button, and a Shortcuts card shows the meaning over the app you are in. Layout B from the mockup: up to three meanings or words, Vietnamese last. Guests see "Sign in" plus the bundled words; a slow network gives "Still working" after 8 seconds. On the simulator the card first came out blank: Shortcuts could not read the app's asset colors, so the card now uses the same colors as numbers. | "In as few steps as possible, even on older devices, without interrupting the current app." |
| 2026-10-17 (afternoon) | On the developer's iPhone 13 (iOS 26.5) the card said "Nothing to explain": iOS cannot ask "Allow Paste" for an app in the background, so it gave nothing. The developer chose to open the app instead: Back Tap now opens Saynata with the copied text explained, or an empty input with the keyboard. | "Back tap is good." Lesson: test clipboard and permission features on the real phone early; the simulator showed the question, the phone did not. |
| 2026-10-17 (evening) | **Quick explain** (TASK-90): for words heard in a full-screen YouTube video. Back Tap, the Action Button or Siri asks for the text in a small iOS box, and a short card (mockup 2B) shows the meaning over the video; Saynata does not open. It reuses the TASK-87 card code, with the text typed instead of read from the clipboard. | "I want to explain some words without exit the full screen mode." |
| 2026-10-18 | Phrases as word cards (TASK-83.10): the backend answers a phrase like "give up" only from its cache (never Claude), and the app tries it as a word card before the sentence path. The ~7,200 phrasal verbs, idioms and collocations from the local collection can now be pushed. The IPA moved onto the headword line on every word card. |
| 2026-10-18 | The admin can draw an AI picture for one sense (TASK-91): the Sparkles button opens a dialog with the prompt already filled in, the developer edits it, and the server draws it with the Codex CLI on the VPS through his ChatGPT login, then links it like an upload. A wand asks Claude for a new scene; caps are 30 pictures and $1 of wand calls a day. | "Fix one wrong picture without leaving the admin." |

## Numbers to show

- Words in the dictionary: 2,750 (NGSL)
- Tasks: 40 created, 22 done (2026-10-11)
- Dictionary cost: Sonnet run inside the $200 credit; usage pass estimated at ~$8
- Server budget: ~$10 VPS

## Video outline (draft)

1. The problem: learning English with a translator does not teach English.
2. The idea and the pivot: explain first, translate last.
3. Building with AI without losing control: CLAUDE.md, Backlog, decisions, feature docs.
4. The AI team: one Opus orchestrator, Sonnet workers, Test it cards.
5. Mistakes and lessons (API key in screenshot, stacked PR, stale UX chat).
6. Demo of a feature on the phone.
7. The boilerplate you can copy.
