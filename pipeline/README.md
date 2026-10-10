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
| `generate.py` | The prompt + LLM call (Gemini or DeepSeek, via the OpenAI-compatible API) |
| `validate.py` | Definition may only use NGSL words, no headword, max 20 words |
| `store.py` | Progress table (`data/pipeline.db`) so runs can stop and resume |
| `export.py` | Builds `dictionary.sqlite` for the iOS app + `dictionary.jsonl` |

## Run

```bash
cp .env.example .env        # pick LLM_PROVIDER and add its key
uv sync
uv run pipeline preview reluctant   # see the prompt input (no key needed)
uv run pipeline generate --limit 200
uv run pipeline report
uv run pipeline export
```

## Test

| What | Command | Uses LLM? |
|---|---|---|
| Unit tests (checker, sense filter) | `uv run pytest` | No |
| See the prompt for a word | `uv run pipeline preview bank` | No |
| Try one word, save nothing | `uv run pipeline try bank` | Yes, 1-3 calls |
| Small real batch | `uv run pipeline generate --limit 20` then `report` | Yes |

Gemini free tier allows 15 requests/minute, so calls are spaced ~4 s apart (`LLM_MAX_PER_MINUTE`).

Changed the prompt? Bump `PROMPT_VERSION` in `generate.py`, and `generate` redoes old rows.

## Licenses

NGSL and Wiktionary (via Kaikki) are CC BY-SA. The app must show attribution, and the
dictionary data we publish is CC BY-SA too.
