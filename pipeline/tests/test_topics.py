"""Offline tests for topic words. WordNet is a small fake and the judge is a fake function, so nothing is paid."""

import json

import pytest

from pipeline import store, topics
from pipeline.topics import Candidate, JudgeResult, Topic, Verdict


class FakeSynset:
    def __init__(self, id, lexfile, definition="", parents=(), pos="n"):
        self.id, self._lexfile, self._definition, self._parents, self.pos = id, lexfile, definition, list(parents), pos

    def lexfile(self):
        return self._lexfile

    def definition(self):
        return self._definition

    def hypernyms(self):
        return self._parents


class FakeSense:
    def __init__(self, synset):
        self._synset = synset

    def synset(self):
        return self._synset


class FakeWord:
    def __init__(self, lemma, pos, synsets):
        self._lemma, self.pos, self._senses = lemma, pos, [FakeSense(s) for s in synsets]

    def lemma(self):
        return self._lemma

    def senses(self):
        return self._senses


class FakeWordnet:
    """Just enough of wn.Wordnet: words(), words(form), synsets(lemma)."""

    def __init__(self, words, anchors):
        self._words, self._anchors = words, anchors

    def words(self, form=None, pos=None):
        return [w for w in self._words if (form is None or w.lemma() == form) and (pos is None or w.pos == pos)]

    def synsets(self, lemma, pos=None):
        return [self._anchors[lemma]] if lemma in self._anchors else []


CLOTHING = FakeSynset("clothing", "noun.artifact", "a covering for the body")
VEHICLE = FakeSynset("vehicle", "noun.artifact", "a thing that carries people")


def fake_wordnet() -> FakeWordnet:
    words = [
        FakeWord("giraffe", "n", [FakeSynset("giraffe.1", "noun.animal", "tall African animal")]),
        FakeWord("scarf", "n", [FakeSynset("scarf.1", "noun.artifact", "worn around the neck", [CLOTHING])]),
        FakeWord("tram", "n", [FakeSynset("tram.1", "noun.artifact", "a vehicle on rails", [VEHICLE])]),
        FakeWord("apple", "n", [FakeSynset("apple.1", "noun.food", "a round fruit")]),  # in NGSL in this test
        FakeWord("rape", "n", [FakeSynset("rape.1", "noun.plant", "a plant")]),  # blocked
        FakeWord("zymurgy", "n", [FakeSynset("zymurgy.1", "noun.food", "brewing science")]),  # too rare
        FakeWord("ministry", "n", [FakeSynset("ministry.1", "noun.group", "a government department")]),  # no topic
        FakeWord("chew", "v", [FakeSynset("chew.1", "verb.consumption", "bite food", pos="v")]),
    ]
    return FakeWordnet(words, {"clothing": CLOTHING, "vehicle": VEHICLE})


@pytest.fixture
def db(tmp_path, monkeypatch):
    monkeypatch.setattr(store, "DB_PATH", tmp_path / "pipeline.db")
    connection = store.connect()
    yield connection
    connection.close()


@pytest.fixture
def no_extras(monkeypatch):
    monkeypatch.setattr(topics, "EXTRA_WORDS", {})


def test_known_words_has_lemmas_and_forms(db):
    db.execute("INSERT INTO words (lemma, rank, forms) VALUES ('apple', 1, ?)", (json.dumps(["apples"]),))
    assert topics.known_words(db) == {"apple", "apples"}


def test_candidates_get_a_topic_and_skip_ngsl_blocked_rare_and_topicless_words(no_extras):
    candidates = {c.word: c for c in topics.build_candidates(fake_wordnet(), known={"apple"})}
    assert set(candidates) == {"giraffe", "scarf", "tram", "chew"}
    assert candidates["giraffe"].topic == Topic.ANIMALS
    assert candidates["scarf"].topic == Topic.CLOTHES  # from the hypernym, not from noun.artifact
    assert candidates["tram"].topic == Topic.TRANSPORT
    assert candidates["chew"].topic == Topic.FOOD and candidates["chew"].pos == "v"


def test_extra_words_are_added_unless_ngsl_has_them(monkeypatch):
    monkeypatch.setattr(topics, "EXTRA_WORDS", {"sunny": Topic.WEATHER, "apple": Topic.FOOD})
    candidates = {c.word: c for c in topics.build_candidates(fake_wordnet(), known={"apple"})}
    assert candidates["sunny"].topic == Topic.WEATHER
    assert "apple" not in candidates


def candidate(word, topic=Topic.ANIMALS) -> Candidate:
    return Candidate(word, "n", 3.5, topic, "a gloss")


def test_saving_candidates_again_keeps_judged_rows(db):
    assert topics.save_candidates(db, [candidate("giraffe"), candidate("charlotte", Topic.FOOD)]) == 2
    db.execute("UPDATE topic_words SET status = 'drop' WHERE lemma = 'charlotte'")
    assert topics.save_candidates(db, [candidate("charlotte", Topic.FOOD), candidate("zebra")]) == 1
    status = dict(db.execute("SELECT lemma, status FROM topic_words").fetchall())
    assert status == {"giraffe": "candidate", "charlotte": "drop", "zebra": "candidate"}


def verdict(word, keep=True, topic=Topic.ANIMALS, reason="common animal") -> Verdict:
    return Verdict(word=word, keep=keep, topic=topic, reason=reason)


def test_verdicts_for_other_words_are_ignored_and_the_first_one_wins():
    batch = [{"lemma": "giraffe"}, {"lemma": "charlotte"}]
    result = JudgeResult(verdicts=[
        verdict("Giraffe"), verdict("giraffe", keep=False), verdict("charlotte", keep=False, reason="a name"),
        verdict("banana"),
    ])
    assert topics.apply_verdicts(batch, result) == [
        ("keep", "animals", "common animal", "giraffe"), ("drop", "animals", "a name", "charlotte"),
    ]


def test_judge_saves_keep_and_drop_and_can_fix_the_topic(db):
    topics.save_candidates(db, [candidate("giraffe"), candidate("charlotte", Topic.FOOD), candidate("bass", Topic.FOOD)])
    asked = []

    def fake_fetch(batch):
        asked.append([row["lemma"] for row in batch])
        return JudgeResult(verdicts=[
            verdict("giraffe"), verdict("charlotte", keep=False, topic=Topic.FOOD, reason="mainly a name"),
            verdict("bass", topic=Topic.ANIMALS, reason="a fish"),
        ])

    counts = topics.run_judge(db, fake_fetch, batch_size=2)
    assert counts == {"keep": 2, "drop": 1, "failed_batches": 0, "no_verdict": 0}
    assert len(asked) == 2  # 3 words in batches of 2
    rows = {r["lemma"]: (r["status"], r["topic"], r["reason"]) for r in db.execute("SELECT * FROM topic_words")}
    assert rows["bass"] == ("keep", "animals", "a fish")
    assert rows["charlotte"] == ("drop", "food and drink", "mainly a name")
    assert topics.rows_to_judge(db, 100) == []  # a second run makes no calls


def test_a_failed_call_or_a_missing_verdict_leaves_the_word_for_the_next_run(db):
    topics.save_candidates(db, [candidate("giraffe"), candidate("zebra")])

    def fails(batch):
        raise RuntimeError("network")

    assert topics.run_judge(db, fails)["failed_batches"] == 1
    assert topics.run_judge(db, lambda batch: JudgeResult(verdicts=[verdict("giraffe")]))["no_verdict"] == 1
    assert [row["lemma"] for row in topics.rows_to_judge(db, 100)] == ["zebra"]


def test_dry_run_saves_nothing(db):
    topics.save_candidates(db, [candidate("giraffe")])
    topics.run_judge(db, lambda batch: JudgeResult(verdicts=[verdict("giraffe")]), dry_run=True)
    assert db.execute("SELECT status FROM topic_words").fetchone()[0] == "candidate"


def test_prompt_lists_every_word_with_its_topic_and_gloss():
    prompt = topics.build_judge_prompt([{"lemma": "tram", "pos": "n", "topic": "transport", "gloss": "a vehicle on rails"}])
    assert "tram (n), proposed topic: transport, WordNet: a vehicle on rails" in prompt


def test_export_tsv_has_one_row_per_word(db, tmp_path):
    topics.save_candidates(db, [candidate("giraffe"), candidate("zebra")])
    path = tmp_path / "topic_words.tsv"
    assert topics.export_tsv(db, path) == 2
    assert path.read_text().splitlines()[0].startswith("word\tpos\tzipf\ttopic\tstatus")


def test_review_drops_moves_and_restores_on_top_of_the_judge(db, monkeypatch):
    monkeypatch.setattr(topics, "REVIEW_DROP", {"sub", "not-a-candidate"})
    monkeypatch.setattr(topics, "REVIEW_MOVE", {"queen": Topic.PEOPLE})
    monkeypatch.setattr(topics, "REVIEW_KEEP", {"mole": Topic.ANIMALS})
    topics.save_candidates(db, [candidate("sub", Topic.FOOD), candidate("queen"), candidate("mole", Topic.FOOD),
                                candidate("giraffe")])
    db.execute("UPDATE topic_words SET status = 'keep' WHERE lemma IN ('sub', 'queen', 'giraffe')")
    db.execute("UPDATE topic_words SET status = 'drop' WHERE lemma = 'mole'")

    assert topics.apply_review(db) == {"drop": 1, "move": 1, "keep": 1}
    rows = {r["lemma"]: (r["status"], r["topic"]) for r in db.execute("SELECT * FROM topic_words")}
    assert rows == {"sub": ("drop", "food and drink"), "queen": ("keep", "people and jobs"),
                    "mole": ("keep", "animals"), "giraffe": ("keep", "animals")}
    assert topics.apply_review(db) == {"drop": 1, "move": 1, "keep": 1}  # running it again changes nothing new


def test_review_lists_do_not_overlap():
    assert not topics.REVIEW_DROP & set(topics.REVIEW_MOVE)
    assert not topics.REVIEW_DROP & set(topics.REVIEW_KEEP)
    assert not set(topics.REVIEW_MOVE) & set(topics.REVIEW_KEEP)


def test_promote_adds_kept_words_after_ngsl_with_topic_and_forms(db):
    db.execute("INSERT INTO words (lemma, rank, forms) VALUES ('apple', 1200, '[]')")
    topics.save_candidates(db, [candidate("giraffe"), Candidate("tram", "n", 4.0, Topic.TRANSPORT, "g"),
                                candidate("charlotte", Topic.FOOD), candidate("apple", Topic.FOOD)])
    db.execute("UPDATE topic_words SET status = 'keep' WHERE lemma != 'charlotte'")
    db.execute("UPDATE topic_words SET status = 'drop' WHERE lemma = 'charlotte'")

    assert topics.promote(db) == 2  # apple is already a word, charlotte was dropped
    rows = {r["lemma"]: r for r in db.execute("SELECT * FROM words")}
    assert (rows["tram"]["rank"], rows["giraffe"]["rank"]) == (10001, 10002)  # most common first
    assert rows["giraffe"]["status"] == "pending" and rows["giraffe"]["topic"] == "animals"
    assert "giraffes" in json.loads(rows["giraffe"]["forms"])
    assert rows["apple"]["topic"] is None
    assert topics.promote(db) == 0  # safe to run again


def test_old_databases_get_the_topic_column(tmp_path, monkeypatch):
    import sqlite3

    path = tmp_path / "old.db"
    old = sqlite3.connect(path)
    old.execute("CREATE TABLE words (lemma TEXT PRIMARY KEY, rank INTEGER NOT NULL, forms TEXT NOT NULL, "
                "status TEXT NOT NULL DEFAULT 'pending', prompt_version INTEGER, attempts INTEGER NOT NULL DEFAULT 0, "
                "ipa TEXT, entry TEXT, problems TEXT, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)")
    old.close()
    monkeypatch.setattr(store, "DB_PATH", path)
    connection = store.connect()
    assert "topic" in {row[1] for row in connection.execute("PRAGMA table_info(words)")}
    connection.close()
