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
  - Natural items a native speaker really says. No very old-fashioned or rare ones.
  - Idioms: when this sense has a real idiom a learner may meet (common or less common, B2-C1 is fine),
    include at least one idiom. It must fit this sense. Its example still uses simple words only.
  - No duplicates: across ALL senses of the word, no two items may repeat the same phrase or the same
    meaning. Each item must teach something new.
  - The placeholder must match the example: "+ noun" needs a noun in the example, "+ -ing" an -ing verb.
    "It depends on how much time we have" is not "depends on + noun".
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
  make (to have just enough money):
    idiom "make ends meet" -> "It is hard to make ends meet in this city."
  take (to believe):
    idiom "take something with a pinch of salt" -> "Take his news with a pinch of salt."
  get (to learn):
    idiom "get the hang of something" -> "I'm getting the hang of this game."
  depend:
    pattern "depend on + something" -> "It depends on the weather."
    phrase "It depends." -> "\"Are you coming?\" \"It depends.\"" """

# Used by this file and by examples.py (the examples pass), so both ask for the same thing.
EXAMPLE_RULES = """examples: exactly 3 example sentences for THIS sense, ordered from easy to hard.
  Each example has a level and a text. The three must differ in STRUCTURE and SITUATION, not just in length.
  - level "easy" (A1-A2): short present simple (about 4-8 words), the most common words, a very clear picture.
  - level "medium" (B1): a DIFFERENT situation and a DIFFERENT structure from the easy one: past tense, a
    question, an imperative, or two clauses (about 8-12 words). Simple words only.
  - level "harder" (B2): a more complex structure, such as present perfect, a conditional, a relative clause,
    the passive or reported speech, and/or a B2 collocation. Under about 16 words. No rare, technical or
    literary words.
  Rules for examples:
  - Easy and medium use ONLY very common words (the 2,800 most common words). Harder may use at most 2
    words outside that list.
  - Every example must use the headword (or a form of it, like "ran" for "run") in THIS sense, not in
    another sense of the word.
  - Natural sentences a native speaker would really say.
  - The three examples must show three different situations and must not reuse the same phrase.
    Bad: "I went for a run today." / "I went for a run before breakfast this morning." (same phrase, same
    situation, only longer). Longer is not harder.
  - Put them in this order: easy, medium, harder.
  Examples of good sets:
    get (to buy):
      easy "I get milk every day."
      medium "Can you get some bread on your way home?"
      harder "If you get a chance, I'd like you to get tickets before they sell out."
    run (to move fast on foot):
      easy "I run in the park."
      medium "Don't run in the house!"
      harder "She has been running every morning since she decided to run a marathon." """

SYSTEM_PROMPT = f"""You write entries for a learner's dictionary for Vietnamese students of English,
in the style of the Cambridge Learner's Dictionary.

Rules:
- Pick the 1-4 senses a learner really needs, most common first. Skip technical, slang and rare senses.
- simple_definition: max 20 words. Use ONLY very common, simple English words (the 2,800 most common words).
  Never use the headword itself. Start like Cambridge does: a noun sense starts with "a/an/the ...",
  a verb sense starts with "to ...", an adjective sense describes the quality.
- {EXAMPLE_RULES}
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
