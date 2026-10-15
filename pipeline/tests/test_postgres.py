"""Import of dictionary.sqlite into Postgres, and the export back (TASK-49.3).

These tests need a Postgres: see conftest.py (TEST_DATABASE_URL).
"""

import json
import sqlite3
from pathlib import Path

import psycopg
import pytest
from PIL import Image

from pipeline import export, postgres, store

BUNDLED_FILE = Path(__file__).resolve().parents[2] / "EnglishTranslation/EnglishTranslation/Resources/dictionary.sqlite"

EXAMPLES = json.dumps(
    [{"level": "easy", "text": "I run."}, {"level": "medium", "text": "She runs."},
     {"level": "harder", "text": "He ran to the bus."}], ensure_ascii=False)
USAGE = json.dumps([{"kind": "phrase", "pattern": "run away", "example": "The dog ran away."}], ensure_ascii=False)


def make_app_db(path: Path) -> sqlite3.Connection:
    """A small dictionary.sqlite with the real schema: 3 words, 4 senses, 3 forms, 2 media files."""
    app_db = sqlite3.connect(path)
    app_db.executescript(export.APP_SCHEMA)
    app_db.executemany("INSERT INTO words (id, lemma, rank, ipa) VALUES (?, ?, ?, ?)",
                       [(1, "the", 1, "ðə"), (2, "run", 7, "rʌn"), (3, "apple", 900, None)])
    app_db.executemany("INSERT INTO forms (form, word_id, type) VALUES (?, ?, ?)",
                       [("ran", 2, "past tense"), ("running", 2, "-ing form"), ("apples", 3, "plural")])
    sense = ("INSERT INTO senses (word_id, position, pos, cefr, simple_definition, vietnamese, examples, usage,"
             " image_type, image_query, image_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    app_db.executemany(sense, [
        (1, 0, "article", "A1", "a word before a noun", "cái", EXAMPLES, "[]", "none", "", None),
        (2, 0, "verb", "A1", "to move fast on your feet", "chạy", EXAMPLES, USAGE, "photo", "person running",
         "media/run_1.mp4"),
        (2, 1, "noun", "B1", "a fast walk for exercise", "cuộc chạy", EXAMPLES, "[]", "none", "", None),
        (3, 0, "noun", "A1", "a round red or green fruit", "quả táo", EXAMPLES, USAGE, "photo", "red apple",
         "media/apple_1.webp"),
    ])
    app_db.execute("INSERT INTO meta (key, value) VALUES ('dictionary_version', '202601010000')")
    app_db.commit()
    return app_db


@pytest.fixture
def media_dir(tmp_path) -> Path:
    folder = tmp_path / "media"
    folder.mkdir()
    Image.new("RGB", (320, 213)).save(folder / "apple_1.webp", "WEBP")
    (folder / "run_1.mp4").write_bytes(b"not a real video, only bytes")
    return folder


@pytest.fixture
def app_db(tmp_path) -> sqlite3.Connection:
    return make_app_db(tmp_path / "dictionary.sqlite")


def count(connection, table: str) -> int:
    return connection.execute(f"SELECT count(*) FROM {table}").fetchone()[0]


def read_app_db(path: Path) -> dict:
    """Everything the app reads from a dictionary.sqlite, except meta (it holds the build time)."""
    connection = sqlite3.connect(path)
    return {
        "schema": connection.execute(
            "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name").fetchall(),
        "words": connection.execute("SELECT * FROM words ORDER BY id").fetchall(),
        "forms": connection.execute("SELECT form, word_id, type FROM forms ORDER BY rowid").fetchall(),
        "senses": connection.execute("SELECT * FROM senses ORDER BY id").fetchall(),
        "meta_keys": connection.execute("SELECT key FROM meta ORDER BY key").fetchall(),
    }


@pytest.fixture
def out_dir(tmp_path, monkeypatch) -> Path:
    monkeypatch.setattr(export, "OUT_DIR", tmp_path / "out")
    return tmp_path / "out"


# --- import ---

def test_import_counts_match_the_file(app_db, pg, media_dir):
    result = postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    assert (count(pg, "dictionary_word"), count(pg, "dictionary_sense"), count(pg, "dictionary_form")) == (
        count(app_db, "words"), count(app_db, "senses"), count(app_db, "forms")) == (3, 4, 3)
    assert count(pg, "media") == 2
    assert (result.words_written, result.words_skipped, result.senses, result.forms, result.media) == (3, 0, 4, 3, 2)


def test_import_fills_the_columns_and_media_facts(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    assert pg.execute("SELECT word_rank, ipa FROM dictionary_word WHERE lemma = 'run'").fetchone() == (7, "rʌn")
    assert pg.execute("SELECT ipa FROM dictionary_word WHERE lemma = 'apple'").fetchone() == (None,)
    sense = pg.execute(
        """SELECT s.cefr, s.vietnamese, s.examples_json, s.usage_json, s.image_type, s.image_query, m.file_name
           FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id LEFT JOIN media m ON m.id = s.media_id
           WHERE w.lemma = 'run' AND s.position = 0""").fetchone()
    assert sense == ("A1", "chạy", EXAMPLES, USAGE, "photo", "person running", "run_1.mp4")
    assert pg.execute("SELECT form, form_type FROM dictionary_form ORDER BY id").fetchall() == [
        ("ran", "past tense"), ("running", "-ing form"), ("apples", "plural")]
    # A photo: size from the file. A clip: no width or height.
    assert pg.execute("SELECT kind, width, height, bytes, license_note FROM media WHERE file_name = 'apple_1.webp'"
                      ).fetchone() == ("photo", 320, 213, (media_dir / "apple_1.webp").stat().st_size,
                                       "Pixabay Content License")
    assert pg.execute("SELECT kind, width, height, bytes FROM media WHERE file_name = 'run_1.mp4'").fetchone() == (
        "clip", None, None, len(b"not a real video, only bytes"))


def test_import_reads_pixabay_attribution_from_pipeline_db(app_db, pg, media_dir):
    pipeline_db = sqlite3.connect(":memory:")
    pipeline_db.executescript(store.SCHEMA)
    entry = {"senses": [{"media": {"type": "photo", "filename": "apple_1.webp", "author": "Jane Photo",
                                    "page_url": "https://pixabay.com/photos/apple-1/", "size_bytes": 999}}]}
    pipeline_db.execute("INSERT INTO words (lemma, rank, forms, status, prompt_version, entry) VALUES (?, ?, ?, ?, ?, ?)",
                        ("apple", 900, "[]", "done", 7, json.dumps(entry)))

    postgres.import_dictionary(app_db, pg, media_dir=media_dir, pipeline_db=pipeline_db)

    assert pg.execute("SELECT photographer, page_url FROM media WHERE file_name = 'apple_1.webp'").fetchone() == (
        "Jane Photo", "https://pixabay.com/photos/apple-1/")
    assert pg.execute("SELECT photographer FROM media WHERE file_name = 'run_1.mp4'").fetchone() == (None,)
    assert pg.execute("SELECT prompt_version FROM dictionary_word WHERE lemma = 'apple'").fetchone() == ("7",)


def test_import_without_media_files_still_imports_the_rows(app_db, pg, tmp_path):
    postgres.import_dictionary(app_db, pg, media_dir=tmp_path / "no-such-folder")

    assert pg.execute("SELECT kind, width, bytes FROM media WHERE file_name = 'apple_1.webp'").fetchone() == (
        "photo", None, None)


def test_import_twice_changes_nothing(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    ids = pg.execute("SELECT id, lemma FROM dictionary_word ORDER BY id").fetchall()

    again = postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    assert (again.words_written, again.words_skipped, again.senses, again.forms) == (0, 3, 0, 0)
    assert (count(pg, "dictionary_word"), count(pg, "dictionary_sense"), count(pg, "dictionary_form"),
            count(pg, "media")) == (3, 4, 3, 2)
    assert pg.execute("SELECT id, lemma FROM dictionary_word ORDER BY id").fetchall() == ids


def test_import_again_keeps_admin_edits_but_overwrite_replaces_them(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    pg.execute("UPDATE dictionary_sense SET simple_definition = 'fixed by the admin' WHERE position = 0 AND cefr = 'A1'"
               " AND word_id = (SELECT id FROM dictionary_word WHERE lemma = 'run')")
    pg.commit()

    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    assert pg.execute("SELECT simple_definition FROM dictionary_sense WHERE vietnamese = 'chạy'").fetchone() == (
        "fixed by the admin",)

    overwritten = postgres.import_dictionary(app_db, pg, media_dir=media_dir, overwrite=True)
    assert overwritten.words_written == 3
    assert pg.execute("SELECT simple_definition FROM dictionary_sense WHERE vietnamese = 'chạy'").fetchone() == (
        "to move fast on your feet",)
    assert (count(pg, "dictionary_word"), count(pg, "dictionary_sense"), count(pg, "dictionary_form"),
            count(pg, "media")) == (3, 4, 3, 2)


def test_link_media_adds_media_to_known_words_without_touching_text(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg)   # words are in, but this import had no media facts yet
    pg.execute("UPDATE dictionary_sense SET media_id = NULL")
    pg.execute("UPDATE dictionary_sense SET simple_definition = 'edited in the admin' WHERE word_id IN "
               "(SELECT id FROM dictionary_word WHERE lemma = 'run')")
    pg.commit()

    linked = postgres.link_media(app_db, pg, media_dir=media_dir)

    assert linked == 2
    rows = pg.execute("""SELECT w.lemma, s.position, m.file_name, m.kind, m.bytes, s.simple_definition
                         FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id
                         LEFT JOIN media m ON m.id = s.media_id ORDER BY w.lemma, s.position""").fetchall()
    by_sense = {(r[0], r[1]): r for r in rows}
    assert by_sense[("run", 0)][2:5] == ("run_1.mp4", "clip", len(b"not a real video, only bytes"))
    assert by_sense[("apple", 0)][2] == "apple_1.webp"
    assert by_sense[("run", 1)][2] is None        # this sense has no image_url
    assert by_sense[("run", 0)][5] == "edited in the admin"
    # running it again changes nothing
    assert postgres.link_media(app_db, pg, media_dir=media_dir) == 0


def test_link_media_does_not_replace_media_a_sense_already_has(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    pg.execute("INSERT INTO media (file_name, kind, created_at) VALUES ('other.webp', 'photo', now())")
    pg.execute("UPDATE dictionary_sense SET media_id = (SELECT id FROM media WHERE file_name = 'other.webp') "
               "WHERE word_id = (SELECT id FROM dictionary_word WHERE lemma = 'apple')")
    pg.commit()

    assert postgres.link_media(app_db, pg, media_dir=media_dir) == 0
    kept = pg.execute("""SELECT m.file_name FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id
                         JOIN media m ON m.id = s.media_id WHERE w.lemma = 'apple'""").fetchone()
    assert kept[0] == "other.webp"


def test_link_media_skips_senses_the_admin_removed_media_from(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg)
    pg.execute("UPDATE dictionary_sense SET media_id = NULL")
    pg.execute("DELETE FROM media")
    # run: "No image" in the admin (image_type none). apple: "Remove all media" (media_status removed).
    pg.execute("UPDATE dictionary_sense SET image_type = 'none' WHERE word_id = "
               "(SELECT id FROM dictionary_word WHERE lemma = 'run')")
    pg.execute("UPDATE dictionary_sense SET media_status = 'removed' WHERE word_id = "
               "(SELECT id FROM dictionary_word WHERE lemma = 'apple')")
    pg.commit()

    assert postgres.link_media(app_db, pg, media_dir=media_dir) == 0
    assert count(pg, "media") == 0   # no media row is made for a removed sense either
    assert pg.execute("SELECT count(*) FROM dictionary_sense WHERE media_id IS NOT NULL").fetchone()[0] == 0


def test_link_media_still_links_a_sense_that_is_not_removed(app_db, pg, media_dir):
    postgres.import_dictionary(app_db, pg)
    pg.execute("UPDATE dictionary_sense SET media_id = NULL")
    pg.execute("UPDATE dictionary_sense SET media_status = 'removed' WHERE word_id = "
               "(SELECT id FROM dictionary_word WHERE lemma = 'apple')")
    pg.commit()

    assert postgres.link_media(app_db, pg, media_dir=media_dir) == 1   # only run
    assert pg.execute("SELECT count(*) FROM media WHERE file_name = 'run_1.mp4'").fetchone()[0] == 1


def test_a_failed_import_changes_nothing(app_db, pg, media_dir):
    # A level that is too long for the column: Postgres refuses it after the words and media were written.
    app_db.execute("UPDATE senses SET cefr = 'MUCH-TOO-LONG' WHERE id = 4")
    app_db.commit()

    with pytest.raises(psycopg.errors.StringDataRightTruncation):
        postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    assert (count(pg, "dictionary_word"), count(pg, "dictionary_sense"), count(pg, "media")) == (0, 0, 0)


# --- export ---

def test_round_trip_gives_the_same_file(app_db, pg, media_dir, out_dir, tmp_path):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    result = export.export_from_postgres(pg, media_dir=media_dir)

    assert read_app_db(result.path) == read_app_db(tmp_path / "dictionary.sqlite")
    assert (result.words, result.senses, result.forms) == (3, 4, 3)
    assert (result.media_copied, result.media_missing) == (2, [])
    assert sorted(f.name for f in (out_dir / "media").iterdir()) == ["apple_1.webp", "run_1.mp4"]
    assert sqlite3.connect(result.path).execute("SELECT value FROM meta WHERE key = 'dictionary_version'"
                                                ).fetchone()[0].isdigit()
    first_line = json.loads((out_dir / "dictionary.jsonl").read_text().splitlines()[0])
    assert first_line["lemma"] == "the" and first_line["senses"][0]["simple_definition"] == "a word before a noun"


def test_export_numbers_words_by_rank_and_keeps_sense_order(app_db, pg, media_dir, out_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    pg.execute("UPDATE dictionary_word SET word_rank = 5 WHERE lemma = 'apple'")  # now before "run" (7)
    pg.commit()

    path = export.export_from_postgres(pg, media_dir=media_dir).path

    exported = sqlite3.connect(path)
    assert exported.execute("SELECT id, lemma FROM words ORDER BY id").fetchall() == [(1, "the"), (2, "apple"), (3, "run")]
    assert exported.execute("SELECT position, pos FROM senses WHERE word_id = 3 ORDER BY id").fetchall() == [
        (0, "verb"), (1, "noun")]
    assert exported.execute("SELECT word_id FROM forms WHERE form = 'ran'").fetchone() == (3,)


def test_an_edit_in_postgres_is_in_the_next_export(app_db, pg, media_dir, out_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    # What the admin does: the backend writes compact JSON (no spaces) and keeps the picture fields.
    pg.execute("""UPDATE dictionary_sense SET simple_definition = 'to go fast on foot',
                  examples_json = '[{"level":"easy","text":"I run to school."}]'
                  WHERE image_query = 'person running'""")
    pg.commit()

    path = export.export_from_postgres(pg, media_dir=media_dir).path

    row = sqlite3.connect(path).execute(
        "SELECT simple_definition, examples, image_type, image_url FROM senses WHERE image_query = 'person running'"
    ).fetchone()
    assert row == ("to go fast on foot", json.dumps([{"level": "easy", "text": "I run to school."}]), "photo",
                   "media/run_1.mp4")


def test_export_lists_media_files_it_cannot_find(app_db, pg, media_dir, out_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    (media_dir / "run_1.mp4").unlink()

    result = export.export_from_postgres(pg, media_dir=media_dir)

    assert (result.media_copied, result.media_missing) == (1, ["run_1.mp4"])
    # The row still points to the file: the app shows no picture if the file is not in the app.
    assert sqlite3.connect(result.path).execute("SELECT image_url FROM senses WHERE image_query = 'person running'"
                                                ).fetchone() == ("media/run_1.mp4",)


def test_export_writes_no_image_url_for_removed_senses(app_db, pg, media_dir, out_dir):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)
    # Even if a media link is left behind, "none" and "removed" win, so the next app build drops the picture.
    pg.execute("UPDATE dictionary_sense SET image_type = 'none' WHERE word_id = "
               "(SELECT id FROM dictionary_word WHERE lemma = 'run')")
    pg.execute("UPDATE dictionary_sense SET media_status = 'removed' WHERE word_id = "
               "(SELECT id FROM dictionary_word WHERE lemma = 'apple')")
    pg.commit()

    result = export.export_from_postgres(pg, media_dir=media_dir)

    assert sqlite3.connect(result.path).execute("SELECT count(*) FROM senses WHERE image_url IS NOT NULL").fetchone() == (0,)
    assert (result.media_copied, result.media_missing) == (0, [])


def test_export_works_on_a_read_only_connection(app_db, pg, media_dir, out_dir, database_url):
    postgres.import_dictionary(app_db, pg, media_dir=media_dir)

    with postgres.connect(database_url, read_only=True) as read_only:
        result = export.export_from_postgres(read_only, media_dir=media_dir)
        assert result.words == 3
        with pytest.raises(psycopg.errors.ReadOnlySqlTransaction):
            read_only.execute("DELETE FROM dictionary_word")


# --- small helpers ---

def test_media_kind_comes_from_the_file_extension():
    assert postgres.media_kind("run_1.mp4") == "clip"
    assert postgres.media_kind("RUN_1.MP4") == "clip"
    assert postgres.media_kind("apple_1.webp") == "photo"


def test_database_url_comes_from_the_option_or_the_environment(monkeypatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError, match="DATABASE_URL"):
        postgres.database_url()
    monkeypatch.setenv("DATABASE_URL", "postgresql://from-env/db")
    assert postgres.database_url() == "postgresql://from-env/db"
    assert postgres.database_url("postgresql://from-option/db") == "postgresql://from-option/db"


# --- the real bundled file ---

@pytest.mark.skipif(not BUNDLED_FILE.is_file(), reason="the bundled dictionary.sqlite is not in this checkout")
def test_full_bundled_dictionary_round_trip(pg, out_dir):
    """Import the whole bundled file, export it, and get the same words, senses and forms back."""
    original = sqlite3.connect(BUNDLED_FILE)

    result = postgres.import_dictionary(original, pg, media_dir=BUNDLED_FILE.parent / "media")
    exported = export.export_from_postgres(pg, media_dir=BUNDLED_FILE.parent / "media")

    for table, pg_table in [("words", "dictionary_word"), ("senses", "dictionary_sense"), ("forms", "dictionary_form")]:
        assert count(pg, pg_table) == count(original, table)
    assert (result.words_written, result.senses, result.forms) == (2750, 7779, 5542)
    assert read_app_db(exported.path) == read_app_db(BUNDLED_FILE)
