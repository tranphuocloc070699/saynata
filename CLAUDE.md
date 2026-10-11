# Saynata

An iOS app for a Vietnamese English learner: look up words, translate sentences, hear them aloud, and
practise saying the meaning with a score. Open source, built mostly with AI help by one developer who is
learning Swift and English at the same time.

| Part | Stack | Job |
|---|---|---|
| `EnglishTranslation/` | SwiftUI, iOS 18 | The app. Talks only to our backend (direct LLM clients are being removed, TASK-18). |
| `backend/` | Spring Boot 4, Java 17, Postgres, Flyway, Claude Haiku 5.5 | `POST /api/translations`, `GET /api/words/{word}`, with a shared cache-first DB. |
| `pipeline/` | Python 3.12, uv, Claude Sonnet 5.5 | Builds `dictionary.sqlite` (NGSL words) bundled in the app. |

Read before working:
- Goal, requirements, decisions: `backlog/docs/doc-1 - Vision-and-spec.md`
- How each flow works (files, lines, diagrams): `backlog/docs/features/`
- Daily workflow: `backlog/docs/doc-2 - Daily-workflow.md`
- UI work (colors, text styles, components, HIG rules): `docs/design/design-system.md`
- What's done and what's next: `backlog task list --plain`, and any In Progress task's notes

Workers: the main chat plans and reviews; `.claude/agents/implementer.md` (Sonnet, own worktree) builds one task and
opens a PR into its feature branch (TASK-31 to 34 are features F1-F4). `.claude/hooks/guard-git-push.sh` blocks
force-push and pushes to main in every permission mode.

Tests: `cd backend && ./mvnw test` · `cd pipeline && uv run pytest` · iOS in Xcode (⌘U).

## Product rule: explain first, translate last

Saynata teaches English. It is not a translator. For every word and sentence, the main result is a **simple
English explanation** of the meaning (short, plain words, B1-B2 level), so the learner thinks in English.
The target language (Vietnamese, etc.) is a small helper line at the **bottom** of the result, never the headline.

- UI order: simple English explanation, then words (IPA, type, level, meaning), then the target-language line last.
- The target-language line is **collapsed by default** (a "Vietnamese" row the user taps to open, per result).
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

## Feature docs

`backlog/docs/features/` has one page per flow (files, `file:line` links, Mermaid diagrams, errors, tests, known gaps).
Read the matching page before changing a flow. When a change moves, renames, or changes the logic of
anything on a page, update that page in the same commit, including line numbers and the "Last traced" date.
Edit pages with `backlog doc update <id> --content` (the files have frontmatter). A new feature gets a new page
from the template in doc-3 (Features overview), created with `backlog doc create "<Feature>" -p features`.

Keep the overview at the top of this file true too: when a change adds a part, changes the stack or a model,
or changes a command, update it in the same commit. Before every commit, run `python3 scripts/check_docs.py`
and fix what it reports (links to missing files or past the end of a file).
