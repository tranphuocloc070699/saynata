"""Offline tests for the prefill queue (TASK-83.2). Word frequency and lemmas are fakes, so no data files are needed.

The tests with `pg` need a Postgres (TEST_DATABASE_URL, see conftest.py) and are skipped without one.
"""

import sqlite3

import pytest

from pipeline import prefill
from pipeline.prefill import Item

ZIPF = {"run": 5.5, "running": 5.0, "apples": 4.0, "tiger": 3.6, "tigers": 3.9, "rare": 2.0, "shit": 5.0, "jump": 4.5}
LEMMAS = {"running": {"run"}, "apples": {"apple"}, "tigers": {"tiger"}, "leaves": {"leaf", "leave"}}


def zipf_of(word):
    return ZIPF.get(word, 4.0)


def lemmas_for(word):
    return LEMMAS.get(word, {word})


def words(scowl, excluded=(), min_zipf=3.5):
    return prefill.collect_words(set(scowl), set(excluded), min_zipf, zipf_of, lemmas_for)


def test_bundled_words_and_their_forms_are_never_queued():
    items = words({"run", "running", "apples", "apple", "jump"}, excluded={"run", "apple"})
    assert [i.text for i in items] == ["jump"]  # "running" and "apples" are forms of bundled lemmas


def test_a_form_is_reduced_to_its_lemma_with_the_best_zipf():
    items = words({"tiger", "tigers"})
    assert [(i.text, i.priority) for i in items] == [("tiger", 390)]


def test_a_lemma_that_is_not_on_the_word_list_is_dropped():
    # The backend rejects words that are not on SCOWL-70, so "leaf" cannot be queued from "leaves" here.
    assert words({"leaves"}) == []


def test_kept_topic_words_and_their_forms_are_excluded():
    assert words({"tiger", "tigers", "jump"}, excluded={"tiger"}) == [Item("word", "jump", "", "scowl-70", 450)]


def test_rare_short_odd_and_rude_words_are_dropped():
    assert words({"rare", "of", "a's", "Paris", "café", "shit", "jump"}) == [
        Item("word", "jump", "", "scowl-70", 450)]


def test_words_are_sorted_by_frequency_then_alphabet():
    items = words({"jump", "run", "tiger", "zebra", "yak"})
    assert [i.text for i in items] == ["run", "jump", "yak", "zebra", "tiger"]
    assert items[0] == Item("word", "run", "", "scowl-70", 550)


def test_lemminflect_reduces_real_forms():
    assert "run" in prefill.lemmas_of("running")
    assert prefill.lemmas_of("apple") == {"apple"}


def test_word_lists_skip_comments_and_blank_lines(tmp_path):
    (tmp_path / "list.txt").write_text("# note\nApple\n\n  run \n", encoding="utf-8")
    assert prefill.read_word_list("list.txt", tmp_path) == {"apple", "run"}


def test_the_real_word_lists_load():
    scowl, bundled = prefill.read_word_list("scowl-70.txt"), prefill.bundled_from_files()
    assert "tiger" in scowl and "the" in bundled and len(scowl) > len(bundled)


def test_only_kept_topic_words_are_read_from_pipeline_db():
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE topic_words (lemma TEXT, status TEXT)")
    db.executemany("INSERT INTO topic_words VALUES (?, ?)", [("Apple", "keep"), ("bishop", "drop"), ("sofa", "candidate")])
    assert prefill.kept_topic_words(db) == {"apple"}
    assert prefill.kept_topic_words(None) == set()


# ---------- phrases ----------


PHRASE_ZIPF = {"give up": 5.0, "look after": 4.6, "hit the hay": 3.8, "rare phrase": 2.0}


def phrase_zipf(text):
    return PHRASE_ZIPF.get(text, 4.5)


def phrases(lemmas, min_zipf=4.0):
    return prefill.collect_phrases(lemmas, min_zipf, phrase_zipf)


def test_phrasal_verbs_and_idioms_are_queued_as_vietnamese_sentences():
    items = phrases({"give up": {"v"}, "look after": {"v"}, "hit the hay": {"v"}})
    assert items == [Item("sentence", "give up", "vi", "oewn-2025", 500),
                     Item("sentence", "look after", "vi", "oewn-2025", 460)]  # "hit the hay" is below 4.0


def test_noun_only_phrases_proper_nouns_and_odd_characters_are_skipped():
    lemmas = {"rat cheese": {"n"}, "New York": {"r"}, "hot dog": {"n"}, "give up": {"v"},
              "one two three four five": {"v"}, "3d printing": {"v"}}
    assert [i.text for i in phrases(lemmas)] == ["give up"]


def test_phrases_with_placeholders_function_words_only_and_rude_words_are_skipped():
    lemmas = {"turn one's stomach": {"v"}, "look after somebody": {"v"}, "to that": {"r"}, "be on": {"v"},
              "shit happens": {"v"}, "give up": {"v"}}
    assert [i.text for i in phrases(lemmas)] == ["give up"]


def test_hyphens_and_apostrophes_are_kept_and_spaces_are_cleaned():
    assert prefill.normalize_phrase("well-known  fact") == "well-known fact"
    assert prefill.normalize_phrase("rock 'n' roll") is None
    assert prefill.normalize_phrase("cat's cradle") == "cat's cradle"
    assert prefill.normalize_phrase("single") is None


def test_costs_use_the_measured_prices():
    assert prefill.estimated_cost(1000, 1000) == pytest.approx(0.9)


def test_all_wordfreq_keeps_zipf_above_zero_but_not_words_wordfreq_never_saw():
    ZIPF.update({"zebra": 0.4, "unseen": 0.0})
    items = prefill.collect_words({"zebra", "unseen", "jump", "rare"}, set(), 0.0, zipf_of, lemmas_for, above_zero=True)
    assert [i.text for i in items] == ["jump", "rare", "zebra"]  # "unseen" has Zipf 0
    assert prefill.collect_words({"unseen"}, set(), 0.0, zipf_of, lemmas_for) != []  # plain --zipf 0 lets it in


def test_all_wordfreq_still_skips_bundled_rude_and_off_list_words():
    items = prefill.collect_words({"run", "jump", "shit", "apples"}, {"run", "apple"}, 0.0, zipf_of, lemmas_for,
                                  above_zero=True)
    assert [i.text for i in items] == ["jump"]  # apples -> apple is bundled; the lemma "apple" is not on the list


def test_priority_follows_frequency_with_all_wordfreq():
    items = prefill.collect_words({"jump", "zebra"}, set(), 0.0, zipf_of, lemmas_for, above_zero=True)
    assert [(i.text, i.priority) for i in items] == [("jump", 450), ("zebra", 40)]


def test_night_estimate_uses_the_cap_and_seven_seconds_a_word():
    cost, per_night, nights = prefill.night_estimate(68330)
    assert cost == pytest.approx(47.83, abs=0.01)
    assert per_night == 714
    assert nights == pytest.approx(95.7, abs=0.1)
    assert "119 minutes" in prefill.night_lines(68330)[1] or "83 minutes" in prefill.night_lines(68330)[1]


# ---------- Postgres ----------

# The table of backend Flyway TASK-83.1. Made here too, so the tests also run before that migration exists.
PREFILL_TABLE = """
CREATE TABLE IF NOT EXISTS prefill_item (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('word', 'sentence')),
    text        VARCHAR(500) NOT NULL,
    target_lang VARCHAR(8)   NOT NULL DEFAULT '',
    source      VARCHAR(32),
    priority    INTEGER      NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'done', 'skipped', 'failed')),
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (kind, text, target_lang)
)
"""


@pytest.fixture
def queue(pg):
    pg.execute(PREFILL_TABLE)
    pg.commit()
    return pg


def test_insert_skips_rows_that_are_already_queued(queue):
    first = [Item("word", "jump", "", "scowl-70", 450), Item("sentence", "give up", "vi", "oewn-2025", 500)]
    assert prefill.insert_items(queue, first) == 2
    # A second run: one old row (its priority must not change) and one new row.
    again = [Item("word", "jump", "", "scowl-70", 999), Item("word", "tiger", "", "scowl-70", 360)]
    assert prefill.insert_items(queue, again) == 1
    assert prefill.queued_keys(queue) == {("word", "jump", ""), ("word", "tiger", ""), ("sentence", "give up", "vi")}
    assert queue.execute("SELECT priority, status, attempts FROM prefill_item WHERE text = 'jump'").fetchone() == (
        450, "pending", 0)


def test_the_same_text_can_be_a_word_and_a_sentence(queue):
    items = [Item("word", "give up", "", "scowl-70", 1), Item("sentence", "give up", "vi", "oewn-2025", 1)]
    assert prefill.insert_items(queue, items) == 2


def test_queue_exists_is_true_once_the_table_is_there(queue):
    assert prefill.queue_exists(queue) is True


def test_report_counts_and_prices_the_pending_items(queue):
    prefill.insert_items(queue, [Item("word", f"w{n}", "", "scowl-70", n) for n in range(10)]
                         + [Item("sentence", f"give up {n}", "vi", "oewn-2025", n) for n in range(5)])
    queue.execute("UPDATE prefill_item SET status = 'done' WHERE text IN ('w0', 'w1')")
    queue.commit()
    text = "\n".join(prefill.report_lines(queue))
    assert "word      done     scowl-70          2" in text
    assert "word      pending  scowl-70          8" in text
    assert "sentence  pending  oewn-2025         5" in text
    assert "Pending: 8 words" in text and "5 sentences" in text and "about $0.01" in text


def test_report_of_an_empty_queue_says_what_to_run(queue):
    assert "prefill collect" in prefill.report_lines(queue)[0]


# ---------- common sentences (TASK-83.11) ----------

HEADER = "text\tsituation\tkind\n"


def sentence_file(tmp_path, *lines):
    path = tmp_path / "sentences.tsv"
    path.write_text(HEADER + "".join(f"{line}\n" for line in lines), encoding="utf-8")
    return path


def test_sentence_rows_are_read_with_spaces_cleaned_and_kind_lowercased(tmp_path):
    path = sentence_file(tmp_path, "Looks  good. \tagreeing\tReply", "", "# a comment", "I'll get back to you.\temail\twork")
    rows, problems = prefill.read_sentence_file(path)
    assert [(r.text, r.situation, r.kind) for r in rows] == [
        ("Looks good.", "agreeing", "reply"), ("I'll get back to you.", "email", "work")]
    assert problems == []


def test_duplicates_after_case_and_final_punctuation_keep_the_first(tmp_path):
    path = sentence_file(tmp_path, "No worries.\tthanks\treply", "no worries!\tthanks\treply", "No worries?!\tx\tchat")
    rows, problems = prefill.read_sentence_file(path)
    assert [r.text for r in rows] == ["No worries."]
    assert len(problems) == 2 and "the same as line 2" in problems[0]


def test_short_long_rude_personal_and_broken_lines_are_left_out(tmp_path):
    path = sentence_file(tmp_path, "Thanks.\tx\treply", " ".join(["word"] * 16) + "\tx\tchat",
                         "What a crap day.\tx\tchat", "Call me on 090 123 4567.\tx\tphone",
                         "Write to me at someone@example.com.\tx\temail", "Only two columns\tx",
                         "No kind here.\tx\t ", "Well done.\tpraise\treply")
    rows, problems = prefill.read_sentence_file(path)
    assert [r.text for r in rows] == ["Well done."]
    assert len(problems) == 7


def test_a_file_without_the_header_is_refused(tmp_path):
    path = tmp_path / "bad.tsv"
    path.write_text("Looks good.\tx\treply\n", encoding="utf-8")
    with pytest.raises(ValueError):
        prefill.read_sentence_file(path)


def test_looks_personal_mirrors_the_backend_detector():
    assert prefill.looks_personal("Visit www.example.com today")
    assert prefill.looks_personal("My number is +84 90 123 45 67")
    assert prefill.looks_personal("Order 1234567 is late")
    assert not prefill.looks_personal("See you at 7 on May 12.")


def test_replies_come_first_and_shorter_lines_win_inside_a_kind(tmp_path):
    path = sentence_file(tmp_path, "Can we move the meeting to Friday?\tx\twork", "Sounds good to me.\tx\treply",
                         "Looks good.\tx\treply", "Is it far?\tx\ttravel", "A new kind of line.\tx\tsomething")
    items = prefill.sentence_items(prefill.read_sentence_file(path)[0])
    assert [i.text for i in items] == ["Looks good.", "Sounds good to me.", "Can we move the meeting to Friday?",
                                       "Is it far?", "A new kind of line."]
    assert all(i.kind == "sentence" and i.target_lang == "vi" and i.source == "common-sentences" for i in items)


def test_the_bundled_list_is_clean_and_big_enough():
    rows, problems = prefill.read_sentence_file()
    assert problems == []
    assert 2000 <= len(rows) <= 3000
    assert len({prefill.loose_key(r.text) for r in rows}) == len(rows)


def test_night_model_prices_are_read_from_application_properties():
    price = prefill.model_price()
    assert price[0] > 0 and price[1] > price[0]
    lines = prefill.sentence_night_lines(1000, (0.30, 1.20))
    assert "$0.54" in lines[0] and "deepseek-v4.1-flash" in lines[0]
    assert "925 sentences a night" in lines[1]


def test_dry_run_counts_and_prices_without_a_database(tmp_path, monkeypatch, capsys):
    from pipeline import cli

    path = sentence_file(tmp_path, "Looks good.\tx\treply", "looks good\tx\treply", "Well done.\tx\treply")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.setattr("sys.argv", ["pipeline", "prefill", "collect", "--source", "sentences", "--file", str(path),
                                     "--dry-run"])
    cli.main()
    out = capsys.readouterr().out
    assert "2 sentences in" in out and "1 lines left out" in out and "2 to add" in out
    assert "Estimated cost" in out and "Dry run: nothing was written." in out


def test_sentences_already_queued_are_skipped_by_their_loose_key(queue):
    prefill.insert_items(queue, [Item("sentence", "looks good", "vi", "oewn-2025", 1)])
    assert prefill.queued_sentence_keys(queue) == {"looks good"}
    items = [Item("sentence", "Looks good.", "vi", "common-sentences", 950),
             Item("sentence", "Well done.", "vi", "common-sentences", 950)]
    new = [i for i in items if prefill.loose_key(i.text) not in prefill.queued_sentence_keys(queue)]
    assert prefill.insert_items(queue, new) == 1
    assert queue.execute("SELECT source, priority FROM prefill_item WHERE text = 'Well done.'").fetchone() == (
        "common-sentences", 950)
