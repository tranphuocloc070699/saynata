---
id: doc-5
title: Word lookup
type: specification
created_date: '2026-10-10 06:23'
updated_date: '2026-10-15 10:02'
---
> Status: **working end to end** (bundle, local cache, backend, word card). iOS lookup: TASK-4, TASK-5, TASK-6; single-word routing and card: TASK-19; "an apple" is the word apple: TASK-44. Last traced from code: 2026-10-15 (examples row layout: TASK-48; stop speech and duck music: TASK-46; media of bundled words loads from the server: TASK-51; a bundled word is answered from Postgres first, the bundled entry wins: TASK-58; pictures for backend words: TASK-49.4; Listen: TASK-7; article before a word: TASK-44; Show translation setting: TASK-29; simple-word check on backend entries: TASK-16; target-language row: TASK-40; IPA from a word list: TASK-39; not-a-word check before Claude: TASK-38; did you mean: TASK-37; levelled examples and form types: TASK-35).
> Spec: R1, R2 in [Vision and spec](../doc-1%20-%20Vision-and-spec.md).

## What it does

A single word gets a **learner dictionary entry**: 1–4 senses, each with part of speech, CEFR level,
a simple English definition, a Vietnamese meaning, 3 **examples** ordered easy, medium, harder (`{ level, text }`), and up to 3 **usage** items
(`{ kind, pattern, example }`, see the glossary in doc-1). It has no collocations or synonyms any more (decision-2, TASK-24).
Most words come from the dictionary bundled in the app (built by the [dictionary pipeline](doc-6%20-%20Dictionary-pipeline.md)).
Only words the bundle doesn't have go to the backend, which asks Claude once and caches the answer for everyone.

## Full flow (spec R2)

```mermaid
flowchart TD
    A["User enters text"] --> T{"CacheKeys.wordQuery gives a word? (one token, or a/an/the + one word)"}
    T -- "no" --> SENT["Sentence path (doc-4)"]
    T -- yes --> N["Normalise: drop the article, trim, collapse spaces, lowercase"]
    N --> B{"In bundled dictionary.sqlite? (lemma or form, e.g. ran → run)"}
    B -- yes --> SHOW[Show entry]
    B -- no --> L{"In local SQLite cache?"}
    L -- yes --> SHOW
    L -- no --> API["GET /api/words/{word}"]
    API --> BW{"In Postgres dictionary_word? (TASK-58)"}
    BW -- yes --> BUNDLED["Answer with the bundled entry + media; no Claude, nothing saved"]
    BW -- no --> WL{"On the English word list? (TASK-38)"}
    WL -- no --> EMPTY
    WL -- yes --> PG{"In Postgres word_lookup?"}
    PG -- yes --> SAVE[Save in local cache]
    PG -- no --> CL[Claude describeWord] --> PGS[Save in Postgres] --> SAVE
    SAVE --> EMPTY{"Any senses?"}
    EMPTY -- yes --> SHOW
    EMPTY -- no --> NOTWORD["Message: not an English word"]
```

## Backend: step by step (works today)

```mermaid
sequenceDiagram
    autonumber
    participant App as Client (curl today, iOS later)
    participant WC as WordController
    participant WS as WordService
    participant DB as Postgres word_lookup
    participant CS as ClaudeService
    participant CL as Claude API
    participant MS as BackendWordMediaService

    App->>WC: GET /api/words/Reluctant
    WC->>WC: @Pattern letters, ' and - only, 1 to 64 chars, else 400
    WC->>WS: lookUp("Reluctant")
    WS->>WS: normalizeWord gives "reluctant"
    WS->>DB: bundled? dictionary_word with lemma "reluctant" (TASK-58); if yes: its entry + media, cached: true, stop
    WS->>WS: word list check (SCOWL + NGSL); not on it: empty senses, no Claude, no save
    WS->>WS: key = sha256 of word, v7, reluctant
    WS->>DB: findByCacheKey(key)
    alt hit
        DB-->>WS: entry_json
        WS->>DB: media of the saved word (word_lookup_media + media)
        WS-->>WC: {word, entry (+ imageUrl per sense), cached: true}
    else miss
        WS->>CS: describeWord("reluctant")
        CS->>CL: WORD_PROMPT, effort LOW, schema = WordEntry
        CL-->>CS: WordEntry JSON (or refusal)
        WS->>DB: saveAndFlush (unique cache_key)
        opt race: another request saved first
            WS->>DB: read the first row back
        end
        WS-)MS: fetchInBackground (own thread, not waited for)
        WS-->>WC: {word, entry, cached: false}
    end
    WC-->>App: 200 JSON
```

| Step | File | What happens |
|---|---|---|
| Endpoint | [WordController.java:20-23](../../../backend/src/main/java/com/englishtranslation/backend/word/WordController.java#L20-L23) | `GET /api/words/{word}`. Only letters, `'` and `-`, 1–64 chars ("don't", "well-known"). A space → 400. |
| Normalise | [CacheKeys.java:24-26](../../../backend/src/main/java/com/englishtranslation/backend/cache/CacheKeys.java#L24-L26) | Sentence rules + `toLowerCase(Locale.ROOT)`. "Run" and "run" share one row. |
| Bundled first | [WordService.java:64-69](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L64-L69), [bundledResponse:115](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L115), [BundledEntryReader](../../../backend/src/main/java/com/englishtranslation/backend/dictionary/BundledEntryReader.java), [findWithSensesByLemma](../../../backend/src/main/java/com/englishtranslation/backend/dictionary/DictionaryWordRepository.java) | **The bundled entry wins (TASK-58).** Before anything else, the normalised word is looked up in `dictionary_word` by **lemma** (the 2,750 bundled words, imported into Postgres by TASK-49.3). If it is there, the answer is that entry (senses with examples and usage, `imageUrl` from `dictionary_sense.media_id`, the bundled IPA or the word-list IPA), `cached: true`. Claude is **not** called and **no `word_lookup` row is made**, so a word can never be in both lists in the admin. The response has the same shape as before. A form such as `working` (a form of `work`) is not a lemma, so it still goes on to the steps below. The iOS app looks bundled words up on the device first, so this mostly protects the admin and other clients. The old duplicates were removed by Flyway [V9](../../../backend/src/main/resources/db/migration/V9__remove_backend_words_that_are_bundled.sql), which deletes `word_lookup` rows (and their `word_lookup_media` links) whose word is a bundled lemma; the media files stay on disk. |
| Word list | [WordService.java:71-76](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L71-L76), [EnglishWords.java](../../../backend/src/main/java/com/englishtranslation/backend/word/EnglishWords.java) | After normalising, the word must be on the list loaded at startup into a `HashSet`: `words/scowl-70.txt` (SCOWL size 70, US/GB/CA/AU, license in `words/SCOWL-LICENSE.txt`) plus `words/ngsl.txt` (lemmas and forms of the bundled dictionary). Not on it (`runk`): **200 with empty `senses`**, Claude is not called, nothing is saved in Postgres (so a list update can fix a missing word), debug log only. Why: Haiku "corrected" `runk` into a `drunk` entry (TASK-38). |
| Cache key | [WordService.java:78](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L78) | `sha256("word" \| wordPromptVersion \| normalizedWord)`. No language: entries are always English + Vietnamese. |
| Hit | [WordService.java:80-83](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L80-L83) | Parse `entry_json` into `WordEntry`, `cached: true`. |
| Miss | [WordService.java:87-106](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L87-L106) | Claude → simple-word check (next row) → save the whole entry as JSON text → `cached: false`. |
| Race | [WordService.java:107-111](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L107-L111) | Same pattern as sentences: the unique key wins, the second request reads the first row. |
| Prompt | [ClaudeService.java:45-115](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeService.java#L45-L115) | `WORD_PROMPT`: 1–4 senses, ≤20-word definition, never the word itself, exactly 3 examples with a level (easy = short present simple, medium = another situation and structure, harder = a complex structure or B2 collocation; same rules as the pipeline's `EXAMPLE_RULES`, with one good set), 0–3 `usage` items with the rules and the agreed few-shot examples (get, make, take, depend). Never correct spelling, never describe a different word; not an English word exactly as written → empty `senses` (TASK-38). |
| Request | [ClaudeService.java:157-171](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeService.java#L157-L171) | `maxTokens 8000`, `effort LOW`, structured output with `WordEntry.class`. The word goes inside `<word>` tags, escaped by `wordMessage` ([174-176](../../../backend/src/main/java/com/englishtranslation/backend/claude/ClaudeService.java#L180-L182)). |
| Shape | [WordEntry.java:7-41](../../../backend/src/main/java/com/englishtranslation/backend/word/WordEntry.java#L7-L41) | Same as the pipeline's `models.py`, in camelCase, **without** `source_index`, `image_type`, `image_query`. `examples` is a list of `{ level, text }` (`level` is a lowercase String, like `kind`). `usage` is a list of `{ kind, pattern, example }`; `kind` is a plain lowercase String (an enum would be written in capitals in the schema). |
| Simple words | [SimpleWords.java](../../../backend/src/main/java/com/englishtranslation/backend/word/SimpleWords.java), [WordService.java:87-98](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L87-L98), [retry 123-134](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L128-L140) | Java copy of the pipeline's `validate.py` rules (TASK-16). The word list is `words/ngsl.txt` plus `words/simple-extra.txt` (the pipeline's `EXTRA_ALLOWED`, `BASIC_WORDS`, `NUMBERS_AND_DATES`, `GRAMMAR_TERMS`, copied; keep them in step). Checked: each `simpleDefinition` (no word outside the list; the possessive `'s` is dropped) and each example (easy 0 outside words, medium 1, harder 2; contractions count as their base word; `me`, `my`, `oh` are allowed; the headword and words starting with it are allowed, because a backend headword may be rare). Usage items and the 20-word limit are **not** checked. If anything fails: **one** retry through `ClaudeService.describeWordAgain(word, feedback)`, where the feedback names the hard words. If the retry also fails, the entry is still saved with `needs_review = true` and a `warn` log line. If the retry call itself throws, the first entry is kept and flagged. Never more than one retry. The flag is not sent to the app. |
| Cache version | [application.properties:18](../../../backend/src/main/resources/application.properties#L18) | `app.claude.word-prompt-version=v7` (TASK-38; v6 was TASK-35). v7 makes the wrong `runk` rows of v6 unreachable on the server and the phone. Rows saved with v3 to v5 have no example levels (or no usage); the new key never reads them. The app has the same value in `PromptVersions.word` ([CacheKeys.swift:15](../../../EnglishTranslation/EnglishTranslation/Cache/CacheKeys.swift#L15)); a test checks they match. |
| Response | [WordResponse.java:9](../../../backend/src/main/java/com/englishtranslation/backend/word/WordResponse.java#L9), [WordEntryView.java](../../../backend/src/main/java/com/englishtranslation/backend/word/WordEntryView.java) | `{ word, entry: { senses: [...] }, cached, ipa }`. `ipa` is nullable. Each sense may have `imageUrl` (TASK-49.4): an absolute address such as `https://api.loffy.me/media/run_1_3fa9c2d1.webp`. The field is **left out** when the sense has no media, and every other field is unchanged, so old app versions see nothing new. `WordEntry` itself (what Claude writes and `entry_json` stores) has no image field; the response uses `WordEntryView`. |
| Media | [WordService.mediaOf:148](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L148), [SenseMediaReader](../../../backend/src/main/java/com/englishtranslation/backend/media/SenseMediaReader.java) | A cached word reads its media rows (`word_lookup_media` joined to `media`) and adds `imageUrl` = `MEDIA_BASE_URL` + `/` + file name. A problem reading media is logged and ignored: the word still answers. |
| IPA | [IpaDictionary.java](../../../backend/src/main/java/com/englishtranslation/backend/word/IpaDictionary.java), [WordService.java:142-145](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L142-L145) | `words/ipa-dict-en_US.txt` (open-dict-data/ipa-dict, MIT, license in `words/IPA-DICT-LICENSE.txt`) is loaded at startup into a map: lowercase word to its **first** IPA, with slashes (for example `/ˈɡɹeɪvˌjɑɹd/`). `WordService.response()` adds it on every answer, at request time, so rows cached before TASK-39 get it too. It is not saved in `entry_json`, so the word prompt and its version did not change. A word missing from the list (or a not-a-word answer) gets `ipa: null`. The list uses `ɹ` and `ɫ` (American, dictionary style). |

### Pictures for backend words (TASK-49.4)

Words that are not in the bundled dictionary get a Pixabay photo or clip **after** the answer was sent. The first lookup has no `imageUrl`; the next one has it.

```mermaid
sequenceDiagram
    autonumber
    participant WS as WordService
    participant MS as BackendWordMediaService (thread media-1)
    participant DB as Postgres
    participant PX as Pixabay
    participant FF as ffmpeg
    participant FS as MEDIA_DIR

    WS->>DB: save the new word_lookup row
    WS-)MS: fetchInBackground (returns at once)
    WS-->>WS: answer the request without imageUrl
    loop first 3 senses that are noun, adjective or verb
        MS->>DB: claim: insert word_lookup_media (word_lookup_id, sense_position)
        alt already claimed
            MS-->>MS: skip, Pixabay is not asked again
        else new
            MS->>PX: verb: search clip (photo if none); noun/adjective: search photo
            PX-->>MS: hit + download
            MS->>FF: 320 px WebP, or 3 s muted H.264 MP4
            FF->>FS: write .tmp file, then move to word_sense_hash8.ext
            MS->>DB: insert media row (Pixabay id, photographer, page, license) and set media_id
        end
    end
```

| Step | File | What happens |
|---|---|---|
| Start | [WordService.startMediaJob:158](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L158) | Called after `saveAndFlush`. If starting it fails, it is logged and the answer is still sent. |
| Thread | [MediaConfig.mediaExecutor](../../../backend/src/main/java/com/englishtranslation/backend/media/MediaConfig.java#L21) | One worker thread, queue of 50. When the queue is full the job is dropped (the word works, it just has no picture). |
| Job | [BackendWordMediaService.fetch:55](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L55) | Does nothing when `PIXABAY_API_KEY` is empty. Nouns and adjectives get a photo, verbs get a clip (a photo if Pixabay has no clip), other parts of speech get nothing. The first 3 senses at most. Search words = the headword. |
| Claim | [claim:88](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L88) | A row in `word_lookup_media` is inserted **before** Pixabay is asked (unique on word and sense position). So a sense is asked once, even if Pixabay found nothing or the download failed. To try again, delete the word in the admin ("Regenerate"). |
| Pixabay | [HttpPixabayClient](../../../backend/src/main/java/com/englishtranslation/backend/media/HttpPixabayClient.java) | Same searches as `pipeline/src/pipeline/media.py` (`safesearch`, `order=popular`, tiny/small video first). At least 1.5 s between requests (40 per minute; Pixabay allows 100 per 60 s). Downloads only from `*.pixabay.com` over https. |
| Convert | [FfmpegMediaConverter](../../../backend/src/main/java/com/englishtranslation/backend/media/FfmpegMediaConverter.java) | One ffmpeg call: photo to WebP (quality 80, max 320 px wide), clip to H.264 MP4 (3 s, no audio, 320 px, `+faststart`). ffmpeg is installed in the Docker image. |
| Save | [fetchOneSense:100](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L100), [MediaStore.save:44](../../../backend/src/main/java/com/englishtranslation/backend/media/MediaStore.java#L44) | File name `word_<sense number>_<8 hex of sha256>.webp\|mp4` (the hash makes the name unique per content, so Caddy can cache it for a year). Written to a `.tmp-` file and moved. If the media row cannot be saved, the file is deleted. Download, convert and save live in `MediaStore`, which the admin also uses when it picks another picture (TASK-52, doc-13). A failed sense is logged and the next sense still runs. |

Flyway [V7](../../../backend/src/main/resources/db/migration/V7__create_word_lookup_media.sql) adds `word_lookup_media (word_lookup_id, sense_position, media_id)`; deleting a word deletes its rows. Bundled words need no table: `dictionary_sense.media_id` already points to the `media` row. Env: `MEDIA_DIR`, `MEDIA_BASE_URL`, `PIXABAY_API_KEY` (doc-11, section 9).

**Table `word_lookup`** ([V1](../../../backend/src/main/resources/db/migration/V1__create_cache_tables.sql#L11-L19), entity [WordLookup.java](../../../backend/src/main/java/com/englishtranslation/backend/word/WordLookup.java)):
`cache_key VARCHAR(64) UNIQUE`, `word VARCHAR(64)`, `entry_json TEXT`, `model`, `prompt_version`, `created_at`, and `needs_review BOOLEAN NOT NULL DEFAULT FALSE` ([V4](../../../backend/src/main/resources/db/migration/V4__add_needs_review_to_word_lookup.sql), TASK-16). To list entries to check: `SELECT word, entry_json FROM word_lookup WHERE needs_review;`. In the admin the flag is cleared with the check button (`POST /api/admin/words/w-ID/reviewed`, doc-13); the admin shows the reasons (TASK-55).
Unlike `sentence_translation`, the entry is stored as **one JSON column**, not split into columns.

`BundledWordWinsTest` covers TASK-58: a bundled word is answered from Postgres without Claude and without a row (also when an old backend row exists), its media becomes `imageUrl`, a word that is not bundled still calls Claude, and the V9 SQL deletes only the backend rows of bundled words with their media links.

Try it:

```bash
curl -s localhost:8081/api/words/depend
```

Expected shape (the words differ from run to run, the keys do not; first call `"cached": false`, second `true`):

```json
{ "word": "depend", "cached": false, "entry": { "senses": [ {
  "pos": "verb", "cefr": "A2", "simpleDefinition": "...", "vietnamese": "...", "examples": [ { "level": "easy", "text": "..." }, { "level": "medium", "text": "..." }, { "level": "harder", "text": "..." } ],
  "usage": [ { "kind": "pattern", "pattern": "depend on + something", "example": "It depends on the weather." } ]
} ] } }
```

## iOS: bundled dictionary lookup (TASK-4)

| Step | File | What happens |
|---|---|---|
| Bundle | `EnglishTranslation/EnglishTranslation/Resources/dictionary.sqlite` | Copy of the pipeline output. Refresh: after a pipeline run, copy `pipeline/data/out/dictionary.sqlite` over it and commit. |
| Normalise | [CacheKeys.swift:51-53](../../../EnglishTranslation/EnglishTranslation/Cache/CacheKeys.swift#L51-L53) | `CacheKeys.normalizeWord`: trim, collapse spaces, lowercase, exactly like the backend. `DictionaryStore.lookUp` calls it. |
| Look up | [DictionaryStore.swift:51-56](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L51-L56) | `lookUp(_:)` returns a `WordEntry` or nil. Tries `words.lemma`, then `forms.form` ("ran" finds "run", and then `matchedForm` is `MatchedForm(text: "ran", type: "past tense")` from `forms.type`; a base-word hit has none). Like `usage`, the type column is checked once at open (`hasFormTypeColumn`); in an old file without it the type is "form". Uses system SQLite3, read-only. |
| Model | [WordEntry.swift](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift) | `WordEntry { word, ipa, senses, matchedForm }` ([9-24](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift#L9-L24)), `MatchedForm { text, type }` ([27-33](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift#L27-L33), the form the user typed, for example "ran" and "past tense"; nil for a base word; `DictionaryStore` fills it from `forms.type`), `Sense { pos, cefr, simpleDefinition, vietnamese, examples, usage }`, `Usage { kind, pattern, example }`, `Example { level, text }` ([98-128](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift#L98-L128); `level` is easy, medium or harder; a plain string in old data decodes with no level; the pipeline exports old strings as easy, medium). `usage` is read from the `senses.usage` JSON column ([DictionaryStore.swift:77-94](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L77-L94), [decodeUsage:148-150](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L148-L150)), `examples` by [decodeExamples:141-144](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L141-L144). The old bundled file has no `usage` column: [columnExists](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L96-L100) checks `PRAGMA table_info(senses)` once at open, and then usage is empty. A missing or unknown `kind` becomes `.pattern`. Collocations and synonyms are not loaded (decision-2). |

Tests: [DictionaryStoreTests.swift](../../../EnglishTranslation/EnglishTranslationTests/DictionaryStoreTests.swift) (hit, miss, form, usage kind default, usage JSON decode, a tiny file with a `usage` column, a tiny file with levelled examples and `forms.type`, an old file without them (type "form"), old string examples without a level; normalising is tested in [CacheTests.swift](../../../EnglishTranslation/EnglishTranslationTests/CacheTests.swift)).

## iOS: the full lookup (TASK-6, TASK-19)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant ST as TranslationStore
    participant WL as WordLookup
    participant DS as DictionaryStore
    participant BC as BackendClient
    participant RC as ResultCache
    participant API as GET /api/words/word

    U->>ST: taps Explain with "serendipity"
    ST->>ST: CacheKeys.wordQuery gives "serendipity", state = .loading
    ST->>WL: lookUp("serendipity")
    WL->>DS: lookUp (lemma, then form)
    alt in the bundle
        DS-->>WL: WordEntry
    else not in the bundle
        WL->>BC: entry(forWord:)
        BC->>RC: data(forKey: wordKey)
        alt in the local cache
            RC-->>BC: saved backend JSON
        else not saved yet
            BC->>API: GET /api/words/serendipity
            API-->>BC: 200 {word, entry, cached}
            BC->>RC: save(JSON as-is, kind .word)
        end
        BC-->>WL: WordEntry (IPA from the reply, may be nil)
    end
    WL-->>ST: WordEntry, or a throw
    ST-->>U: .loadedWord shows WordCardView, or .failed shows a short message
```

| Step | File | What happens |
|---|---|---|
| Word or sentence | [CacheKeys.swift:77-95](../../../EnglishTranslation/EnglishTranslation/Cache/CacheKeys.swift#L77-L95) | `wordQuery` (TASK-44) is the one rule. One token gives the word without edge punctuation. `a`, `an` or `the` (any case) plus exactly one word gives that word: "an apple", "The  Apple!" and `“an apple”` all give the word apple, so the word card and the cache key are the same as for "apple". "the red apple" gives nil (a sentence). An article alone ("the") is one token and is looked up as a word. Only marks ("?!") give "" so the word path shows its error. The share extension's `TextRoute` (on `feature/f6-share-extension`) should call this function after the merge. |
| One token | [CacheKeys.swift:56-62](../../../EnglishTranslation/EnglishTranslation/Cache/CacheKeys.swift#L56-L62) | `isSingleToken`: normalise like the backend, then no space left and not empty. "don't" and "well-known" are one token, "go home" is two. The no-break space does not split (same as Java). `wordQuery` and the sentence word cards use it. |
| Route | [TranslationStore.swift:56-65](../../../EnglishTranslation/EnglishTranslation/Stores/TranslationStore.swift#L56-L65) | `CacheKeys.wordQuery` decides: a word goes to `lookUpWord(word)` with the bare word (no article, no edge punctuation), anything else goes to `explainSentence` (doc-4). The input is trimmed first. The target-language picker does not matter for a word: entries are English plus Vietnamese. |
| Word path | [TranslationStore.swift:68-77](../../../EnglishTranslation/EnglishTranslation/Stores/TranslationStore.swift#L68-L77) | `makeWordLookup().lookUp(...)`, then `.loadedWord(entry)` or `.failed(error.localizedDescription, spelling:)` (spelling help: see "iOS: did you mean" below). The lookup is built per request (like the translator), by [TranslatorFactory.swift:25-33](../../../EnglishTranslation/EnglishTranslation/Network/TranslatorFactory.swift#L25-L33). |
| Punctuation | [CacheKeys.swift:64-75](../../../EnglishTranslation/EnglishTranslation/Cache/CacheKeys.swift#L64-L75) | `trimmingWordPunctuation`: word path only. Removes punctuation and symbols at the start and end ("Run!", "(run)", "“run”" give "Run"); inner `'` and `-` stay. `WordLookup` calls it first, so the bundle, the cache key and the backend all get the clean word. Nothing left ("?!") gives `notOneWord`. Sentences are not touched. |
| Order | [WordLookup.swift:54-75](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordLookup.swift#L54-L75) | 1. bundle (`DictionaryStore.lookUp`, a broken file counts as a miss), 2. and 3. `backend.entry(forWord:)`. Protocols `WordLooking` and `WordFetching` ([WordLookup.swift:9-27](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordLookup.swift#L9-L27)) let tests use fakes. |
| Cache and fetch | [BackendClient.swift:86-101](../../../EnglishTranslation/EnglishTranslation/Network/BackendClient.swift#L86-L101) | `entry(forWord:)`: key = `CacheKeys.wordKey` (same as the backend), local hit returns with no network. A miss calls `GET {baseURL}/api/words/{normalised word}` (the word is percent-encoded as one path part), then saves the **raw JSON** under the key. Errors are never saved. |
| Decode | [BackendClient.swift:19-34](../../../EnglishTranslation/EnglishTranslation/Network/BackendClient.swift#L19-L34) | `WordResponse` reads `word`, `entry.senses` and the optional `ipa` into `WordEntry.ipa`. A reply without `ipa` (JSON saved on the phone before TASK-39) or with `null` decodes with no IPA, and the card shows no IPA line. Old `collocations` and `synonyms` in an older backend reply are ignored. |
| Status check | [BackendClient.swift:104-112](../../../EnglishTranslation/EnglishTranslation/Network/BackendClient.swift#L104-L112) | Shared with sentences: not 200 gives `BackendError.badStatus(code, detail)`. |
| Not a word | [WordLookup.swift:69-73](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordLookup.swift#L69-L73) | Empty `senses` gives `WordLookupError.notAnEnglishWord`. The empty answer is saved in the local cache too (the server keeps it as well). |
| Result | [TranslateResultView.swift:22-23](../../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateResultView.swift#L22-L23) | `.loadedWord(entry)` shows `WordCardView(entry:)`. |

What the user sees when it goes wrong:

| Cause | HTTP | Message |
|---|---|---|
| Only punctuation ("?!"), or digits, symbols inside, or more than 64 letters | none / 400 | "Type one English word, with letters only." |
| Made-up word (`asdkj`) | 200, empty senses | "\"asdkj\" is not an English word. Check the spelling." |
| Claude refused | 422 | "Claude declined to answer this text. (status 422)" (same text as sentences) |
| Claude down | 502 | "The translation service is not available. Try again later. (status 502)" |
| Offline, or backend not running (and not in the bundle or cache) | none | The system text, for example "The Internet connection appears to be offline." |
| `BackendBaseURL` missing | none | "Missing BackendBaseURL. Check Secrets.xcconfig and the Info tab." (it stops even bundled words, because the lookup is built whole) |

Tests: [WordLookupTests.swift](../../../EnglishTranslation/EnglishTranslationTests/WordLookupTests.swift) (bundle first, backend on a miss, empty senses, 400 message, other errors pass through), the word tests in [BackendClientTests.swift](../../../EnglishTranslation/EnglishTranslationTests/BackendClientTests.swift) (GET path and decode, save then serve with no network, errors not saved), the token rule in [CacheTests.swift](../../../EnglishTranslation/EnglishTranslationTests/CacheTests.swift), and the word or sentence choice in [TranslationStoreTests.swift](../../../EnglishTranslation/EnglishTranslationTests/TranslationStoreTests.swift).

## iOS: did you mean (TASK-37)

When the word path fails, the result can show up to 3 close bundled words under the error. Tapping one fills the input and looks it up. Works offline (it only reads the bundle). Last traced: 2026-10-11.

```mermaid
flowchart TD
    F["lookUp throws"] --> C{"Which error?"}
    C -- "notAnEnglishWord (backend: empty senses)" --> S
    C -- "URLError (offline or backend down; word is not in the bundle)" --> S
    C -- "anything else (422, 502, 400...)" --> NOHELP[".failed with no suggestions"]
    S["WordLookup.suggestions(for:)"] --> SS["SpellingSuggester: distance 1 (up to 4 letters) or 2, rank, max 3"]
    SS --> E{"Any close word?"}
    E -- yes --> CHIPS[".failed(message, spelling:) shows chips"]
    E -- no --> NOHELP
    CHIPS --> TAP["Tap a chip: inputText = word, translate()"]
```

A real word that is not in the bundle (for example "serendipity") still goes to the backend first: suggestions are asked for only after the lookup failed.

| Step | File | What happens |
|---|---|---|
| Candidates | [DictionaryStore.swift:58-71](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L58-L71) | `spellingCandidates()`: every lemma, plus every form joined to its lemma, with `words.rank`. |
| Suggest | [SpellingSuggester.swift](../../../EnglishTranslation/EnglishTranslation/Dictionary/SpellingSuggester.swift) | Loads the candidate list on first use and keeps it. Damerau-Levenshtein distance (a swap of two neighbour letters counts as 1), max 1 for words of 4 letters or fewer, 2 for longer. A word that is already bundled gives nothing. One result per lemma (best distance), sorted by distance, then rank (lower = more common), then alphabet. Max 3. |
| Ask | [WordLookup.swift:50-52](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordLookup.swift#L50-L52) | `suggestions(for:)` removes punctuation, then asks the suggester. The protocol has a default that returns nothing, so fakes need no change. |
| Wire | [TranslatorFactory.swift:28-31](../../../EnglishTranslation/EnglishTranslation/Network/TranslatorFactory.swift#L28-L31) | One shared suggester for the app, built on the bundled dictionary. |
| State | [TranslationStore.swift:66-96](../../../EnglishTranslation/EnglishTranslation/Stores/TranslationStore.swift#L66-L96) | Only `notAnEnglishWord` and `URLError` ask for suggestions. `.failed(message, spelling: SpellingHelp?)` keeps the old message. `lookUp(suggestion:)` handles a tap. |
| View | [SpellingSuggestionsView.swift](../../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/SpellingSuggestionsView.swift) | "No word "runj". Did you mean:" and chips in the `LevelChip` style, 44 pt tall, VoiceOver label "Look up run". Shown by [TranslateResultView.swift:26-33](../../../EnglishTranslation/EnglishTranslation/Views/Translate/Subviews/TranslateResultView.swift#L26-L33). |

Note: a form is suggested by its base word ("rung" is a form of "ring", so "runj" can show "run" and "ring").

Tests: [SpellingSuggesterTests.swift](../../../EnglishTranslation/EnglishTranslationTests/SpellingSuggesterTests.swift) (runj gives run first, aple gives apple, zzzzqq gives none, a correct word is not corrected, swaps, length limits, one result per lemma, max 3, loaded once, and the real bundle), [TranslationStoreTests.swift](../../../EnglishTranslation/EnglishTranslationTests/TranslationStoreTests.swift) (suggestions after not-English and offline, none for other errors, a tap looks the word up), [WordLookupTests.swift](../../../EnglishTranslation/EnglishTranslationTests/WordLookupTests.swift).

## iOS word card (v2, TASK-36)

Built in TASK-27, shown for real lookups since TASK-19, redesigned in TASK-36 (developer mockup v3, doc-7).
Uses the model from the TASK-4 section above. An empty `cefr` or `vietnamese` (what the store gives for a missing value)
hides the level or the Vietnamese row. Last traced: 2026-10-14 (Listen: TASK-7).

| Part | File |
|---|---|
| Sample words for previews (`ran`, `apple`, `get`, `run`, `depend`, `oldData`, `sparse`) | [WordEntry+Samples.swift](../../../EnglishTranslation/EnglishTranslation/Models/WordEntry+Samples.swift) |
| Full card: header, one card per sense, folded rows | [WordCardView.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift) |
| Compact card (word list in a sentence): first sense only | [WordCardCompactView.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardCompactView.swift) |
| Sense title: number circle, simple explanation, level chip, small speaker | [SenseTitleRow.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseTitleRow.swift) |
| Form pill ("past tense of run /rʌn/") | [FormPill.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/FormPill.swift) |
| "Examples" content: per example, line 1 is the easy / medium / harder label and a plain speaker at the right edge, line 2 is the sentence at full width | [WordExamplesSection.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordExamplesSection.swift) |
| "Grammar" content (usage grouped by kind) | [WordUsageSection.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordUsageSection.swift) |
| Text helpers: `chipText`, `usageGroups`, `formPillText` | [WordEntry+Display.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordEntry+Display.swift) |

Top to bottom:

1. **Header** ([WordCardView.swift:51-60](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift#L51-L60) calls [WordHeaderView.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordHeaderView.swift), which the share sheet reuses with its own buttons, see doc-10): the headword as typed (`matchedForm.text`, else `word`), and the word speaker (see Listen below); the word gets the speech highlight through `isSpeaking`. Under it, either the form pill (when `matchedForm` is set; it has the lemma IPA) or the IPA (when the user typed the base word).
2. **One card per sense** ([senseCard:62-104](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift#L62-L104)): surface, hairline border, card radius. Title = `SenseTitleRow` (with its small speaker).
3. **Three folded rows**, in this order: Examples, Grammar, and the picked language (title from `TargetLanguage.title`, "Vietnamese" by default). No counters. Examples and Grammar are closed and only one of them is open at a time per card. The language row starts closed unless "Show translation" in Settings says otherwise (TASK-29); it is tracked apart from the other two (`openTargetRows`), so "Always open" can open it on every sense. Examples is hidden with no examples, Grammar with no usage. The language row is hidden for English, for Vietnamese with no text, and for other languages with no simple explanation (`Sense.hasTargetLanguageRow(for:)`).
4. **Examples (open)**: one line each, with a level label (`ExampleLevelLabel`: easy teal, medium amber, harder coral). No level (old data) means no label.
5. **Grammar (open)**: `Sense.usageGroups` ([WordEntry+Display.swift:22-33](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordEntry+Display.swift#L22-L33)) groups by kind in a fixed order: Patterns, Collocations, Phrases and idioms (phrase and idiom together). Empty groups are hidden. Each item is a bullet: pattern in medium weight, example below in secondary serif.

Compact card: word, IPA, the word speaker (the only speaker on it), then the same `SenseTitleRow` for sense 1, then the language row (closed by default, follows "Show translation" too).

### Listen (F3, TASK-7)

Last traced: 2026-10-15. Spec R4, scope in doc-7 (2026-10-14). The app reads three things aloud, on the device, in an en-US voice, so it works offline: the headword as typed ("ran", not "run"), each sense's simple explanation, and each example sentence. It does **not** read the Vietnamese line, Grammar examples, other languages. No slow speed and no accent choice in v1.

| Part | Where | Notes |
|---|---|---|
| Word speaker | [WordCardView.swift:51-60](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift#L51-L60) (header, drawn by `WordHeaderView`; the share sheet's Listen button reads the word or sentence too), [WordCardCompactView.swift:19-46](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardCompactView.swift#L19-L46) | Regular 44 pt. Reads the displayed word (`matchedForm.text`, else `word`), so "ran" stays "ran". The compact card keeps only this speaker. |
| Explanation speaker | [SenseTitleRow.swift:18-53](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseTitleRow.swift#L18-L53) | Compact `IconButton`, right of the level chip. Reads `sense.simpleDefinition`. The compact card passes no speech id, so it shows none. |
| Example speaker | [WordExamplesSection.swift:11-53](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordExamplesSection.swift#L11-L53) | Plain `IconButton` (`style: .plain`, TASK-48) at the right edge of line 1, on the same line as the level label; the sentence is on line 2 at full width. Old data with no level has no label but still shows the speaker. Reads only `example.text`; ids use `example-{sense}-{example}`. |
| Button and highlight | [IconButton.swift](../../../EnglishTranslation/EnglishTranslation/DesignSystem/Components/IconButton.swift), [SpeechPlayer.swift:67-76](../../../EnglishTranslation/EnglishTranslation/Speech/SpeechPlayer.swift#L67-L76) | The button has a compact 32 pt circle inside its 44 pt tap target, and a `plain` style (icon only, no circle, border or shadow, same 44 pt tap target; `isActive` draws it in the accent colour, used for `stop.fill` on examples). Every view changes its icon to `stop.fill` and VoiceOver label to "Stop" while its id plays. `speechHighlight(isPlaying:)` puts a soft accent background (12% opacity) behind that text. |
| Player | [SpeechPlayer.swift:14-58](../../../EnglishTranslation/EnglishTranslation/Speech/SpeechPlayer.swift#L14-L58) | `@MainActor @Observable`; API `speak(_:id:)`, `stop()`, `currentID`. A new id stops the old utterance. Tokens ignore a late cancellation callback, and empty text does nothing. One instance is shared with `.environment(speechPlayer)` in [EnglishTranslationApp.swift:9-25](../../../EnglishTranslation/EnglishTranslation/EnglishTranslationApp.swift#L9-L25). |
| Voice | [SpeechSynthesizing.swift:22-88](../../../EnglishTranslation/EnglishTranslation/Speech/SpeechSynthesizing.swift#L22-L88) | `AppleSpeechSynthesizer` wraps `AVSpeechSynthesizer`, voice `en-US`. Audio session `.playback` + `.spokenAudio` with `.duckOthers` plays with the silent switch on and makes music quieter instead of pausing it (TASK-46). Finish/cancel deactivates it with `.notifyOthersOnDeactivation`, so music returns to full volume. A protocol hides audio from unit tests. |

**Stopping (TASK-46).** Speech stops when the shown result changes (`TranslateView` watches `TranslationState.resultKey` with `onChange`: a new search goes through loading, so it always changes; the streamed `.meaning` state has the same key as `.loaded`, so meaning turning into the full result does not stop speech), when `SentenceWordSheet` closes (`onDisappear`), when the app goes to the background (`EnglishTranslationApp` watches `scenePhase`), and when the share sheet closes (`SelectedTextResultView`, already there). All of them call `SpeechPlayer.stop()`.

Tests: [SpeechPlayerTests.swift:40-129](../../../EnglishTranslation/EnglishTranslationTests/SpeechPlayerTests.swift#L40-L129) (speak sets `currentID`, a new id replaces and stops the old one, stop clears, finish clears, late callbacks are ignored, and empty text does nothing).

### Target-language row (TASK-40, doc-7)

The picked language reaches the card as `TranslationStore.targetLanguage`, through `TranslateView`, `TranslateResultView` and a `targetLanguage` parameter on `WordCardView` and `WordCardCompactView`. The row content is [TargetLanguageRowContent.swift](../../../EnglishTranslation/EnglishTranslation/Views/Word/TargetLanguageRowContent.swift). It exists only while the row is open, so nothing is translated before the user asks.

- **Vietnamese**: shows `sense.vietnamese` from the dictionary, as before. No translation.
- **Other languages**: translate `sense.simpleDefinition` from English on the device with Apple's Translation framework (`.translationTask` and `TranslationSession`, iOS 18). Steps: read [TranslationCache](../../../EnglishTranslation/EnglishTranslation/Translation/TranslationCache.swift) (key = English text + language); on a miss, ask `LanguageAvailability` for the pair; if `.unsupported`, show "<Language> is not available for on-device translation."; else set the configuration, so iOS shows its one-time language download sheet if needed. While it works: a small `ProgressView`. On success: the text, a muted note "Translated on this iPhone", and the text goes into the cache. On error (download declined, failure): "Could not translate. The <Language> language pack may be missing."
- **English**: no row (the explanation is already English).
- Chinese uses Apple's code `zh-Hans` (`TargetLanguage.appleIdentifier`).
- The cache lives in memory only. It is gone when the app quits.
- **Show translation (TASK-29)**: `TranslationRowPreference.startsOpen` sets the first state of the row on `WordCardView`, `WordCardCompactView` and the sentence `MeaningCard`. Opening or closing a row calls `rowChanged(isOpen:)`, which "Remember last choice" reuses. Details in doc-4.
- Known gap: the iOS Simulator reports the pair as unsupported, so the real translation and the download sheet can only be tested on a device.


Tests: [TranslationRowPreferenceTests.swift](../../../EnglishTranslation/EnglishTranslationTests/TranslationRowPreferenceTests.swift) (Show translation logic), [TargetLanguageRowTests.swift](../../../EnglishTranslation/EnglishTranslationTests/TargetLanguageRowTests.swift) (row title, Vietnamese uses the dictionary text, other languages need translation, English has no row, Chinese code, cache), [WordEntryTests.swift](../../../EnglishTranslation/EnglishTranslationTests/WordEntryTests.swift) (usage grouping and order, empty groups, form pill text, example decoding of old strings and new objects, entry without `matchedForm`).

## Tests (iOS, TASK-44)

`CacheTests.swift` (`wordQuery…` tests): articles, case, extra spaces, punctuation `! . , ? ; :` and straight/curly quotes, sentences give nil, same cache key for every form. `TranslationStoreTests.swift`: "an apple" calls `lookUp("apple")`, "the red apple" uses the sentence translator, "the" alone is looked up, spelling help uses the bare word.

## Errors

A word that is neither bundled nor cached needs a new Claude call, so it needs a signed-in user: a guest gets **401** `sign_in_required`. Bundled words, cached words and not-a-word answers work for everyone. When today's Claude budget is used up the answer is 503 `daily_limit_reached` (no Claude call), and a user over the limit gets 429 `rate_limited`. See [Accounts and limits](doc-17%20-%20Accounts-and-limits.md); the gate is [WordService.java:86](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L86). The retry for hard words only re-checks the budget.

Same handler as sentences ([ApiExceptionHandler.java](../../../backend/src/main/java/com/englishtranslation/backend/common/ApiExceptionHandler.java)):
bad word → 400, Claude refused → 422, Claude down → 502. A word that is not on the English word list (`asdfg`, `runk`) returns **200 with empty `senses`** straight away: Claude is not called and nothing is saved on the server (the phone's ResultCache still keeps the answer). A word on the list that Claude still rejects also returns empty `senses`, and that answer is cached.

## Tests

| Test | Covers |
|---|---|
| [WordServiceTest.java](../../../backend/src/test/java/com/englishtranslation/backend/word/WordServiceTest.java) | a simple entry has no retry; a hard definition gets exactly one retry (with the hard word in the feedback) and the better entry is saved; a second failure is saved with `needs_review` and no third call; a throwing retry keeps the first entry flagged; possessives and contractions are not hard; example limits per level; The second lookup is `cached: true` and calls Claude once; a saved entry reads back the same; `runk` is rejected with no Claude call and no saved row; `serendipity`, `run`, `running` and uppercase input reach Claude; the list has SCOWL and NGSL words; `graveyard` has IPA, `aardwolf` (on the word list, not the IPA list) has `null`, a row saved without IPA gets it when read from cache, `hello` uses its first IPA |
| [TranslationControllerTest.java:69](../../../backend/src/test/java/com/englishtranslation/backend/translation/TranslationControllerTest.java#L69) | A word with spaces → 400 |
| [CacheKeysTest.java](../../../backend/src/test/java/com/englishtranslation/backend/cache/CacheKeysTest.java) | Words are lowercased |

## Known gaps

- Backend-word pictures (TASK-49.4): the job runs only when a word is created. If the server restarts while the job is queued, that word gets no picture until an admin regenerates it. `GET /api/words/{word}` serves bundled words' media only through the exported `dictionary.sqlite` (the app reads bundled words from its own file, never from this endpoint).

- **Keep the keys equal.** `CacheKeys.swift` must copy the backend normalising **exactly**, or the phone and server use different keys. The word prompt version lives in `PromptVersions.word` and must equal `app.claude.word-prompt-version`.
- **Phone-cached backend words keep their old JSON**, saved before TASK-39, so they show no IPA until the phone cache entry is cleared or replaced. The usage list of a backend word can be empty if the saved answer is from an older prompt version. A word that is not on the IPA list shows no IPA line.
- **The backend simple-word check is lighter than the pipeline's** (TASK-16). It checks definitions and examples, with one retry, then flags `needs_review`. It does not check usage items, the 20-word limit, the headword rule or the Vietnamese field. Flagged rows are only in the database and the log; there is no screen to review them. `simple-extra.txt` is a copy of the pipeline's lists, so a change in `validate.py` must be copied by hand.
- **Empty answers from Claude are cached forever** (for that prompt version). Not-a-word answers from the word list are not saved on the server, but the phone's ResultCache keeps them, so a later list update does not reach a phone that already asked.
- **The word list can miss a real word** (new slang, rare names). Such a word shows "not an English word" until the list is updated (regenerate `scowl-70.txt` at app.aspell.net/create, see `SCOWL-LICENSE.txt`).
- **No check that the senses match the headword.** If the word is on the list but Claude describes another word, it is still saved. Only the prompt rule guards this.
- **Own prompt version:** `app.claude.word-prompt-version`, separate from the sentence one (TASK-15). Changing `WORD_PROMPT`? Bump it, or old entries keep being served.

## Media row (photo or clip under the sense title)

Last traced: 2026-10-15 (TASK-23, TASK-47, TASK-51; a bundled word's media loads from the server). A sense with `imageUrl` shows one small photo (WebP) or a muted looping clip (MP4) between the title row and the folded rows ([WordCardView.swift:68-71](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift#L68-L71)). The frame is always 240 x 135, so the card never jumps. It is hidden from VoiceOver.

`Sense.imageUrl` holds one of two things. `SenseMediaSource` ([SenseMediaSource.swift:15-56](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaSource.swift#L15-L56)) tells them apart:

| Value | Example | Where the file comes from |
|---|---|---|
| Bundled path (dictionary.sqlite) | `media/run_1.mp4`, `dog_1.webp` | The app bundle (`Resources/media`) if the file is there (only 3 files are). Otherwise the server, `https://api.loffy.me/media/<file>`, then the disk cache, like an https address. The server address is `AppConfig.backendBaseURL()`. |
| https address (backend, TASK-49.4) | `https://api.loffy.me/media/run_1_3fa9c2d1.mp4` | The server, then the disk cache. A photo shows offline after its first view. Plain http and other schemes are ignored (nothing is shown). |

The JSON key is `imageUrl` from the backend and `image_url` in older data; both decode ([WordEntry.swift:47-90](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift#L47-L90)). `DictionaryStore` reads `senses.image_url` only when the column exists, else `imageUrl` is nil ([DictionaryStore.swift:104-122](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L104-L122)). The backend leaves `imageUrl` out when a sense has no media, so both cases work.

```mermaid
flowchart TD
    V["SenseMediaView(path)"] --> S{"SenseMediaSource(path)"}
    S -- "empty, http, other" --> NONE["Nothing shown"]
    S -- bundled --> BF{"File in bundle?"}
    BF -- yes --> SHOW["Photo or clip"]
    BF -- no --> MAP["Server address: BackendBaseURL/media/file"] --> S2{"photo or clip"}
    S2 -- photo --> PC
    S2 -- clip --> CC
    S -- "https photo" --> PC{"In MediaFileCache?"}
    PC -- yes --> SHOW
    PC -- no --> DL["Download, save, trim cache"]
    DL -- ok --> SHOW
    DL -- fail --> NONE
    S -- "https clip" --> CC{"Saved file?"}
    CC -- yes --> SHOW
    CC -- no --> STREAM["AVPlayer streams the URL; file saved in the background"] --> SHOW
```

| Step | File | What happens |
|---|---|---|
| Load | [SenseMediaView.swift:81-113](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L81-L113) | `loadState`: loading (frame kept, opacity 0), photo, clip, or failed. Failed removes the view: no media, no error text. |
| Disk cache | [MediaFileCache.swift:13-122](../../../EnglishTranslation/EnglishTranslation/Views/Word/MediaFileCache.swift#L13-L122) | Folder `Caches/SenseMedia`, name = sha256 of the address + extension (AVPlayer needs `.mp4`). Limit 50 MB: the file used longest ago is removed first. A file bigger than a quarter of the limit is not saved. One download per address at a time. A photo is shown offline after its first view. A clip is saved after its first play, so the second play uses the saved file. |
| Clip player | [SenseMediaView.swift:115-178](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L115-L178) | `LoopingPlayerUIView` uses an empty `AVQueuePlayer()` plus `AVPlayerLooper(templateItem:)` (Apple's pattern, no stutter at the first loop). Muted. If the looper status is `.failed`, the media is hidden. |
| Play only when seen | [SenseMediaView.swift:49-52](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L49-L52) | `onScrollVisibilityChange(threshold: 0.33)` (iOS 18) sets `isOnScreen`. The clip plays only when `isOnScreen` and `scenePhase == .active`; it pauses when scrolled away or when the app goes to the background. |

Tests: [SenseMediaTests.swift](../../../EnglishTranslation/EnglishTranslationTests/SenseMediaTests.swift) (Sense decoding, `image_url` column, bundled path vs https, cache hit, offline, failure, size limit). Not covered by tests: the visibility and background rule (needs a real scroll view), checked by hand in the PR card.
