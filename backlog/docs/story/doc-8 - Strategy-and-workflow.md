---
id: doc-8
title: Strategy and workflow
type: guide
created_date: '2026-10-11 08:52'
updated_date: '2026-10-11 09:10'
---
# Strategy and workflow

How Saynata is built, why it is built this way, and who else works like this. Use it as the script
for a video, and as the plan for a boilerplate for new projects.

## The situation

- One developer, 3-5 years of experience, most of it in Java (so the backend is Java: "easier for me to review"). New to Swift, learning English (B1-B2).
- Most of the code is written by AI ("I vibe code this project").
- Many projects in one day, so it is easy to forget how a flow works.
- Small budget: a ~$10 VPS, $200 of Claude API credit, free tools first.

The strategy below is the answer to one problem: **how to keep control of a codebase that an AI writes
for you.**

## 1. The product strategy: teach, don't translate

**What:** each result is a simple English explanation first (CEFR B1-B2). The Vietnamese line is a small
helper at the bottom, collapsed by default. Each word shows how to use it in a sentence (patterns like
"be reluctant to + verb"), not lists of similar words.

**Why:** the developer is the first user, and English is their top goal. Reading a translation does not
build English. Reading a simple English explanation does. In the developer's words: "I want to learn vocabulary, and I think using English to explain English
is the best way to learn it." Mobile first, because "I want to use it on my phone first. I usually read books."

**Who else does it:**
- Learner's dictionaries explain words only with simple English. The Longman dictionary uses a
  defining vocabulary of about 2,000 words; Collins COBUILD explains words in full, natural sentences.
- "How to use it" follows *pattern grammar* (Francis, Hunston and Manning, *COBUILD Grammar Patterns*, 1996).
- CEFR levels: https://www.coe.int/en/web/common-european-framework-reference-languages
- NGSL word list: https://www.newgeneralservicelist.com/

## 2. The technical strategy: cache first, offline first, cheap

**What:** words come from a dictionary bundled in the app. Repeats come from the phone's cache. New
results come from a shared server cache. Only then does the app call an LLM, and the result is saved.
Speech runs on the phone.

**Why:** speed ("users need a response as fast as possible"), offline use, and cost: the same sentence
is paid for once, for every user. The cache also gives "same input, same output", which LLM settings
cannot promise.

**Who else does it:**
- Cache-aside pattern, Microsoft Azure Architecture Center:
  https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside
- Offline-first apps, Android developer guide:
  https://developer.android.com/topic/architecture/data-layer/offline-first
- Keep API keys on a server, not in the app: OWASP MASVS, https://mas.owasp.org/MASVS/05-MASVS-STORAGE/ .
  The open-source IceCubes app (Mastodon client) calls its own relay server for the same reason.
- Build the dictionary once with a strong model (Sonnet), answer at runtime with a fast, cheap one (Haiku).

## 3. The memory strategy: write everything down where the AI reads it

**What:**

| Layer | File | Holds |
|---|---|---|
| Rules | `CLAUDE.md` | Product and UI rules every chat must follow |
| Reasons | `backlog/decisions/`, this folder | Why each choice was made |
| Spec | doc-1 Vision and spec | Goal, requirements, API |
| How flows work | `backlog/docs/features/` | Files, `file:line` links, diagrams, known gaps |
| Work | Backlog tasks | Plan, notes, acceptance criteria, "Next step" |
| Progress | `~/.agents/daily/` | Daily notes and lessons |

Docs must change in the same commit as the code (Definition of Done), and `check_docs.py` checks the
links in CI.

**Why:** "I forget easily." A new chat starts with no memory. If a decision lives only in a chat, the
next chat breaks it. That happened: a UX chat brought back word partners after they were removed.

**Who else does it:**
- Architecture Decision Records (Michael Nygard, 2011, https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions): https://adr.github.io/ . AWS recommends ADRs too:
  https://docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/introduction.html
- Docs as code (Write the Docs): https://www.writethedocs.org/guide/docs-as-code/
- GitLab runs the whole company from a written handbook ("handbook first"):
  https://handbook.gitlab.com/handbook/about/handbook-usage/
- Backlog.md, the open-source tool used here: https://github.com/MrLesk/Backlog.md
- `CLAUDE.md` project memory: https://code.claude.com/docs/en/memory

## 4. The team strategy: one orchestrator, several workers

**What:**
- **Orchestrator** = the main chat, Opus 5.5. It plans, starts workers, reviews PRs and merges after
  the developer says yes. It does not write feature code.
- **Workers** = `implementer` subagents, Sonnet 5.5, each in its own git worktree (its own copy of the
  repo). Each builds one task, runs the tests, and opens a PR.
- **Feature branches:** one branch per testable feature (F1 Look up a word, F2 Explain a sentence,
  F3 Listen, F4 Speak and score). Task PRs merge into it. One feature PR goes to `main`.
- **Test it card** on every PR: goal, steps on the simulator or a device, expected result.
- **3-4 workers per wave**, because the developer's review time is the real limit.
- **Guards:** a hook blocks force-push and push to `main` in every permission mode.

**Why:** "The current process is too long." A strong model is best used for judgement (plan, review)
and a cheaper one for building. Worktrees let workers run at the same time without touching each other.
The Test it card means the developer checks behaviour, not every line of code they could not absorb.

**Who else does it:**
- Anthropic's research feature uses the same split: an Opus lead agent plans and Sonnet subagents work
  in parallel. https://www.anthropic.com/engineering/multi-agent-research-system
- "Orchestrator-workers" is one of the basic agent patterns in Anthropic's *Building effective agents*:
  https://www.anthropic.com/engineering/building-effective-agents
- Parallel Claude Code sessions with git worktrees: https://code.claude.com/docs/en/common-workflows
- Branch patterns (feature branches, integration branches): Martin Fowler,
  https://martinfowler.com/articles/branching-patterns.html
- PR templates with test steps: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-prs/creating-a-pull-request-template-for-your-repository

## 5. The design strategy: follow Apple, not taste

**What:** Apple's Human Interface Guidelines, iOS text styles, a small token set (Sage and Teal), and a
SwiftUI preview gallery as the "mockup". Icon-only buttons with VoiceOver labels.

**Why:** "I don't believe in my taste." HIG already decides most values. Real SwiftUI previews are
more honest than Figma or HTML mockups. Mobbin (real app screens) is used for references. Icon-only buttons: "they look clean, and
I've seen a lot of apps do it." No dark mode yet: a mini game is planned, and dark mode does not suit a game UI.

**Who else does it:** Apple HIG, https://developer.apple.com/design/human-interface-guidelines/ ;
design tokens are used by most design systems (Material, Carbon).

## 6. The personal strategy: learn while shipping

- Learn Swift by building a real app with good patterns (IceCubes as the reference).
- Learn English from the app itself and from every AI message (corrections, `mistakes.md`).
- Open source, so the work can be shown, and later a video about the process.
- Prefer what ships: small features you can test on a phone beat a big plan.

## Boilerplate checklist (for a new project)

1. `CLAUDE.md`: overview table, product rules, UI rules, "read before working" list.
2. Backlog.md: tasks, `backlog/decisions/` (one record per decision, with the reason), spec doc,
   feature docs, daily workflow doc.
3. Definition of Done: tests pass, feature doc updated, `CLAUDE.md` overview updated.
4. `scripts/check_docs.py` + CI that runs tests and the doc check.
5. `.claude/agents/implementer.md`, `.claude/settings.json` deny rules, `.claude/hooks/guard-git-push.sh`,
   `.worktreeinclude` for gitignored files that builds need.
6. Rule: every decision gets a record with its reason, the same day.
