"""Offline tests for the Sources page word lists (TASK-108). Word frequency and WordNet are fakes."""

from pipeline import source_lists

ZIPF = {"give up": 4.2, "the": 7.0, "zebra": 3.1, "ghost": 0.0}


def zipf_of(text):
    return ZIPF.get(text, 1.5)


def test_words_need_a_zipf_above_zero_and_a_plain_lowercase_spelling():
    rows = source_lists.word_rows({"zebra", "ghost", "Paris", "don't", "the"}, zipf_of)
    assert rows == [("the", 7.0), ("zebra", 3.1)]


def test_only_verb_adjective_and_adverb_phrases_are_kept_in_plain_form():
    lemmas = {
        "give up": {"v"},
        "rat cheese": {"n"},  # a compound noun
        "New York": {"n"},  # a proper noun
        "look after somebody": {"v"},  # WordNet placeholder
        "in the long run": {"r"},
    }
    rows = source_lists.phrase_rows(lemmas, zipf_of)
    assert rows == [("give up", 4.2), ("in the long run", 1.5)]


def test_export_writes_both_files(tmp_path):
    (tmp_path / "scowl-70.txt").write_text("zebra\nthe\n", encoding="utf-8")
    (tmp_path / "ngsl.txt").write_text("# comment\ngive\n", encoding="utf-8")
    counts = source_lists.export(tmp_path, {"give up": {"v"}}, zipf_of)
    assert counts == (3, 1)
    assert (tmp_path / "zipf-en.tsv").read_text().splitlines()[-1] == "zebra\t3.10"
    assert (tmp_path / "phrases-en.tsv").read_text() == "give up\t4.20\n"
