"""Claude Haiku 5.5 through the Message Batches API for the local collection (TASK-83.9). Half price, usually done
within an hour, at most 24 hours. Codex (collection.py) stays the free writer; this is the fast, paid one.

Each request is the backend's own word call (ClaudeService.describeWord): the same system prompt, the same
`<word>` message, effort low, 8,000 max tokens, the WordEntry JSON. The retry is the backend's too
(describeWordAgain: the word message, a new line, the feedback), sent in a second batch.

    submit   -> pending / failed rows (and retry rows, with feedback) go into batches of at most 5,000 requests;
                each row gets batch_id, so it is never sent twice
    collect  -> for each ended batch: done | retry (failed the checks the first time) | needs_review | skipped |
                failed (errored or expired: sent again by the next submit); the real cost is saved per batch
    status   -> the batches and their counts

The system prompt is marked for prompt caching: the batch shares it across requests (cache hits in a batch are
best effort, so the estimate below does not count on them).
"""

import json
import sqlite3
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass, field

from pipeline import collection as c

MODEL = c.CLAUDE_MODEL
MAX_TOKENS = 8000  # ClaudeService.describeWordWithMessage
BATCH_DISCOUNT = 0.5
CHUNK = 5000  # requests per batch: each carries the 10 KB prompt, and a batch may be at most 256 MB
# Claude Haiku 5.5 cache prices, as multiples of the input price: a 5-minute cache write costs 1.25x, a read 0.1x.
CACHE_WRITE_FACTOR = 1.25
CACHE_READ_FACTOR = 0.1

RETRY_FEEDBACK = ("Your last answer used words that are too hard. Write the whole entry again. "
                  "Use only very common words in definitions and in easy examples. Problems: ")

BATCH_SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS collection_batch (
    batch_id     TEXT PRIMARY KEY,
    model        TEXT NOT NULL,
    requests     INTEGER NOT NULL,
    status       TEXT NOT NULL DEFAULT 'in_progress',   -- in_progress | ended | collected
    counts       TEXT,                                   -- JSON: succeeded, errored, ... from the API
    cost_usd     REAL,                                   -- real cost from usage, after collect
    created_at   TEXT DEFAULT CURRENT_TIMESTAMP,
    collected_at TEXT
);
"""

ENTRY_SCHEMA = c._strict({"senses": {"type": "array", "items": c.SENSE_SCHEMA}})


def prepare(connection: sqlite3.Connection) -> None:
    """The batch table (collection.connect already added the batch_id column)."""
    connection.executescript(BATCH_SCHEMA_SQL)
    connection.commit()


def system_blocks(kind: str, word_prompt: str) -> list[dict]:
    text = word_prompt + ("\n" + c.PHRASE_NOTE if kind != "word" else "")
    return [{"type": "text", "text": text, "cache_control": {"type": "ephemeral"}}]


def user_message(text: str, feedback: list[str] | None) -> str:
    message = f"<word>{c.escape_tags(text)}</word>"
    return message + "\n" + RETRY_FEEDBACK + "; ".join(feedback) if feedback else message


def request_for(row: sqlite3.Row, word_prompt: str) -> dict:
    feedback = json.loads(row["problems"] or "[]") if row["status"] == "retry" else None
    return {"custom_id": f"c{row['id']}-a{row['attempts'] + 1}",
            "params": {"model": MODEL, "max_tokens": MAX_TOKENS, "system": system_blocks(row["kind"], word_prompt),
                       "messages": [{"role": "user", "content": user_message(row["text"], feedback)}],
                       "output_config": {"effort": "low", "format": {"type": "json_schema", "schema": ENTRY_SCHEMA}}}}


def rows_to_submit(connection: sqlite3.Connection, kinds: list[str] | None, limit: int | None,
                   per_kind: int | None) -> list[sqlite3.Row]:
    """Retry rows first (their first answer is waiting), then what Codex would write: pending and failed with
    tries left. Rows already waiting in a batch are left out."""
    retry = connection.execute(
        "SELECT * FROM collection WHERE status = 'retry' AND batch_id IS NULL"
        + (f" AND kind IN ({','.join('?' * len(kinds))})" if kinds else "") + " ORDER BY priority DESC, id",
        kinds or []).fetchall()
    rows = retry + c.rows_to_write(connection, kinds, None, None, per_kind)
    return rows[:limit] if limit else rows


def estimate_usd(rows: int) -> float:
    return rows * c.CLAUDE_COST_PER_ITEM * BATCH_DISCOUNT


def submit(connection: sqlite3.Connection, rows: list[sqlite3.Row], client, word_prompt: str | None = None,
           chunk: int = CHUNK) -> list[str]:
    """Creates the batches and marks every row with its batch id. Returns the batch ids."""
    word_prompt = word_prompt or c.backend_word_prompt()
    ids = []
    for start in range(0, len(rows), chunk):
        part = rows[start:start + chunk]
        batch = client.messages.batches.create(requests=[request_for(row, word_prompt) for row in part])
        connection.execute("INSERT INTO collection_batch (batch_id, model, requests) VALUES (?, ?, ?)",
                           (batch.id, MODEL, len(part)))
        connection.executemany("UPDATE collection SET batch_id = ? WHERE id = ?", [(batch.id, row["id"]) for row in part])
        connection.commit()
        ids.append(batch.id)
    return ids


def usage_cost(usage) -> float:
    """Dollars for one batch result: input, cache writes and reads, output, all at the batch discount."""
    inp = getattr(usage, "input_tokens", 0) or 0
    write = getattr(usage, "cache_creation_input_tokens", 0) or 0
    read = getattr(usage, "cache_read_input_tokens", 0) or 0
    out = getattr(usage, "output_tokens", 0) or 0
    dollars = ((inp + write * CACHE_WRITE_FACTOR + read * CACHE_READ_FACTOR) * c.CLAUDE_INPUT_USD_PER_MTOK
               + out * c.CLAUDE_OUTPUT_USD_PER_MTOK) / 1e6
    return dollars * BATCH_DISCOUNT


@dataclass
class CollectSummary:
    counts: Counter = field(default_factory=Counter)
    cost: float = 0.0
    batches: int = 0
    waiting: int = 0


def _answer_text(message) -> str | None:
    if getattr(message, "stop_reason", None) == "refusal":
        return None
    return next((block.text for block in message.content if block.type == "text"), None)


def apply_result(connection: sqlite3.Connection, row: sqlite3.Row, result, vocabulary: set[str],
                 prompt_version: str) -> str:
    """Saves one batch result on its row and returns the new status."""
    first_try = row["status"] != "retry"
    entry, problems, status = None, [], "failed"
    if result.type == "succeeded":
        text = _answer_text(result.message)
        try:
            answer = json.loads(text) if text else None
        except json.JSONDecodeError:
            answer = None
        entry, problems, status = c._judge(answer if isinstance(answer, dict) else None, row["text"], vocabulary)
        if not text:
            problems = ["Claude refused or gave no text"]
    else:
        problems = [f"batch result {result.type}"]
    if status == "retry":
        status = "retry" if first_try else "needs_review"
    elif not first_try and status in ("failed", "skipped"):
        # Like WordService: a broken retry keeps the first entry, marked for review.
        entry, status = None, "needs_review"
        problems = json.loads(row["problems"] or "[]")
    connection.execute(
        "UPDATE collection SET status = ?, attempts = attempts + 1, model = ?, prompt_version = ?, "
        "entry = COALESCE(?, entry), problems = ?, batch_id = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (status, MODEL, prompt_version, entry.model_dump_json() if entry and entry.senses else None,
         json.dumps(problems), row["id"]))
    return status


def collect(connection: sqlite3.Connection, client, vocabulary: set[str] | None = None,
            prompt_version: str | None = None, progress: Callable[[str], None] | None = None) -> CollectSummary:
    """Reads every ended batch that was not collected yet. A batch still running is left for the next call."""
    vocabulary = vocabulary if vocabulary is not None else c.simple_vocabulary()
    prompt_version = prompt_version or c.backend_word_prompt_version()
    summary = CollectSummary()
    open_batches = connection.execute(
        "SELECT batch_id FROM collection_batch WHERE status != 'collected' ORDER BY created_at").fetchall()
    for (batch_id,) in open_batches:
        batch = client.messages.batches.retrieve(batch_id)
        counts = batch.request_counts
        connection.execute("UPDATE collection_batch SET counts = ? WHERE batch_id = ?",
                           (json.dumps({k: getattr(counts, k) for k in
                                        ("processing", "succeeded", "errored", "canceled", "expired")}), batch_id))
        if batch.processing_status != "ended":
            summary.waiting += 1
            connection.commit()
            continue
        cost = 0.0
        for item in client.messages.batches.results(batch_id):
            row_id = int(item.custom_id.split("-")[0][1:])
            row = connection.execute("SELECT * FROM collection WHERE id = ?", (row_id,)).fetchone()
            if row is None or row["batch_id"] != batch_id:
                continue  # already collected, or sent again since
            if item.result.type == "succeeded":
                cost += usage_cost(item.result.message.usage)
            status = apply_result(connection, row, item.result, vocabulary, prompt_version)
            summary.counts[status] += 1
            if progress:
                progress(f"{status:<12} {row['text']}")
        # A row with no result at all (should not happen) is freed so the next submit sends it again.
        connection.execute("UPDATE collection SET batch_id = NULL WHERE batch_id = ?", (batch_id,))
        connection.execute("UPDATE collection_batch SET status = 'collected', cost_usd = ?, "
                           "collected_at = CURRENT_TIMESTAMP WHERE batch_id = ?", (round(cost, 4), batch_id))
        connection.commit()
        summary.cost += cost
        summary.batches += 1
    return summary


def status_lines(connection: sqlite3.Connection) -> list[str]:
    rows = connection.execute("SELECT * FROM collection_batch ORDER BY created_at").fetchall()
    if not rows:
        return ["No batches yet. Run: uv run pipeline collection batch submit --dry-run"]
    lines = [f"{'batch':<34} {'requests':>8} {'status':<12} {'cost':>8}  counts"]
    for row in rows:
        cost = f"${row['cost_usd']:.2f}" if row["cost_usd"] is not None else "-"
        lines.append(f"{row['batch_id']:<34} {row['requests']:>8} {row['status']:<12} {cost:>8}  {row['counts'] or ''}")
    spent = sum(row["cost_usd"] or 0 for row in rows)
    lines.append(f"Spent so far: ${spent:.2f}")
    return lines

