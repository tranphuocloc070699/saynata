"""Builds the files the apps use: dictionary.sqlite (bundled in the iOS app) and a JSONL copy."""

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

OUT_DIR = Path("data/out")

APP_SCHEMA = """
CREATE TABLE meta  (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL, ipa TEXT);
CREATE TABLE forms (form TEXT NOT NULL, word_id INTEGER NOT NULL REFERENCES words(id));  -- "ran" -> run
CREATE TABLE senses (
    id                INTEGER PRIMARY KEY,
    word_id           INTEGER NOT NULL REFERENCES words(id),
    position          INTEGER NOT NULL,
    pos               TEXT NOT NULL,
    cefr              TEXT NOT NULL,
    simple_definition TEXT NOT NULL,
    vietnamese        TEXT NOT NULL,
    examples          TEXT NOT NULL,  -- JSON list
    usage             TEXT NOT NULL,  -- JSON list of {kind, pattern, example}
    image_type        TEXT NOT NULL,
    image_query       TEXT NOT NULL,
    image_url         TEXT            -- filled by the image step
);
CREATE INDEX idx_words_lemma ON words(lemma COLLATE NOCASE);
CREATE INDEX idx_forms_form  ON forms(form COLLATE NOCASE);
CREATE INDEX idx_senses_word ON senses(word_id);
"""


def export(pipeline_db: sqlite3.Connection) -> tuple[Path, int, int]:
    """Returns (path, words exported, words whose senses have no usage yet)."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    app_path = OUT_DIR / "dictionary.sqlite"
    app_path.unlink(missing_ok=True)
    app_db = sqlite3.connect(app_path)
    app_db.executescript(APP_SCHEMA)

    rows = pipeline_db.execute("SELECT * FROM words WHERE status = 'done' ORDER BY rank").fetchall()
    without_usage = 0
    with (OUT_DIR / "dictionary.jsonl").open("w") as jsonl:
        for row in rows:
            entry = json.loads(row["entry"])
            without_usage += any("usage" not in sense for sense in entry["senses"])
            word_id = app_db.execute(
                "INSERT INTO words (lemma, rank, ipa) VALUES (?, ?, ?)",
                (row["lemma"], row["rank"], row["ipa"]),
            ).lastrowid
            app_db.executemany(
                "INSERT INTO forms (form, word_id) VALUES (?, ?)",
                [(form, word_id) for form in json.loads(row["forms"])],
            )
            for position, sense in enumerate(entry["senses"]):
                app_db.execute(
                    """INSERT INTO senses (word_id, position, pos, cefr, simple_definition, vietnamese,
                       examples, usage, image_type, image_query)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        word_id, position, sense["pos"], sense["cefr"], sense["simple_definition"],
                        sense["vietnamese"], json.dumps(sense["examples"], ensure_ascii=False),
                        json.dumps(sense.get("usage", []), ensure_ascii=False),
                        sense["image_type"], sense["image_query"],
                    ),
                )
            for sense in entry["senses"]:  # old fields from before TASK-24 stay out of the files
                sense.pop("collocations", None)
                sense.pop("synonyms", None)
            jsonl.write(json.dumps({"lemma": row["lemma"], "ipa": row["ipa"], **entry}, ensure_ascii=False) + "\n")

    app_db.execute(
        "INSERT INTO meta (key, value) VALUES ('dictionary_version', ?)",
        (datetime.now(timezone.utc).strftime("%Y%m%d%H%M"),),
    )
    app_db.commit()
    app_db.close()
    return app_path, len(rows), without_usage
