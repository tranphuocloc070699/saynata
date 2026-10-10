"""Downloads raw data: the NGSL word list and Wiktionary entries from Kaikki.

Every download is cached in data/raw/, so running twice never downloads twice.
"""

import csv
import json
from dataclasses import dataclass
from pathlib import Path

import httpx

RAW_DIR = Path("data/raw")
NGSL_BASE = "https://www.newgeneralservicelist.com/s"
KAIKKI_BASE = "https://kaikki.org/dictionary/English/meaning"


@dataclass
class NgslWord:
    lemma: str
    rank: int
    forms: list[str]  # inflected forms, e.g. "abandons", "abandoned"


def _download(url: str, path: Path) -> Path:
    if path.exists():
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    response = httpx.get(url, follow_redirects=True, timeout=30)
    response.raise_for_status()
    path.write_bytes(response.content)
    return path


def _normalize(lemma: str) -> str:
    # The CSV was saved through Excel, which turned "true"/"false" into "TRUE"/"FALSE".
    return lemma if lemma == "I" else lemma.lower()


def load_ngsl() -> list[NgslWord]:
    """Returns NGSL words in frequency order, each with its inflected forms."""
    stats = _download(f"{NGSL_BASE}/NGSL_12_stats.csv", RAW_DIR / "ngsl_stats.csv")
    lemmas = _download(
        f"{NGSL_BASE}/NGSL_12_lemmatized_for_teaching.csv", RAW_DIR / "ngsl_lemmas.csv"
    )

    forms_by_lemma: dict[str, list[str]] = {}
    for line in lemmas.read_text().splitlines():
        if not line.strip() or line.startswith("##"):
            continue
        parts = [_normalize(p.strip()) for p in line.split(",") if p.strip()]
        forms_by_lemma.setdefault(parts[0], []).extend(parts[1:])

    with stats.open() as file:
        rows = list(csv.DictReader(file))
    return [
        NgslWord(
            lemma=_normalize(row["Lemma"]),
            rank=int(row["SFI Rank"]),
            forms=forms_by_lemma.get(_normalize(row["Lemma"]), []),
        )
        for row in rows
    ]


def load_kaikki(word: str) -> list[dict]:
    """Returns all Wiktionary entries for a word (one per part of speech)."""
    url = f"{KAIKKI_BASE}/{word[0]}/{word[:2]}/{word}.jsonl"
    path = _download(url, RAW_DIR / "kaikki" / f"{word}.jsonl")
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
