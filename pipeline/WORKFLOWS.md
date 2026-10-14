# Dictionary Pipeline: Architecture and Workflows

> **Scope**: Detailed design, workflows, and data pipelines for `pipeline/`, building the offline dictionary database (`dictionary.sqlite`) bundled in the iOS application.

---

## 1. System Overview & Data Flow

The dictionary pipeline is an offline, multi-stage processing system. Its goal is to take frequency-ranked English word lists and Wiktionary entries, synthesize simple English definitions with Vietnamese translations, enrich them with levelled examples and usage patterns, and export them into an indexed SQLite database for offline mobile lookup.

### End-to-End Pipeline Architecture

```mermaid
flowchart TD
    subgraph S1["1. Raw Ingestion & Filtering"]
        NGSL["NGSL CSVs (Rank, Lemmas, Inflections)"] --> SRC["sources.py (Download & Cache in data/raw/)"]
        KAIKKI["Kaikki.org Wiktionary JSONL"] --> SRC
        SRC --> SEN["senses.py (Filter archaic/rare senses, max 5 per entry)"]
    end

    subgraph S2["2. Generation & Validation Loop"]
        SEN --> GEN["generate.py (Claude Haiku / Sonnet 5.5)"]
        GEN --> VAL{"validate.py (Vocabulary & Style Checks)"}
        VAL -->|"Passed"| ST[("store.py (pipeline.db progress table)")]
        VAL -->|"Failed (attempts < 3)"| GEN
        VAL -->|"Failed (attempts = 3)"| REV["status: needs_review"]
        REV --> ST
    end

    subgraph S3["3. Independent Enrichment Passes"]
        ST -->|"pipeline usage"| USG["usage.py (Patterns, collocations, idioms)"]
        USG -->|"Update entry in place"| ST
        ST -->|"pipeline examples"| EXM["examples.py (3 levelled examples: easy/medium/harder)"]
        EXM -->|"Update entry in place"| ST
    end

    subgraph S4["4. Export & Mobile Packaging"]
        ST --> EXP["export.py"]
        FRM["forms.py (lemminflect: past tense, plural, etc.)"] --> EXP
        EXP --> OUT_SQL["data/out/dictionary.sqlite (iOS App Bundle)"]
        EXP --> OUT_JSONL["data/out/dictionary.jsonl"]
    end
```

---

## 2. Core Workflows Step-by-Step

### Workflow A: Primary Word Generation (`pipeline generate`)

This workflow extracts source words, queries Claude using structured JSON outputs, and validates definitions against a strict **Defining Vocabulary**. If checks fail, it feeds errors back to Claude for correction up to 3 attempts.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / CLI
    participant CLI as cli.py (Worker Pool)
    participant SRC as sources.py
    participant SEN as senses.py
    participant LLM as Claude (Anthropic API)
    participant VAL as validate.py
    participant DB as store.py (SQLite Main Thread)

    Dev->>CLI: run pipeline generate --limit 200 --workers 8
    CLI->>SRC: load_ngsl() (ranks & forms)
    CLI->>VAL: build_vocabulary(ngsl)
    CLI->>DB: add_words() (INSERT OR IGNORE)
    CLI->>DB: words_to_generate(limit, PROMPT_VERSION)

    par Parallel generation across 8 worker threads
        CLI->>SRC: load_kaikki(lemma) (cached on disk)
        CLI->>SEN: build_source(entries)
        Note over SEN: Drops archaic, slang, form-of.<br/>Keeps top 5 senses per POS origin.<br/>Extracts US IPA & Vietnamese hints.

        loop Attempt 1 to 3
            CLI->>LLM: generate_entry(lemma, source, feedback)
            LLM-->>CLI: WordEntry JSON (structured output)
            CLI->>VAL: find_problems(lemma, entry, vocabulary, rank)
            alt All checks pass
                Note over CLI: validation OK -> status: "done"
            else Has problems (attempts < 3)
                Note over CLI: Problems appended as feedback for next retry
            else Has problems (attempts = 3)
                Note over CLI: Retries exhausted -> status: "needs_review"
            end
        end
    end

    CLI->>DB: save_result(status, attempts, ipa, entry, problems)
```

#### Key Rules & Algorithms in Primary Generation

1. **Sense Filtering ([senses.py](src/pipeline/senses.py))**:
   - Skips non-target parts of speech: `name`, `character`, `symbol`, `prefix`, `suffix`, `phrase`, `proverb`.
   - Skips noise tags: `archaic`, `obsolete`, `dated`, `rare`, `dialectal`, `form-of`, `abbreviation`.
   - Capped at **5 senses per entry** (per etymological origin/POS), ensuring homographs like *bank* (river bank) and *bank* (financial institution) are both retained.
   - Selects US / General American IPA first.

2. **Defining Vocabulary Validation ([validate.py](src/pipeline/validate.py))**:
   - Definitions are restricted to ~2,800 NGSL lemmas + their inflected forms.
   - Allows numbers, days of the week, months, grammatical terms, and 58 curated everyday words (`container`, `rude`, `queen`, `tv`, etc.).
   - Possessive `'s` is stripped before vocabulary lookup (`person's` -> `person`).
   - Max length: **20 words per definition**.
   - No self-referential headword in definition (exempt only for the top 100 grammar words such as *a*, *the*).

---

### Workflow B: Usage Enrichment Pass (`pipeline usage`)

The usage pass adds collocations, sentence patterns, phrasal verbs, and idioms to entries that are already in `done` status, **without modifying existing definitions, examples, or Vietnamese meanings**.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / CLI
    participant CLI as cli.py
    participant USG as usage.py
    participant LLM as Claude (Anthropic API)
    participant VAL as validate.py
    participant DB as store.py

    Dev->>CLI: run pipeline usage --workers 8
    CLI->>DB: SELECT * FROM words WHERE status = 'done'
    Note over CLI: Filter words where any sense lacks "usage"

    par 8 parallel workers (1 call per word, no retry)
        CLI->>LLM: fetch_from_claude(lemma, entry)
        LLM-->>CLI: UsageResult (senses -> items with kind, pattern, example)
        CLI->>VAL: usage_problems(item, vocabulary)
        Note over USG: Clean usage items:<br/>1. Drop banned placeholders (sb, sth)<br/>2. Drop examples with non-vocabulary words<br/>3. Drop duplicate patterns across ALL senses<br/>4. Keep maximum 3 per sense
    end

    CLI->>DB: save_entry(lemma, updated_entry_json)
    Note over DB: Updates only the entry JSON blob.<br/>Status, attempts, and prompt_version remain intact.
```

#### Usage Pattern Tokenization & Deduplication

In [`usage.py:pattern_words`](src/pipeline/usage.py), patterns are converted into tokens to detect cross-sense duplicates:
* `depend on + something` $\rightarrow$ `("depend", "on", "SOMETHING")`
* `depend on + someone` $\rightarrow$ `("depend", "on", "SOMEONE")`
* Trailing `s` is stripped to match inflections (`depends` $\rightarrow$ `depend`).
* Sub-pattern matching prevents overlapping or redundant entries (e.g., `make a decision` suppresses redundant broader patterns).

---

### Workflow C: Levelled Examples Pass (`pipeline examples`)

The examples pass upgrades every sense to have exactly 3 levelled example sentences ordered from easy to hard.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer / CLI
    participant CLI as cli.py
    participant EXM as examples.py
    participant LLM as Claude (Anthropic API)
    participant VAL as validate.py
    participant DB as store.py

    Dev->>CLI: run pipeline examples --workers 8
    CLI->>DB: SELECT * FROM words WHERE status = 'done'
    Note over CLI: Filter entries where examples are still plain strings

    par 8 parallel workers (1 call per word)
        CLI->>LLM: fetch_from_claude(lemma, entry with old_examples)
        LLM-->>CLI: ExamplesResult (senses -> 3 examples: easy, medium, harder)
        CLI->>VAL: example_problems(example, headword, forms, vocabulary)
        Note over EXM: Clean examples:<br/>- Easy: 0 outside words, present simple<br/>- Medium: max 1 outside word<br/>- Harder: max 2 outside words<br/>- Must contain headword or inflected form<br/>- Drop if >60% content words overlap with another example
        alt No new example passes
            Note over EXM: Fallback: preserve old examples as easy, medium
        end
    end

    CLI->>DB: save_entry(lemma, updated_entry_json)
```

---

### Workflow D: Inflected Forms Labeling (`forms.py`)

When exporting to the mobile SQLite database, each inflected form of a lemma is mapped to a grammatical label using `lemminflect`:

```mermaid
flowchart TD
    INPUT["Form (e.g. 'ran') + Lemma ('run') + POS list"] --> CHECK{"Is form identical to lemma?"}
    CHECK -->|"Yes"| UNK["'form'"]
    CHECK -->|"No"| QUERY["lemminflect.getAllInflections(lemma)"]
    QUERY --> MATCH{"Match against sense POS?"}
    MATCH -->|"Multiple conflicting groups (e.g. noun plural vs verb third person)"| UNK
    MATCH -->|"Single group matches"| LABEL["First matching tag wins"]

    LABEL -->|"VBD"| VBD["'past tense' (e.g. walked, ran)"]
    LABEL -->|"VBN"| VBN["'past participle' (e.g. seen, done)"]
    LABEL -->|"VBG"| VBG["'-ing form' (e.g. running)"]
    LABEL -->|"VBZ"| VBZ["'third person' (e.g. runs, goes)"]
    LABEL -->|"NNS"| NNS["'plural' (e.g. apples, dogs)"]
    LABEL -->|"JJR / RBR"| JJR["'comparative' (e.g. faster)"]
    LABEL -->|"JJS / RBS"| JJS["'superlative' (e.g. fastest)"]
```

---

## 3. Database State Machine & Concurrency

### Pipeline Progress Lifecycle (`data/pipeline.db`)

Progress is tracked per lemma in `pipeline.db` so any command can be interrupted and safely resumed at any time.

```mermaid
stateDiagram-v2
    [*] --> pending: add_words (INSERT OR IGNORE)
    pending --> done: Validation checks pass (attempts <= 3)
    pending --> needs_review: Fails checks after 3 attempts
    pending --> failed: Network / HTTP / Timeout exception

    failed --> pending: Next 'pipeline generate' run retries failed
    needs_review --> done: 'pipeline revalidate' (checks loosened, no LLM)
    needs_review --> done: 'pipeline generate --retry-review'

    done --> done: 'pipeline usage' (adds usage JSON in place)
    done --> done: 'pipeline examples' (adds levelled examples JSON in place)
    done --> pending: PROMPT_VERSION bumped in generate.py
```

### Threading & Concurrency Architecture

SQLite does not support concurrent write operations from multiple Python threads without locking errors. The pipeline separates network I/O from database writes:

```mermaid
flowchart LR
    subgraph Workers["ThreadPoolExecutor (8 Worker Threads)"]
        W1["Worker 1: Download & LLM Call"]
        W2["Worker 2: Download & LLM Call"]
        W3["Worker 3: Download & LLM Call"]
        W4["Worker 4: Download & LLM Call"]
    end

    subgraph MainThread["Main Thread (Single Writer)"]
        FUT["as_completed(futures)"] --> SAVE["store.save_result() / store.save_entry()"]
        SAVE --> SQL[("pipeline.db (SQLite WAL)")]
    end

    W1 -.-> FUT
    W2 -.-> FUT
    W3 -.-> FUT
    W4 -.-> FUT
```

---

## 4. Export Schema: `dictionary.sqlite` for iOS

When `pipeline export` runs, it packages all `done` records into a normalised SQLite database bundled directly inside `EnglishTranslation/Resources/dictionary.sqlite`.

```mermaid
erDiagram
    meta {
        text key PK
        text value
    }
    words {
        integer id PK
        text lemma
        integer rank
        text ipa
    }
    forms {
        text form
        integer word_id FK
        text type
    }
    senses {
        integer id PK
        integer word_id FK
        integer position
        text pos
        text cefr
        text simple_definition
        text vietnamese
        text examples
        text usage
        text image_type
        text image_query
        text image_url
    }

    words ||--o{ forms : "has inflections"
    words ||--o{ senses : "has meanings"
```

* **Indexes**: Case-insensitive collation indexes (`idx_words_lemma ON words(lemma COLLATE NOCASE)`, `idx_forms_form ON forms(form COLLATE NOCASE)`).
* **Speed on Mobile**: An iOS search for *"ran"* hits `forms` in $<1\text{ ms}$, retrieves `word_id`, and loads the `run` lemma entry without network round-trips.

---

## 5. CLI Command Reference & Safety Levels

| Command | LLM Calls | Cost Profile | Description |
|---|---|---|---|
| `uv run pipeline preview <word>` | 0 | Free | Inspect Wiktionary senses, IPA, and prompt payload without calling Claude. |
| `uv run pipeline try <word>` | 1–3 | ~$0.008 | Test generation and validation on a single word; outputs to stdout, saves nothing. |
| `uv run pipeline generate --limit N` | ~1.5 per word | ~$0.012 / word | Generate batch of words by frequency rank using 8 parallel worker threads. |
| `uv run pipeline revalidate` | 0 | Free | Re-evaluate `needs_review` words against updated rules without calling Claude. |
| `uv run pipeline usage --dry-run --words ...` | 1 per word | ~$0.003 / word | Test usage pattern extraction; outputs results without writing to DB. |
| `uv run pipeline usage` | 1 per word | ~$8.00 total | Run usage pass for all `done` words lacking usage; resume-safe. |
| `uv run pipeline examples --dry-run --words ...` | 1 per word | ~$0.003 / word | Test levelled examples extraction; outputs results without writing to DB. |
| `uv run pipeline examples` | 1 per word | ~$8.00 total | Run examples pass for all `done` words lacking levels; resume-safe. |
| `uv run pipeline report` | 0 | Free | Display status counts and current validation errors. |
| `uv run pipeline export` | 0 | Free | Build `data/out/dictionary.sqlite` and `data/out/dictionary.jsonl`. |
