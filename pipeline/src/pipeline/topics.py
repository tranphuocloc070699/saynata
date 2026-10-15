"""Topic words (TASK-61): everyday concrete words NGSL misses ("apple", "giraffe", "scarf"), each with one topic.

Two steps, both resumable and saved in the topic_words table of data/pipeline.db (the NGSL words table is not touched):
1. candidates: Open English WordNet 2025 gives the topic, wordfreq keeps only common words, NGSL words are removed.
   The word's FIRST WordNet sense decides the topic. That keeps out most rare meanings, but OEWN 2025 does not order
   senses by frequency ("tiger" starts with "a fierce person"), so EXTRA_WORDS adds the everyday words it misses.
2. judge: Claude Haiku looks at 50 words per call and keeps a word only when its everyday meaning is a concrete thing
   (or a common action or quality) a learner needs. It drops names (wordfreq has no capital letters, so "charlotte"
   looks common), rare meanings ("bishop" as a drink) and technical words. `fetch` is a parameter, so tests use a fake.

Sources: Open English WordNet (CC BY 4.0) and wordfreq (Apache 2.0 code, CC BY-SA 4.0 data), see licenses/.
"""

import csv
import json
import sqlite3
from collections import defaultdict
from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

import httpx
from pydantic import BaseModel, Field

from pipeline.sources import RAW_DIR

OEWN_URL = "https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz"
OEWN_LEXICON = "oewn:2025"
WN_DIR = RAW_DIR / "wn"

JUDGE_MODEL = "claude-haiku-5-5"
JUDGE_BATCH = 50


class Topic(str, Enum):
    ANIMALS = "animals"
    FOOD = "food and drink"
    PLANTS = "plants"
    BODY = "the body"
    CLOTHES = "clothes"
    HOME = "home"
    TRANSPORT = "transport"
    WEATHER = "weather"
    TOOLS = "tools and objects"
    PLACES = "places and the street"
    NATURE = "nature"
    PEOPLE = "people and jobs"


# Nouns whose lexicographer file already is the topic.
LEXFILE_TOPIC = {
    "noun.animal": Topic.ANIMALS,
    "noun.food": Topic.FOOD,
    "noun.plant": Topic.PLANTS,
    "noun.object": Topic.NATURE,  # rock, river, island
    "noun.location": Topic.PLACES,
}
# noun.artifact mixes shirts, buses and hammers, so these nouns get their topic from a hypernym ("anchor").
# Checked in this order, first match wins. Each anchor is (lemma, lexfile or None) to pick the right synset.
ANCHORS: list[tuple[Topic, list[tuple[str, str | None]]]] = [
    (Topic.WEATHER, [("weather", "noun.phenomenon"), ("atmospheric phenomenon", "noun.phenomenon"),
                     ("precipitation", "noun.phenomenon"), ("wind", "noun.phenomenon"), ("cloud", "noun.phenomenon")]),
    (Topic.CLOTHES, [("clothing", "noun.artifact"), ("footwear", "noun.artifact"), ("headdress", "noun.artifact"),
                     ("jewelry", "noun.artifact"), ("bag", "noun.artifact"), ("glasses", "noun.artifact")]),
    (Topic.TRANSPORT, [("conveyance", "noun.artifact"), ("vehicle", "noun.artifact"), ("craft", "noun.artifact"),
                       ("wheel", "noun.artifact")]),
    (Topic.HOME, [("furniture", "noun.artifact"), ("furnishing", "noun.artifact"), ("room", "noun.artifact"),
                  ("home appliance", "noun.artifact"), ("kitchen utensil", "noun.artifact"), ("tableware", "noun.artifact"),
                  ("cutlery", "noun.artifact"), ("crockery", "noun.artifact"), ("bedclothes", "noun.artifact"),
                  ("toiletry", "noun.artifact"), ("door", "noun.artifact"), ("window", "noun.artifact"),
                  ("roof", "noun.artifact"), ("stairs", "noun.artifact"), ("lamp", "noun.artifact"),
                  ("floor cover", "noun.artifact"), ("piece of cloth", "noun.artifact"), ("cleansing agent", None)]),
    (Topic.PLACES, [("structure", "noun.artifact"), ("way", "noun.artifact"), ("facility", "noun.artifact"),
                    ("establishment", "noun.artifact"), ("area", "noun.artifact"), ("geographical area", "noun.location"),
                    ("place of business", "noun.artifact")]),
    (Topic.TOOLS, [("tool", "noun.artifact"), ("implement", "noun.artifact"), ("device", "noun.artifact"),
                   ("container", "noun.artifact"), ("equipment", "noun.artifact"), ("toy", "noun.artifact"),
                   ("musical instrument", "noun.artifact"), ("building material", "noun.artifact"),
                   ("paper", "noun.artifact"), ("protective covering", "noun.artifact")]),
    # noun.body also has cells and chromosomes: keep the parts you can point at.
    (Topic.BODY, [("body part", "noun.body"), ("body covering", "noun.body"), ("hair", "noun.body"),
                  ("tooth", "noun.body"), ("fluid", "noun.body")]),
    # noun.person is huge (lord, republican, genius): keep only jobs and family.
    (Topic.PEOPLE, [("worker", "noun.person"), ("professional", "noun.person"), ("relative", "noun.person"),
                    ("child", "noun.person")]),
]
ANCHOR_LEXFILES = {"noun.artifact", "noun.phenomenon", "noun.location", "noun.person", "noun.body"}

# A few verbs: the first sense's lexfile decides. Verbs must be a little more common than nouns.
VERB_TOPIC = {"verb.consumption": Topic.FOOD, "verb.body": Topic.BODY, "verb.weather": Topic.WEATHER}
VERB_ZIPF_BONUS = 0.3

# Picture words children learn early but adults rarely write (giraffe 3.2) may be a little less common.
LOWER_ZIPF = {Topic.ANIMALS: 0.4, Topic.FOOD: 0.3, Topic.PLANTS: 0.3, Topic.HOME: 0.3, Topic.CLOTHES: 0.3,
              Topic.TRANSPORT: 0.3}
LOWER_ZIPF_MAX = max(LOWER_ZIPF.values())

# Never in a learner list. The gloss check catches most rude words; these slip through it.
BLOCKED = {"ass", "crap", "cock", "dick", "pussy", "rape", "tit", "tits", "bitch", "piss", "fag", "boob", "boobs",
           "marijuana", "weed", "pot", "dope", "sperm", "condom", "lingerie"}
BLOCKED_GLOSS = ("obscene", "vulgar", "offensive", "disparaging", "slang", "ethnic slur", "derogatory")

# Everyday words the first-sense rule misses: compounds, a wrong first sense in OEWN 2025, and the adjectives a
# learner uses with these topics. Our own list; words already in NGSL are skipped when it is used.
EXTRA_WORDS: dict[str, Topic] = {
    **dict.fromkeys(["tiger", "whale", "donkey", "goat", "parrot", "octopus", "jellyfish", "tortoise", "beetle",
                     "butterfly", "squirrel", "camel", "kitten", "puppy", "fur"], Topic.ANIMALS),
    **dict.fromkeys(["pancake", "sandwich", "noodle", "lemonade", "cupcake", "popcorn", "breakfast"], Topic.FOOD),
    **dict.fromkeys(["sunflower", "cactus"], Topic.PLANTS),
    **dict.fromkeys(["moustache", "beard", "eyebrow", "toe", "curly", "bald"], Topic.BODY),
    **dict.fromkeys(["handbag", "scarf", "pyjamas", "pajamas", "trainers", "sneakers", "swimsuit", "bracelet",
                     "necklace", "umbrella", "helmet", "striped", "spotted"], Topic.CLOTHES),
    **dict.fromkeys(["toothbrush", "toothpaste", "armchair", "bookcase", "cupboard", "wardrobe", "pillow",
                     "shampoo", "towel", "upstairs", "downstairs", "doorbell", "bathtub", "dishwasher"], Topic.HOME),
    **dict.fromkeys(["motorbike", "railway", "motorway", "spaceship", "subway", "taxi"], Topic.TRANSPORT),
    **dict.fromkeys(["sunny", "cloudy", "windy", "rainy", "foggy", "snowy", "stormy", "rainbow", "puddle"],
                    Topic.WEATHER),
    **dict.fromkeys(["scissors", "paintbrush", "flashlight", "torch", "screwdriver", "ladder", "kite"], Topic.TOOLS),
    **dict.fromkeys(["sidewalk", "pavement", "crossroads", "playground", "supermarket", "roundabout"], Topic.PLACES),
    **dict.fromkeys(["astronaut", "businessman", "clown", "pirate", "firefighter", "dentist"], Topic.PEOPLE),
}


@dataclass
class Candidate:
    word: str
    pos: str  # "n", "v" or "a"
    zipf: float
    topic: Topic
    gloss: str  # the WordNet definition the topic came from, shown to the judge


# ---------- step 1: candidates ----------


def ensure_wordnet():
    """Downloads OEWN 2025 once (11 MB) and adds it to a local wn database (once, takes several minutes)."""
    import wn

    wn.config.data_directory = str(WN_DIR)
    if not wn.lexicons(lexicon=OEWN_LEXICON):
        archive = RAW_DIR / "english-wordnet-2025.xml.gz"
        if not archive.exists():
            archive.parent.mkdir(parents=True, exist_ok=True)
            with httpx.stream("GET", OEWN_URL, follow_redirects=True, timeout=120) as response:
                response.raise_for_status()
                with archive.open("wb") as file:
                    for chunk in response.iter_bytes():
                        file.write(chunk)
        print("Adding Open English WordNet 2025 to the local wn database (several minutes, only once)...")
        wn.add(str(archive), progress_handler=None)
    return wn.Wordnet(OEWN_LEXICON)


def known_words(connection: sqlite3.Connection) -> set[str]:
    """NGSL lemmas and all their forms, so "apples" never comes back as a new word."""
    known = set()
    for row in connection.execute("SELECT lemma, forms FROM words"):
        known.add(row[0].lower())
        known.update(form.lower() for form in json.loads(row[1] or "[]"))
    return known


def is_simple_word(form: str) -> bool:
    return form.isalpha() and form.islower() and len(form) > 2


def min_zipf_for(topic: Topic, pos: str, min_zipf: float) -> float:
    return min_zipf - LOWER_ZIPF.get(topic, 0) + (VERB_ZIPF_BONUS if pos == "v" else 0)


def _anchor_ids(wordnet) -> list[tuple[Topic, set[str]]]:
    result = []
    for topic, pairs in ANCHORS:
        ids = set()
        for lemma, lexfile in pairs:
            for synset in wordnet.synsets(lemma, pos="n"):
                if lexfile is None or synset.lexfile() == lexfile:
                    ids.add(synset.id)
                    break
        result.append((topic, ids))
    return result


def _hypernym_ids(synset) -> set[str]:
    seen, stack = {synset.id}, [synset]
    while stack:
        for parent in stack.pop().hypernyms():
            if parent.id not in seen:
                seen.add(parent.id)
                stack.append(parent)
    return seen


def _noun_topic(synset, anchors) -> Topic | None:
    lexfile = synset.lexfile()
    if lexfile in ANCHOR_LEXFILES:
        ids = _hypernym_ids(synset)
        for topic, anchor_ids in anchors:
            if ids & anchor_ids:
                return topic
    return LEXFILE_TOPIC.get(lexfile)


def _sense_count(wordnet, form: str, pos: str) -> int:
    return sum(len(word.senses()) for word in wordnet.words(form, pos=pos))


def _mainly(wordnet, form: str, pos: str) -> bool:
    """Nouns: not mainly an adjective ("holy"). Verbs: not mainly a noun."""
    if pos == "n":
        return _sense_count(wordnet, form, "n") >= _sense_count(wordnet, form, "a") + _sense_count(wordnet, form, "s")
    return _sense_count(wordnet, form, "v") >= _sense_count(wordnet, form, "n")


def _is_rude(wordnet, form: str) -> bool:
    return any(bad in (sense.synset().definition() or "")
               for word in wordnet.words(form) for sense in word.senses()[:2] for bad in BLOCKED_GLOSS)


def build_candidates(wordnet, known: set[str], min_zipf: float = 3.5) -> list[Candidate]:
    """Every common WordNet word (not in NGSL) whose first sense sits in a topic, plus EXTRA_WORDS."""
    from wordfreq import zipf_frequency

    anchors = _anchor_ids(wordnet)
    found: dict[str, Candidate] = {}
    for pos, topic_of in (("n", lambda s: _noun_topic(s, anchors)), ("v", lambda s: VERB_TOPIC.get(s.lexfile()))):
        for word in wordnet.words(pos=pos):
            form = word.lemma()
            if form in found or form in known or form in BLOCKED or not is_simple_word(form):
                continue
            zipf = zipf_frequency(form, "en")
            if zipf < min_zipf - LOWER_ZIPF_MAX + (VERB_ZIPF_BONUS if pos == "v" else 0) or not word.senses():
                continue
            first = word.senses()[0].synset()
            topic = topic_of(first)
            if topic is None or zipf < min_zipf_for(topic, pos, min_zipf):
                continue
            if _is_rude(wordnet, form) or not _mainly(wordnet, form, pos):
                continue
            found[form] = Candidate(form, pos, zipf, topic, (first.definition() or "")[:120])
    for form, topic in EXTRA_WORDS.items():
        if form not in found and form not in known:
            senses = [s for w in wordnet.words(form) for s in w.senses()]
            gloss = (senses[0].synset().definition() or "") if senses else ""
            pos = senses[0].synset().pos if senses else "n"
            found[form] = Candidate(form, "a" if pos == "s" else pos, zipf_frequency(form, "en"), topic, gloss[:120])
    return sorted(found.values(), key=lambda c: (c.topic.value, -c.zipf))


def save_candidates(connection: sqlite3.Connection, candidates: list[Candidate]) -> int:
    """Adds new candidates. Words that are already in the table (judged or not) stay as they are."""
    before = connection.total_changes
    connection.executemany(
        "INSERT OR IGNORE INTO topic_words (lemma, pos, zipf, topic, gloss) VALUES (?, ?, ?, ?, ?)",
        [(c.word, c.pos, round(c.zipf, 2), c.topic.value, c.gloss) for c in candidates],
    )
    connection.commit()
    return connection.total_changes - before


# ---------- step 2: judge ----------


class Verdict(BaseModel):
    word: str
    keep: bool = Field(description="true only when the everyday meaning is a concrete, useful word for this list")
    topic: Topic = Field(description="The best topic for the everyday meaning (may differ from the proposed one)")
    reason: str = Field(description="At most 8 words, e.g. 'common fruit' or 'mainly a first name'")


class JudgeResult(BaseModel):
    verdicts: list[Verdict]


# fetch(batch of rows) -> JudgeResult. The real one calls Claude; tests pass a fake.
Fetch = Callable[[list[dict]], JudgeResult]

JUDGE_SYSTEM = """You choose words for a picture dictionary for adult learners of English (A1-B2), grouped by topic:
animals, food and drink, plants, the body, clothes, home, transport, weather, tools and objects,
places and the street, nature, people and jobs.

For each word you get a proposed topic and a WordNet definition. Think about what the word USUALLY means
in everyday English today, not only that definition.

keep = true when the everyday meaning is a concrete thing a person can see or touch (giraffe, kettle, tram,
pavement, elbow), or a common action or quality that clearly belongs to a topic (chew, sneeze, sunny).
keep = false when:
- the word is mainly a name, surname, place name or brand (charlotte, carter, cisco), even if it has a rare common meaning
- the proposed meaning is rare and the everyday meaning does not fit any topic (bishop as a drink, trump as a card)
- it is technical, scientific, medical, military or legal (receptor, chromosome, howitzer, magistrate)
- it is rude, sexual, a drug, or not suitable for a learner app
- it is too rare for a learner (wainscot, mead) or an old-fashioned word
topic: the best topic for the everyday meaning. Return one verdict for every word, in any order."""


def build_judge_prompt(batch: list[dict]) -> str:
    lines = [f"- {row['lemma']} ({row['pos']}), proposed topic: {row['topic']}, WordNet: {row['gloss']}" for row in batch]
    return "Words:\n" + "\n".join(lines)


def fetch_from_claude(batch: list[dict]) -> JudgeResult:
    """One real Claude Haiku call for up to 50 words. Raises RuntimeError on a refusal or an empty reply."""
    from pipeline.generate import _client, _wait_for_rate_limit

    _wait_for_rate_limit()
    response = _client().messages.parse(
        model=JUDGE_MODEL,
        max_tokens=8000,
        system=JUDGE_SYSTEM,
        messages=[{"role": "user", "content": build_judge_prompt(batch)}],
        output_format=JudgeResult,
        output_config={"effort": "low"},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    if response.parsed_output is None:
        raise RuntimeError(f"No verdicts returned (stop_reason={response.stop_reason})")
    return response.parsed_output


def rows_to_judge(connection: sqlite3.Connection, limit: int) -> list[dict]:
    rows = connection.execute(
        "SELECT lemma, pos, topic, gloss FROM topic_words WHERE status = 'candidate' ORDER BY topic, zipf DESC LIMIT ?",
        (limit,),
    ).fetchall()
    return [dict(row) for row in rows]


def apply_verdicts(batch: list[dict], result: JudgeResult) -> list[tuple[str, str, str, str]]:
    """(status, topic, reason, lemma) for each word of the batch that got a verdict.
    Verdicts for words not in the batch are ignored; words without a verdict stay candidates for the next run."""
    asked = {row["lemma"] for row in batch}
    updates = {}
    for verdict in result.verdicts:
        word = verdict.word.strip().lower()
        if word in asked and word not in updates:
            updates[word] = ("keep" if verdict.keep else "drop", verdict.topic.value, verdict.reason[:120], word)
    return list(updates.values())


def run_judge(connection: sqlite3.Connection, fetch: Fetch, *, limit: int = 100000, batch_size: int = JUDGE_BATCH,
              dry_run: bool = False) -> dict[str, int]:
    rows = rows_to_judge(connection, limit)
    counts = {"keep": 0, "drop": 0, "failed_batches": 0, "no_verdict": 0}
    for start in range(0, len(rows), batch_size):
        batch = rows[start:start + batch_size]
        try:
            updates = apply_verdicts(batch, fetch(batch))
        except Exception as error:  # keep going; the words stay candidates and are retried on the next run
            counts["failed_batches"] += 1
            print(f"Batch {start // batch_size + 1} failed: {error!r}", flush=True)
            continue
        counts["no_verdict"] += len(batch) - len(updates)
        for status, topic, reason, word in updates:
            counts[status] += 1
            if dry_run:
                print(f"{status:>4}  {word:<16} {topic:<22} {reason}")
        if not dry_run:
            connection.executemany(
                "UPDATE topic_words SET status = ?, topic = ?, reason = ?, judged_at = CURRENT_TIMESTAMP WHERE lemma = ?",
                updates,
            )
            connection.commit()
        print(f"[{min(start + batch_size, len(rows))}/{len(rows)}] judged", flush=True)
    return counts


# ---------- review ----------


def report(connection: sqlite3.Connection, samples: int = 20) -> None:
    by_topic: dict[str, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
    for row in connection.execute("SELECT lemma, topic, status FROM topic_words ORDER BY zipf DESC"):
        by_topic[row["topic"]][row["status"]].append(row["lemma"])
    for topic, statuses in sorted(by_topic.items(), key=lambda kv: -sum(len(v) for v in kv[1].values())):
        counts = ", ".join(f"{status} {len(words)}" for status, words in sorted(statuses.items()))
        print(f"## {topic}: {counts}")
        for status in ("keep", "candidate", "drop"):
            if statuses.get(status):
                print(f"   {status:<9} " + ", ".join(statuses[status][:samples]))


def export_tsv(connection: sqlite3.Connection, path: Path) -> int:
    """One row per word, for review in a spreadsheet: change status to keep or drop and import it back later."""
    path.parent.mkdir(parents=True, exist_ok=True)
    rows = connection.execute(
        "SELECT lemma, pos, zipf, topic, status, reason, gloss FROM topic_words ORDER BY topic, status, zipf DESC"
    ).fetchall()
    with path.open("w", newline="") as file:
        writer = csv.writer(file, delimiter="\t")
        writer.writerow(["word", "pos", "zipf", "topic", "status", "reason", "wordnet_gloss"])
        writer.writerows(tuple(row) for row in rows)
    return len(rows)
