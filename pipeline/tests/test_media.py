"""Unit tests for the media pass (TASK-23).

Tests offline using mock fetchers and temporary directories without calling external APIs.
"""

import json
from pathlib import Path
from PIL import Image
import pytest

from pipeline import media, store


def sample_entry_needs_media() -> dict:
    return {
        "senses": [
            {
                "pos": "noun",
                "image_type": "photo",
                "image_query": "red apple fruit",
                "simple_definition": "a round red fruit",
                "examples": [{"level": "easy", "text": "I eat an apple."}],
                "vietnamese": "quả táo",
                "usage": [],
            },
            {
                "pos": "verb",
                "image_type": "none",
                "image_query": "",
                "simple_definition": "to do something",
                "examples": [{"level": "easy", "text": "I do it."}],
                "vietnamese": "làm",
                "usage": [],
            },
        ]
    }


def sample_entry_done_media() -> dict:
    return {
        "senses": [
            {
                "pos": "noun",
                "image_type": "photo",
                "image_query": "red apple fruit",
                "media": {
                    "type": "photo",
                    "filename": "apple_1.webp",
                    "author": "TestPhotographer",
                    "page_url": "https://pixabay.com/test",
                },
                "simple_definition": "a round red fruit",
                "examples": [{"level": "easy", "text": "I eat an apple."}],
                "vietnamese": "quả táo",
                "usage": [],
            }
        ]
    }


def test_needs_media():
    assert media.needs_media(sample_entry_needs_media()) is True
    assert media.needs_media(sample_entry_done_media()) is False
    assert media.needs_media({"senses": [{"image_type": "none"}]}) is False


def test_sanitize_filename():
    assert media.sanitize_filename("Run") == "run"
    assert media.sanitize_filename("it's") == "it_s"
    assert media.sanitize_filename("apple-tree") == "apple-tree"


def test_resize_and_save_webp(tmp_path: Path):
    target = tmp_path / "test.webp"
    # Create 640x480 test image in memory
    img = Image.new("RGB", (640, 480), color="blue")
    import io
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    raw_bytes = buf.getvalue()

    size = media.resize_and_save_webp(raw_bytes, target, max_width=320)
    assert target.exists()
    assert size > 0

    saved_img = Image.open(target)
    assert saved_img.format == "WEBP"
    assert saved_img.width == 320
    assert saved_img.height == 240


def test_process_word_media_dry_run(tmp_path: Path):
    entry = sample_entry_needs_media()

    def fake_fetcher(lemma: str, sense: dict):
        return {
            "media_type": "photo",
            "download_url": "https://example.com/fake.jpg",
            "author": "MockUser",
            "page_url": "https://example.com/page",
            "id": 12345,
        }

    new_entry, logs = media.process_word_media(
        lemma="apple",
        entry=entry,
        fetcher=fake_fetcher,
        media_dir=tmp_path,
        dry_run=True,
    )

    senses = new_entry["senses"]
    assert len(senses) == 2
    assert senses[0]["media"]["type"] == "photo"
    assert senses[0]["media"]["filename"] == "apple_1.webp"
    assert senses[0]["media"]["author"] == "MockUser"
    assert senses[1]["media"] is None
    # Dry run should not write to disk
    assert not (tmp_path / "apple_1.webp").exists()


@pytest.fixture
def test_db(tmp_path: Path, monkeypatch):
    db_path = tmp_path / "pipeline.db"
    monkeypatch.setattr(store, "DB_PATH", db_path)
    connection = store.connect()
    yield connection
    connection.close()


def test_run_pass_with_mock_fetcher(test_db, tmp_path: Path):
    media_dir = tmp_path / "media"

    # Add a done word to db
    entry = sample_entry_needs_media()
    test_db.execute(
        "INSERT INTO words (lemma, rank, forms, status, prompt_version, attempts, entry) VALUES (?, ?, '[]', 'done', 2, 1, ?)",
        ("apple", 1, json.dumps(entry)),
    )
    test_db.commit()

    def fake_fetcher(lemma: str, sense: dict):
        return {
            "media_type": "photo",
            "download_url": "https://example.com/fake.jpg",
            "author": "MockAuthor",
            "page_url": "https://example.com/apple",
            "id": 999,
        }

    # Run pass in dry run mode
    counts = media.run_pass(
        test_db,
        fetcher=fake_fetcher,
        media_dir=media_dir,
        dry_run=True,
    )
    assert counts["media_found"] == 1
    assert counts["saved"] == 0

    # In dry run, row in DB is not updated, so needs_media is still True
    row = test_db.execute("SELECT entry FROM words WHERE lemma = 'apple'").fetchone()
    loaded = json.loads(row["entry"])
    assert media.needs_media(loaded) is True
