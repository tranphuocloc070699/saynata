"""Calls Claude to write simple definitions + Vietnamese for one word.

Structured outputs make Claude return exactly the WordEntry shape, so the prompt
no longer needs the JSON schema pasted in. Pick the model with CLAUDE_MODEL in .env.
"""

import json
import os
import time
from functools import cache

import anthropic

from pipeline.models import WordEntry
from pipeline.senses import WordSource

# Bump this when the prompt or model changes, then re-run only rows with an older version.
# Not bumped for TASK-24: that would redo every done entry (~$36). Usage is added by `pipeline usage` instead.
PROMPT_VERSION = 2

DEFAULT_MODEL = "claude-haiku-5-5"

# Used by this file and by usage.py (the usage pass), so both ask for the same thing.
USAGE_RULES = """usage: 0-3 items that show how to use THIS sense. Give fewer, or none, when there are no good ones.
  Each item has a kind, a pattern and one example sentence.
  - kind "pattern": a sentence pattern, e.g. "depend on + something"
  - kind "collocation": words that go together, e.g. "make a decision" (not "do a decision")
  - kind "phrase": a phrasal verb or fixed phrase, e.g. "get used to + -ing", "It depends."
  - kind "idiom": a common idiom, e.g. "take it easy"
  Rules for usage:
  - Placeholders are: someone, something, + noun, + verb, + -ing, + adjective. Never write sb or sth.
  - Only common, natural items a native speaker uses often. No rare, old or formal idioms.
  - Every item must fit the meaning of this sense. "get up" does not belong on the "buy" sense of "get".
  - The example is one short natural sentence with simple words only (the 2,800 most common words).
  Examples of good usage:
  get (to buy, to find):
    pattern "get + noun" -> "I need to get some milk."
    collocation "get a job" -> "He got a new job last month."
    idiom "get hold of something" -> "I finally got hold of two tickets."
  get (to become):
    pattern "get + adjective" -> "It's getting dark outside."
    collocation "get better" -> "I hope you get better soon."
    phrase "get used to + -ing" -> "I'm getting used to living here."
  make (to decide):
    collocation "make a decision" -> "We need to make a decision today."
    idiom "make up your mind" -> "I can't make up my mind."
  take (to rest):
    collocation "take a break" -> "Let's take a break."
    idiom "take it easy" -> "Take it easy. You've worked a lot today."
  depend:
    pattern "depend on + something" -> "It depends on the weather."
    phrase "It depends." -> "\"Are you coming?\" \"It depends.\"" """

SYSTEM_PROMPT = f"""You write entries for a learner's dictionary for Vietnamese students of English,
in the style of the Cambridge Learner's Dictionary.

Rules:
- Pick the 1-4 senses a learner really needs, most common first. Skip technical, slang and rare senses.
- simple_definition: max 20 words. Use ONLY very common, simple English words (the 2,800 most common words).
  Never use the headword itself. Start like Cambridge does: a noun sense starts with "a/an/the ...",
  a verb sense starts with "to ...", an adjective sense describes the quality.
- examples: exactly 2 short, natural sentences a learner might hear in daily life.
- vietnamese: a short, natural Vietnamese meaning (1-6 words), like a good bilingual dictionary.
- {USAGE_RULES}
- image_type: "photo" if a stock photo clearly shows it, "icon" if a simple icon can show it,
  otherwise "none". Most abstract words are "none".
- source_index: the index of the Wiktionary sense you based it on."""


@cache
def _client() -> anthropic.Anthropic:
    # Created once and reused. max_retries: the SDK waits and retries on 429, 5xx and connection errors.
    return anthropic.Anthropic(max_retries=5)


def model_name() -> str:
    return os.environ.get("CLAUDE_MODEL", DEFAULT_MODEL)


_last_call_at = 0.0


def _wait_for_rate_limit() -> None:
    """Spaces out calls when LLM_MAX_PER_MINUTE is set. Off by default: the SDK retries 429s itself."""
    global _last_call_at
    per_minute = int(os.environ.get("LLM_MAX_PER_MINUTE", "0"))
    if per_minute > 0:
        gap = 60 / per_minute - (time.monotonic() - _last_call_at)
        if gap > 0:
            time.sleep(gap)
    _last_call_at = time.monotonic()


def build_prompt(word: str, source: WordSource, feedback: list[str]) -> str:
    candidates = [
        {"index": c.index, "pos": c.pos, "gloss": c.gloss, "example": c.example}
        for c in source.candidates
    ]
    prompt = (
        f"Headword: {word}\n"
        f"Wiktionary senses:\n{json.dumps(candidates, ensure_ascii=False, indent=1)}\n"
        f"Vietnamese hints from Wiktionary (may be incomplete): {source.vietnamese_hints}\n"
    )
    if feedback:
        prompt += "\nYour last answer failed these checks. Fix them:\n- " + "\n- ".join(feedback)
    return prompt


def generate_entry(word: str, source: WordSource, feedback: list[str]) -> WordEntry:
    """Raises RuntimeError when Claude refuses or the reply is cut off."""
    _wait_for_rate_limit()
    response = _client().messages.parse(
        model=model_name(),
        max_tokens=16000,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": build_prompt(word, source, feedback)}],
        output_format=WordEntry,
        output_config={"effort": "low"},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    if response.parsed_output is None:
        raise RuntimeError(f"No entry returned (stop_reason={response.stop_reason})")
    return response.parsed_output
