---
id: doc-7
title: Decisions and reasons
type: guide
created_date: '2026-10-11 08:52'
updated_date: '2026-10-11 09:10'
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
| 10-10 | Icon-only buttons with VoiceOver labels | "I think they look clean, and I've seen a lot of apps do it that way." It came after "too much text, users don't know where to focus." | Labelled Listen / Speak buttons | Developer |

## Design

| Date | Decision | Why | Lost options | Proposed by |
|---|---|---|---|---|
| 10-11 | Palette **A: Sage and Teal** | The first design looked "the same as Claude". Sage and Teal is the furthest from Claude's cream and clay and still quiet. All text passes WCAG AA. | B Paper and Indigo, C Fog and Plum | Developer chose, Claude recommended |
| 10-11 | Follow Apple HIG and a design system | "I want to follow a design system and Apple's design guidelines. I don't believe in my taste." | Own custom style | Developer |
| 10-11 | Code-first design system (Theme.swift + preview gallery), no Figma or Claude Design | HIG already sets most values, and SwiftUI previews show real iOS parts. HTML mockups only copy them. | Figma, Claude Design | Claude, accepted |
| 10-11 | No dark mode for now | "I'll make a mini game later, and dark mode won't work well with a game UI." | — | Developer |

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
| 10-03 | Pipeline in **Python** (uv) | Reason: not recorded (Claude's choice in the first brainstorm). | — | Claude |
| 10-10 | App talks only to our backend; remove direct LLM clients | "Remove the LLM client in the app, always call the API." Also: API keys in the app bundle can be pulled out of the .ipa. | DeepSeek / OpenAI / Claude in the app | Developer |
| 10-09 | On-device text to speech and speech to text | Free and works offline. | Cloud voices | Developer |

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

## Questions answered

The eight open reasons above were answered by the developer on 2026-10-11. New decisions without a reason
go here as questions.

- Python for the pipeline: Claude's choice in the first brainstorm. Reason not recorded; ask if it matters.
