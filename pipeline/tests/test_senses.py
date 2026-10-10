"""Offline tests for sense filtering, using small hand-made Kaikki entries."""

from pipeline.senses import MAX_PER_ENTRY, build_source


def sense(gloss, tags=(), form_of=None):
    return {"glosses": [gloss], "tags": list(tags), **({"form_of": form_of} if form_of else {})}


def test_drops_archaic_and_word_form_senses():
    entries = [{"pos": "verb", "senses": [
        sense("simple past of run", tags=["form-of"], form_of=[{"word": "run"}]),
        sense("an old meaning", tags=["archaic"]),
        sense("to move fast"),
    ]}]
    assert [c.gloss for c in build_source(entries).candidates] == ["to move fast"]


def test_keeps_both_homograph_entries():
    money = {"pos": "noun", "senses": [sense(f"money sense {i}") for i in range(10)]}
    river = {"pos": "noun", "senses": [sense("the edge of a river")]}
    glosses = [c.gloss for c in build_source([money, river]).candidates]
    assert len(glosses) == MAX_PER_ENTRY + 1
    assert "the edge of a river" in glosses


def test_skips_proper_names_and_reads_vietnamese_hints():
    entries = [
        {"pos": "name", "senses": [sense("a surname")]},
        {"pos": "adj", "senses": [sense("unwilling")], "translations": [{"code": "vi", "word": "miễn cưỡng"}]},
    ]
    source = build_source(entries)
    assert [c.pos for c in source.candidates] == ["adj"]
    assert source.vietnamese_hints == ["miễn cưỡng"]
