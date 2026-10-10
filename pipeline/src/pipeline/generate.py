"""Calls the LLM to write simple definitions + Vietnamese for one word.

Gemini and DeepSeek both speak the OpenAI API format, so one client works for
both. Pick one with LLM_PROVIDER in .env.
"""

import json
import os
import time
from functools import cache

from openai import OpenAI

from pipeline.models import WordEntry
from pipeline.senses import WordSource

# Bump this when the prompt changes, then re-run only rows with an older version.
PROMPT_VERSION = 1

PROVIDERS = {
    "gemini": {
        "base_url": "https://generativelanguage.googleapis.com/v1beta/openai/",
        "key_env": "GEMINI_API_KEY",
        "model_env": "GEMINI_MODEL",
        "default_model": "gemini-3.5-flash-lite",
        "max_per_minute": 15,  # free tier limit
    },
    "deepseek": {
        "base_url": "https://api.deepseek.com",
        "key_env": "DEEPSEEK_API_KEY",
        "model_env": "DEEPSEEK_MODEL",
        "default_model": "deepseek-v4-flash",
        "max_per_minute": 0,  # 0 = no pacing
    },
}

SYSTEM_PROMPT = """You write entries for a learner's dictionary for Vietnamese students of English,
in the style of the Cambridge Learner's Dictionary.

Rules:
- Pick the 1-4 senses a learner really needs, most common first. Skip technical, slang and rare senses.
- simple_definition: max 20 words. Use ONLY very common, simple English words (the 2,800 most common words).
  Never use the headword itself. Start like Cambridge does: a noun sense starts with "a/an/the ...",
  a verb sense starts with "to ...", an adjective sense describes the quality.
- examples: exactly 2 short, natural sentences a learner might hear in daily life.
- vietnamese: a short, natural Vietnamese meaning (1-6 words), like a good bilingual dictionary.
- image_type: "photo" if a stock photo clearly shows it, "icon" if a simple icon can show it,
  otherwise "none". Most abstract words are "none".
- source_index: the index of the Wiktionary sense you based it on.

Reply with JSON only, matching this JSON schema:
""" + json.dumps(WordEntry.model_json_schema())


def _provider() -> dict:
    return PROVIDERS[os.environ.get("LLM_PROVIDER", "gemini")]


@cache
def _client() -> OpenAI:
    # Created once and reused. A new client per call gets garbage-collected
    # and closed while its request is still running.
    provider = _provider()
    # max_retries: the SDK waits and retries on 429 (rate limit) and 5xx errors.
    return OpenAI(base_url=provider["base_url"], api_key=os.environ[provider["key_env"]], max_retries=5)


def model_name() -> str:
    provider = _provider()
    return os.environ.get(provider["model_env"], provider["default_model"])


_last_call_at = 0.0


def _wait_for_rate_limit() -> None:
    """Spaces out calls so we stay under the provider's requests-per-minute limit.

    Retrying after a 429 is not enough on Gemini's free tier: it asks you to wait
    ~40 seconds, longer than the SDK's own retry delays.
    """
    global _last_call_at
    per_minute = int(os.environ.get("LLM_MAX_PER_MINUTE", _provider()["max_per_minute"]))
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
    """Raises pydantic.ValidationError if the reply doesn't match WordEntry."""
    _wait_for_rate_limit()
    response = _client().chat.completions.create(
        model=model_name(),
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": build_prompt(word, source, feedback)},
        ],
        response_format={"type": "json_object"},
        temperature=0.3,
    )
    return WordEntry.model_validate_json(response.choices[0].message.content)
