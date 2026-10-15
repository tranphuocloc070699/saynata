"""Checks that LLM definitions only use simple words.

This is the "defining vocabulary" method that learner dictionaries use: every
definition may only use words from a fixed list. Our list is NGSL plus all its
inflected forms, so no lemmatizer is needed.
"""

import re

from pipeline.forms import word_forms
from pipeline.models import EXAMPLE_LEVELS, MAX_USAGE_PER_SENSE, Example, ExampleLevel, Usage, WordEntry

WORD_RE = re.compile(r"[a-zA-Z]+(?:'[a-z]+)?")
MAX_DEFINITION_WORDS = 20

# Short words that are fine in any definition but are not NGSL lemmas.
EXTRA_ALLOWED = {"someone", "something", "somebody", "etc", "e", "g", "s", "t", "don't", "isn't"}

# Basic words that NGSL misses but learners know early. Found in definitions that
# failed only because of these words (TASK-3).
BASIC_WORDS = {
    "container", "cloth", "trousers", "pants", "insect", "bee", "ant", "snake", "pet", "fur", "feathers", "wool",
    "punishment", "punished", "polite", "politely", "rude", "unhappy", "sadness", "secretly", "officially",
    "unpleasant", "unwanted", "painful", "annoying", "awake", "shiny", "sour", "spicy", "outer",
    "queen", "mrs", "waiter", "ghost", "uk", "europe", "english", "alphabet", "tv", "internet", "password",
    "goods", "jewellery", "thread", "threads", "powder", "blankets", "calendar", "oven", "soap", "ceiling",
    "carrot", "grapes", "butter", "corn", "flour", "kilo", "kilograms", "kilometres",
}

# NGSL leaves out numbers, days and months on purpose, but they are basic words.
NUMBERS_AND_DATES = {
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
    "nineteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    "hundred", "hundreds", "thousand", "thousands", "million", "millions",
    "first", "second", "third", "half",
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", "august",
    "september", "october", "november", "december",
}

# Grammar words ("a", "to", "the") can only be explained with grammar terms.
GRAMMAR_TERMS = {
    "noun", "nouns", "verb", "verbs", "adjective", "adjectives", "adverb", "adverbs",
    "pronoun", "singular", "plural", "infinitive", "tense", "phrase", "phrases", "clause",
}

# The most common words are mostly grammar words. Their definitions often need
# the word itself ("a" is used in "used before a noun"), so we skip that check.
GRAMMAR_WORD_MAX_RANK = 100


# Learner books use "sb" and "sth". We write the full words, so the card is easy to read.
BANNED_PLACEHOLDERS = {"sb", "sth", "smb", "somebody"}

# Everyday words that NGSL misses (found by checking the agreed usage examples). Only for usage examples.
EXAMPLE_EXTRA_WORDS = {"me", "my", "oh"}

# "can't" is "can" + not. Other "n't" words just drop the ending ("don't" -> "do").
IRREGULAR_NEGATIVES = {"can't": "can", "won't": "will", "shan't": "shall"}
CONTRACTION_ENDINGS = ("'s", "'m", "'re", "'ve", "'ll", "'d", "n't")


def _plain_word(word: str) -> str:
    """"I'm" -> "i", "They've" -> "they": a contraction counts as its base word."""
    word = word.lower()
    if word in IRREGULAR_NEGATIVES:
        return IRREGULAR_NEGATIVES[word]
    for ending in CONTRACTION_ENDINGS:
        if word.endswith(ending):
            return word.removesuffix(ending)
    return word


def usage_problems(item: Usage, vocabulary: set[str], headword_forms: frozenset[str] | set[str] = frozenset()) -> list[str]:
    """Checks one usage item. An empty list means it is good enough to keep.
    `headword_forms`: the headword and its forms may always appear (topic words like "giraffe" are not in NGSL)."""
    problems: list[str] = []
    if not item.pattern.strip() or not item.example.strip():
        return ["pattern or example is empty"]
    pattern_words = {w.lower() for w in WORD_RE.findall(item.pattern)}
    if pattern_words & BANNED_PLACEHOLDERS:
        problems.append(f"pattern '{item.pattern}' uses sb/sth style placeholders (write someone, something)")
    example = item.example.replace("’", "'")  # curly apostrophe -> straight
    hard = sorted({_plain_word(w) for w in WORD_RE.findall(example)} - vocabulary - EXAMPLE_EXTRA_WORDS - headword_forms)
    if hard:
        problems.append(f"example '{item.example}' uses hard words {hard}")
    return problems


# A "harder" example may use a few words outside the vocabulary (B2 words), but not many.
# Words outside the vocabulary that an example may have: easy none, medium 1, harder 2.
MAX_OUTSIDE_WORDS = {ExampleLevel.EASY: 0, ExampleLevel.MEDIUM: 1, ExampleLevel.HARDER: 2}


def example_problems(example: Example, headword: str, headword_forms: set[str], vocabulary: set[str]) -> list[str]:
    """Checks one example. An empty list means it is good enough to keep.

    Easy: every word must be in the vocabulary (the same check as usage examples).
    Medium: up to 1 word may be outside it. Harder: up to 2. Every level must contain the headword or one of its forms.
    The headword and its forms never count as outside words (topic words like "giraffe" are not in NGSL).
    `headword_forms` holds the lemma and its inflected forms, lowercase.
    """
    text = example.text.strip()
    if not text:
        return ["example is empty"]
    words = {_plain_word(w) for w in WORD_RE.findall(text.replace("’", "'"))}
    problems: list[str] = []
    outside = sorted(words - vocabulary - EXAMPLE_EXTRA_WORDS - headword_forms - {headword.lower()})
    allowed = MAX_OUTSIDE_WORDS[example.level]
    if len(outside) > allowed:
        problems.append(f"{example.level.value} example '{text}' uses hard words {outside}")
    if not words & (headword_forms | {headword.lower()}):
        problems.append(f"{example.level.value} example '{text}' does not use '{headword}'")
    return problems


def build_vocabulary(ngsl_words) -> set[str]:
    vocabulary = EXTRA_ALLOWED | BASIC_WORDS | NUMBERS_AND_DATES | GRAMMAR_TERMS
    for word in ngsl_words:
        vocabulary.add(word.lemma.lower())
        vocabulary.update(form.lower() for form in word.forms)
    return vocabulary


def find_problems(headword: str, entry: WordEntry, vocabulary: set[str], rank: int | None = None) -> list[str]:
    """Returns human-readable problems. An empty list means the entry passed."""
    problems: list[str] = []
    if not entry.senses:
        return ["no senses returned"]
    headword_forms = word_forms(headword)

    for number, sense in enumerate(entry.senses, start=1):
        # "a person's name" checks "person": the possessive 's is not a new word.
        words = [w.lower().removesuffix("'s") for w in WORD_RE.findall(sense.simple_definition)]
        hard = sorted({w for w in words if w not in vocabulary})
        if hard:
            problems.append(f"sense {number}: definition uses hard words {hard}")
        is_grammar_word = rank is not None and rank <= GRAMMAR_WORD_MAX_RANK
        if headword.lower() in words and not is_grammar_word:
            problems.append(f"sense {number}: definition uses the headword itself")
        if len(words) > MAX_DEFINITION_WORDS:
            problems.append(f"sense {number}: definition has {len(words)} words (max {MAX_DEFINITION_WORDS})")
        if not sense.vietnamese.strip():
            problems.append(f"sense {number}: Vietnamese is empty")
        if len(sense.examples) < 2:
            problems.append(f"sense {number}: needs 2 examples")
        if len(sense.usage) > MAX_USAGE_PER_SENSE:
            problems.append(f"sense {number}: {len(sense.usage)} usage items (max {MAX_USAGE_PER_SENSE})")
        for item in sense.usage:
            problems.extend(f"sense {number}: usage: {problem}" for problem in usage_problems(item, vocabulary, headword_forms))
    return problems
