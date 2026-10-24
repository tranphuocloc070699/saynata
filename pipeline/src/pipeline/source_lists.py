"""Word lists for the admin Sources page (TASK-108). No paid call.

The backend (`sources/`) reads a book and needs two small files in `backend/src/main/resources/words/`:

- `zipf-en.tsv`: `word<TAB>zipf` for every word on the backend word list (SCOWL-70 and NGSL) that wordfreq has
  seen. The Zipf score tells how common a word is (7 = "the", 3 = a word you meet now and then, 1 = very rare).
- `phrases-en.tsv`: `phrase<TAB>zipf` for the phrasal verbs and idioms of Open English WordNet 2025 (2-4 words,
  lower case, same rules as `prefill collect --source phrases`, but without a minimum score).

Run it again when the word list or WordNet changes: `uv run pipeline source-lists`.
"""

from collections.abc import Callable
from pathlib import Path

from pipeline import prefill

ZIPF_FILE = "zipf-en.tsv"
PHRASES_FILE = "phrases-en.tsv"


def default_zipf(text: str) -> float:
    from wordfreq import zipf_frequency

    return zipf_frequency(text, "en")


def word_rows(words: set[str], zipf_of: Callable[[str], float] = default_zipf) -> list[tuple[str, float]]:
    """Lower-case plain words with a Zipf above 0, sorted by word."""
    rows = []
    for word in sorted(words):
        if not (word.isascii() and word.isalpha() and word.islower()):
            continue
        zipf = zipf_of(word)
        if zipf > 0:
            rows.append((word, zipf))
    return rows


def phrase_rows(
    lemmas: dict[str, set[str]], zipf_of: Callable[[str], float] = default_zipf
) -> list[tuple[str, float]]:
    """Phrasal verbs and idioms (verb, adjective or adverb in WordNet), sorted by phrase."""
    found: dict[str, float] = {}
    for lemma, parts_of_speech in lemmas.items():
        text = prefill.normalize_phrase(lemma)
        if text is None or text in found or not parts_of_speech & prefill.PHRASE_POS:
            continue
        found[text] = zipf_of(text)
    return sorted(found.items())


def write_tsv(path: Path, rows: list[tuple[str, float]]) -> None:
    path.write_text("".join(f"{text}\t{zipf:.2f}\n" for text, zipf in rows), encoding="utf-8")


def export(
    directory: Path = prefill.WORDS_DIR,
    lemmas: dict[str, set[str]] | None = None,
    zipf_of: Callable[[str], float] = default_zipf,
) -> tuple[int, int]:
    """Writes both files. Returns (words, phrases). `lemmas` is for tests; the default loads WordNet."""
    words = prefill.read_word_list("scowl-70.txt", directory) | prefill.read_word_list("ngsl.txt", directory)
    if lemmas is None:
        lemmas = prefill.wordnet_lemmas(prefill.load_wordnet())
    word_list = word_rows(words, zipf_of)
    phrase_list = phrase_rows(lemmas, zipf_of)
    write_tsv(directory / ZIPF_FILE, word_list)
    write_tsv(directory / PHRASES_FILE, phrase_list)
    return len(word_list), len(phrase_list)
