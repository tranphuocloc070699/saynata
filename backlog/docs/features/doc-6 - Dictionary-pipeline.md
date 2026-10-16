---
id: doc-6
title: Dictionary pipeline
type: specification
created_date: '2026-10-10 06:23'
updated_date: '2026-10-15 18:52'
---
> Status: **done: 2,750 of 2,809 words exported**, 59 left as `needs_review` (TASK-3). Last traced from code: 2026-10-16 (new `media-check` command: Claude looks at every sense picture, TASK-78; `link-media` and `export` skip senses the admin removed media from: TASK-76; topic words, TASK-61; before that Postgres as the source of truth, TASK-49.3). **The usage and examples passes have not run on the real data yet:** until they do, the `done` entries have no usage and only 2 plain examples (exported as easy, medium).
> **Since TASK-49.3 the server Postgres is the source of truth** for the bundled words, senses, forms and media rows. `dictionary.sqlite` is built *from* Postgres by `export`; the generate passes below still fill `data/pipeline.db`.
> Spec: R1 in [Vision and spec](../doc-1%20-%20Vision-and-spec.md). How to run: [pipeline/README.md](../../../pipeline/README.md).

## What it does

An offline Python job that builds the word data bundled in the iOS app. The words live in Postgres (edited in the admin web app, see [Admin API](doc-13%20-%20Admin-API.md)); `export` turns them into `dictionary.sqlite`.
For each of the ~2,800 most common English words (NGSL), it takes the Wiktionary senses, asks an LLM
to write a **simple definition** + **Vietnamese meaning** + **3 levelled examples per sense** + **usage** (patterns with an example), checks that the definition only
uses simple words, and retries with feedback when it doesn't.

## Big picture

```mermaid
flowchart LR
    NGSL["NGSL CSVs (word, rank, forms)"] --> SRC[sources.py]
    KK["Kaikki.org Wiktionary JSONL per word"] --> SRC
    SRC -->|cached in data/raw/| SEN[senses.py filter senses]
    SEN --> GEN[generate.py Claude call]
    GEN --> VAL[validate.py simple-word check]
    VAL -->|problems → feedback, max 3 tries| GEN
    VAL --> ST[(data/pipeline.db progress table)]
    ST -->|"pipeline usage: one call per word"| USE[usage.py adds usage to done entries]
    USE --> ST
    ST -->|"pipeline examples: one call per word"| EXA[examples.py gives each sense 3 levelled examples]
    EXA --> ST
    FOR[forms.py lemminflect labels each form] --> EXL[export-local, old]
    ST --> EXL
    EXL --> OUT0[data/out/dictionary.sqlite]
    SQL0[bundled dictionary.sqlite] -->|"import-to-postgres, one time"| PG[(Postgres: dictionary_word, dictionary_sense, dictionary_form, media)]
    ADMIN[Admin web app, /api/admin/words] <-->|edits bundled words| PG
    PG -->|"export, read-only"| EXP[export.py]
    MED[media files on disk] --> EXP
    EXP --> OUT1[data/out/dictionary.sqlite → iOS app]
    EXP --> OUT2[data/out/dictionary.jsonl]
    EXP --> OUT3[data/out/media/]
```

## `generate` command: step by step

```mermaid
sequenceDiagram
    autonumber
    participant CLI as cli.generate
    participant SO as sources
    participant DB as store (pipeline.db)
    participant SE as senses
    participant GE as generate
    participant LLM as Claude (Haiku / Sonnet 5.5)
    participant VA as validate

    CLI->>SO: load_ngsl() → 2,809 words + forms
    CLI->>VA: build_vocabulary(ngsl)
    CLI->>DB: add_words (INSERT OR IGNORE)
    CLI->>DB: words_to_generate(limit, PROMPT_VERSION)
    loop each word, by rank
        CLI->>SO: load_kaikki(lemma) (download once, then cached)
        CLI->>SE: build_source(entries) → candidates, IPA, Vietnamese hints
        loop attempt 1..3
            CLI->>GE: generate_entry(lemma, source, feedback)
            GE->>GE: optional pacing (LLM_MAX_PER_MINUTE)
            GE->>LLM: system prompt + candidates + feedback
            LLM-->>GE: JSON
            GE-->>CLI: WordEntry (or ValidationError → feedback, retry)
            CLI->>VA: find_problems(lemma, entry, vocabulary, rank)
            alt no problems
                Note over CLI: break → status "done"
            else problems
                Note over CLI: problems become feedback for the next try
            end
        end
        CLI->>DB: save_result(status, attempts, ipa, entry, problems)
        Note over CLI,DB: any exception (network, …) → status "failed", keep going
    end
```

## Step by step

**Parallel:** `generate` runs `_generate_word` ([cli.py:53-75](../../../pipeline/src/pipeline/cli.py#L53-L75)) for 8 words at a time in worker threads ([cli.py:89-94](../../../pipeline/src/pipeline/cli.py#L89-L94)). Workers only do network and LLM work; the main thread is the only one that writes to SQLite, because a `sqlite3` connection must not be shared across threads. Full NGSL on Sonnet 5.5: about 40 minutes.

### 1. Sources: [sources.py](../../../pipeline/src/pipeline/sources.py)

| Step | Line | What happens |
|---|---|---|
| Download once | [25-32](../../../pipeline/src/pipeline/sources.py#L25-L32) | `_download` skips the request if the file is already in `data/raw/`. |
| NGSL list | [40-63](../../../pipeline/src/pipeline/sources.py#L40-L63) | Reads two CSVs: stats (rank) and lemmatised forms ("run" → "runs, ran, running"). |
| Excel fix | [35-37](../../../pipeline/src/pipeline/sources.py#L35-L37) | Lowercases everything except "I", because Excel turned "true" into "TRUE". |
| Wiktionary | [66-70](../../../pipeline/src/pipeline/sources.py#L66-L70) | `kaikki.org/.../{w[0]}/{w[:2]}/{word}.jsonl`: one JSON line per part of speech. |

### 2. Sense filter: [senses.py](../../../pipeline/src/pipeline/senses.py)

| Rule | Line | Why |
|---|---|---|
| Skip parts of speech | [9](../../../pipeline/src/pipeline/senses.py#L9) | name, symbol, prefix, suffix, phrase, proverb… a learner doesn't need them. |
| Skip tags | [10-13](../../../pipeline/src/pipeline/senses.py#L10-L13) | archaic, obsolete, rare, dialectal, form-of ("ran" = past of run), abbreviation… |
| Max 5 per entry | [17](../../../pipeline/src/pipeline/senses.py#L17), [43-58](../../../pipeline/src/pipeline/senses.py#L43-L58) | Per entry, not per word, so "bank" (money) and "bank" (river) both survive. |
| IPA | [74-80](../../../pipeline/src/pipeline/senses.py#L74-L80) | Prefers US / General American. |
| Vietnamese hints | [83-90](../../../pipeline/src/pipeline/senses.py#L83-L90) | Wiktionary's `vi` translations, passed to the LLM as hints. |

### 3. LLM call: [generate.py](../../../pipeline/src/pipeline/generate.py)

| Step | Line | What happens |
|---|---|---|
| Model | [21](../../../pipeline/src/pipeline/generate.py#L21), [76-77](../../../pipeline/src/pipeline/generate.py#L76-L77) | `CLAUDE_MODEL`: `claude-haiku-5-5` (default, ~$0.0004/call) or `claude-sonnet-5-5` (~$0.008/call, better definitions). |
| Client | [70-73](../../../pipeline/src/pipeline/generate.py#L70-L73) | Anthropic SDK, created once (`@cache`). `max_retries=5` for 429, 5xx and connection errors. Key: `ANTHROPIC_API_KEY`. |
| Pacing | [83-91](../../../pipeline/src/pipeline/generate.py#L83-L91) | Off by default. `LLM_MAX_PER_MINUTE` spaces calls if you hit rate limits. |
| System prompt | [54-67](../../../pipeline/src/pipeline/generate.py#L54-L67) | Cambridge Learner's style rules. No schema in the prompt: structured output enforces the shape. |
| Example rules | [67-93](../../../pipeline/src/pipeline/generate.py#L67-L93) | `EXAMPLE_RULES`: 3 examples per sense that differ in **structure and situation**, not just length. Easy (A1-A2): short present simple. Medium (B1): a different situation and structure (past, question, imperative, two clauses). Harder (B2): a more complex structure (present perfect, conditional, relative clause, passive, reported speech) and/or a B2 collocation, under ~16 words. Easy uses only the 2,800 common words, medium may have 1 word outside, harder 2. Every example uses the headword (or a form) in **this** sense. Has a bad example ("Longer is not harder") and two good sets (get, run). Used by the system prompt **and** by the examples pass. |
| Usage rules | [24-64](../../../pipeline/src/pipeline/generate.py#L24-L64) | `USAGE_RULES`: kinds, placeholders, "natural, not old-fashioned", "at least one idiom when the sense has a real one (B2-C1 is fine)", "no duplicates across all senses", "the placeholder must match the example", "must fit this sense", simple example words, and the agreed few-shot examples. Used by the system prompt **and** by the usage pass. |
| User prompt | [94-106](../../../pipeline/src/pipeline/generate.py#L94-L106) | Headword + candidate senses + Vietnamese hints + **feedback from the last failed try**. |
| Call | [109-124](../../../pipeline/src/pipeline/generate.py#L109-L124) | `messages.parse(output_format=WordEntry)`, `effort: low`. A refusal or empty reply raises, and the word becomes `failed`. No `temperature`: the 5.5 models reject it. |
| Version | [19](../../../pipeline/src/pipeline/generate.py#L19) | `PROMPT_VERSION = 2` (v1 = Gemini). TASK-35 did not bump it either (same reason): the examples pass adds the levels instead. Bump it and `generate` redoes `done` rows with an older version. **Not bumped for TASK-24**: that would redo all 2,750 entries (~$36). The usage pass adds usage instead (~$8). |

Output shape: [models.py](../../../pipeline/src/pipeline/models.py): `senses[]` with `source_index, pos, cefr, simple_definition, examples, vietnamese, usage, image_type, image_query`.
`examples` is 3 items of `{ level: easy|medium|harder, text }` ([models.py:32-55](../../../pipeline/src/pipeline/models.py#L32-L55)). A `BeforeValidator` turns the old list of strings into easy, medium, so old stored rows still load.
`usage` is 0–3 items of `{ kind: pattern|collocation|phrase|idiom, pattern, example }` ([models.py:15-29](../../../pipeline/src/pipeline/models.py#L15-L29)).
`WordEntry.from_stored` reads old rows that still have `collocations`/`synonyms` and no `usage`.

### 4. Quality check: [validate.py](../../../pipeline/src/pipeline/validate.py)

The **defining vocabulary** method from learner dictionaries: a definition may only use words from a fixed list.

| Check | Line | Rule |
|---|---|---|
| Vocabulary | [91-96](../../../pipeline/src/pipeline/validate.py#L91-L96) | NGSL lemmas + all forms + numbers/days/months + grammar terms + "someone/something…" + `BASIC_WORDS` ([21-28](../../../pipeline/src/pipeline/validate.py#L21-L28)): 58 basic words NGSL misses (container, rude, queen, tv…). |
| Hard words | [108-111](../../../pipeline/src/pipeline/validate.py#L108-L111) | Any word outside the vocabulary → problem. A possessive `'s` is removed first, so "person's" is checked as "person". |
| Headword | [112-114](../../../pipeline/src/pipeline/validate.py#L112-L114) | Can't use the word itself, **except** the top 100 grammar words ("a", "the"). |
| Length | [115-116](../../../pipeline/src/pipeline/validate.py#L115-L116) | Max 20 words. |
| Vietnamese / examples | [validate.py:125-151](../../../pipeline/src/pipeline/validate.py#L125-L151) | Vietnamese not empty, at least 2 examples. |
| Example levels | [example_problems:95-114](../../../pipeline/src/pipeline/validate.py#L95-L114) | Used by the examples pass. Easy: every word must be in the vocabulary (same check as usage examples). Medium: up to 1 word outside it. Harder: up to 2. All levels: the sentence must contain the headword or one of its forms, and the headword never counts as an outside word. |
| Usage | [121-124](../../../pipeline/src/pipeline/validate.py#L121-L124), [75-88](../../../pipeline/src/pipeline/validate.py#L75-L88) | At most 3 items. Each example may only use NGSL words, plus the headword and its forms, because topic words like "giraffe" are not in NGSL (contractions like "I'm" and "can't" count as their base word; `me`, `my`, `oh` are allowed too). The pattern must not use `sb`/`sth`. |

### 5. Progress table: [store.py](../../../pipeline/src/pipeline/store.py)

```mermaid
stateDiagram-v2
    [*] --> pending: add_words
    pending --> done: checks pass
    pending --> needs_review: still failing after 3 tries
    pending --> failed: exception (network, timeout)
    failed --> done: next generate run
    failed --> needs_review
    needs_review --> done: generate --retry-review
    needs_review --> done: revalidate (checks changed, no LLM)
    done --> done: PROMPT_VERSION bumped, generated again
```

| Part | Line | Note |
|---|---|---|
| Schema | [15-29](../../../pipeline/src/pipeline/store.py#L15-L29) | `words(lemma PK, rank, forms, status, prompt_version, attempts, ipa, entry, problems, updated_at, topic)`. `topic` is NULL for NGSL words; `connect` adds the column to older databases. |
| What runs next | [65-75](../../../pipeline/src/pipeline/store.py#L65-L75) | `pending` + `failed` (+ `needs_review` with `--retry-review`) + old-version `done`, ordered by rank. |
| Save | [86-95](../../../pipeline/src/pipeline/store.py#L86-L95) | `attempts` is **added to**, so `report` shows the total LLM calls. |
| Save entry only | [78-83](../../../pipeline/src/pipeline/store.py#L78-L83) | Used by the usage pass: replaces only the `entry` JSON. Status, prompt version and attempts stay. |

### 6. Usage pass: [usage.py](../../../pipeline/src/pipeline/usage.py) (TASK-24)

Adds `usage` to entries that are already `done`, **without touching** the definitions, examples or Vietnamese.

```mermaid
sequenceDiagram
    autonumber
    participant CLI as cli.add_usage
    participant US as usage.run_pass
    participant DB as store (pipeline.db)
    participant LLM as Claude
    CLI->>US: vocabulary, fetch_from_claude, --limit, --workers, --words, --dry-run
    US->>DB: done rows where some sense has no "usage" key
    loop each word, in parallel (workers)
        US->>LLM: ONE call: headword + senses (definition, examples)
        LLM-->>US: usage items per sense number
        US->>US: drop bad items, keep max 3 per sense
    end
    US->>DB: save_entry (main thread only). A failed call saves nothing, no retry.
```

| Part | Line | Rule |
|---|---|---|
| Finished test | [usage.py:33-34](../../../pipeline/src/pipeline/usage.py#L33-L34) | A word is done when every sense has a `usage` key (an empty list counts). So a stopped run resumes where it stopped, and a second run makes no calls. |
| The call | [usage.py:45-67](../../../pipeline/src/pipeline/usage.py#L45-L67) | `messages.parse(output_format=UsageResult)`, `effort: low`, system prompt = short intro + `USAGE_RULES`. `fetch` is a parameter, so tests pass a fake and never call Claude. |
| Clean up | [usage.py:70-141](../../../pipeline/src/pipeline/usage.py#L70-L141) | One call, no retries. An item that fails the checks (hard word in the example only, `sb`/`sth`, more than 3) is **dropped**. A duplicate is dropped too: after ignoring punctuation and case (placeholders become tokens: `someone`, `something`/`+ noun`, `+ verb`, `+ -ing`, `+ adjective`, `+ clause`), a pattern that equals, or sits inside, another kept pattern of the same word (across all senses) is a duplicate. One-word patterns like `get` are too short to count as contained. A sense may end with fewer items, or none. |
| Save | [usage.py:144-157](../../../pipeline/src/pipeline/usage.py#L144-L157) | Copies the sense as it was, adds `usage`, and removes the old `collocations` and `synonyms`. |
| Errors | [usage.py:159-166](../../../pipeline/src/pipeline/usage.py#L159-L166) | Any error (network, refusal) leaves the row as it is. The next run picks it up again. |

### 7. Examples pass: [examples.py](../../../pipeline/src/pipeline/examples.py) (TASK-35)

Same shape as the usage pass. Gives every sense 3 examples `{ level, text }`, easy first. Never changes the definition, Vietnamese or usage.

| Part | Line | Rule |
|---|---|---|
| Finished test | [examples.py:35-37](../../../pipeline/src/pipeline/examples.py#L35-L37) | A word is done when no example is a plain string any more. A second run makes no calls. |
| The call | [examples.py:44-79](../../../pipeline/src/pipeline/examples.py#L44-L79) | One call per word. The prompt lists each sense with its definition and its **old examples**; the system prompt tells the model it may reuse a good one. `effort: low`. `fetch` is a parameter, so tests pass a fake. |
| Clean up | [examples.py:104-132](../../../pipeline/src/pipeline/examples.py#L104-L132) | One call, no retries. An example that fails the checks is **dropped**. At most one example per level, saved in the order easy, medium, harder (even if the model mixes them up). An example is also dropped when more than 60% of its content words (no headword forms, no stop words like "the", "I", "was") are shared with an earlier kept example of the sense ([content_words](../../../pipeline/src/pipeline/examples.py#L92-L101)). A sense may end with fewer than 3. |
| No good example | [examples.py:143-158](../../../pipeline/src/pipeline/examples.py#L143-L158) | If none of a sense's new examples is good, it keeps its old two (as easy, medium), so it is never empty. |
| Errors | [examples.py:161-170](../../../pipeline/src/pipeline/examples.py#L161-L170) | Any error leaves the row as it is; the next run tries again. |

### 8. Form types: [forms.py](../../../pipeline/src/pipeline/forms.py) (TASK-35)

Uses the free library **lemminflect** to say what each form is, so the app can show "past tense of run" for "ran".

| Tag | Label | Note |
|---|---|---|
| VBD / VBN / VBG / VBZ | past tense / past participle / -ing form / third person | The first one that fits wins: "walked" is both VBD and VBN, so it is "past tense". |
| NNS | plural | |
| JJR / JJS (RBR / RBS) | comparative / superlative | |

[form_type:40-56](../../../pipeline/src/pipeline/forms.py#L40-L56) only looks at the parts of speech the word has senses for. The answer is `"form"` when lemminflect does not know the form, when two kinds of word fit ("runs" = plural noun or third person verb), or when the form is the word itself. [word_forms:59-64](../../../pipeline/src/pipeline/forms.py#L59-L64) lists every form of a word; the examples check uses it to see that a sentence contains the headword.

### 9. Postgres tables and import: [postgres.py](../../../pipeline/src/pipeline/postgres.py) (TASK-49.3)

The tables are made by the backend (Flyway [V6__create_dictionary_tables.sql](../../../backend/src/main/resources/db/migration/V6__create_dictionary_tables.sql)), so run the backend (or its migrations) once before the first import.

| Table | What it holds |
|---|---|
| `dictionary_word` | `lemma` (unique), `word_rank` (NGSL), `ipa`, `needs_review`, `model`, `prompt_version`, timestamps. |
| `dictionary_sense` | One meaning: `position`, `pos`, `cefr`, `simple_definition`, `vietnamese`, `examples_json`, `usage_json` (the same JSON text the app reads), `image_type`, `image_query`, `media_id`. Unique on (word, position). |
| `dictionary_form` | `form` and `form_type` ("past tense", "plural", … or "form"). |
| `media` | One row per file: `file_name` (unique, for example `run_1.mp4`), `kind` (`photo` or `clip`), `width`, `height`, `bytes`, `pixabay_id`, `photographer`, `page_url`, `license_note` (Pixabay attribution). **The files are not in Postgres**: they are on disk (on the VPS the folder `/var/lib/saynata/media`, a bind mount into the backend container and served read-only by Caddy at `https://api.loffy.me/media/<file>`, TASK-49.4). |
| `word_lookup` | Unchanged: the words the backend asked Claude about. |

`uv run pipeline import-to-postgres` ([cli.py:163](../../../pipeline/src/pipeline/cli.py#L163), [import_dictionary:89](../../../pipeline/src/pipeline/postgres.py#L89)) reads the bundled `dictionary.sqlite` and fills the tables in **one transaction** (an error changes nothing):

| Step | Line | What happens |
|---|---|---|
| Media | [107-132](../../../pipeline/src/pipeline/postgres.py#L107-L132) | One row per distinct `senses.image_url` (`media/run_1.mp4` → `run_1.mp4`). Size, width and height come from the file in `--media-dir` when it is there (Pillow reads photos; clips get no width/height). Pixabay author and page URL come from `data/pipeline.db` when it exists. `license_note` is "Pixabay Content License". |
| Words | [134-163](../../../pipeline/src/pipeline/postgres.py#L134-L163) | A lemma that is already in Postgres is **skipped**, so the command is safe to run again and never overwrites an admin edit. `--overwrite` replaces such a word (and its senses and forms) with the file. |
| Senses, forms | [164-192](../../../pipeline/src/pipeline/postgres.py#L164-L192) | `examples` and `usage` JSON text is copied as it is. Forms keep their order. |

`DATABASE_URL` (in `pipeline/.env`, or `--database-url`) is a normal Postgres URL, for example `postgresql://app:password@localhost:55432/english_translation` ([database_url:23](../../../pipeline/src/pipeline/postgres.py#L23)). How to reach the VPS database: [Deploy to a VPS](../guides/doc-11%20-%20Deploy-to-a-VPS.md).

### 10. Export: [export.py](../../../pipeline/src/pipeline/export.py)

`uv run pipeline export` ([export_from_postgres:117](../../../pipeline/src/pipeline/export.py#L117)) reads Postgres on a **read-only connection** ([connect:34](../../../pipeline/src/pipeline/postgres.py#L34)) and writes three things into `data/out/`:

| Part | Line | Note |
|---|---|---|
| App schema | [24-49](../../../pipeline/src/pipeline/export.py#L24-L49) | Unchanged: `meta`, `words`, `forms` ("ran" → run, with a `type` column), `senses` (`examples` JSON list of `{level, text}`, `usage` JSON list of `{kind, pattern, example}`, `image_url`). Case-insensitive indexes on lemma and form. [DictionaryStore.swift](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift) reads it as before. |
| Words | [130](../../../pipeline/src/pipeline/export.py#L130), [149](../../../pipeline/src/pipeline/export.py#L149) | Every `dictionary_word`, ordered by `word_rank`, numbered 1, 2, 3… Senses by `position`. Forms in the order they were imported. |
| JSON text | [160-161](../../../pipeline/src/pipeline/export.py#L160-L161) | `examples_json` and `usage_json` are parsed and written again, so the text has one format also after an edit in the admin. |
| `image_url` | [162](../../../pipeline/src/pipeline/export.py#L162) | `media/<file_name>` from the sense's media row, else empty. |
| Rebuild | [125](../../../pipeline/src/pipeline/export.py#L125) | Deletes and recreates the file each time. `meta.dictionary_version` = UTC timestamp ([181](../../../pipeline/src/pipeline/export.py#L181)). |
| Media folder | [191-204](../../../pipeline/src/pipeline/export.py#L191-L204) | Copies the files the senses use from `--media-dir` (default `data/media`) into `data/out/media/` (emptied first). Files that are not there are listed as a warning; the sense still points to them. |
| `export-local` | [52](../../../pipeline/src/pipeline/export.py#L52) | The old export from `data/pipeline.db` (only `done` words; the usage and examples warnings). Use it only for a fresh `generate` run until those words can be imported into Postgres. |

An edit made in the admin for a bundled word is in the next `export` (tested: edit one definition in the admin, export, only that sense row differs from the bundled file).

Copy `data/out/dictionary.sqlite` and `data/out/media/` into `EnglishTranslation/EnglishTranslation/Resources/` to ship them.

### 11. Topic words: [topics.py](../../../pipeline/src/pipeline/topics.py) (TASK-61)

NGSL is chosen by frequency, so everyday concrete words are missing ("apple", "giraffe", "scarf", "tram"). Topic words add them, each with **one topic**: animals, food and drink, plants, the body, clothes, home, transport, weather, tools and objects, places and the street, nature, people and jobs ([Topic](../../../pipeline/src/pipeline/topics.py#L36)). They live in their own table, `topic_words` ([store.py:32-42](../../../pipeline/src/pipeline/store.py#L32-L42)); the NGSL `words` table is not touched until the developer approves the list.

```mermaid
flowchart LR
    OEWN["Open English WordNet 2025 (CC BY 4.0)"] --> C[topics candidates]
    WF["wordfreq Zipf (CC BY-SA data)"] --> C
    NGSL[(words table: NGSL lemmas + forms)] -->|removed| C
    X[EXTRA_WORDS hand list] --> C
    C --> TW[(topic_words: candidate)]
    TW --> J["topics judge: Claude Haiku 5.5, 50 words per call"]
    J --> TW2[(topic_words: keep / drop + topic + reason)]
    TW2 --> RV[topics review: reviewed drop / move / keep lists]
    RV --> R[topics report / export-tsv]
    RV --> PR[topics promote: keep words join the words table as pending]
    PR --> G[generate, the normal Sonnet pass]
```

| Step | Line | Rule |
|---|---|---|
| WordNet | [ensure_wordnet:137-153](../../../pipeline/src/pipeline/topics.py#L137-L153) | Downloads OEWN 2025 (11 MB) to `data/raw/` once and adds it to a local `wn` database in `data/raw/wn/` (several minutes, only once). |
| Topic | [_noun_topic:196-203](../../../pipeline/src/pipeline/topics.py#L196-L203), [ANCHORS:61-91](../../../pipeline/src/pipeline/topics.py#L61-L91) | The word's **first** WordNet sense decides. `noun.animal`, `noun.food`, `noun.plant` are topics as they are; `noun.artifact`, `noun.body`, `noun.person`… get the topic of a hypernym ("anchor": clothing, conveyance, furniture, tool, body part, worker…). Three verb lexfiles too: consumption, body, weather. |
| Common words only | [min_zipf_for:169-170](../../../pipeline/src/pipeline/topics.py#L169-L170), [LOWER_ZIPF:95-97](../../../pipeline/src/pipeline/topics.py#L95-L97) | wordfreq Zipf ≥ 3.5 (`--zipf`). Animals, food, plants, home, clothes and transport may be 0.3-0.4 lower (giraffe is 3.2); verbs need 0.3 more. |
| Filters | [build_candidates:222-249](../../../pipeline/src/pipeline/topics.py#L222-L249) | Not in NGSL (lemma or form), lowercase letters only, more than 2 letters, not in `BLOCKED`, no rude gloss, and mainly that part of speech ("holy" is an adjective). |
| Hand list | [EXTRA_WORDS:106-122](../../../pipeline/src/pipeline/topics.py#L106-L122) | OEWN 2025 does **not** order senses by frequency ("tiger" starts with "a fierce person"), so the first-sense rule misses some everyday words. This short list adds them, plus compounds (toothbrush) and topic adjectives (sunny, striped). Our own list: the Cambridge Young Learners lists were only used to check coverage and are not copied (© Cambridge). |
| Save | [save_candidates:252-260](../../../pipeline/src/pipeline/topics.py#L252-L260) | `INSERT OR IGNORE`: a second run adds new words only and never resets a judged row. |
| Judge | [JUDGE_SYSTEM:280-295](../../../pipeline/src/pipeline/topics.py#L280-L295), [fetch_from_claude:303-320](../../../pipeline/src/pipeline/topics.py#L303-L320) | `claude-haiku-5-5`, `effort: low`, structured output `JudgeResult` (word, keep, topic, reason). Keeps a word when its **everyday** meaning is concrete (or a common action or quality of a topic). Drops names (wordfreq has no capital letters, so "charlotte" looks common), rare meanings ("bishop" as a drink), technical and rude words. It may move a word to a better topic. |
| Clean up | [apply_verdicts:331-340](../../../pipeline/src/pipeline/topics.py#L331-L340), [run_judge:343-370](../../../pipeline/src/pipeline/topics.py#L343-L370) | Verdicts for words not in the batch are ignored; the first verdict per word wins. A failed call or a missing verdict leaves the word as `candidate`, so the next run retries only those. `--dry-run` prints and saves nothing. |
| Reviewed overrides | [REVIEW_DROP:404-410](../../../pipeline/src/pipeline/topics.py#L404-L410), [apply_review:432-448](../../../pipeline/src/pipeline/topics.py#L432-L448) | A person read every verdict of the first judge run. The judge (Haiku, effort low) mostly keeps the proposed topic even when its own reason disagrees ("queen: royal ruler" stayed in animals) and dropped a few good words (mole, cabin, psychiatrist). `REVIEW_DROP` (47), `REVIEW_MOVE` (21) and `REVIEW_KEEP` (11) are applied on top, so a new judge run followed by `topics review` gives the same list. |
| Promote | [promote:457-476](../../../pipeline/src/pipeline/topics.py#L457-L476) | Adds every `keep` word to the `words` table as `pending`, with its topic, its forms (lemminflect) and a rank after NGSL (10,001 and up, most common first). Skips words that are already there, so it is safe to run again. Then the normal `generate` pass writes the entries. |
| Review | [report:373-382](../../../pipeline/src/pipeline/topics.py#L373-L382), [export_tsv:385-395](../../../pipeline/src/pipeline/topics.py#L385-L395) | Counts and samples per topic; `data/out/topic_words.tsv` for a spreadsheet. |

Numbers on 2026-10-15 (Zipf 3.5): **1,378 candidates**. The judge (28 Haiku calls, under $0.10; Haiku 5.5 is $0.10 / $0.50 per million tokens) kept 980; after `topics review`: **944 keep**, 434 drop. Kept per topic: food and drink 160, animals 157, tools and objects 96, home 96, places and the street 80, plants 71, people and jobs 61, clothes 55, transport 56, nature 52, the body 40, weather 20.

### 12. Media check: [media_check.py](../../../pipeline/src/pipeline/media_check.py) (TASK-78)

Developer decision (2026-10-15, doc-7): check **every** Pixabay picture and clip with Claude first, mark the wrong ones `needs_ai`, and make AI images only for those (TASK-53). Full flow and the admin side: doc-15.

`uv run pipeline media-check` reads the bundled senses that have media straight from Postgres (through the SSH tunnel, `DATABASE_URL`), loads each file from `--media-dir` (default `data/media`), and asks Claude Haiku 5.5 for `{"verdict": "good" | "wrong" | "unsure", "reason": "..."}`. A clip is checked on one frame from its middle (ffprobe for the length, ffmpeg for the frame). Results are saved one by one in the table `media_check` (Flyway V12), so stopping the run loses nothing and the next run goes on.

| Part | Where | What |
|---|---|---|
| Senses to check | `PostgresStore.pending_senses` | Has media, `image_type` is not `none`, `media_status` is not `removed` or `needs_ai`, and no check exists for the media it has now (`--recheck` ignores that). Most common words first. |
| One check | `check_one` | Worker thread: file, ffmpeg, Claude. A missing file, a failed call or a malformed answer is a failure: nothing is saved and the sense is tried again next run (the tokens of a malformed answer still count in the cost). |
| The run | `run` | 4 calls at a time (`--workers`). No new call starts once the running cost reaches `--max-cost` (default $2); at most 4 calls finish after that. The SDK client retries 429, 5xx and network errors by itself (`max_retries=5`). |
| `--apply` | `PostgresStore.apply_wrong` | Sets `media_status = 'needs_ai'` on every sense whose **newest** check of its current media is `wrong` and that has no status yet. It also covers checks from an earlier run, so you can review the report first and apply later without paying again. `unsure` is never applied. The old media stays until an AI image replaces it. |
| Report | `write_report` | `data/out/media-check.html`: thumbnail (a clip plays on hover), word, meaning, verdict, reason; wrong first, then unsure, then good. It lists the newest check of every sense. |

Price: Haiku 5.5 is $0.10 / $0.50 per million tokens (`PRICES` in `media_check.py`, same numbers as `application.properties`). A 320 px picture is about 100 image tokens; the whole call is about 500 input tokens.

## Commands ([cli.py](../../../pipeline/src/pipeline/cli.py))

| Command | LLM? | Use |
|---|---|---|
| `uv run pipeline preview bank` | No | See the exact prompt for a word |
| `uv run pipeline try bank` | 1–3 calls | Try one word, save nothing, print the checks |
| `uv run pipeline generate --limit 200` | Yes | Next 200 words by rank, 8 in parallel (`--workers`) |
| `uv run pipeline generate --retry-review` | Yes | Also redo `needs_review` |
| `uv run pipeline usage --dry-run --words get make depend` | 3 calls (~$0.01) | Try the usage pass on 3 done words, print the result, save nothing |
| `uv run pipeline examples --dry-run --words run make get` | 3 calls (~$0.01) | Try the examples pass on 3 done words, print the result, save nothing |
| `uv run pipeline examples` | Yes, 1 call per word (about $8 for 2,750 words, like the usage pass) | Give every done word 3 levelled examples per sense. Resume-safe; run it again to retry failed words |
| `uv run pipeline usage` | Yes, 1 call per word (~$8 for 2,750 words) | Add usage to every done word that has none. Resume-safe; run it again to retry failed words ([cli.py:118-129](../../../pipeline/src/pipeline/cli.py#L118-L129)) |
| `uv run pipeline revalidate` | No | Re-check stored `needs_review` entries after the checks change; passing ones become `done` ([cli.py:99-115](../../../pipeline/src/pipeline/cli.py#L99-L115)) |
| `uv run pipeline report` | No | Counts per status + the problem list |
| `uv run pipeline import-to-postgres` | No | One time: copy the bundled `dictionary.sqlite` (+ media facts) into Postgres. Re-run safe; `--overwrite`, `--sqlite`, `--media-dir`, `--database-url` |
| `uv run pipeline link-media` | No | After the media pass: add its media rows and links to senses in Postgres, changing no text and no existing media. It also skips a sense with `image_type = 'none'` or `media_status = 'removed'` (the admin removed its media, TASK-76), so a removed picture does not come back from an older file. `--sqlite` (default `data/out/dictionary.sqlite`, from `export-local`), `--media-dir`, `--database-url`. Then `rsync` the files to the server (doc-11, section 9) |
| `uv run pipeline media-check --limit 50` | Yes, 1 Haiku call per sense (about $0.0001 each; full run of 3,372 senses about $0.35) | Claude looks at each sense picture or clip and says good, wrong or unsure. Needs the SSH tunnel. Saves to `media_check`, prints counts, cost and the wrong ones, writes `data/out/media-check.html`. `--apply` marks the wrong ones `needs_ai`; `--recheck`, `--words`, `--max-cost` (default 2), `--workers` (default 4), `--model`, `--media-dir`, `--report`, `--database-url` |
| `uv run pipeline export` | No | Build `data/out/dictionary.sqlite`, `.jsonl` and `media/` from Postgres (read-only). A sense with `image_type = 'none'` or `media_status = 'removed'` gets no `image_url` (TASK-76). `--media-dir`, `--database-url` |
| `uv run pipeline export-local` | No | Old: build `dictionary.sqlite` from `data/pipeline.db` |
| `uv run pipeline topics candidates` | No | Topic word candidates from WordNet + wordfreq into `topic_words` (`--zipf`, default 3.5). Resume-safe ([cli.py:207-233](../../../pipeline/src/pipeline/cli.py#L207-L233)) |
| `uv run pipeline topics judge --dry-run --limit 50` | 1 Haiku call (under $0.01) | Judge 50 candidates, print the verdicts, save nothing |
| `uv run pipeline topics judge` | Yes, about 28 Haiku calls (under $0.10) | Keep or drop every candidate, with a topic and a reason. Run again to retry failed calls |
| `uv run pipeline topics review` | No | Apply the reviewed drop / move / keep lists on top of the judge. Safe to run again |
| `uv run pipeline topics promote` | No | Add the kept topic words to `words` as `pending` (rank 10,001+). Then `uv run pipeline generate --limit 1000` (Sonnet, about $12 for 944 words) |
| `uv run pipeline topics report` / `topics export-tsv` | No | Counts and samples per topic / `data/out/topic_words.tsv` for review |

## Tests

| Test | Covers |
|---|---|
| [test_senses.py](../../../pipeline/tests/test_senses.py) | Drops archaic and form-of senses, keeps both homograph entries, skips names, reads Vietnamese hints |
| [test_validate.py](../../../pipeline/tests/test_validate.py) | Simple definition passes, hard word and headword reported, missing Vietnamese/examples, numbers and grammar terms allowed, grammar words may use themselves, possessive `'s`, basic words outside NGSL, a topic word may use itself in its usage and example sentences |

| [test_usage.py](../../../pipeline/tests/test_usage.py) | Usage checks (contractions, hard words, `sb`/`sth`), the pass with a fake LLM (definitions untouched, resume, no retry on error, bad items dropped, max 3, dry run), the prompt has the agreed examples, export stores usage as JSON |
| [test_examples.py](../../../pipeline/tests/test_examples.py) | Example checks (levels, 2 words outside the list for harder, headword or a form), an example that repeats another one is dropped, the examples pass with a fake LLM (definitions untouched, order, resume, no retry, drops, old examples kept as a fallback, dry run), old string examples load, form types (ran, went, running, apples, runs, …), export stores levels and form types |
| [test_topics.py](../../../pipeline/tests/test_topics.py) | Topic words with a fake WordNet and a fake judge: topics from lexfile and hypernym, NGSL, blocked, rare and topicless words skipped, the hand list, re-saving keeps judged rows, verdicts for other words ignored, the judge can change the topic, failed calls and missing verdicts are retried, dry run, prompt and TSV, the reviewed overrides (applied on top, safe to run again, lists do not overlap), promote (rank after NGSL, topic, forms, skips existing words, safe to run again), older databases get the `topic` column |

| [test_media_check.py](../../../pipeline/tests/test_media_check.py) | TASK-78 with a fake Claude client and a fake ffmpeg: the prompt, base64 image content, JSON parsing (code fence, malformed answers), the clip frame from the middle, a failed check is not saved, already-checked senses are skipped (unless `--recheck`), the stop at `--max-cost`, `--apply` marks only `wrong`, the HTML report. Two Postgres tests (need `TEST_DATABASE_URL`): which senses are pending, `apply_wrong`. |
| [test_postgres.py](../../../pipeline/tests/test_postgres.py) | Import counts match the file (words, senses, forms, media), columns and media facts, Pixabay attribution from `pipeline.db`, import twice changes nothing, admin edits survive a re-import (and `--overwrite` replaces them), a failed import changes nothing, round trip (import then export gives the same schema and rows), word numbering by rank, an edit in Postgres is in the next export, missing media files, read-only connection. Last test: the whole bundled file (2,750 words, 7,779 senses, 5,542 forms) round-trips. |

Run: `uv run pytest` in `pipeline/` (no API key needed). The Postgres tests need `TEST_DATABASE_URL` (a Postgres you can write to, for example `postgresql://postgres@localhost:5432/postgres`); without it they are skipped. Each test makes its own schema from the backend's Flyway files and drops it. CI starts a Postgres for them.

## Known gaps

- **Topic words stop at `pipeline.db`** (TASK-61). `promote` puts them in the `words` table and `generate` writes them, but a `topic` column in Postgres and `dictionary.sqlite`, the import into Postgres, the media pass and browsing by topic are still to do. Their rank (10,001+) is not an NGSL rank.

- **Network errors become `failed` with `attempts=1`**, even when some LLM calls already ran ([cli.py:74-75](../../../pipeline/src/pipeline/cli.py#L74-L75)). `report` undercounts calls for these words.
- **3 schema errors in a row → `needs_review` with no entry** ([cli.py:61-69](../../../pipeline/src/pipeline/cli.py#L61-L69)). Harmless for export, but `report` shows the schema error as the problem.
- **No sampling control** on Claude 5.5 models. Fine here: the output is generated once and stored, so R3's "same input, same output" holds through the file.
- **Words from a new `generate` run are not imported into Postgres yet.** `import-to-postgres` reads a `dictionary.sqlite`, not `data/pipeline.db`. Until a pipeline.db import exists, use `export-local` for a fresh run (it does not include admin edits).
- **Media files are not in Postgres and `import-to-postgres` does not upload them.** Only 3 sample files are in the repo (`Resources/media`). The full set comes with the media pass (TASK-23); copying the folder to the VPS is `rsync` (doc-11, section 9; TASK-49.4 added the volume and Caddy).
- **`needs_review` words from `pipeline.db` (59) are not in Postgres**, because they are not in the bundled file.
- **Bundled words cannot be deleted in the admin** (a delete would drop the word from the next `dictionary.sqlite`); the admin edits them instead.
- **The usage pass has not run on the real data yet** (TASK-24): until the developer runs `uv run pipeline usage`, `dictionary.sqlite` has an empty `usage` for every word. Words whose call fails keep no usage until the next run.
- **The examples pass has not run on the real data yet** (TASK-35): until the developer runs `uv run pipeline examples` and then `export`, the bundled `dictionary.sqlite` has old examples and no `forms.type`. The app reads both old and new files.
- **59 words are not in the file** (`needs_review` after allowlist + Sonnet retry on 2026-10-11: 18 use the headword, 3 too long, the rest use rarer words). The backend fills them on demand.
