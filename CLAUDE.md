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
- What's done and what's next: `backlog task list --plain`, and any In Progress task's notes

Tests: `cd backend && ./mvnw test` · `cd pipeline && uv run pytest` · iOS in Xcode (⌘U).

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
