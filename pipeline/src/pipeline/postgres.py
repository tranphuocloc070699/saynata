"""The server Postgres is the source of truth for the bundled dictionary (TASK-49.3).

This file connects to it and does the one-time import of the bundled dictionary.sqlite into the tables
dictionary_word, dictionary_sense, dictionary_form and media. The tables are made by the backend (Flyway,
backend/src/main/resources/db/migration/V6__create_dictionary_tables.sql). The export back to a
dictionary.sqlite is in export.py.
"""

import json
import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import psycopg
from PIL import Image

LICENSE_NOTE = "Pixabay Content License"
MEDIA_PREFIX = "media/"  # senses.image_url in dictionary.sqlite is "media/<file name>"


def database_url(given: str | None = None) -> str:
    """The connection string: the --database-url option, else DATABASE_URL (pipeline/.env)."""
    url = (given or os.environ.get("DATABASE_URL", "")).strip()
    if not url:
        raise RuntimeError(
            "No database. Set DATABASE_URL in pipeline/.env, for example "
            "postgresql://saynata:password@localhost:5432/saynata"
        )
    return url


def connect(url: str, read_only: bool = False) -> psycopg.Connection:
    """read_only=True makes Postgres refuse every write on this connection (the export uses it)."""
    connection = psycopg.connect(url)
    if read_only:
        connection.read_only = True
    return connection


@dataclass
class ImportResult:
    words_written: int = 0  # added, or replaced with overwrite
    words_skipped: int = 0  # the lemma was already in Postgres and --overwrite was not used
    senses: int = 0
    forms: int = 0
    media: int = 0


def media_kind(file_name: str) -> str:
    return "clip" if file_name.lower().endswith(".mp4") else "photo"


def read_media_metadata(pipeline_db: sqlite3.Connection | None) -> tuple[dict[str, dict], dict[str, int]]:
    """Pixabay attribution and prompt versions saved by the media pass in data/pipeline.db.

    Returns ({file name: {author, page_url}}, {lemma: prompt version}). Both are empty without pipeline.db.
    """
    attribution: dict[str, dict] = {}
    versions: dict[str, int] = {}
    if pipeline_db is None:
        return attribution, versions
    for row in pipeline_db.execute("SELECT lemma, prompt_version, entry FROM words WHERE entry IS NOT NULL"):
        if row[1] is not None:
            versions[row[0]] = row[1]
        for sense in json.loads(row[2]).get("senses", []):
            media = sense.get("media")
            if isinstance(media, dict) and media.get("filename"):
                attribution[media["filename"]] = media
    return attribution, versions


def file_facts(media_dir: Path | None, file_name: str) -> tuple[int | None, int | None, int | None]:
    """(width, height, bytes) from the file on disk. None for what we cannot know (no file, or a video)."""
    path = media_dir / file_name if media_dir else None
    if path is None or not path.is_file():
        return None, None, None
    size = path.stat().st_size
    if media_kind(file_name) == "clip":
        return None, None, size
    try:
        with Image.open(path) as image:
            return image.width, image.height, size
    except OSError:
        return None, None, size


def import_dictionary(
    app_db: sqlite3.Connection,
    pg: psycopg.Connection,
    media_dir: Path | None = None,
    pipeline_db: sqlite3.Connection | None = None,
    overwrite: bool = False,
) -> ImportResult:
    """Copies words, senses, forms and media rows from dictionary.sqlite into Postgres, in one transaction.

    Safe to run again: a word whose lemma is already in Postgres is skipped, so edits made in the admin
    are not lost. With overwrite=True such a word is replaced by the one in the file instead.
    """
    result = ImportResult()
    now = datetime.now(timezone.utc)
    attribution, versions = read_media_metadata(pipeline_db)
    app_db.row_factory = sqlite3.Row

    with pg.transaction(), pg.cursor() as cursor:
        # 1. Media rows (the files stay on disk).
        file_names = [
            row[0].removeprefix(MEDIA_PREFIX)
            for row in app_db.execute("SELECT DISTINCT image_url FROM senses WHERE image_url IS NOT NULL ORDER BY 1")
        ]
        on_conflict = (
            """ON CONFLICT (file_name) DO UPDATE SET kind = EXCLUDED.kind,
               width = COALESCE(EXCLUDED.width, media.width), height = COALESCE(EXCLUDED.height, media.height),
               bytes = COALESCE(EXCLUDED.bytes, media.bytes),
               photographer = COALESCE(EXCLUDED.photographer, media.photographer),
               page_url = COALESCE(EXCLUDED.page_url, media.page_url), license_note = EXCLUDED.license_note"""
            if overwrite
            else "ON CONFLICT (file_name) DO NOTHING"
        )
        for file_name in file_names:
            width, height, size = file_facts(media_dir, file_name)
            meta = attribution.get(file_name, {})
            cursor.execute(
                f"""INSERT INTO media (file_name, kind, width, height, bytes, photographer, page_url, license_note,
                    created_at) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) {on_conflict}""",
                (file_name, media_kind(file_name), width, height, size if size is not None else meta.get("size_bytes"),
                 meta.get("author"), meta.get("page_url"), LICENSE_NOTE, now),
            )
        cursor.execute("SELECT file_name, id FROM media")
        media_ids = dict(cursor.fetchall())
        result.media = len(file_names)

        # 2. Words: new ones are inserted; a known lemma is skipped, or replaced with overwrite.
        cursor.execute("SELECT lemma, id FROM dictionary_word")
        known = dict(cursor.fetchall())
        words = app_db.execute("SELECT id, lemma, rank, ipa FROM words ORDER BY id").fetchall()
        imported = {}  # sqlite word id -> postgres word id, only for the words we write
        for word in words:
            version = versions.get(word["lemma"])
            prompt_version = str(version) if version is not None else None
            if word["lemma"] in known and not overwrite:
                result.words_skipped += 1
                continue
            if word["lemma"] in known:
                pg_id = known[word["lemma"]]
                cursor.execute(
                    """UPDATE dictionary_word SET word_rank = %s, ipa = %s, prompt_version = %s, updated_at = %s
                       WHERE id = %s""",
                    (word["rank"], word["ipa"], prompt_version, now, pg_id),
                )
                cursor.execute("DELETE FROM dictionary_sense WHERE word_id = %s", (pg_id,))
                cursor.execute("DELETE FROM dictionary_form WHERE word_id = %s", (pg_id,))
            else:
                cursor.execute(
                    """INSERT INTO dictionary_word (lemma, word_rank, ipa, prompt_version, created_at, updated_at)
                       VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                    (word["lemma"], word["rank"], word["ipa"], prompt_version, now, now),
                )
                pg_id = cursor.fetchone()[0]
            imported[word["id"]] = pg_id
            result.words_written += 1

        # 3. Senses and forms of the words we wrote.
        sense_rows = []
        for sense in app_db.execute("SELECT * FROM senses ORDER BY word_id, position"):
            if sense["word_id"] not in imported:
                continue
            file_name = sense["image_url"].removeprefix(MEDIA_PREFIX) if sense["image_url"] else None
            sense_rows.append((
                imported[sense["word_id"]], sense["position"], sense["pos"], sense["cefr"],
                sense["simple_definition"], sense["vietnamese"], sense["examples"], sense["usage"],
                sense["image_type"], sense["image_query"], media_ids[file_name] if file_name else None,
            ))
        cursor.executemany(
            """INSERT INTO dictionary_sense (word_id, position, pos, cefr, simple_definition, vietnamese,
               examples_json, usage_json, image_type, image_query, media_id)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
            sense_rows,
        )
        result.senses = len(sense_rows)

        form_rows = [
            (imported[form["word_id"]], form["form"], form["type"])
            for form in app_db.execute("SELECT rowid, * FROM forms ORDER BY rowid")
            if form["word_id"] in imported
        ]
        cursor.executemany(
            "INSERT INTO dictionary_form (word_id, form, form_type) VALUES (%s, %s, %s)", form_rows
        )
        result.forms = len(form_rows)
    return result


def link_media(
    app_db: sqlite3.Connection,
    pg: psycopg.Connection,
    media_dir: Path | None = None,
    pipeline_db: sqlite3.Connection | None = None,
) -> int:
    """Gives senses in Postgres the media of a newer dictionary.sqlite (for example after the media pass).

    import_dictionary skips words that are already in Postgres, so it cannot bring in media for them, and
    --overwrite would replace edits made in the admin. This only touches the media: it adds the media rows
    and sets media_id on a sense that has none. A sense that already has media is left alone, and no text changes.
    Returns how many senses were linked.
    """
    attribution, _ = read_media_metadata(pipeline_db)
    app_db.row_factory = sqlite3.Row
    now = datetime.now(timezone.utc)
    linked = 0
    with pg.transaction(), pg.cursor() as cursor:
        rows = app_db.execute(
            """SELECT w.lemma, s.position, s.image_url FROM senses s JOIN words w ON w.id = s.word_id
               WHERE s.image_url IS NOT NULL ORDER BY w.id, s.position"""
        ).fetchall()
        for row in rows:
            file_name = row["image_url"].removeprefix(MEDIA_PREFIX)
            width, height, size = file_facts(media_dir, file_name)
            meta = attribution.get(file_name, {})
            cursor.execute(
                """INSERT INTO media (file_name, kind, width, height, bytes, photographer, page_url, license_note,
                   created_at) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                   ON CONFLICT (file_name) DO UPDATE SET
                   width = COALESCE(EXCLUDED.width, media.width), height = COALESCE(EXCLUDED.height, media.height),
                   bytes = COALESCE(EXCLUDED.bytes, media.bytes),
                   photographer = COALESCE(EXCLUDED.photographer, media.photographer),
                   page_url = COALESCE(EXCLUDED.page_url, media.page_url)
                   RETURNING id""",
                (file_name, media_kind(file_name), width, height, size if size is not None else meta.get("size_bytes"),
                 meta.get("author"), meta.get("page_url"), LICENSE_NOTE, now),
            )
            media_id = cursor.fetchone()[0]
            cursor.execute(
                """UPDATE dictionary_sense s SET media_id = %s FROM dictionary_word w
                   WHERE s.word_id = w.id AND w.lemma = %s AND s.position = %s AND s.media_id IS NULL""",
                (media_id, row["lemma"], row["position"]),
            )
            linked += cursor.rowcount
    return linked
