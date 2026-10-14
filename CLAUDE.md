# Saynata

An iOS app for a Vietnamese English learner: look up words, translate sentences, hear them aloud, and
practise saying the meaning with a score. Open source, built mostly with AI help by one developer who is
learning Swift and English at the same time.

| Part | Stack | Job |
|---|---|---|
| `EnglishTranslation/` | SwiftUI, iOS 18 | The app. Talks only to our backend (the direct LLM clients were removed in TASK-18); `Speech/` reads English aloud on device with `AVSpeechSynthesizer`. |
| `backend/` | Spring Boot 4, Java 17, Postgres, Flyway, Claude Haiku 5.5 | `POST /api/translations`, `GET /api/words/{word}`, with a shared cache-first DB. Words not on the bundled SCOWL/NGSL word list are rejected before Claude is called. |
| `pipeline/` | Python 3.12, uv, Claude Sonnet 5.5 | Builds `dictionary.sqlite` (NGSL words, 3 levelled examples per sense, form types via lemminflect) bundled in the app. |

Read before working:
- Goal, requirements, decisions: `backlog/docs/doc-1 - Vision-and-spec.md`
- Why each choice was made, the strategy, and the progress story: `backlog/docs/story/` (doc-7, doc-8, doc-9).
  When the developer makes a decision, add a row to doc-7 the same day with the reason in their words and the
  options that lost. No reason given? Write "Reason: not recorded" and add it to the questions list.
  Add a line to doc-9 at the end of each working day.
- How each flow works (files, lines, diagrams): `backlog/docs/features/`
- Daily workflow: `backlog/docs/doc-2 - Daily-workflow.md`
- UI work (colors, text styles, components, HIG rules): `docs/design/design-system.md`
- What's done and what's next: `backlog task list --plain`, and any In Progress task's notes

Workers: the main chat plans and reviews; `.claude/agents/implementer.md` (Sonnet, own worktree) builds one task and
opens a PR into its feature branch (TASK-31 to 34 are features F1-F4). `.claude/hooks/guard-git-push.sh` blocks
force-push and pushes to main in every permission mode.

Tests: `cd backend && ./mvnw test` · `cd pipeline && uv run pytest` · iOS in Xcode (⌘U).
Deploy the backend to a VPS: `cd backend && docker compose -f docker-compose.prod.yml up -d --build` (guide: `backlog/docs/guides/doc-11 - Deploy-to-a-VPS.md`).

**Simulator: always iPhone 16 Pro (iOS 18.5)** for iOS tests, builds and screenshots, by agents and in Test it cards:
`-destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.5'`. It is the developer's device, so results match what they see.

## Product rule: explain first, translate last

Saynata teaches English. It is not a translator. For every word and sentence, the main result is a **simple
English explanation** of the meaning (short, plain words, B1-B2 level), so the learner thinks in English.
The target language (Vietnamese, etc.) is a small helper line at the **bottom** of the result, never the headline.

- UI order: simple English explanation, then words (IPA, type, level, meaning), then the target-language line last.
- The target-language line is **collapsed by default** (a row titled with the picked language, for example "Vietnamese" or "Korean", that the user taps to open, per result).
  Vietnamese shows the dictionary text; other languages show the simple explanation translated on the device (Apple Translation).
  Settings has a "Show translation" option to change this: Collapsed (default), Always open, or Remember last choice.
- Words in a sentence use the same word card as a word search, in compact form.
- Prompts and response models: ask the LLM for the English explanation first; the target-language text is a
  secondary field. Do not name or design a feature as "translate X".
- When a doc, task, or mockup says "translation first", this rule wins. Update the doc.

## UI rule: icon-only buttons

Buttons have **no text labels** (no "Listen", "Speak", "Copy"). Use an icon only, with an accessibility label
for VoiceOver. This applies to mockups, designs, and SwiftUI code. Add a visible word to a button only when the
developer asks for it. Section rows and headings (for example "Vietnamese", "How to use it") are not buttons
and keep their text.

<!-- BACKLOG.MD GUIDELINES START -->
<!-- backlog.md-instructions-version: 1.53.0 -->
<CRITICAL_INSTRUCTION>

## Backlog.md Workflow

This project uses Backlog.md for task and project management.

**At the beginning of each conversation in this project, run `backlog instructions overview` before answering or taking action. Re-read it only if you have not read it yet in the current conversation.**

Use the overview to decide whether to search, read, create, or update Backlog tasks.

Before task lifecycle actions, read the matching detailed guide:
- `backlog instructions task-creation` before creating or splitting tasks
- `backlog instructions task-execution` before planning, changing status or assignee, adding a plan or implementation notes, or implementing task work
- `backlog instructions task-finalization` before checking acceptance criteria, writing final summaries, or moving tasks to terminal statuses

Use `backlog <command> --help` before running unfamiliar commands. Help shows options, fields, and examples.

Do not edit Backlog task, draft, document, decision, or milestone markdown files directly. Use the `backlog` CLI so metadata, relationships, and history stay consistent.

</CRITICAL_INSTRUCTION>
<!-- BACKLOG.MD GUIDELINES END -->

## Working with the developer

- **Feature status with every "please test".** When you ask the developer to test, start with a short status
  table of all features (F1-F4 and other user-facing tasks): done and merged, ready to test now, in progress,
  not started. Say exactly which ones this build contains, so they do not test something that is not built.
  Give branch-switch commands as `git fetch origin && git switch <branch> && git pull`.
- **Mockup before UI code.** Any change to how a screen looks (layout, new section, separators, labels, order)
  starts with a mockup (an inline visual or a page) and waits for the developer's choice. Only then change
  SwiftUI code. Bug fixes that keep the agreed design (flicker, wrong colour token) do not need a mockup.

## Feature docs

`backlog/docs/features/` has one page per flow (files, `file:line` links, Mermaid diagrams, errors, tests, known gaps).
Read the matching page before changing a flow. When a change moves, renames, or changes the logic of
anything on a page, update that page in the same commit, including line numbers and the "Last traced" date.
Edit pages with `backlog doc update <id> --content` (the files have frontmatter). A new feature gets a new page
from the template in doc-3 (Features overview), created with `backlog doc create "<Feature>" -p features`.

Keep the overview at the top of this file true too: when a change adds a part, changes the stack or a model,
or changes a command, update it in the same commit. Before every commit, run `python3 scripts/check_docs.py`
and fix what it reports (links to missing files or past the end of a file).
