---
id: TASK-106
title: Language lines from the LLM for every language (words and sentences)
status: To Do
assignee: []
created_date: '2026-10-24 09:51'
labels: []
dependencies: []
priority: high
ordinal: 112000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The developer wants the target-language line (Vietnamese, Japanese, Korean, Chinese, French, Spanish, German) on the web app, iPhone and Mac apps and the extension to come from the LLM through our backend, not from the dictionary (sense.vietnamese) or Apple on-device Translation. Today word cards show the dictionary Vietnamese, iOS/Mac translate other languages on the device, and web/extension show Vietnamese only. Sentences pay the whole explanation again per language because the sentence cache key holds the language.

Decisions (2026-10-24, developer): split every answer into the English part (paid once, no language in the key) and a small language line per language (paid once per language, shared by all users). Fill all lines for all words and sentences locally with the Codex CLI (ChatGPT plan, no per-call cost). Remove Thai. Keep English as the "no translation row" option. Keep the dictionary Vietnamese as an instant fallback; iOS/Mac keep Apple Translation as a fallback; Quick explain and the extension use the lines too.

API contract (all subtasks build against this, so they can run in parallel):
POST /api/translate-lines   body {"targetLang":"ko","texts":["a place that keeps your money safe", "..."]}  (1-20 texts, each 1-500 chars; targetLang "en" -> 400)
optional ?cachedOnly=true -> never reaches ClaudeGate; misses come back with translation null.
200 {"targetLang":"ko","lines":[{"text":"...","translation":"..." or null,"cached":true}]}  same order as texts.
Without cachedOnly a miss goes through limits/ClaudeGate (guest -> 401 sign_in_required unless REQUIRE_SIGN_IN=false; 429 rate_limited; 503 daily_limit_reached); all misses of one request are one LLM call.
Table line_translation: text_hash (sha256 hex of the normalized text: trimmed, inner whitespace collapsed), text, target_lang, prompt_version, translation, source (llm | codex | seed), created_at; unique (text_hash, target_lang, prompt_version). Prompt version app.claude.line-prompt-version=v1. Usage saved as a new Feature LINE (counts toward the users $1 budget).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Tests pass
- [ ] #2 Feature doc (backlog/docs/features/) updated if a flow changed
- [ ] #3 CLAUDE.md overview updated if the stack, folders, or commands changed
<!-- DOD:END -->
