"""Checks that LLM definitions only use simple words.

This is the "defining vocabulary" method that learner dictionaries use: every
definition may only use words from a fixed list. Our list is NGSL plus all its
inflected forms, so no lemmatizer is needed.
"""

import re

from pipeline.models import WordEntry

WORD_RE = re.compile(r"[a-zA-Z]+(?:'[a-z]+)?")
MAX_DEFINITION_WORDS = 20

# Short words that are fine in any definition but are not NGSL lemmas.
EXTRA_ALLOWED = {"someone", "something", "somebody", "etc", "e", "g", "s", "t", "don't", "isn't"}

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


def build_vocabulary(ngsl_words) -> set[str]:
    vocabulary = EXTRA_ALLOWED | NUMBERS_AND_DATES | GRAMMAR_TERMS
    for word in ngsl_words:
        vocabulary.add(word.lemma.lower())
        vocabulary.update(form.lower() for form in word.forms)
    return vocabulary


def find_problems(headword: str, entry: WordEntry, vocabulary: set[str], rank: int | None = None) -> list[str]:
    """Returns human-readable problems. An empty list means the entry passed."""
    problems: list[str] = []
    if not entry.senses:
        return ["no senses returned"]

    for number, sense in enumerate(entry.senses, start=1):
        words = [w.lower() for w in WORD_RE.findall(sense.simple_definition)]
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
    return problems
