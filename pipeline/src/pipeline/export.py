"""Builds the files the apps use: dictionary.sqlite (bundled in the iOS app), a JSONL copy and the media folder.

export_from_postgres is the real export: the server Postgres is the source of truth (TASK-49.3).
export_from_pipeline_db is the old export from data/pipeline.db, kept for a fresh generate run until the new
words can be imported into Postgres.
"""

import json
import shutil
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import psycopg

from pipeline.examples import needs_examples, old_examples_as_levels
from pipeline.forms import form_type

OUT_DIR = Path("data/out")
MEDIA_URL_PREFIX = "media/"  # senses.image_url is "media/<file name>"
MEDIA_DIR = Path("data/media")  # where the media files are (a copy of the server folder, or the one the media pass fills)

APP_SCHEMA = """
CREATE TABLE meta  (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE words (id INTEGER PRIMARY KEY, lemma TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL, ipa TEXT);
CREATE TABLE forms (
    form    TEXT NOT NULL,
    word_id INTEGER NOT NULL REFERENCES words(id),   -- "ran" -> run
    type    TEXT NOT NULL                            -- "past tense", "plural", ... or "form" when unsure
);
CREATE TABLE senses (
    id                INTEGER PRIMARY KEY,
    word_id           INTEGER NOT NULL REFERENCES words(id),
    position          INTEGER NOT NULL,
    pos               TEXT NOT NULL,
    cefr              TEXT NOT NULL,
    simple_definition TEXT NOT NULL,
    vietnamese        TEXT NOT NULL,
    examples          TEXT NOT NULL,  -- JSON list of {level, text}, easy first
    usage             TEXT NOT NULL,  -- JSON list of {kind, pattern, example}
    image_type        TEXT NOT NULL,
    image_query       TEXT NOT NULL,
    image_url         TEXT            -- filled by the image step
);
CREATE INDEX idx_words_lemma ON words(lemma COLLATE NOCASE);
CREATE INDEX idx_forms_form  ON forms(form COLLATE NOCASE);
CREATE INDEX idx_senses_word ON senses(word_id);
"""


def export_from_pipeline_db(pipeline_db: sqlite3.Connection) -> tuple[Path, int, int, int]:
    """Returns (path, words exported, words without usage yet, words without levelled examples yet)."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    app_path = OUT_DIR / "dictionary.sqlite"
    app_path.unlink(missing_ok=True)
    app_db = sqlite3.connect(app_path)
    app_db.executescript(APP_SCHEMA)

    rows = pipeline_db.execute("SELECT * FROM words WHERE status = 'done' ORDER BY rank").fetchall()
    without_usage = 0
    without_levels = 0
    with (OUT_DIR / "dictionary.jsonl").open("w") as jsonl:
        for row in rows:
            entry = json.loads(row["entry"])
            without_usage += any("usage" not in sense for sense in entry["senses"])
            without_levels += needs_examples(entry)
            sense_pos = [sense["pos"] for sense in entry["senses"]]
            word_id = app_db.execute(
                "INSERT INTO words (lemma, rank, ipa) VALUES (?, ?, ?)",
                (row["lemma"], row["rank"], row["ipa"]),
            ).lastrowid
            app_db.executemany(
                "INSERT INTO forms (form, word_id, type) VALUES (?, ?, ?)",
                [(form, word_id, form_type(row["lemma"], form, sense_pos)) for form in json.loads(row["forms"])],
            )
            for position, sense in enumerate(entry["senses"]):
                media_meta = sense.get("media")
                media_file = media_meta.get("filename") if isinstance(media_meta, dict) else None
                image_url = f"media/{media_file}" if media_file else None
                app_db.execute(
                    """INSERT INTO senses (word_id, position, pos, cefr, simple_definition, vietnamese,
                       examples, usage, image_type, image_query, image_url)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        word_id, position, sense["pos"], sense["cefr"], sense["simple_definition"],
                        sense["vietnamese"], json.dumps(old_examples_as_levels(sense), ensure_ascii=False),
                        json.dumps(sense.get("usage", []), ensure_ascii=False),
                        sense["image_type"], sense["image_query"], image_url,
                    ),
                )
            for sense in entry["senses"]:  # old fields stay out of the files; plain-string examples get levels
                sense.pop("collocations", None)
                sense.pop("synonyms", None)
                sense["examples"] = old_examples_as_levels(sense)
            jsonl.write(json.dumps({"lemma": row["lemma"], "ipa": row["ipa"], **entry}, ensure_ascii=False) + "\n")

    app_db.execute(
        "INSERT INTO meta (key, value) VALUES ('dictionary_version', ?)",
        (datetime.now(timezone.utc).strftime("%Y%m%d%H%M"),),
    )
    app_db.commit()
    app_db.close()
    return app_path, len(rows), without_usage, without_levels


@dataclass
class ExportResult:
    path: Path
    words: int
    senses: int
    forms: int
    media_copied: int
    media_missing: list[str]


def export_from_postgres(pg: psycopg.Connection, media_dir: Path = MEDIA_DIR) -> ExportResult:
    """Builds data/out/dictionary.sqlite, dictionary.jsonl and data/out/media/ from the dictionary tables.

    The file has the same schema and the same JSON text in examples and usage as before, so the app reads it
    unchanged. Words are numbered 1, 2, 3... by NGSL rank. Only reads from Postgres.
    """
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    app_path = OUT_DIR / "dictionary.sqlite"
    app_path.unlink(missing_ok=True)
    app_db = sqlite3.connect(app_path)
    app_db.executescript(APP_SCHEMA)

    with pg.cursor() as cursor:
        cursor.execute("SELECT id, lemma, word_rank, ipa FROM dictionary_word ORDER BY word_rank, id")
        words = cursor.fetchall()
        cursor.execute(
            """SELECT s.word_id, s.pos, s.cefr, s.simple_definition, s.vietnamese, s.examples_json, s.usage_json,
                      s.image_type, s.image_query, m.file_name
               FROM dictionary_sense s LEFT JOIN media m ON m.id = s.media_id
               ORDER BY s.word_id, s.position"""
        )
        senses_of: dict[int, list[tuple]] = {}
        for row in cursor.fetchall():
            senses_of.setdefault(row[0], []).append(row[1:])
        cursor.execute("SELECT word_id, form, form_type FROM dictionary_form ORDER BY id")
        forms_of: dict[int, list[tuple]] = {}
        for word_id, form, kind in cursor.fetchall():
            forms_of.setdefault(word_id, []).append((form, kind))

    sense_count = form_count = 0
    media_files: list[str] = []
    with (OUT_DIR / "dictionary.jsonl").open("w") as jsonl:
        for app_id, (pg_id, lemma, rank, ipa) in enumerate(words, start=1):
            app_db.execute("INSERT INTO words (id, lemma, rank, ipa) VALUES (?, ?, ?, ?)", (app_id, lemma, rank, ipa))
            app_db.executemany(
                "INSERT INTO forms (form, word_id, type) VALUES (?, ?, ?)",
                [(form, app_id, kind) for form, kind in forms_of.get(pg_id, [])],
            )
            form_count += len(forms_of.get(pg_id, []))
            jsonl_senses = []
            for position, sense in enumerate(senses_of.get(pg_id, [])):
                pos, cefr, definition, vietnamese, examples, usage, image_type, image_query, file_name = sense
                # Parse and write again, so the text is always the same format, also after an admin edit.
                examples_json = json.dumps(json.loads(examples), ensure_ascii=False)
                usage_json = json.dumps(json.loads(usage), ensure_ascii=False)
                image_url = MEDIA_URL_PREFIX + file_name if file_name else None
                if file_name:
                    media_files.append(file_name)
                app_db.execute(
                    """INSERT INTO senses (word_id, position, pos, cefr, simple_definition, vietnamese,
                       examples, usage, image_type, image_query, image_url)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (app_id, position, pos, cefr, definition, vietnamese, examples_json, usage_json,
                     image_type, image_query, image_url),
                )
                jsonl_senses.append({
                    "pos": pos, "cefr": cefr, "simple_definition": definition, "vietnamese": vietnamese,
                    "examples": json.loads(examples), "usage": json.loads(usage),
                    "image_type": image_type, "image_query": image_query, "image_url": image_url,
                })
            sense_count += len(jsonl_senses)
            jsonl.write(json.dumps({"lemma": lemma, "ipa": ipa, "senses": jsonl_senses}, ensure_ascii=False) + "\n")

    app_db.execute(
        "INSERT INTO meta (key, value) VALUES ('dictionary_version', ?)",
        (datetime.now(timezone.utc).strftime("%Y%m%d%H%M"),),
    )
    app_db.commit()
    app_db.close()

    copied, missing = copy_media(media_files, media_dir, OUT_DIR / "media")
    return ExportResult(app_path, len(words), sense_count, form_count, copied, missing)


def copy_media(file_names: list[str], media_dir: Path, target_dir: Path) -> tuple[int, list[str]]:
    """Copies the media files the senses use into target_dir (emptied first). Returns (copied, missing names)."""
    shutil.rmtree(target_dir, ignore_errors=True)
    target_dir.mkdir(parents=True)
    copied = 0
    missing = []
    for file_name in sorted(set(file_names)):
        source = media_dir / file_name
        if source.is_file():
            shutil.copy2(source, target_dir / file_name)
            copied += 1
        else:
            missing.append(file_name)
    return copied, missing
