"""Offline tests for the usage pass. The LLM is a fake function, so nothing is paid."""

import json

import pytest

from pipeline import export, store, usage
from pipeline.models import Usage, UsageKind, WordEntry
from pipeline.usage import SenseUsage, UsageResult
from pipeline.validate import find_problems, usage_problems

VOCABULARY = {
    "i", "got", "need", "to", "get", "some", "milk", "he", "a", "new", "job", "last", "month", "it", "is", "dark",
    "outside", "hold", "of", "two", "tickets", "finally", "can", "make", "my", "mind", "you", "the", "go",
    "up", "early", "will", "do", "have", "buy", "become", "she", "say", "now", "are", "coming", "depend",
    "weather", "on", "something", "someone", "let", "work", "a", "lot", "today", "take", "easy", "have",
}


def item(kind, pattern, example) -> Usage:
    return Usage(kind=UsageKind(kind), pattern=pattern, example=example)


def old_entry(definition="to buy or find something") -> dict:
    """An entry as it sits in pipeline.db before the usage pass: collocations and synonyms, no usage."""
    return {"senses": [{
        "source_index": 0, "pos": "verb", "cefr": "A1", "simple_definition": definition,
        "examples": ["I need to get some milk.", "He got a new job."], "vietnamese": "lấy",
        "collocations": ["get a job"], "synonyms": ["obtain"], "image_type": "none", "image_query": "",
    }]}


@pytest.fixture
def db(tmp_path, monkeypatch):
    monkeypatch.setattr(store, "DB_PATH", tmp_path / "pipeline.db")
    connection = store.connect()
    yield connection
    connection.close()


def add_done_word(db, lemma, rank, entry):
    db.execute(
        "INSERT INTO words (lemma, rank, forms, status, prompt_version, attempts, entry) VALUES (?, ?, '[]', 'done', 2, 1, ?)",
        (lemma, rank, json.dumps(entry)),
    )
    db.commit()


def fake_fetch(calls: list):
    good = UsageResult(senses=[SenseUsage(sense_number=1, usage=[
        item("pattern", "get + noun", "I need to get some milk."),
        item("collocation", "get a job", "He got a new job last month."),
        item("idiom", "get hold of something", "I finally got hold of two tickets."),
    ])])

    def fetch(lemma, entry):
        calls.append(lemma)
        return good

    return fetch


# --- checks ---

def test_good_item_passes():
    assert usage_problems(item("collocation", "get a job", "He got a new job last month."), VOCABULARY) == []


def test_contractions_count_as_their_base_word():
    assert usage_problems(item("phrase", "make up your mind", "I can't make up my mind."), VOCABULARY) == []
    assert usage_problems(item("idiom", "take it easy", "Take it easy. You've worked a lot today."), VOCABULARY | {"worked"}) == []


def test_hard_word_in_example_is_reported():
    problems = usage_problems(item("pattern", "get + noun", "I need to get a laptop."), VOCABULARY)
    assert problems == ["example 'I need to get a laptop.' uses hard words ['laptop']"]


def test_sb_sth_placeholders_are_reported():
    problems = usage_problems(item("pattern", "depend on sth", "It depends on something."), VOCABULARY | {"depends"})
    assert len(problems) == 1 and "sb/sth" in problems[0]


def test_find_problems_checks_usage_of_new_entries():
    entry = WordEntry.model_validate({"senses": [{
        "source_index": 0, "pos": "verb", "cefr": "A1", "simple_definition": "to buy something",
        "examples": ["One.", "Two."], "vietnamese": "lấy", "image_type": "none", "image_query": "",
        "usage": [{"kind": "pattern", "pattern": "get + noun", "example": "I need a laptop."}],
    }]})
    problems = find_problems("get", entry, VOCABULARY)
    assert problems == ["sense 1: usage: example 'I need a laptop.' uses hard words ['laptop']"]


def test_old_stored_entry_loads_with_empty_usage():
    entry = WordEntry.from_stored(json.dumps(old_entry()))
    assert entry.senses[0].usage == []


# --- the pass ---

def test_pass_adds_usage_and_keeps_definitions(db):
    add_done_word(db, "get", 1, old_entry())
    calls: list = []

    counts = usage.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)

    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    sense = saved["senses"][0]
    assert counts == {"saved": 1, "failed": 0, "items": 3, "dropped": 0}
    assert [u["kind"] for u in sense["usage"]] == ["pattern", "collocation", "idiom"]
    # Definition, examples, Vietnamese stay exactly as they were. Old fields are gone.
    original = old_entry()["senses"][0]
    for key in ("simple_definition", "examples", "vietnamese", "pos", "cefr", "source_index"):
        assert sense[key] == original[key]
    assert "collocations" not in sense and "synonyms" not in sense
    row = db.execute("SELECT status, prompt_version, attempts FROM words WHERE lemma = 'get'").fetchone()
    assert (row["status"], row["prompt_version"], row["attempts"]) == ("done", 2, 1)


def test_second_run_makes_no_calls(db):
    add_done_word(db, "get", 1, old_entry())
    calls: list = []
    usage.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    usage.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    assert calls == ["get"]


def test_a_failed_call_is_not_retried_and_leaves_the_row_alone(db):
    add_done_word(db, "get", 1, old_entry())
    attempts: list = []

    def broken(lemma, entry):
        attempts.append(lemma)
        raise RuntimeError("network down")

    counts = usage.run_pass(db, VOCABULARY, broken, limit=10, workers=1)

    assert attempts == ["get"]  # one call, no retry
    assert counts["failed"] == 1 and counts["saved"] == 0
    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    assert saved == old_entry()
    # Resume: the next run tries this word again.
    calls: list = []
    usage.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    assert calls == ["get"]


def test_bad_items_are_dropped_and_the_rest_kept(db):
    add_done_word(db, "get", 1, old_entry())
    result = UsageResult(senses=[SenseUsage(sense_number=1, usage=[
        item("pattern", "get + noun", "I need to get some milk."),
        item("collocation", "get a laptop", "I need a laptop."),  # hard word
        item("pattern", "Get + Noun", "I need to get some milk."),  # same pattern again
        item("phrase", "get up", "I get up early."),
    ])])

    counts = usage.run_pass(db, VOCABULARY, lambda lemma, entry: result, limit=10, workers=1)

    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    assert [u["pattern"] for u in saved["senses"][0]["usage"]] == ["get + noun", "get up"]
    assert counts["dropped"] == 2


def test_at_most_three_items_per_sense():
    many = [item("pattern", f"get {word}", "I need to get some milk.") for word in ["up", "out", "in", "off", "away"]]
    kept, dropped = usage.clean_usage(many, VOCABULARY)
    assert len(kept) == 3 and len(dropped) == 2


def test_sense_with_no_good_items_gets_an_empty_list(db):
    add_done_word(db, "get", 1, old_entry())
    result = UsageResult(senses=[])  # the model returned nothing for the sense

    usage.run_pass(db, VOCABULARY, lambda lemma, entry: result, limit=10, workers=1)

    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    assert saved["senses"][0]["usage"] == []
    assert not usage.needs_usage(saved)  # finished: it will not be asked again


def test_dry_run_and_words_filter_save_nothing(db):
    add_done_word(db, "get", 1, old_entry())
    add_done_word(db, "make", 2, old_entry())
    calls: list = []

    usage.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1, words=["make"], dry_run=True)

    assert calls == ["make"]
    for lemma in ("get", "make"):
        saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = ?", (lemma,)).fetchone()["entry"])
        assert usage.needs_usage(saved)


def test_prompt_lists_senses_and_the_agreed_examples():
    from pipeline.generate import SYSTEM_PROMPT

    prompt = usage.build_usage_prompt("get", old_entry())
    assert "Headword: get" in prompt and "to buy or find something" in prompt
    assert "get hold of something" in SYSTEM_PROMPT and "make up your mind" in SYSTEM_PROMPT
    assert "collocations" not in SYSTEM_PROMPT and "synonyms" not in SYSTEM_PROMPT


# --- export ---

def test_export_stores_usage_as_json_and_counts_missing(db, tmp_path, monkeypatch):
    monkeypatch.setattr(export, "OUT_DIR", tmp_path / "out")
    add_done_word(db, "get", 1, old_entry())
    add_done_word(db, "make", 2, old_entry())
    usage.run_pass(db, VOCABULARY, fake_fetch([]), limit=10, workers=1, words=["get"])

    path, count, without_usage = export.export(db)

    import sqlite3

    app = sqlite3.connect(path)
    columns = [c[1] for c in app.execute("PRAGMA table_info(senses)")]
    assert "usage" in columns and "collocations" not in columns and "synonyms" not in columns
    stored = json.loads(app.execute("SELECT usage FROM senses s JOIN words w ON w.id = s.word_id WHERE w.lemma = 'get'").fetchone()[0])
    assert stored[0] == {"kind": "pattern", "pattern": "get + noun", "example": "I need to get some milk."}
    assert (count, without_usage) == (2, 1)


def test_depend_on_someone_is_kept_next_to_depend_on_something():
    # Different meaning: "be decided by" vs "rely on a person". Only the same placeholder is a repeat.
    items = [
        item("pattern", "depend on + noun", "It depends on the weather."),
        item("pattern", "depend on + someone", "You can depend on me."),
        item("pattern", "It depends on + something", "It depends on the weather."),
    ]
    kept, dropped = usage.clean_usage(items, VOCABULARY | {"depends", "me"})
    assert [k["pattern"] for k in kept] == ["depend on + noun", "depend on + someone"]
    assert len(dropped) == 1 and "It depends on + something" in dropped[0]
