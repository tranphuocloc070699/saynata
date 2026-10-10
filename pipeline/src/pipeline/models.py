"""The shape of one dictionary entry. The LLM must return exactly this JSON."""

from enum import Enum

from pydantic import BaseModel, Field


class ImageType(str, Enum):
    PHOTO = "photo"  # concrete things: apple, bridge, dog
    ICON = "icon"  # simple actions or qualities: run, happy, big
    NONE = "none"  # abstract: however, ability, reluctant (Phase 2: AI scenes)


class Sense(BaseModel):
    source_index: int = Field(description="Index of the Wiktionary sense this is based on")
    pos: str = Field(description="Part of speech: noun, verb, adjective, ...")
    cefr: str = Field(description="CEFR level of this sense: A1, A2, B1, B2, C1 or C2")
    simple_definition: str = Field(description="Max 20 words, only very common simple words")
    examples: list[str] = Field(description="Exactly 2 short, natural example sentences")
    vietnamese: str = Field(description="Short Vietnamese meaning, like a bilingual dictionary")
    collocations: list[str] = Field(description="0-3 common word partners, e.g. 'reluctant to do sth'")
    synonyms: list[str] = Field(description="0-3 simpler or common synonyms")
    image_type: ImageType
    image_query: str = Field(description="2-4 English keywords to search a stock photo/icon site; empty if image_type is none")


class WordEntry(BaseModel):
    senses: list[Sense] = Field(description="1-4 senses a learner really needs, most common first")
