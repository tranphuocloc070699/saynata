"""Turns raw Kaikki entries into a short list of candidate senses for the LLM.

We drop senses a learner never needs (archaic, rare, word forms like "ran"),
so the prompt stays small and the LLM picks from useful meanings only.
"""

from dataclasses import dataclass

SKIP_POS = {"name", "character", "symbol", "prefix", "suffix", "infix", "phrase", "proverb"}
SKIP_TAGS = {
    "archaic", "obsolete", "dated", "rare", "dialectal", "nonstandard",
    "misspelling", "form-of", "alt-of", "abbreviation", "initialism",
}
# Wiktionary lists the most common senses first, so we keep the top few of each
# entry. One entry = one part of speech of one origin, so "bank" (money) and
# "bank" (river) are separate entries and both survive. A global cap would not.
MAX_PER_ENTRY = 5


@dataclass
class Candidate:
    index: int
    pos: str
    gloss: str
    example: str | None


@dataclass
class WordSource:
    """Everything we take from Wiktionary for one word, before the LLM step."""

    ipa: str | None
    candidates: list[Candidate]
    vietnamese_hints: list[str]


def build_source(entries: list[dict]) -> WordSource:
    candidates: list[Candidate] = []
    for entry in entries:
        if entry.get("pos") in SKIP_POS:
            continue
        kept = 0
        for sense in entry.get("senses", []):
            if kept >= MAX_PER_ENTRY:
                break
            tags = set(sense.get("tags", []))
            glosses = sense.get("glosses")
            if not glosses or tags & SKIP_TAGS or sense.get("form_of"):
                continue
            candidates.append(
                Candidate(
                    index=len(candidates),
                    pos=entry["pos"],
                    gloss=glosses[-1],
                    example=_first_example(sense),
                )
            )
            kept += 1

    return WordSource(
        ipa=_first_ipa(entries),
        candidates=candidates,
        vietnamese_hints=_vietnamese_hints(entries),
    )


def _first_example(sense: dict) -> str | None:
    for example in sense.get("examples", []):
        if example.get("type") == "example" and example.get("text"):
            return example["text"]
    return None


def _first_ipa(entries: list[dict]) -> str | None:
    # Prefer US pronunciation; fall back to any IPA.
    sounds = [s for e in entries for s in e.get("sounds", []) if s.get("ipa")]
    for sound in sounds:
        if "US" in sound.get("tags", []) or "General-American" in sound.get("tags", []):
            return sound["ipa"]
    return sounds[0]["ipa"] if sounds else None


def _vietnamese_hints(entries: list[dict]) -> list[str]:
    hints = {
        t["word"]
        for e in entries
        for t in e.get("translations", [])
        if t.get("code") == "vi" and t.get("word")
    }
    return sorted(hints)
