"""Word forms: what each form of a word is ("ran" is the past tense of "run").

Uses the free library lemminflect. The export stores the label in the `type` column of the forms table.
"""

from lemminflect import getAllInflections

# lemminflect tag -> label shown in the app, grouped by kind of word.
# Inside a group the first tag wins ("walked" is both VBD and VBN: we say past tense).
TAG_GROUPS = {
    "VERB": [("VBD", "past tense"), ("VBN", "past participle"), ("VBG", "-ing form"), ("VBZ", "third person")],
    "NOUN": [("NNS", "plural")],
    "ADJ": [("JJR", "comparative"), ("JJS", "superlative")],
    "ADV": [("RBR", "comparative"), ("RBS", "superlative")],
}

# Our sense.pos words -> the part of speech names lemminflect uses.
UPOS_BY_POS = {"verb": "VERB", "noun": "NOUN", "adjective": "ADJ", "adverb": "ADV"}

UNKNOWN_TYPE = "form"


def _groups(pos_list: list[str] | None) -> list[str]:
    groups = [UPOS_BY_POS[p] for p in (pos_list or []) if p in UPOS_BY_POS]
    return list(dict.fromkeys(groups)) or list(TAG_GROUPS)  # unknown parts of speech: try every group


def _tags_by_form(lemma: str, pos_list: list[str] | None) -> dict[str, set[tuple[str, str]]]:
    """Returns {form: {(group, tag), ...}} for the groups that fit the word's parts of speech."""
    result: dict[str, set[tuple[str, str]]] = {}
    for group in _groups(pos_list):
        wanted = {tag for tag, _ in TAG_GROUPS[group]}
        for tag, forms in getAllInflections(lemma, upos=group).items():
            if tag in wanted:
                for form in forms:
                    result.setdefault(form.lower(), set()).add((group, tag))
    return result


def form_type(lemma: str, form: str, pos_list: list[str] | None = None) -> str:
    """The label for one form of a word, e.g. form_type("run", "ran", ["verb"]) == "past tense".

    The answer is "form" when we do not know, when two kinds of word fit ("runs" is a plural noun and
    the third person of a verb), or when the form is the word itself.
    """
    form = form.lower()
    if form == lemma.lower():
        return UNKNOWN_TYPE
    found = _tags_by_form(lemma, pos_list).get(form)
    if not found or len({group for group, _ in found}) > 1:
        return UNKNOWN_TYPE
    group = next(iter(found))[0]
    for tag, label in TAG_GROUPS[group]:
        if (group, tag) in found:
            return label
    return UNKNOWN_TYPE


def word_forms(lemma: str) -> set[str]:
    """The word and every form lemminflect knows for it, lowercase. Used to check that an example contains the word."""
    result = {lemma.lower()}
    for forms in getAllInflections(lemma).values():
        result.update(form.lower() for form in forms)
    return result
