"""The shape of one dictionary entry. The LLM must return exactly this JSON."""

import json
from enum import Enum
from typing import Annotated

from pydantic import BaseModel, BeforeValidator, Field


class ImageType(str, Enum):
    PHOTO = "photo"  # concrete things: apple, bridge, dog
    ICON = "icon"  # simple actions or qualities: run, happy, big
    NONE = "none"  # abstract: however, ability, reluctant (Phase 2: AI scenes)


class UsageKind(str, Enum):
    PATTERN = "pattern"  # a sentence pattern: depend on + something
    COLLOCATION = "collocation"  # words that go together: make a decision
    PHRASE = "phrase"  # a phrasal verb or fixed phrase: get used to + -ing, It depends.
    IDIOM = "idiom"  # a common idiom: take it easy


class Usage(BaseModel):
    kind: UsageKind
    pattern: str = Field(description="How to build it, with placeholders: someone, something, + noun, + verb, + -ing")
    example: str = Field(description="One short natural sentence that uses the pattern, simple words only")


MAX_USAGE_PER_SENSE = 3


class ExampleLevel(str, Enum):
    EASY = "easy"  # only the most common words
    MEDIUM = "medium"  # still simple words, a longer or less obvious sentence
    HARDER = "harder"  # may use some B2 words


# The order a learner reads them in.
EXAMPLE_LEVELS = [ExampleLevel.EASY, ExampleLevel.MEDIUM, ExampleLevel.HARDER]


class Example(BaseModel):
    level: ExampleLevel
    text: str = Field(description="One short natural sentence that uses the headword in this sense")


def _examples_from_old_or_new(value):
    """Old entries stored examples as plain strings. They become easy, medium (the old pair was never sorted)."""
    if not isinstance(value, list):
        return value
    return [
        {"level": EXAMPLE_LEVELS[min(index, 1)].value, "text": item} if isinstance(item, str) else item
        for index, item in enumerate(value)
    ]


class Sense(BaseModel):
    source_index: int = Field(description="Index of the Wiktionary sense this is based on")
    pos: str = Field(description="Part of speech: noun, verb, adjective, ...")
    cefr: str = Field(description="CEFR level of this sense: A1, A2, B1, B2, C1 or C2")
    simple_definition: str = Field(description="Max 20 words, only very common simple words")
    examples: Annotated[list[Example], BeforeValidator(_examples_from_old_or_new)] = Field(
        description="Exactly 3 examples of this sense, ordered easy, medium, harder",
    )
    vietnamese: str = Field(description="Short Vietnamese meaning, like a bilingual dictionary")
    usage: list[Usage] = Field(
        description="0-3 common, natural ways to use this sense. Fewer (or none) when there are no good ones",
    )
    image_type: ImageType
    image_query: str = Field(description="2-4 English keywords to search a stock photo/icon site; empty if image_type is none")


class WordEntry(BaseModel):
    senses: list[Sense] = Field(description="1-4 senses a learner really needs, most common first")

    @classmethod
    def from_stored(cls, text: str) -> "WordEntry":
        """Reads an entry saved in pipeline.db. Old rows have no usage yet (and still carry collocations/synonyms), and plain-string examples."""
        data = json.loads(text)
        for sense in data["senses"]:
            sense.setdefault("usage", [])
        return cls.model_validate(data)
