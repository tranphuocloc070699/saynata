---
id: doc-1
title: Vision and spec
type: specification
created_date: '2026-10-10 04:30'
updated_date: '2026-10-10 04:30'
---
# Spec: EnglishTranslation — translator for a Vietnamese English learner

Status: draft · 2026-10-09

## Goal

An iOS app that replaces DeepL for daily use. It looks up words and translates sentences
(English → Vietnamese by default, or another language the user picks), reads them aloud, and lets the user practise saying the meaning.

## Glossary

- **Word** — input that is one token after normalising (`run`, `ran`, `reluctant`).
- **Sentence** — any input with more than one token.
- **Entry** — the dictionary data for one word: senses with a simple English definition,
  a Vietnamese meaning, examples, collocations and synonyms (shape: `pipeline/src/pipeline/models.py`).
- **Cache key** — `sha256("sentence" | promptVersion | targetLang | normalisedText)` for a sentence,
  `sha256("word" | promptVersion | normalisedWord)` for a word.
- **Target language** — the language a sentence is translated into, picked by the user in the app
  (`vi`, `en`, `ja`, `ko`, `zh`, `th`, `fr`, `es`, `de`). Word entries stay English + Vietnamese.
- **Normalise** — trim and collapse inner whitespace to one space. Words are also lowercased;
  sentences keep their case ("US" ≠ "us"). Done the same way on the phone and the server.

## Requirements

### R1 — Words use the dictionary, sentences use the LLM
- A word is looked up in `dictionary.sqlite`, which is bundled in the app (built by `pipeline/`,
  ~2,800 NGSL words + their forms).
- A word missing from the bundled dictionary goes to the backend. The backend asks the LLM for an
  Entry in the same JSON shape, saves it, and returns it.
- A sentence always goes to the backend, which asks the LLM.

### R2 — Cache first, on two levels
Lookup order:

1. Bundled dictionary (words only)
2. Local cache on the phone (SQLite)
3. Backend cache (Postgres), shared by every user
4. LLM, then save in the backend DB and the local DB

When another user asks for the same cache key, the backend returns the saved result and does
not call the LLM.

### R3 — Same input, same output
- The cache is the guarantee: once a key is saved, it is never generated again.
- The LLM request uses the lowest-randomness settings the model allows (see "Decisions").
- Changing the prompt means bumping `promptVersion`, which creates new keys instead of serving
  answers from the old prompt.

### R4 — Text to speech
- Read the word, the English definition, and the Vietnamese meaning aloud.
- Use the on-device `AVSpeechSynthesizer` first (free, offline). A cloud voice is out of scope for v1.

### R5 — Speech to text with a score
- The user says the meaning of a word or sentence. The app transcribes it on the device
  (`SFSpeechRecognizer`, `requiresOnDeviceRecognition = true`).
- Score = correct words / expected words, using a word-level edit distance (Levenshtein on
  normalised tokens). Show which words were missing or wrong.
- Runs fully on the phone. No backend call.

### R6 — Java backend
- Spring Boot 4, Java 17, Postgres, Flyway, Claude API (env `CLAUDE_API_KEY`).
- Stateless. No user accounts in v1.

## Backend API (v1)

| Method | Path | Body / param | Returns |
|---|---|---|---|
| `POST` | `/api/translations` | `{ "text": "...", "targetLang": "vi" }` | `{ text, targetLang, explanation, cached }` |
| `GET`  | `/api/words/{word}` | path: the word | `{ word, entry, cached }` |

`explanation` = `{ meaning, words: [{ word, partOfSpeech, meaning }], translation }`. Only
`translation` is in `targetLang`; `meaning` and `words` are simple English (difficult = above B1).

`entry` = `{ senses: [{ pos, cefr, simpleDefinition, vietnamese, examples, collocations, synonyms }] }`,
the pipeline shape in camelCase, without the image fields.

Rules:
- `text`: 1–500 characters. `word`: 1–64 characters, one token.
- `targetLang`: required, one of the codes above. Unknown or missing → 400.
- Each language is cached on its own: the same sentence in `vi` and `ja` is two rows and two LLM calls.
- `cached: true` when the result came from the DB.
- Two users asking for the same new key at the same moment: one LLM call may be wasted, but only
  one row is saved (unique key). The second request reads the saved row.

## Decisions

- **Model: Claude Haiku 5.5** (`app.claude.model`). It has no server-side refusal fallback, so a
  refusal returns HTTP 422.
- **Temperature 0 is not possible on current Claude models** (the API rejects sampling parameters with a
  400). We keep the model's default sampling, use `effort: low`, and rely on R2/R3's cache for
  repeatable output. The model ID is config (`app.claude.model`), so it can be switched to a model
  that accepts `temperature` if this proves to be a problem.
- **Sentences are shared between users.** A sentence can contain personal text. Accepted for v1
  (single user). Revisit before public release.
- **No backend import of the dictionary.** The phone already has it, so the backend only stores
  words the dictionary lacks.

## Out of scope (v1)

User accounts, rate limiting per user, cloud TTS, images for senses, word entries in languages other
than Vietnamese, adapting output to the user's English level.

## Build order

1. Backend: `/api/translations` with cache (this ticket).
2. Backend: `/api/words/{word}` with cache.
3. iOS: lookup flow (bundled dictionary → local cache → backend).
4. iOS: text to speech.
5. iOS: speech to text + score.
