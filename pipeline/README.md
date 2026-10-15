# Dictionary pipeline

Builds the word data for the app: a simple English definition, a Vietnamese meaning,
examples, and an image hint for each sense of each word.

## Flow

```
NGSL word list ─► Kaikki (Wiktionary JSON) ─► filter senses ─► LLM ────► validate ─► retry
                                                                              │
                                                                              │
                          (one time) import-to-postgres ◄─ dictionary.sqlite ─┘
                                          │
                                          ▼
                              server Postgres = source of truth  ◄─ edits in the admin web app
                                          │
                      data/out/dictionary.sqlite + media/ ◄─ export
```

Since TASK-49.3 the **server Postgres** holds the bundled words, senses, forms and media rows. `export` builds
`dictionary.sqlite` (the same file format the app already reads) from it. The generate passes still write to
`data/pipeline.db`; their words are not imported into Postgres yet.

| File | Job |
|---|---|
| `sources.py` | Downloads NGSL + Kaikki entries (cached in `data/raw/`) |
| `senses.py` | Drops archaic/rare/word-form senses, keeps top 5 per entry |
| `models.py` | The JSON shape the LLM must return |
| `generate.py` | The prompt + Claude call (structured output in the `WordEntry` shape) |
| `validate.py` | Definition may only use NGSL words, no headword, max 20 words |
| `store.py` | Progress table (`data/pipeline.db`) so runs can stop and resume |
| `usage.py` | The usage pass: adds `usage` (pattern + example) to done entries |
| `examples.py` | The examples pass: gives each sense 3 examples (easy, medium, harder) |
| `forms.py` | Labels each word form (past tense, plural, ...) with lemminflect |
| `media.py` | The media pass: fetches & resizes Pixabay photos (320px WebP) and action loops (MP4) |
| `postgres.py` | Connects to the server Postgres (`DATABASE_URL`) and imports `dictionary.sqlite` into it (one time, re-run safe) |
| `topics.py` | Topic words (TASK-61): candidates from Open English WordNet + wordfreq, then a Claude Haiku judge (keep or drop, topic) |
| `export.py` | Builds `dictionary.sqlite` + `dictionary.jsonl` + `media/` from Postgres (read-only). `export-local` is the old export from `data/pipeline.db` |

## Run

```bash
cp .env.example .env        # add ANTHROPIC_API_KEY, PIXABAY_API_KEY, pick CLAUDE_MODEL
uv sync
uv run pipeline preview reluctant   # see the prompt input (no key needed)
uv run pipeline generate --limit 200
uv run pipeline report
uv run pipeline revalidate          # after changing the checks: re-check needs_review words, no LLM
uv run pipeline usage --dry-run --words get make depend  # 3 calls (~$0.01), saves nothing
uv run pipeline usage               # add usage to every done word that has none (1 call per word, ~$8)
uv run pipeline examples --dry-run --words run make get  # 3 calls (~$0.01), saves nothing
uv run pipeline examples            # 3 levelled examples per sense for every done word (1 call per word, ~$8)
uv run pipeline media --dry-run --words apple run bank   # preview Pixabay photo & video matches, saves nothing
uv run pipeline media               # download, resize, and cache media for all eligible senses
uv run pipeline import-to-postgres  # one time: copy the bundled dictionary.sqlite into Postgres (DATABASE_URL in .env)
uv run pipeline export              # build data/out/dictionary.sqlite and data/out/media/ from Postgres
uv run pipeline topics candidates   # topic words (apple, giraffe, scarf) into topic_words, no LLM; first run adds WordNet (minutes)
uv run pipeline topics judge --dry-run --limit 50   # 1 Haiku call (under $0.01), prints verdicts, saves nothing
uv run pipeline topics judge        # Haiku keeps or drops every candidate (~28 calls, under $0.10)
uv run pipeline topics review       # apply the reviewed drop / move / keep lists in topics.py (no LLM)
uv run pipeline topics report       # counts and samples per topic; `topics export-tsv` writes data/out/topic_words.tsv
```

`import-to-postgres` skips words that are already in Postgres, so it is safe to run again and it never overwrites
edits made in the admin. `--overwrite` replaces them with the file. Options: `--sqlite PATH` (default: the file in the
app's Resources), `--media-dir PATH` (default: `media/` next to that file), `--database-url`.
`export` uses a read-only connection and copies the media files named in Postgres from `--media-dir`
(default `data/media`) into `data/out/media/`.

`usage` only adds the `usage` list to entries that are already `done`. It never changes definitions,
makes one call per word with no retries, and can be stopped and run again.
`examples` works the same way, but only for the `examples` list.

## Test

| What | Command | Uses LLM? |
|---|---|---|
| Unit tests (checker, sense filter) | `uv run pytest` | No |
| Import and export tests (need a Postgres) | `TEST_DATABASE_URL=postgresql://user@host/db uv run pytest` | No |
| See the prompt for a word | `uv run pipeline preview bank` | No |
| Try one word, save nothing | `uv run pipeline try bank` | Yes, 1-3 calls |
| Small real batch | `uv run pipeline generate --limit 20` then `report` | Yes |

Calls are not paced by default; the SDK retries rate limits (429) itself. Set `LLM_MAX_PER_MINUTE` to slow down.

Changed the prompt? Bump `PROMPT_VERSION` in `generate.py`, and `generate` redoes old rows.

## Licenses

NGSL and Wiktionary (via Kaikki) are CC BY-SA. The app must show attribution, and the
dictionary data we publish is CC BY-SA too. Topic words also use Open English WordNet (CC BY 4.0) and wordfreq
(CC BY-SA data). Every source with its attribution: [licenses/NOTICE.md](licenses/NOTICE.md).
