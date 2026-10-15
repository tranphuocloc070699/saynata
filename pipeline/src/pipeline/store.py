"""Progress table, so the pipeline can stop and resume at any word.

status: pending -> done | needs_review | failed
- done: passed all checks
- needs_review: still failed checks after all retries (kept, but not exported)
- failed: an error (network, bad JSON); will be retried on the next run
"""

import json
import sqlite3
from pathlib import Path

DB_PATH = Path("data/pipeline.db")

SCHEMA = """
CREATE TABLE IF NOT EXISTS words (
    lemma          TEXT PRIMARY KEY,
    rank           INTEGER NOT NULL,
    forms          TEXT NOT NULL,          -- JSON list
    status         TEXT NOT NULL DEFAULT 'pending',
    prompt_version INTEGER,
    attempts       INTEGER NOT NULL DEFAULT 0,
    ipa            TEXT,
    entry          TEXT,                   -- JSON WordEntry
    problems       TEXT,                   -- JSON list of failed checks
    updated_at     TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Topic words (TASK-61): candidates from WordNet + wordfreq, then judged by Claude Haiku. See topics.py.
-- status: candidate -> keep | drop. Approved words move to the words table later, for the generate pass.
CREATE TABLE IF NOT EXISTS topic_words (
    lemma     TEXT PRIMARY KEY,
    pos       TEXT NOT NULL,              -- n, v or a
    zipf      REAL NOT NULL,              -- wordfreq Zipf frequency
    topic     TEXT NOT NULL,              -- one of topics.Topic
    gloss     TEXT NOT NULL,              -- the WordNet definition the topic came from
    status    TEXT NOT NULL DEFAULT 'candidate',
    reason    TEXT,                       -- the judge's short reason
    judged_at TEXT
);
"""


def connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    connection.executescript(SCHEMA)
    return connection


def add_words(connection: sqlite3.Connection, ngsl_words) -> None:
    connection.executemany(
        "INSERT OR IGNORE INTO words (lemma, rank, forms) VALUES (?, ?, ?)",
        [(w.lemma, w.rank, json.dumps(w.forms)) for w in ngsl_words],
    )
    connection.commit()


def words_to_generate(connection: sqlite3.Connection, limit: int, prompt_version: int, retry_review: bool) -> list[sqlite3.Row]:
    statuses = ("pending", "failed", "needs_review") if retry_review else ("pending", "failed", "")
    return connection.execute(
        """
        SELECT * FROM words
        WHERE status IN (?, ?, ?)
           OR (status = 'done' AND prompt_version < ?)
        ORDER BY rank LIMIT ?
        """,
        (*statuses, prompt_version, limit),
    ).fetchall()


def save_entry(connection, lemma, entry_json):
    """Replaces only the entry JSON (the usage pass). Status, version and attempts stay as they are."""
    connection.execute(
        "UPDATE words SET entry = ?, updated_at = CURRENT_TIMESTAMP WHERE lemma = ?", (entry_json, lemma)
    )
    connection.commit()


def save_result(connection, lemma, *, status, prompt_version, attempts, ipa=None, entry=None, problems=None):
    connection.execute(
        """
        UPDATE words SET status = ?, prompt_version = ?, attempts = attempts + ?, ipa = ?,
               entry = ?, problems = ?, updated_at = CURRENT_TIMESTAMP
        WHERE lemma = ?
        """,
        (status, prompt_version, attempts, ipa, entry, json.dumps(problems or []), lemma),
    )
    connection.commit()
