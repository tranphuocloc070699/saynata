---
id: doc-9
title: Progress timeline
type: guide
created_date: '2026-10-11 08:52'
updated_date: '2026-10-14 09:12'
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
