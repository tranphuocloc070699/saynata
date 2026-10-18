# Saynata

An iOS app for a Vietnamese English learner: look up words, translate sentences, hear them aloud, and
practise saying the meaning with a score. Open source, built mostly with AI help by one developer who is
learning Swift and English at the same time.

| Part | Stack | Job |
|---|---|---|
| `EnglishTranslation/` | SwiftUI, iOS 18 | The app, plus two extensions that show the same explain sheet for selected text: `ShareExtension` (Share, then Saynata) and `TranslationExtension` (the system Translate item, when Saynata is the default translation app, iOS 18.4+). Both compile the app folder, see doc-10. Talks only to our backend (the direct LLM clients were removed in TASK-18). **Accounts (TASK-70, `Account/`):** sign in with an email code, tokens in the Keychain; on iOS also an emailed link (opens the app with `saynata://sign-in`), Sign in with Apple (paid account only) and Google (no SDK: `webAuthenticationSession` + PKCE, `GoogleIOSClientID` in Info.plist) (TASK-92, doc-17); a sign-in sheet when the backend says `sign_in_required`; the extensions share the app's sign-in through the App Group Keychain (doc-17); `Speech/` reads English aloud on device with `AVSpeechSynthesizer`. **Explain copied text (TASK-87, `Intents/`, doc-21):** an App Intent and App Shortcut for Back Tap and the Action Button that opens the app; the main screen pastes the copied text and explains it, or empties the input and gives it the keyboard (`CopiedTextLaunch`, `TranslationStore.startFromClipboard`). **Quick explain (TASK-90, `QuickExplainIntent`, doc-21):** an App Intent that stays over the current app (a full-screen video too): iOS asks for the text in its own box, and a short snippet card (`QuickExplainCard`, colors as numbers in `Theme.SnippetColors`) shows the meaning. App target only. **Sentence result (TASK-89.2, `Views/Sentence/`, doc-4):** one `SentenceCard` with the original and its simple summary (`explanation.meaning`), the target language folded last; every word is tappable (dotted underline, opens the word sheet) and a selected part opens a popup under it that explains the part (`SentencePartStore`, same stream as a sentence). The popup needs a `UITextView` wrapper, so it is iOS only; the Mac has tappable words and the normal selection menu. |
| `EnglishTranslation/SaynataMac/` | SwiftUI + AppKit, macOS 15 | The Mac app (F6, TASK-85): a menu-bar icon with a popover, no Dock icon; ⌥⇧Space opens a Spotlight-style floating panel (KeyboardShortcuts package) or Services → Explain with Saynata; open at login with `SMAppService`. The `SaynataMac` target compiles the iOS app folder too (like the extensions), so models, network, stores and result views are shared; the few UIKit/AppKit differences live in `Platform/PlatformSupport.swift` and `#if os(...)` blocks. Build and run the `SaynataMac` scheme on "My Mac" (free Apple ID: Sign to Run Locally). |
| `backend/` | Spring Boot 4, Java 17, Postgres, Flyway, Claude Haiku 5.5 (or any Anthropic-compatible gateway: `CLAUDE_BASE_URL`, `CLAUDE_AUTH_TOKEN`, `CLAUDE_MODEL`, doc-11) | `POST /api/translations`, `POST /api/translations/stream` (server-sent events: `meaning-delta` events make the meaning grow word by word, then `meaning`, then `done`; doc-4), `GET /api/words/{word}`, with a shared cache-first DB. Words not on the bundled SCOWL/NGSL word list are rejected before Claude is called. `/api/admin/**` (Spring Security, ADMIN login in an HttpOnly cookie, `users` table) lets the admin web app list, edit, review and delete words and sentences, see doc-13. **Postgres is the source of truth for the bundled dictionary too** (tables `dictionary_word`, `dictionary_sense`, `dictionary_form`, `media`; Flyway V6): the admin lists and edits bundled words next to the words Claude wrote (ids `b-12` / `w-45`, filters `source`, `cefr` and `media`). The bundled entry wins: `GET /api/words/{word}` answers a bundled word from Postgres and never calls Claude or saves a `word_lookup` row for it (Flyway V9 removed the old duplicates). Media files stay on disk (`MEDIA_DIR`, a Docker volume that Caddy serves at `/media/`), only their rows are in Postgres. A new backend word gets a Pixabay photo or clip in the background (not a word saved by the prefill job) (ffmpeg, `PIXABAY_API_KEY`, Flyway V7); `imageUrl` in `GET /api/words/{word}` is its address (doc-5, doc-11). **Accounts and limits (doc-17):** app users sign in with an email code (`/api/auth/*`, `/api/me`, mail through Resend (AWS SES as an option), `users` + `email_login_code` + `refresh_token`, Bearer access token; Flyway V10), an emailed link (`/api/auth/email-link`, page `GET /auth/link`, `PUBLIC_BASE_URL`), or a Google / Apple ID token checked against the provider's JWKS (`/api/auth/google`, `/apple`, `GOOGLE_CLIENT_IDS`, `APPLE_CLIENT_IDS`; `user_identity`, `email_login_link`, Flyway V22; TASK-92). No domain is in the code; doc-11 section 13 lists what changes when the domain moves. A guest gets only cached and bundled answers; a new Claude call needs a signed-in user (401 `sign_in_required`; `REQUIRE_SIGN_IN=false` lets guests make new calls until the app with sign-in is released, still capped by the budget and a per-IP limit), is rate limited (429 `rate_limited`) and stops when today's spend (UTC day, from real token usage saved in `claude_usage`, Flyway V11) reaches `app.claude.daily-budget-usd` (default $1; 503 `daily_limit_reached`). Every Claude call goes through `limits/ClaudeGate`. The iOS sign-in screen is built (TASK-70). **Prefill (TASK-83.1, `prefill/`, doc-18):** a queue (`prefill_item`, Flyway V17) of words and phrases that a night job (`PREFILL_CRON`, UTC, empty = off) and `POST /api/admin/prefill/run?maxUsd=&limit=` answer through the same `WordService` / `TranslationService` as users, so the shared cache is full before anyone asks. It has no user: `ClaudeGate` checks the run cap (`app.prefill.max-usd-per-run`, default $0.50) instead of sign-in and the users' budget, and its calls are saved as feature `PREFILL`, which the users' $1 budget leaves out. `GET /api/admin/prefill` shows the queue and the spend. **While typing (TASK-83.4, doc-4, doc-5):** `?cachedOnly=true` on `GET /api/words/{word}` and both sentence endpoints answers only from the bundle or the cache, else 404 `not_cached`, and never reaches `ClaudeGate`; word answers send `Cache-Control` (bundled 1 day, cached 1 hour); `GET /api/suggest?q=<prefix>&lang=` (`suggest/`) gives up to 8 bundled words, cached words and cached phrases that start with the prefix (prefix indexes, Flyway V19). **Phrases (TASK-83.10, doc-5):** `GET /api/words/{phrase}` (2-6 words, "give up") is answered only from `word_lookup` (404 `not_cached` on a miss, never Claude); the app (`CacheKeys.phraseQuery`) asks it with `cachedOnly=true` before the sentence path and shows a hit as a word card. **Admin AI picture (TASK-91, `aipicture/`, doc-15):** `GET/POST /api/admin/words/{id}/senses/{n}/media/ai-prompt[/scene]` and `.../ai-picture` give an editable image prompt (newest `media-ai` scene, else a template; the wand asks Claude Sonnet 5.5, `ADMIN` usage, $1 a day cap) and run a background job (one worker, `ai_picture_job`, Flyway V21) that draws it with the **Codex CLI** inside the backend image, signed in with the developer's ChatGPT `auth.json` (`AI_PICTURES_ENABLED`, default off; clean environment; 30 pictures a day; doc-11 section 12). The picture replaces the sense's picture like an upload and goes to "Media to review". |
| `pipeline/` | Python 3.12, uv, Claude Sonnet 5.5, OpenAI GPT Image mini | Generates the NGSL words (3 levelled examples per sense, form types via lemminflect) into `data/pipeline.db`. `topics` (TASK-61) finds everyday concrete words NGSL misses (apple, giraffe) by topic from Open English WordNet + wordfreq, and a Claude Haiku 5.5 judge keeps or drops them (`topic_words` table). `import-to-postgres` copies the bundled `dictionary.sqlite` into the server Postgres once; `export` (needs `DATABASE_URL` in `pipeline/.env`, read-only) builds the `dictionary.sqlite` and `media/` folder bundled in the app from Postgres, see doc-6. `media-check` (TASK-78) has Claude Haiku mark wrong sense pictures `needs_ai`; `media-generate` (TASK-53, `OPENAI_API_KEY`) makes an AI picture for each (`gpt-image-1-mini`, $5 cap per run, `media_generation` table, Flyway V14), then `--link` after rsync, see doc-15. `media-ai` (TASK-82) is the way to give **every** sense an AI picture: Claude Sonnet 5.5 writes a scene, the local **Codex CLI** (`codex exec`, the developer's ChatGPT plan, about 1 minute a picture, no per-picture cost) draws it, Claude Haiku 5.5 checks it with a blind guess (open guess, then multiple choice, up to 3 tries; `--max-cost` is the Claude cap, default $5; Flyway V16 adds scene and guess columns to `media_generation`); `--link` after rsync replaces any picture and keeps the old media row, see doc-15. `prefill collect --source words|phrases` (TASK-83.2, no paid call) queues the common words not bundled (SCOWL-70 minus the bundled dictionary and kept topic words, lemmas, wordfreq Zipf >= 3.5) and WordNet phrasal verbs and idioms into the backend `prefill_item` table for the night job; `prefill report` counts the queue and prices the pending rows, see doc-6. **Local collection (TASK-83.9, `collection.py`, doc-18):** `collection collect` fills a `collection` table in `pipeline.db` (no paid call) with common words not bundled, the pending topic and needs_review words, WordNet phrasal verbs and idioms, and the collocations of the bundled usage items; `collection write` has the local **Codex CLI** (ChatGPT plan, no per-call cost) write each one with the backend word prompt (read from `ClaudeService.java`) and the backend checks; `collection batch submit/collect` does the same with Claude Haiku 5.5 through the Message Batches API (half price, `collection_batch.py`); `collection compare` makes a Codex-next-to-Claude HTML page; `collection push --yes` copies done words into the server cache `word_lookup` (never over a Claude row). |
| `admin/` | React, Vite, TypeScript, Refine, shadcn/ui (Tailwind) | The admin CMS at `admin.loffy.me` (F5): sign in, then review, edit, mark reviewed and regenerate words and sentences through `/api/admin`, and draw an AI picture for a sense from a dialog (TASK-91). Builds to static files (`npm run build`); tests: `cd admin && npm test`. |
| `extension/` | Chromium Manifest V3, TypeScript, Vite, Vitest | The browser extension (Brave, Chrome; TASK-86, doc-19): select English on any page, a small teal "S" icon appears, and a card shows the simple English first, words next, the target language last and closed. Also a right-click item and Alt+Shift+S. One word calls `GET /api/words/{word}`, more calls `POST /api/translations/stream`. Every call goes through the service worker (`host_permissions`, so no backend CORS change). Build: `cd extension && npm run build`, then load `extension/dist` unpacked. Tests: `npm test`. |

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

Tests: `cd backend && ./mvnw test` · `cd pipeline && uv run pytest` (the Postgres tests need `TEST_DATABASE_URL`, else they are skipped) · `cd admin && npm test` · `cd extension && npm test` · iOS in Xcode (⌘U).
Install on the developer's real iPhone (build, then install or replace; open it by hand): `scripts/install-on-phone.sh` (free Apple ID: signs without App Groups and the default-translation-app capability; `PAID_ACCOUNT=1` for the real entitlements).
Deploy the backend to a VPS: `cd backend && ./scripts/deploy.sh` on the server, or automatically: `.github/workflows/deploy.yml` runs it over SSH after CI passes on main, then builds the admin web app and copies `admin/dist` to the server (guide and secrets: `backlog/docs/guides/doc-11 - Deploy-to-a-VPS.md`).

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

## Conventions

Follow the code that is already there; these are the rules it uses (TASK-12).

- **iOS** (`EnglishTranslation/`): SwiftUI views in `Views/<Flow>/` (Translate, Word, Sentence, SelectedText,
  Settings, Shared); state in `@Observable` classes (`Stores/`, `Speech/`) injected with `.environment`, so every
  `#Preview` that needs one adds it (for example `.environment(SpeechPlayer.silent())`). Colors, spacing, radius and
  text styles only from `Theme` and `DesignSystem/Components` (`IconButton`, `cardSurface()`…), per
  `docs/design/design-system.md`. System APIs sit behind a small protocol so tests use a fake. Tests use Swift
  Testing (`import Testing`, `@Test`, `#expect`) in `EnglishTranslationTests/`.
  Test: ⌘U, or `xcodebuild test -project EnglishTranslation/EnglishTranslation.xcodeproj -scheme EnglishTranslation -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.5' -only-testing:EnglishTranslationTests -parallel-testing-enabled NO`.
- **Backend** (`backend/`): one package per feature (`translation/`, `word/`, `claude/`, `cache/`, `auth/`, `limits/`, `prefill/`, `suggest/`, `common/`),
  each with its controller, service, repository and records. Errors are `ProblemDetail` responses from
  `common/ApiExceptionHandler` (an `ApiException` has a `code` the app can read, like `sign_in_required`). Any new code that calls Claude goes through `limits/ClaudeGate` first. Schema changes only as a new Flyway file `V<n>__<what>.sql`; never edit an old one.
  A prompt change bumps its version in `application.properties` (`app.claude.*-prompt-version`). Secrets come from env
  or `backend/.env` (git-ignored); new keys go in `.env.example` and doc-11. Tests are JUnit 5 + `@SpringBootTest`
  with a fake Claude key; they never call the real API. Test: `cd backend && ./mvnw -B test`.
- **Pipeline** (`pipeline/`): one module per step in `src/pipeline/` (`senses`, `examples`, `usage`, `forms`, `media`, `media_check`, `media_generate`,
  `validate`, `export`), run through `cli.py`; progress is saved in `pipeline.db`, so every step can resume.
  Agents never start paid passes (LLM, large media runs); they write the code and say the command and its cost.
  Test: `cd pipeline && uv run pytest`.
- **Everywhere**: branches `feature/f<n>-<name>` for features and `f<n>/task-<id>` for their tasks, `fix/<what>` for
  fixes; merge, never rebase or force-push; every PR has a "Test it" card; only the main chat marks a task Done,
  after the developer has tested it. Commit messages start with the task ID.
- **Test branch `feature/local`** (developer's rule, 2026-10-16): finished work is merged into `feature/local` first,
  not into `main`. The developer tests from `feature/local`; only after they say the test succeeded does that work go
  to `main`. New PRs target `feature/local` (or their feature branch, which is then merged into `feature/local`).

## Feature docs

`backlog/docs/features/` has one page per flow (files, `file:line` links, Mermaid diagrams, errors, tests, known gaps).
Read the matching page before changing a flow. When a change moves, renames, or changes the logic of
anything on a page, update that page in the same commit, including line numbers and the "Last traced" date.
Edit pages with `backlog doc update <id> --content` (the files have frontmatter). A new feature gets a new page
from the template in doc-3 (Features overview), created with `backlog doc create "<Feature>" -p features`.

Keep the overview at the top of this file true too: when a change adds a part, changes the stack or a model,
or changes a command, update it in the same commit. Before every commit, run `python3 scripts/check_docs.py`
and fix what it reports (links to missing files or past the end of a file).
