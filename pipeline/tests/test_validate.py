"""Offline tests for the checker. No network, no LLM."""

from pipeline.models import WordEntry
from pipeline.validate import find_problems

VOCABULARY = {"not", "wanting", "to", "do", "something", "a", "feeling"}


def make_entry(definition: str, examples=("One.", "Two."), vietnamese="miễn cưỡng") -> WordEntry:
    return WordEntry.model_validate({"senses": [{
        "source_index": 0, "pos": "adjective", "cefr": "B2",
        "simple_definition": definition, "examples": list(examples), "vietnamese": vietnamese,
        "usage": [], "image_type": "none", "image_query": "",
    }]})


def test_simple_definition_passes():
    assert find_problems("reluctant", make_entry("not wanting to do something"), VOCABULARY) == []


def test_hard_word_is_reported():
    problems = find_problems("reluctant", make_entry("a recalcitrant feeling"), VOCABULARY)
    assert problems == ["sense 1: definition uses hard words ['recalcitrant']"]


def test_headword_in_definition_is_reported():
    VOCABULARY_WITH_HEADWORD = VOCABULARY | {"reluctant"}
    problems = find_problems("reluctant", make_entry("a reluctant feeling"), VOCABULARY_WITH_HEADWORD)
    assert problems == ["sense 1: definition uses the headword itself"]


def test_missing_vietnamese_and_examples_are_reported():
    problems = find_problems("reluctant", make_entry("not wanting to do something", examples=("One.",), vietnamese=" "), VOCABULARY)
    assert problems == ["sense 1: Vietnamese is empty", "sense 1: needs 2 examples"]


def test_numbers_and_grammar_terms_are_allowed():
    from pipeline.validate import build_vocabulary

    vocabulary = build_vocabulary([])
    entry = make_entry("used before a singular noun when there are two")
    assert find_problems("than", entry, vocabulary | {"a", "used", "before", "when", "there", "are"}) == []


def test_grammar_words_may_use_themselves():
    vocabulary = VOCABULARY | {"a", "used", "before"}
    entry = make_entry("used before a something")
    assert find_problems("a", entry, vocabulary, rank=6) == []
    assert find_problems("a", entry, vocabulary, rank=500) == ["sense 1: definition uses the headword itself"]


def test_possessive_counts_as_the_base_word():
    vocabulary = VOCABULARY | {"person", "name"}
    assert find_problems("reluctant", make_entry("a person's name"), vocabulary) == []


def test_basic_words_outside_ngsl_are_allowed():
    from pipeline.validate import build_vocabulary

    vocabulary = build_vocabulary([])
    assert {"container", "rude", "tv"} <= vocabulary


def test_a_topic_word_may_use_itself_in_usage_examples():
    """Topic words (TASK-61) like "giraffe" are not in NGSL. Their own usage example must not fail because of them."""
    from pipeline.models import Usage, UsageKind
    from pipeline.validate import usage_problems

    item = Usage(kind=UsageKind.PHRASE, pattern="a tall giraffe", example="The giraffes are tall.")
    vocabulary = {"the", "are", "tall", "a"}
    assert usage_problems(item, vocabulary) != []
    assert usage_problems(item, vocabulary, {"giraffe", "giraffes"}) == []


def test_a_topic_word_is_not_an_outside_word_in_its_own_example():
    from pipeline.models import Example, ExampleLevel
    from pipeline.validate import example_problems

    easy = Example(level=ExampleLevel.EASY, text="I see a giraffe.")
    assert example_problems(easy, "giraffe", {"giraffe", "giraffes"}, {"i", "see", "a"}) == []
