"""Offline tests for the examples pass and the form types. The LLM is a fake function, so nothing is paid."""

import json
import sqlite3

import pytest

from pipeline import examples, export, store
from pipeline.examples import ExamplesResult, SenseExamples
from pipeline.forms import form_type
from pipeline.models import Example, ExampleLevel, WordEntry
from pipeline.validate import example_problems

VOCABULARY = {
    "i", "need", "to", "get", "some", "milk", "he", "got", "a", "new", "job", "last", "month", "it", "is",
    "the", "my", "go", "up", "early", "she", "was", "very", "happy", "when", "her", "friends", "came",
    "home", "from", "school", "after", "long", "day", "we", "can", "at", "this", "store", "buy", "food",
    "and",
}


def example(level, text) -> Example:
    return Example(level=ExampleLevel(level), text=text)


def old_entry() -> dict:
    return {"senses": [{
        "source_index": 0, "pos": "verb", "cefr": "A1", "simple_definition": "to buy or find something",
        "examples": ["I need to get some milk.", "He got a new job."], "vietnamese": "lấy",
        "usage": [{"kind": "pattern", "pattern": "get + noun", "example": "I need to get some milk."}],
        "image_type": "none", "image_query": "",
    }]}


@pytest.fixture
def db(tmp_path, monkeypatch):
    monkeypatch.setattr(store, "DB_PATH", tmp_path / "pipeline.db")
    connection = store.connect()
    yield connection
    connection.close()


def add_done_word(db, lemma, rank, entry, forms=()):
    db.execute(
        "INSERT INTO words (lemma, rank, forms, status, prompt_version, attempts, entry) VALUES (?, ?, ?, 'done', 2, 1, ?)",
        (lemma, rank, json.dumps(list(forms)), json.dumps(entry)),
    )
    db.commit()


GOOD = ExamplesResult(senses=[SenseExamples(sense_number=1, examples=[
    example("easy", "I need to get some milk."),
    example("medium", "He got a new job last month."),
    example("harder", "She was very happy when she got her exam results."),  # 2 words outside the vocabulary
])])


def fake_fetch(calls: list, result=GOOD):
    def fetch(lemma, entry):
        calls.append(lemma)
        return result

    return fetch


def saved_examples(db, lemma="get"):
    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = ?", (lemma,)).fetchone()["entry"])
    return saved["senses"][0]["examples"]


# --- checks ---

def test_easy_example_with_the_headword_passes():
    assert example_problems(example("easy", "I need to get some milk."), "get", set(), VOCABULARY) == []


def test_easy_allows_no_hard_words():
    problems = example_problems(example("easy", "I need to get a laptop."), "get", set(), VOCABULARY)
    assert problems == ["easy example 'I need to get a laptop.' uses hard words ['laptop']"]


def test_medium_allows_one_word_outside_the_list_but_not_two():
    one = example("medium", "I need to get a laptop.")
    two = example("medium", "I need to get a laptop and a charger.")
    assert example_problems(one, "get", set(), VOCABULARY) == []
    assert example_problems(two, "get", set(), VOCABULARY) == [
        "medium example 'I need to get a laptop and a charger.' uses hard words ['charger', 'laptop']"]


def test_harder_allows_two_words_outside_the_list_but_not_three():
    two = example("harder", "I need to get a laptop and a charger.")
    three = example("harder", "I need to get a laptop and a charger and a keyboard.")
    assert example_problems(two, "get", set(), VOCABULARY) == []
    assert len(example_problems(three, "get", set(), VOCABULARY)) == 1


def test_example_must_use_the_headword_or_a_form():
    assert example_problems(example("easy", "He was very happy."), "get", {"got"}, VOCABULARY)
    assert example_problems(example("easy", "He got a new job."), "get", {"got"}, VOCABULARY) == []


# --- the pass ---

def test_old_string_examples_load_as_easy_and_medium():
    entry = WordEntry.from_stored(json.dumps(old_entry()))
    assert [(e.level.value, e.text) for e in entry.senses[0].examples] == [
        ("easy", "I need to get some milk."), ("medium", "He got a new job."),
    ]


def test_pass_saves_three_levelled_examples_and_keeps_the_rest(db):
    add_done_word(db, "get", 1, old_entry())

    counts = examples.run_pass(db, VOCABULARY, fake_fetch([]), limit=10, workers=1)

    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    sense = saved["senses"][0]
    assert counts == {"saved": 1, "failed": 0, "items": 3, "dropped": 0}
    assert [e["level"] for e in sense["examples"]] == ["easy", "medium", "harder"]
    original = old_entry()["senses"][0]
    for key in ("simple_definition", "vietnamese", "usage", "pos", "cefr", "source_index"):
        assert sense[key] == original[key]
    row = db.execute("SELECT status, prompt_version, attempts FROM words WHERE lemma = 'get'").fetchone()
    assert (row["status"], row["prompt_version"], row["attempts"]) == ("done", 2, 1)


def test_examples_are_ordered_easy_first_even_if_the_model_mixes_them(db):
    add_done_word(db, "get", 1, old_entry())
    mixed = ExamplesResult(senses=[SenseExamples(sense_number=1, examples=[
        example("harder", "She was very happy when she got her exam results."),
        example("easy", "I need to get some milk."),
        example("medium", "He got a new job last month."),
    ])])
    examples.run_pass(db, VOCABULARY, fake_fetch([], mixed), limit=10, workers=1)
    assert [e["level"] for e in saved_examples(db)] == ["easy", "medium", "harder"]


def test_second_run_makes_no_calls(db):
    add_done_word(db, "get", 1, old_entry())
    calls: list = []
    examples.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    examples.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    assert calls == ["get"]


def test_a_failed_call_is_not_retried_and_leaves_the_row_alone(db):
    add_done_word(db, "get", 1, old_entry())
    attempts: list = []

    def broken(lemma, entry):
        attempts.append(lemma)
        raise RuntimeError("network down")

    counts = examples.run_pass(db, VOCABULARY, broken, limit=10, workers=1)

    assert attempts == ["get"]  # one call, no retry
    assert counts["failed"] == 1 and counts["saved"] == 0
    assert json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"]) == old_entry()
    calls: list = []
    examples.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1)
    assert calls == ["get"]  # resume: the next run tries again


def test_bad_examples_are_dropped_and_the_rest_kept(db):
    add_done_word(db, "get", 1, old_entry())
    result = ExamplesResult(senses=[SenseExamples(sense_number=1, examples=[
        example("easy", "I need to get some milk."),
        example("medium", "I need to get a laptop and a charger."),  # 2 hard words
        example("harder", "She was very happy at home."),  # no headword
    ])])
    counts = examples.run_pass(db, VOCABULARY, fake_fetch([], result), limit=10, workers=1)
    assert [e["level"] for e in saved_examples(db)] == ["easy"]
    assert counts["dropped"] == 2


def test_example_that_repeats_an_earlier_one_is_dropped():
    # "He got a new job." and "He got a new job last month." share all their content words.
    kept, dropped = examples.clean_examples(
        [example("easy", "He got a new job."), example("medium", "He got a new job last month."),
         example("harder", "I need to get some milk.")],
        "get", {"got"}, VOCABULARY,
    )
    assert [e["level"] for e in kept] == ["easy", "harder"]
    assert len(dropped) == 1 and "repeats another example" in dropped[0]


def test_examples_about_different_things_are_all_kept():
    kept, dropped = examples.clean_examples(
        [example("easy", "I need to get some milk."), example("medium", "He got a new job last month."),
         example("harder", "She was very happy when she got her exam results.")],
        "get", {"got"}, VOCABULARY,
    )
    assert len(kept) == 3 and dropped == []


def test_prompt_rules_ask_for_different_structures_and_show_a_good_set():
    from pipeline.generate import EXAMPLE_RULES

    assert "present perfect" in EXAMPLE_RULES and "Longer is not harder" in EXAMPLE_RULES
    assert "Examples of good sets" in EXAMPLE_RULES


def test_sense_with_no_good_example_keeps_its_old_ones_as_easy_and_medium(db):
    add_done_word(db, "get", 1, old_entry())
    examples.run_pass(db, VOCABULARY, fake_fetch([], ExamplesResult(senses=[])), limit=10, workers=1)
    assert saved_examples(db) == [
        {"level": "easy", "text": "I need to get some milk."}, {"level": "medium", "text": "He got a new job."},
    ]
    saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = 'get'").fetchone()["entry"])
    assert not examples.needs_examples(saved)  # finished: it will not be asked again


def test_example_with_a_stored_form_counts_as_the_headword(db):
    add_done_word(db, "go", 1, old_entry(), forms=["zork"])  # "zork" is not a real form: only the stored list knows it
    result = ExamplesResult(senses=[SenseExamples(sense_number=1, examples=[example("easy", "She zork home.")])])
    counts = examples.run_pass(db, VOCABULARY | {"zork"}, fake_fetch([], result), limit=10, workers=1)
    assert counts["dropped"] == 0


def test_dry_run_and_words_filter_save_nothing(db):
    add_done_word(db, "get", 1, old_entry())
    add_done_word(db, "make", 2, old_entry())
    calls: list = []
    examples.run_pass(db, VOCABULARY, fake_fetch(calls), limit=10, workers=1, words=["make"], dry_run=True)
    assert calls == ["make"]
    for lemma in ("get", "make"):
        saved = json.loads(db.execute("SELECT entry FROM words WHERE lemma = ?", (lemma,)).fetchone()["entry"])
        assert examples.needs_examples(saved)


def test_prompt_gives_the_old_examples_to_the_model():
    prompt = examples.build_examples_prompt("get", old_entry())
    assert "Headword: get" in prompt and "to buy or find something" in prompt and "I need to get some milk." in prompt


def test_usage_prompt_accepts_levelled_examples():
    from pipeline import usage

    entry = old_entry()
    entry["senses"][0]["examples"] = [{"level": "easy", "text": "One."}]
    assert "One." in usage.build_usage_prompt("get", entry)


# --- form types ---

@pytest.mark.parametrize("lemma, form, pos, expected", [
    ("run", "ran", ["verb"], "past tense"),
    ("go", "went", ["verb"], "past tense"),
    ("go", "gone", ["verb"], "past participle"),
    ("run", "running", ["noun", "verb"], "-ing form"),
    ("apple", "apples", ["noun"], "plural"),
    ("run", "runs", ["noun", "verb"], "form"),  # plural noun or third person verb: unsure
    ("walk", "walked", ["verb"], "past tense"),  # also the past participle: past tense wins
    ("read", "reading", ["verb"], "-ing form"),
    ("good", "better", ["adjective"], "comparative"),
    ("good", "best", ["adjective"], "superlative"),
    ("make", "makes", ["verb"], "third person"),
    ("run", "run", ["verb"], "form"),  # the word itself
    ("be", "xyz", ["verb"], "form"),  # not a form we know
    ("apple", "apples", [], "plural"),  # no part of speech: try all
])
def test_form_type(lemma, form, pos, expected):
    assert form_type(lemma, form, pos) == expected


# --- export ---

def test_export_stores_levels_and_form_types(db, tmp_path, monkeypatch):
    monkeypatch.setattr(export, "OUT_DIR", tmp_path / "out")
    add_done_word(db, "go", 1, old_entry(), forms=["went", "gone", "going", "goes"])
    add_done_word(db, "make", 2, old_entry())
    go_result = ExamplesResult(senses=[SenseExamples(sense_number=1, examples=[
        example("easy", "I go to school."), example("medium", "We can go to the store."),
        example("harder", "She was very happy when she went home."),
    ])])
    examples.run_pass(db, VOCABULARY | {"went"}, fake_fetch([], go_result), limit=10, workers=1, words=["go"])

    path, count, _, without_levels = export.export(db)

    app = sqlite3.connect(path)
    types = dict(app.execute("SELECT form, type FROM forms"))
    assert types == {"went": "past tense", "gone": "past participle", "going": "-ing form", "goes": "third person"}
    query = "SELECT examples FROM senses s JOIN words w ON w.id = s.word_id WHERE w.lemma = ?"
    stored = json.loads(app.execute(query, ("go",)).fetchone()[0])
    assert [e["level"] for e in stored] == ["easy", "medium", "harder"]
    # A word the pass has not reached yet is still exported, with its old examples as easy, medium.
    old = json.loads(app.execute(query, ("make",)).fetchone()[0])
    assert [e["level"] for e in old] == ["easy", "medium"]
    assert (count, without_levels) == (2, 1)
