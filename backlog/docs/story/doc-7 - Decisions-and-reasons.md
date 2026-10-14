---
id: doc-7
title: Decisions and reasons
type: guide
created_date: '2026-10-11 08:52'
updated_date: '2026-10-14 11:22'
---
# Decisions and reasons

Every product, stack and workflow decision in Saynata so far, with the reason, the options that lost,
and who proposed it. Built on 2026-10-11 from past chat transcripts, daily notes, tasks and git history.
Quotes are the developer's words, with spelling fixed.

**"Reason: not recorded"** means no reason was ever written down. Those go in the list at the end.

## Product

| Date | Decision | Why | Lost options | Proposed by |
|---|---|---|---|---|
| 10-03 | Simple-English dictionary for a Vietnamese learner, "like Cambridge Dictionary" | "Explain in English, simple terms, and a Vietnamese version." | — | Developer |
| 10-03 | Word list = NGSL (~2,800 words) | "All English vocabulary" is 170k+ words, too big. NGSL covers ~92% of normal text and is CC BY-SA, so it is free to use. Copying Cambridge text would break copyright. | All vocabulary; copying Cambridge | Claude |
| 10-06 | iOS app as a daily translator instead of DeepL | "I'll build it and make it my default translator instead of DeepL." | — | Developer |
| 10-09 | No English-level picker | Answers per level break the shared cache: up to 6 LLM calls per sentence instead of 1. | Level picker | Claude, accepted |
| 10-10 | Name **Saynata** (say + sonata), saynata.com | Brief: "something related to plan and music", a .com under $20. Simple names (Score, Étude, Cadence...) were taken, so names were blended and checked with whois. Why Saynata over Phrasody: "saynata.com was available, and I think it is easy to remember." | Phrasody (Claude's pick), Phrasonata, Phrasetto, Wordetto, Lexicato | Developer |
| 10-10 | **Explain first, translate last.** Saynata teaches English; the Vietnamese line is at the bottom, collapsed | "I want to explain word or sentence meanings rather than translate to the target language." In the developer's words: "I want to learn vocabulary, and I think using English to explain English is the best way to learn it." | Translation as the headline | Developer |
| 10-10 | Vietnamese line collapsed by default; Settings can change it | "Default is collapsed, and in Settings there is a place to change it." | — | Developer |
| 10-10 | **decision-2:** "How to use it" (sentence patterns) replaces word partners and simpler words | Chips show word groups but not how to build a sentence. 19% of "simpler words" were harder than the headword (get → obtain). A usage pass costs ~$8 instead of ~$36 to regenerate. | Fix the chips; remove them; rename only | Developer chose from Claude's options |
| 10-11 | "How to use it" = option A: up to 3 lines per sense, each a pattern, collocation, phrase or idiom with one example and a small kind label; placeholders someone / something / + noun | The developer wants natural collocations and idioms, not only grammar. One line + one example is the simplest, and a kind tag lets the future mini game pick, e.g., idioms only. `sb`/`sth` are unclear to B1 learners. | B: pattern + note; C: + common mistake; D: + practice gap | Developer chose A from Claude's options, added the collocation/idiom focus |
| 10-11 | Usage lines: include at least one idiom when the sense has one (less common idioms are OK), and no duplicates across a word's senses | After the first dry run: "I want more idioms, not only common ones, and no duplicates." The dry run had few idioms and pairs like "depend on + something" / "It depends on + noun". | "Common items only" (the first rule) | Developer |
| 10-11 | Word card v2: each meaning in its own card, titled "number + simple explanation"; Examples (3, easy to harder) and Grammar (usage grouped by kind, bullets) as folded rows; "past tense of run" line for forms; "Did you mean" for typos | After testing F1: meanings looked the same and were hard to tell apart; "ran" mixed with "run"; the usage block was too flat. Did you mean is free and offline. | Option A (numbered list with dividers); "Meaning 1 of 3" titles; a short 1-3 word label | Developer chose from mockups v1-v3 |
| 10-11 | Other target languages on word cards: Apple on-device Translation of the English explanation; Vietnamese stays from the dictionary; add backend short meanings (Haiku, cached) later only if quality is not good enough | "I don't want to spend a lot of money." Only one line per meaning changes with the language; on-device translation is $0 and offline. | B: Haiku on demand (~$0.0002/word); C: pre-build 8 languages (~$7, bigger app) | Developer chose A |
| 10-12 | Grow the dictionary to about 50,000 words, from a word file the developer gives (read from a dictionary) | Reason: not recorded. The developer wants words like "apple" and "house", which the NGSL list (2,750) does not have. | wordfreq top lemmas, NGSL family lists (~4,000), wordfreq + topic lists | Developer |
| 10-12 | A word with an article ("an apple", "the apple!") is looked up as the bare word; only for one word, sentences keep their articles (TASK-44) | The developer typed "an apple" and got a sentence explanation instead of the word card. | — | Developer |
| 10-14 | **Stop** growing the dictionary to 50,000 words; it stays at the NGSL list (2,750 words), and other words come from the backend (Claude Haiku, cached) | "Because it costs too much money. I chose to generate words when a user needs them." Replaces the 10-12 rows on 50,000 words and their word file. | Keep the 50,000-word plan | Developer |
| 10-14 | Sense photos and action clips from **Pixabay** instead of Pexels (TASK-23) | "Because Pixabay has an API." Pixabay's license also allows storing resized copies, so files can be bundled in the app. | Pexels (the plan in TASK-23), Unsplash (needs hotlinking) | Developer (built by a local agent) |
| 10-14 | F3 Listen first; F4 Speak and score later | "I think it's necessary at this time." | F3 and F4 together | Developer |
| 10-14 | F3 reads three things: the headword, each sense's simple explanation, each example sentence. Not the Vietnamese line, Grammar examples, other languages, slow speed or accent choice | "I think those are necessary. I can add other types of content later." Claude's brainstorm also suggested slow speed on a second tap and hiding a speaker with no voice. | Read all text; also Vietnamese; also Grammar examples | Developer chose from Claude's list |
| 10-10 | Icon-only buttons with VoiceOver labels | "I think they look clean, and I've seen a lot of apps do it that way." It came after "too much text, users don't know where to focus." | Labelled Listen / Speak buttons | Developer |

## Design

| Date | Decision | Why | Lost options | Proposed by |
|---|---|---|---|---|
| 10-11 | Palette **A: Sage and Teal** | The first design looked "the same as Claude". Sage and Teal is the furthest from Claude's cream and clay and still quiet. All text passes WCAG AA. | B Paper and Indigo, C Fog and Plum | Developer chose, Claude recommended |
| 10-11 | Follow Apple HIG and a design system | "I want to follow a design system and Apple's design guidelines. I don't believe in my taste." | Own custom style | Developer |
| 10-11 | Code-first design system (Theme.swift + preview gallery), no Figma or Claude Design | HIG already sets most values, and SwiftUI previews show real iOS parts. HTML mockups only copy them. | Figma, Claude Design | Claude, accepted |
| 10-11 | No dark mode for now | "I'll make a mini game later, and dark mode won't work well with a game UI." | — | Developer |
| 10-14 | F3 speakers: word speaker stays in the header; a small speaker on each sense title row; a small speaker at the end of each example line (option A) | Easy to find, and VoiceOver reads it as a button. | B: tap the example text itself (cleaner, but hard to find and clashes with text selection) | Developer chose A from the mockup; Claude recommended |
| 10-14 | Examples rows: level label and a **plain** speaker icon (no circle, no border) on line 1, the sentence on line 2; speaker at the right edge (mockup option A) | The developer's phone test: "it's too narrow". The label, the sentence and a round speaker shared one line, so the sentence wrapped every 2-3 words. A keeps all speakers in one column. | B: speaker right after the label; keep one line (TASK-48) | Developer (layout idea), chose A from Claude's mockup |

## Stack

| Date | Decision | Why | Lost options | Proposed by |
|---|---|---|---|---|
| 10-03 | Mobile first, Swift | "I want to use it on my phone first. I usually read books" (so lookups happen on the phone, away from a computer). Also: "I want to learn Swift and follow good patterns, like IceCubes." | Web first (Claude) | Developer |
| 10-09 | Backend in **Java Spring Boot** | "I have more experience with Java, so it's easier for me to review the code if the project uses Java." A ~$10 VPS has enough memory, and `java.md` practices exist. | Python FastAPI (Claude) | Developer |
| 10-09 | **Cache first, offline first** (bundled dictionary → phone cache → shared server cache → LLM) | "Users need a response as fast as possible." "Users should still be able to do everything offline." "If other users search the same thing, reuse it." | — | Developer |
| 10-09 | Same input, same output, by cache (not temperature 0) | Current Claude models reject `temperature`, and even temperature 0 is not fully repeatable. A cache keyed on normalised text + prompt version is the real guarantee. | temperature 0 | Claude, accepted |
| 10-09 | No `@Transactional` around the LLM call | A transaction would hold a DB connection for seconds. A unique key + catching the duplicate-key error handles two requests at once. | — | Claude |
| 10-09 | Backend model **Claude Haiku 5.5** | "It's cheaper than Sonnet, and the answers are just as good." | — | Developer |
| 10-10 | Pipeline model **Claude Sonnet 5.5**, 8 threads in parallel | Built once and it is the core of the app. Sonnet gave simpler definitions and better Vietnamese ($40-55) than Haiku (~$3). Paid from $200 credit. | Haiku, Gemini, DeepSeek | Developer chose after Claude's test |
| 10-12 | Pipeline model for the next words: **Claude Haiku 5.5** | "Could you change to Haiku, because I want to explain around 50,000 words." Sonnet would cost several hundred dollars at that size. Real cost is measured on 50 words first. | Sonnet (same model as the first 2,750 words) | Developer |
| 10-03 | Pipeline in **Python** (uv) | Reason: not recorded (Claude's choice in the first brainstorm). | — | Claude |
| 10-10 | App talks only to our backend; remove direct LLM clients | "Remove the LLM client in the app, always call the API." Also: API keys in the app bundle can be pulled out of the .ipa. | DeepSeek / OpenAI / Claude in the app | Developer |
| 10-09 | On-device text to speech and speech to text | Free and works offline. | Cloud voices | Developer |
| 10-14 | Host the backend on a **VPS** (`vps_sieutoc`), keep Spring Boot | "I want to ship my app fast, I want to use it on my phone." Workers would need a TypeScript rewrite and no Postgres; the VPS guide already existed. | Cloudflare Workers (ChatGPT's idea), Cloudflare Containers | Developer, after Claude's comparison |
| 10-14 | **Auto deploy**: GitHub Action SSHes into the VPS after CI passes on main | Developer asked for auto deploy. A deploy key in GitHub secrets means no Claude session needs SSH access to the server. | Claude sessions SSH in directly; manual deploys | Claude, accepted |
| 10-14 | **Admin CMS** = a separate web app: React (Vite) + React-Admin, served as static files by Caddy (admin.loffy.me), talking to a new Spring Boot `/api/admin` REST API with Spring Security (ADMIN role) | "I want to create a CMS to manage, because later I want to implement user features." Reason for B over A/C: not recorded (Claude's reasons: tables, search and forms for free; the same REST API and Spring Security serve app users later; light on the small VPS). | A: admin pages inside Spring Boot (Thymeleaf + htmx); C: ready-made headless CMS (Directus/Strapi) on the same Postgres | Developer chose B, Claude recommended |
| 10-14 | **The server Postgres becomes the source of truth** for words, sentences and media; the pipeline exports the bundled `dictionary.sqlite` from it at release time | Needed for the CMS: one place to edit. Backend entries change live; bundled words reach users with the next app update. Reason in the developer's words: not recorded. | Keep `dictionary.sqlite` as the source and edit it only through the pipeline | Developer chose, Claude recommended |
| 10-14 | Admin UI built with **shadcn/ui** components and **Refine** (data, login, CRUD on the REST API), instead of React-Admin; the entry detail is a plain table of fields, not the phone layout; layout **A**: list and detail side by side | "I want an open-source library like shadcn to create the UI and connect to the backend. No need to make the UI like the phone." | React-Admin (Material UI look); detail shown as the app shows it; B: detail on its own page | Developer |
| 10-14 | Admin login with a **users table with roles** (ADMIN now; app users later reuse it) | Reason: not recorded (Claude: app users are planned, so one account system from the start). | One admin account from `.env` | Developer chose, Claude recommended |
| 10-14 | Sense images and clips are **served from the server** (`https://api.loffy.me/media/...`, files on the VPS behind Caddy, rows in Postgres), downloaded by the app once and cached; words that come from the backend get an image when their entry is created (Pixabay) | The developer saw no image for "apple" (a backend word; only 3 sample files existed). Reason for B in the developer's words: not recorded (Claude: works for backend words, keeps the app small, fits the CMS). | A: bundle all media in the app (~20-40 MB, nothing for backend words) | Developer chose B, Claude recommended |
| 10-14 | Shared sentence cache privacy (TASK-9): keep the cache shared, but **do not store** sentences that look personal (email address, phone number, link, long number); they are still explained, just not cached | Reason: not recorded (Claude: the cache returns one user's text to others; this keeps the cost saving without storing personal data). | Per-user cache (needs logins); keep as is | Developer chose 1, Claude recommended |

## Workflow

| Date | Decision | Why | Lost options | Proposed by |
|---|---|---|---|---|
| 10-10 | **Backlog.md** for tasks, docs and decisions | "I vibe code this project, so I need something to track the progress and goal. I prefer open source tools over building my own." | Spec Kit, Task Master, GitHub Issues | Developer |
| 10-10 | Feature docs with `file:line` links and diagrams | "I forget easily. How can I still remember the flow after a few days?" | — | Developer |
| 10-10 | Docs must stay in sync with code (Definition of Done + `check_docs.py` in CI) | "I want to make sure they are up to date with the code." | — | Developer |
| 10-10 | Open source, public repo | "I want to make it open source." | Private repo | Developer |
| 10-10 | Decisions go into the spec and `backlog/decisions/`, not only chat | A UX chat still showed word partners after decision-2, because it never saw that decision. | Chat only | Claude, after that bug |
| 10-10 | `ui-researcher` agent (Sonnet + Mobbin) in its own chat | A design partner that shows real apps; "focus on UX first: fewer steps, more convenient". | — | Developer |
| 10-11 | **Orchestrator + workers**: Opus 5.5 plans and reviews, Sonnet 5.5 builds | "The current process is too long." Opus for judgement, Sonnet for cheaper, faster building. | One chat does everything | Developer |
| 10-11 | One GitHub PR per testable feature, with a **Test it** card | "I want features that I can test on a real device or simulator, and I want to know what to test and what the goal is." | PR per task; local-only branches | Developer (PR per feature: Claude) |
| 10-11 | 3-4 workers per wave | The developer's review time is the real limit, not the number of agents. | — | Claude |
| 10-11 | Workers in bypassPermissions mode | "I'm lazy, and I want to see how convenient bypassPermissions is." An experiment, kept safe by the guard hook. Note: it only works when the main chat is also in that mode. | acceptEdits (Claude) | Developer |
| 10-11 | No stacked PRs; feature branches deleted only after their main PR merges | PR #2 was closed by GitHub when #1's branch was deleted. | Stacked PRs | Claude, after that bug |
| 10-11 | Guard hook blocks force-push and push to main | Bash deny rules miss forms like `git -C . push`. A hook blocks in every permission mode. Tested. | Deny rules only | Claude |
| 10-14 | Merge all features (F3 Listen, share extension, sense media, app icon, "an apple") into main before testing on the phone; test later | "I'll install on my phone and test later." Breaks the "merge only after the developer tests" rule for this round. iOS tests ran only on the worker's Mac (Codex: 127 passed) and not after the F3/share-extension merge. | Test each feature first, then merge | Developer |

## Questions answered

The eight open reasons above were answered by the developer on 2026-10-11. New decisions without a reason
go here as questions.

- Python for the pipeline: Claude's choice in the first brainstorm. Reason not recorded; ask if it matters.
- 10-14: Why the separate React admin (B) over admin pages inside Spring Boot, and why make the server database the source of truth? Reason not recorded.
- 10-14: Why images from the server (B), and why the "do not store personal-looking sentences" rule (1)? Reason not recorded.
