"""Local collection written by Codex (TASK-83.9). No test calls Codex or Claude: they use fakes.
The push tests need a Postgres (TEST_DATABASE_URL, see conftest.py) and are skipped without one."""

import hashlib
import json
import sqlite3
import subprocess
from pathlib import Path

import pytest

from pipeline import collection as c

VOCAB = {"a", "the", "to", "stop", "trying", "do", "something", "i", "he", "she", "too", "soon", "after", "three",
         "tests", "if", "you", "now", "may", "never", "know", "what", "can", "it", "is", "go", "on", "with", "work",
         "we", "be", "not", "have", "very", "good", "day", "make", "decision", "need", "today", "time", "in", "made",
         "big", "last", "year", "think", "about", "before", "this", "was", "high", "town", "place", "of", "height",
         "above", "sea", "level", "felt", "cold", "because", "village", "at", "road", "which", "reaches", "closed",
         "winter", "gave", "up", "has", "given", "give"}


def sense(definition="to stop trying to do something", examples=None, usage=None, cefr="B1", pos="verb"):
    return {"pos": pos, "cefr": cefr, "simpleDefinition": definition, "vietnamese": "từ bỏ",
            "examples": examples if examples is not None else [
                {"level": "easy", "text": "I give up."},
                {"level": "medium", "text": "She gave up after three tests."},
                {"level": "harder", "text": "If you give up now, you may never know."}],
            "usage": usage or []}


def entry(*senses):
    return c.Entry.model_validate({"senses": list(senses) or [sense()]})


# ---------- the backend prompt ----------


def test_java_text_block_removes_indent_and_reads_escapes():
    source = ('        static final String P = """\n'
              '                First line.\n'
              '                  Indented "quoted" line.   \n'
              '                say "\\"hi\\""\n'
              '                """;\n')
    assert c.java_text_block(source, "P") == 'First line.\n  Indented "quoted" line.\nsay ""hi""\n'


def test_backend_word_prompt_is_read_from_claude_service():
    prompt = c.backend_word_prompt()
    assert prompt.startswith("You write learner dictionary entries for Vietnamese people learning English.\n")
    assert prompt.endswith("return an empty list of senses.\n")
    assert '"Are you coming?"' in prompt and "\\" not in prompt
    assert not any(line.startswith(" " * 8) for line in prompt.splitlines())  # Java's indent is gone


def test_prompt_version_and_cache_key_match_the_backend():
    assert c.backend_word_prompt_version().startswith("v")
    expected = hashlib.sha256("word|v7|give up".encode()).hexdigest()
    assert c.cache_key("  Give   UP ", "v7") == expected and len(expected) == 64
    # the vector in CacheTests.swift (wordKeyMatchesTheBackendRule), the same rule as CacheKeysTest.java
    assert c.cache_key(" Reluctant ", "v7") == "2a0d9f57d68b0d3f67a41639893acf5086f4c620c71733a1e5c30badf8365553"


def test_build_prompt_adds_the_phrase_note_only_for_phrases_and_escapes_tags():
    words_only = c.build_prompt([("word", "abide"), ("word", "a<b")], "PROMPT")
    assert words_only.startswith("PROMPT\n") and c.PHRASE_NOTE not in words_only
    assert "<word>abide</word>" in words_only and "<word>a&lt;b</word>" in words_only
    with_phrase = c.build_prompt([("idiom", "break the ice")], "PROMPT", {"break the ice": ["sense 1: hard"]})
    assert c.PHRASE_NOTE in with_phrase
    assert "Your last answer for <word>break the ice</word>" in with_phrase and "Problems: sense 1: hard" in with_phrase


# ---------- the checks ----------


def test_definition_check_like_simple_words():
    assert c.hard_in_definition("a person's height above sea level", VOCAB | {"person"}) == []
    assert c.hard_in_definition("to abstain from something", VOCAB) == ["abstain", "from"]


def test_example_check_skips_the_headword_and_contractions():
    vocabulary = VOCAB | {"don't"}
    assert c.hard_in_example("I don't give up.", "give", vocabulary) == []  # "don't" counts as "do"
    assert c.hard_in_example("Elevation of the road is high.", "elevation", VOCAB) == []
    assert c.hard_in_example("We abandoned it.", "abide", VOCAB) == ["abandoned"]
    assert c.hard_in_example("She is giving up.", "give", VOCAB) == ["giving"]  # the backend rule for one word
    # a phrase: each of its words and their forms count as the headword
    assert c.hard_in_example("She gave it up.", "give up", VOCAB - {"gave"}) == []
    assert c.hard_in_example("She is giving up.", "give up", VOCAB) == []
    assert c.hard_in_example("She is giving up reluctantly.", "give up", VOCAB) == ["reluctantly"]


def test_simple_word_problems_use_the_backend_limits_and_wording():
    examples = [{"level": "easy", "text": "I give up reluctantly."},  # easy: none allowed
                {"level": "medium", "text": "She gave up after three exams."},  # medium: 1 allowed
                {"level": "harder", "text": "If you give up now, you may never discover anything."}]  # harder: 2
    problems = c.simple_word_problems(entry(sense(examples=examples)), "give up", VOCAB)
    assert problems == ["sense 1: the easy example 'I give up reluctantly.' uses hard words [reluctantly]"]
    problems = c.simple_word_problems(entry(sense(definition="to abstain")), "give up", VOCAB)
    assert problems == ["sense 1: the definition uses hard words [abstain]"]


def test_one_more_common_word_is_allowed_only_when_asked():
    common = {"apples", "fox"}.__contains__
    one = entry(sense(definition="to stop trying apples"))
    two = entry(sense(definition="to stop trying apples fox"))
    rare = entry(sense(definition="to stop trying abstain"))
    assert c.simple_word_problems(one, "give up", VOCAB) != []  # the default stays the backend rule
    assert c.simple_word_problems(one, "give up", VOCAB, common) == []
    assert c.simple_word_problems(two, "give up", VOCAB, common) != []  # only ONE more word
    assert c.simple_word_problems(rare, "give up", VOCAB, common) != []  # and only a common one
    easy = [{"level": "easy", "text": "I give up apples."}, {"level": "medium", "text": "She gave up after three exams."},
            {"level": "harder", "text": "If you give up now, you may never discover anything."}]
    assert c.simple_word_problems(entry(sense(examples=easy)), "give up", VOCAB, common) == []


def test_shape_problems():
    assert c.shape_problems(entry()) == []
    two = sense(examples=[{"level": "easy", "text": "I give up."}, {"level": "harder", "text": "I gave up."}])
    bad_usage = sense(usage=[{"kind": "proverb", "pattern": "give sth up", "example": "x"}] * 4, cefr="D1")
    problems = c.shape_problems(entry(two, bad_usage))
    assert "sense 1: needs exactly 3 examples in the order easy, medium, harder" in problems
    assert "sense 2: cefr 'D1' is not A1-C2" in problems
    assert "sense 2: 4 usage items (max 3)" in problems
    assert any("usage kind 'proverb'" in p for p in problems) and any("sb/sth" in p for p in problems)
    assert c.shape_problems(entry(*[sense()] * 5)) == ["5 senses (max 4)"]


# ---------- collect ----------


def test_phrase_kind():
    assert c.phrase_kind("give up", {"v"}) == "phrasal_verb"
    assert c.phrase_kind("put up with", {"v"}) == "phrasal_verb"
    assert c.phrase_kind("break the ice", {"v"}) == "idiom"
    assert c.phrase_kind("in time", {"r"}) == "idiom"
    assert c.phrase_kind("look up", {"a"}) == "idiom"  # not a verb in WordNet


def test_wordnet_items_split_kinds_and_block_rude_and_numbers():
    lemmas = {"give up": {"v"}, "break the ice": {"v"}, "bugger off": {"v"}, "one hundred": {"a"}, "in time": {"r"}}
    items = c.wordnet_items(lemmas, 0.0, lambda text: 4.0)
    assert {(i.kind, i.text) for i in items} == {("phrasal_verb", "give up"), ("idiom", "break the ice"),
                                                ("idiom", "in time")}
    assert all(i.source == c.SOURCE_WORDNET and i.priority == 400 for i in items)


def test_typed_phrase_keeps_only_what_a_learner_types():
    assert c.typed_phrase("Make a decision.") == "make a decision"
    assert c.typed_phrase("depend on + something") is None
    assert c.typed_phrase("be angry with someone") is None
    assert c.typed_phrase("cannot abide somebody/something") is None
    assert c.typed_phrase("in the") is None  # only function words
    assert c.typed_phrase("strike while the iron is hot") == "strike while the iron is hot"
    assert c.typed_phrase("kill yourself") is None


def test_bundled_usage_items_take_collocations_and_idioms_once():
    entry_json = json.dumps({"senses": [
        {"usage": [{"kind": "collocation", "pattern": "make a decision", "example": "x"},
                   {"kind": "pattern", "pattern": "make + noun", "example": "x"},
                   {"kind": "phrase", "pattern": "make up", "example": "x"},
                   {"kind": "idiom", "pattern": "make ends meet", "example": "x"}]},
        {"usage": [{"kind": "idiom", "pattern": "make a decision", "example": "x"}]}]})
    items = c.bundled_usage_items([entry_json], lambda text: 3.0)
    assert sorted((i.kind, i.text) for i in items) == [("collocation", "make a decision"), ("idiom", "make ends meet")]


def test_name_only_words_skips_capitalised_nouns_but_keeps_adjectives():
    lemmas = [("David", "n"), ("American", "n"), ("American", "a"), ("Bill", "n"), ("bill", "n"), ("run", "v")]
    assert c.name_only_words(lemmas) == {"david"}


def test_word_items_mark_names_and_blocked_words_skipped():
    from pipeline.prefill import Item as PrefillItem

    found = [PrefillItem("word", "david", "", "scowl-70", 500), PrefillItem("word", "abide", "", "scowl-70", 350),
             PrefillItem("word", "bugger", "", "scowl-70", 300)]
    items = {i.text: i for i in c.word_items(found, {"david"})}
    assert items["abide"].status == "pending" and items["abide"].zipf == 3.5
    assert items["david"].status == "skipped" and "name" in items["david"].reason
    assert items["bugger"].status == "skipped"


def make_pipeline_db() -> sqlite3.Connection:
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE words (lemma TEXT, status TEXT, topic TEXT, entry TEXT)")
    db.executemany("INSERT INTO words VALUES (?, ?, ?, ?)", [
        ("giraffe", "pending", "animals", None), ("teapot", "done", "kitchen", None),
        ("abide", "needs_review", None, json.dumps({"senses": [{
            "pos": "verb", "cefr": "C1", "simple_definition": "to accept a rule", "vietnamese": "tuân theo",
            "examples": ["We abide by the rules.", "I can't abide noise."], "usage": []}]})),
        ("run", "done", None, None), ("zzzx", "pending", "food", None)])
    return db


def test_pipeline_word_items_take_pending_topic_and_review_words():
    items = {i.text: i for i in c.pipeline_word_items(make_pipeline_db(), {"giraffe", "abide"}, lambda t: 3.0)}
    assert set(items) == {"giraffe", "abide", "zzzx"}
    assert items["giraffe"].source == c.SOURCE_TOPIC and items["abide"].source == c.SOURCE_NGSL_REVIEW
    assert items["zzzx"].status == "skipped" and items["zzzx"].reason == "not on the backend word list"


@pytest.fixture
def db(tmp_path) -> sqlite3.Connection:
    return c.connect(tmp_path / "pipeline.db")


def test_insert_items_is_safe_to_run_again_and_report_counts(db):
    items = [c.Item("word", "abide", c.SOURCE_SCOWL, 3.2), c.Item("phrasal_verb", "give up", c.SOURCE_WORDNET, 5.0),
             c.Item("word", "david", c.SOURCE_SCOWL, 5.0, "skipped", "a name")]
    assert c.insert_items(db, items) == {"word": 2, "phrasal_verb": 1}
    assert sum(c.insert_items(db, items + [c.Item("idiom", "give up", "x", 1.0)]).values()) == 0
    row = db.execute("SELECT * FROM collection WHERE text = 'david'").fetchone()
    assert row["status"] == "skipped" and json.loads(row["problems"]) == ["a name"] and row["priority"] == 500
    report = "\n".join(c.report_lines(db))
    assert "Per kind: word 2, phrasal_verb 1, idiom 0, collocation 0" in report
    assert "To write: 2 rows" in report


# ---------- write ----------


class FakeWriter:
    """Answers by text from a dict: a list of senses, None (no answer), an exception, or a tuple of those (one per
    call, for the retry)."""

    name = "codex:fake"

    def __init__(self, answers):
        self.answers = answers
        self.prompts = []
        self.calls = {}

    def write(self, prompt):
        self.prompts.append(prompt)
        texts = [line[6:-7] for line in prompt.splitlines() if line.startswith("<word>") and line.endswith("</word>")]
        out = []
        for text in texts:
            answer = self.answers[text]
            if isinstance(answer, tuple):
                n = self.calls.get(text, 0)
                self.calls[text] = n + 1
                answer = answer[n]
            if isinstance(answer, Exception):
                raise answer
            if answer is not None:
                out.append({"word": text, "senses": answer})
        return out


HARD = [sense(definition="to relinquish something")]
GOOD = [sense()]


def add(db, *texts, kind="phrasal_verb"):
    c.insert_items(db, [c.Item(kind, text, "test", 5.0 - n / 10) for n, text in enumerate(texts)])


def run(db, writer, rows=None, **kwargs):
    rows = rows if rows is not None else c.rows_to_write(db, None, None, None, None)
    return c.write_rows(db, rows, writer, word_prompt="PROMPT", prompt_version="v7", vocabulary=VOCAB,
                        workers=1, **kwargs)


def status_of(db, text):
    return db.execute("SELECT * FROM collection WHERE text = ?", (text,)).fetchone()


def test_write_saves_done_entries_with_model_and_prompt_version(db):
    add(db, "give up", "go on")
    writer = FakeWriter({"give up": GOOD, "go on": GOOD})
    summary = run(db, writer)
    assert summary.counts == {"done": 2} and summary.calls == 2 and len(writer.prompts) == 1  # one batch
    row = status_of(db, "give up")
    assert (row["status"], row["model"], row["prompt_version"], row["attempts"]) == ("done", "codex:fake", "v7", 1)
    assert json.loads(row["entry"])["senses"][0]["simpleDefinition"] == "to stop trying to do something"
    assert json.loads(row["problems"]) == []


def test_write_retries_once_with_feedback_then_needs_review(db):
    add(db, "give up", "go on")
    writer = FakeWriter({"give up": (HARD, GOOD), "go on": (HARD, HARD)})
    summary = run(db, writer)
    assert summary.counts == {"done": 1, "needs_review": 1} and summary.calls == 4
    assert status_of(db, "give up")["status"] == "done" and status_of(db, "give up")["attempts"] == 2
    review = status_of(db, "go on")
    assert review["status"] == "needs_review" and "relinquish" in review["problems"]
    assert "Problems: sense 1: the definition uses hard words [relinquish]" in writer.prompts[-1]


def test_write_marks_no_senses_skipped_and_errors_failed_for_a_later_run(db):
    add(db, "zzz up", "go on")
    summary = run(db, FakeWriter({"zzz up": [], "go on": None}))
    assert summary.counts == {"skipped": 1, "failed": 1}
    assert status_of(db, "zzz up")["status"] == "skipped"
    assert status_of(db, "go on")["status"] == "failed"
    assert [r["text"] for r in c.rows_to_write(db, None, None, None, None)] == ["go on"]  # tried again later
    db.execute("UPDATE collection SET attempts = 3 WHERE text = 'go on'")
    assert c.rows_to_write(db, None, None, None, None) == []  # until it has used its tries


def test_a_bad_call_fails_the_whole_batch_and_provider_stop_ends_the_run(db):
    add(db, "give up", "go on")
    summary = run(db, FakeWriter({"give up": c.WriteError("timeout"), "go on": GOOD}))
    assert summary.counts == {"failed": 2}
    add(db, "make up", kind="idiom")
    summary = run(db, FakeWriter({"give up": c.ProviderStop("usage limit"), "go on": GOOD, "make up": GOOD}),
                  batch_size=1)
    assert summary.stopped == "usage limit"
    assert status_of(db, "give up")["status"] == "failed"  # left for the next run


def test_answers_match_by_word_else_by_position():
    answers = [{"word": "B", "senses": []}, {"word": "a", "senses": []}]
    assert c._match(answers, "a", 0, 2)["word"] == "a"
    assert c._match([{"word": "?", "senses": []}], "a", 0, 1)["word"] == "?"
    assert c._match([{"word": "?", "senses": []}], "a", 0, 2) is None


def test_rows_to_write_per_kind_texts_and_batches_split_words_and_phrases(db):
    add(db, "give up", "go on", "make up")
    add(db, "abide", "elevation", kind="word")
    picked = c.rows_to_write(db, None, None, None, per_kind=1)
    assert [r["text"] for r in picked] == ["give up", "abide"]
    assert [r["text"] for r in c.rows_to_write(db, ["word"], None, None, None)] == ["abide", "elevation"]
    assert [r["text"] for r in c.rows_to_write(db, None, ["Go On"], None, None)] == ["go on"]
    groups = c.batches(c.rows_to_write(db, None, None, None, None), 2)
    assert [[r["text"] for r in g] for g in groups] == [["abide", "elevation"], ["give up", "go on"], ["make up"]]


def test_codex_writer_command_and_answer(tmp_path):
    calls = []

    def fake_run(command, **kwargs):
        calls.append((command, kwargs))
        out = Path(command[command.index("-o") + 1])
        schema = json.loads(Path(command[command.index("--output-schema") + 1]).read_text())
        assert schema["properties"]["entries"]["items"]["required"] == ["word", "senses"]
        out.write_text(json.dumps({"entries": [{"word": "abide", "senses": []}]}))
        return subprocess.CompletedProcess(command, 0, "", "")

    writer = c.CodexWriter("gpt-x", "low", run=fake_run)
    assert writer.name == "codex:gpt-x"
    assert writer.write("PROMPT") == [{"word": "abide", "senses": []}]
    command, kwargs = calls[0]
    for flag in ("--ignore-user-config", "--ephemeral", "--skip-git-repo-check", "read-only", "gpt-x",
                 "model_reasoning_effort=low"):
        assert flag in command
    assert command[-1] == "PROMPT" and kwargs["stdin"] == subprocess.DEVNULL


def test_codex_writer_stops_on_plan_limits_and_fails_on_no_answer():
    def no_answer(output):
        return lambda command, **kwargs: subprocess.CompletedProcess(command, 1, output, "")

    with pytest.raises(c.ProviderStop):
        c.CodexWriter(None, run=no_answer("You've hit your usage limit.")).write("P")
    with pytest.raises(c.WriteError):
        c.CodexWriter(None, run=no_answer("something else")).write("P")


def test_default_codex_model_reads_the_config(tmp_path):
    (tmp_path / "config.toml").write_text('model = "gpt-5.6-terra"\n')
    assert c.default_codex_model(tmp_path) == "gpt-5.6-terra"
    assert c.default_codex_model(tmp_path / "missing") is None


# ---------- compare ----------


def test_claude_entries_from_the_pipeline_and_the_page(db):
    add(db, "abide", kind="word")
    run(db, FakeWriter({"abide": GOOD}))
    assert c.claude_from_pipeline(db, make_pipeline_db()) == 1
    row = status_of(db, "abide")
    claude = json.loads(row["claude_entry"])["senses"][0]
    assert claude["simpleDefinition"] == "to accept a rule"
    assert [e["level"] for e in claude["examples"]] == ["easy", "medium"]  # old plain examples
    page = c.compare_page(c.compare_rows(db, 20))
    assert "abide" in page and "to accept a rule" in page and "codex:fake" in page
    assert "<script" not in page


def test_compare_page_escapes_html(db):
    add(db, "x up")
    run(db, FakeWriter({"x up": (HARD, [sense(definition="<img src=x>")])}))
    page = c.compare_page(c.compare_rows(db, 20))
    assert "<img src=x>" not in page and "&lt;img src=x&gt;" in page


def test_claude_from_api_stops_at_the_cap(db):
    add(db, "give up", "go on", "make up")
    run(db, FakeWriter({"give up": GOOD, "go on": GOOD, "make up": GOOD}))
    asked = []

    def ask(text, kind, prompt):
        asked.append((text, kind))
        return entry(), 0.001

    answered, spent = c.claude_from_api(db, c.compare_rows(db, 20), max_cost=0.0021, ask=ask, word_prompt="P")
    assert answered == 2 and spent == pytest.approx(0.002)
    assert asked == [("give up", "phrasal_verb"), ("go on", "phrasal_verb")]
    assert status_of(db, "give up")["claude_model"] == c.CLAUDE_MODEL


# ---------- push (Postgres) ----------


def test_push_adds_new_words_and_never_overwrites(pg, db):
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO dictionary_word (lemma, word_rank, created_at, updated_at) "
                       "VALUES ('run', 1, now(), now())")
        cursor.execute("INSERT INTO word_lookup (cache_key, word, entry_json, model, prompt_version, created_at) "
                       "VALUES (%s, 'abide', '{\"senses\": []}', 'claude-haiku-5-5', 'v7', now())",
                       (c.cache_key("abide", "v7"),))
    pg.commit()
    add(db, "abide", "elevation", "run", "giraffe", kind="word")
    run(db, FakeWriter({"abide": GOOD, "elevation": GOOD, "run": GOOD, "giraffe": (HARD, HARD)}))
    rows = c.push_rows(db, ["word"], include_review=False, limit=None)
    assert [r["text"] for r in rows] == ["abide", "elevation", "run"]
    plan = c.plan_push(pg, c.push_rows(db, ["word"], include_review=True, limit=None), "v7")
    assert plan.bundled == ["run"] and plan.exists == ["abide"]
    assert [r["text"] for r in plan.to_insert] == ["elevation", "giraffe"]
    assert c.push(db, pg, plan, "v7") == 2
    with pg.cursor() as cursor:
        cursor.execute("SELECT word, model, prompt_version, needs_review, cefr, entry_json FROM word_lookup "
                       "ORDER BY word")
        saved = cursor.fetchall()
    assert [(w, m, v, r, cefr) for w, m, v, r, cefr, _ in saved] == [
        ("abide", "claude-haiku-5-5", "v7", False, None),  # the Claude row is untouched
        ("elevation", "codex:fake", "v7", False, "B1"),
        ("giraffe", "codex:fake", "v7", True, "B1")]
    assert json.loads(saved[1][5])["senses"][0]["examples"][0] == {"level": "easy", "text": "I give up."}
    # a bundled word is offered again (the dictionary may drop it later); the rest is marked pushed
    assert [r["text"] for r in c.push_rows(db, ["word"], include_review=True, limit=None)] == ["run"]
    assert status_of(db, "abide")["pushed_at"] is not None  # already there: not offered again


def test_revalidate_marks_entries_done_when_the_word_list_allows_them(db):
    add(db, "give up", "go on")
    run(db, FakeWriter({"give up": (HARD, HARD), "go on": GOOD}))
    db.execute("UPDATE collection SET status = 'retry' WHERE text = 'give up'")
    assert c.revalidate(db, VOCAB) == {"retry -> retry": 1}
    assert c.revalidate(db, VOCAB | {"relinquish"}) == {"retry -> done": 1}
    row = status_of(db, "give up")
    assert row["status"] == "done" and json.loads(row["problems"]) == []
