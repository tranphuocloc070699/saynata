# Dictionary pipeline

Builds the word data for the app: a simple English definition, a Vietnamese meaning,
examples, and an image hint for each sense of each word.

## Flow

```
NGSL word list ─► Kaikki (Wiktionary JSON) ─► filter senses ─► LLM ────► validate ─► retry
                                                                              │
                                     data/out/dictionary.sqlite ◄─ export ◄───┘
```

| File | Job |
|---|---|
| `sources.py` | Downloads NGSL + Kaikki entries (cached in `data/raw/`) |
| `senses.py` | Drops archaic/rare/word-form senses, keeps top 5 per entry |
| `models.py` | The JSON shape the LLM must return |
| `generate.py` | The prompt + Claude call (structured output in the `WordEntry` shape) |
| `validate.py` | Definition may only use NGSL words, no headword, max 20 words |
| `store.py` | Progress table (`data/pipeline.db`) so runs can stop and resume |
| `usage.py` | The usage pass: adds `usage` (pattern + example) to done entries |
| `export.py` | Builds `dictionary.sqlite` for the iOS app + `dictionary.jsonl` |

## Run

```bash
cp .env.example .env        # add ANTHROPIC_API_KEY, pick CLAUDE_MODEL
uv sync
uv run pipeline preview reluctant   # see the prompt input (no key needed)
uv run pipeline generate --limit 200
uv run pipeline report
uv run pipeline revalidate          # after changing the checks: re-check needs_review words, no LLM
uv run pipeline usage --dry-run --words get make depend  # 3 calls (~$0.01), saves nothing
uv run pipeline usage               # add usage to every done word that has none (1 call per word, ~$8)
uv run pipeline export
```

`usage` only adds the `usage` list to entries that are already `done`. It never changes definitions,
makes one call per word with no retries, and can be stopped and run again.

## Test

| What | Command | Uses LLM? |
|---|---|---|
| Unit tests (checker, sense filter) | `uv run pytest` | No |
| See the prompt for a word | `uv run pipeline preview bank` | No |
| Try one word, save nothing | `uv run pipeline try bank` | Yes, 1-3 calls |
| Small real batch | `uv run pipeline generate --limit 20` then `report` | Yes |

Calls are not paced by default; the SDK retries rate limits (429) itself. Set `LLM_MAX_PER_MINUTE` to slow down.

Changed the prompt? Bump `PROMPT_VERSION` in `generate.py`, and `generate` redoes old rows.

## Licenses

NGSL and Wiktionary (via Kaikki) are CC BY-SA. The app must show attribution, and the
dictionary data we publish is CC BY-SA too.
