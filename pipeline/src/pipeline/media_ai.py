"""AI pictures for every sense: Claude writes the scene, Codex draws it, Claude guesses the word blind (TASK-82).

The developer decided on 2026-10-16 that every sense gets an AI picture ("Pixabay still didn't show exactly what
the word means"). The pictures are drawn by the local Codex CLI through his ChatGPT plan, so a picture has no
per-picture cost (about 1 minute each). Claude only costs money for the scene and the guess.

The flow, in the order of the functions below:
    PostgresStore.pending_senses  ->  make_sense (worker thread, up to 3 tries):
        write_scene (Claude Sonnet)  ->  codex_prompt  ->  CodexDrawer.draw  ->  prepare_images (webp file)
        ->  blind_guess (Claude Haiku: open guess, then multiple choice)  ->  on a fail: scene again with feedback
    ->  run (main thread: cost cap, save every try in media_generation)  ->  write_report
    ->  after rsync:  link_passed  (the passed picture replaces the sense picture, the old media row stays)

Workers only call Claude, run Codex and write files. Only the main thread talks to Postgres (a psycopg
connection is not thread-safe). The prompt text (STYLE, KID, SCENE_SYSTEM, codex_prompt) comes from the 12-sense
trial that passed 12 of 12; change it only with a new PROMPT_VERSION.
"""

import base64
import hashlib
import html
import io
import json
import os
import random
import re
import shutil
import subprocess
import tempfile
import time
from collections.abc import Callable
from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol

from PIL import Image
from psycopg.types.json import Json

from pipeline.media import sanitize_filename
from pipeline.media_check import easy_example
from pipeline.media_generate import DEFAULT_MEDIA_BASE_URL, ProviderStop
from pipeline.postgres import file_facts

PROMPT_VERSION = "ai-v1"  # a new version means "draw every sense again"
PROVIDER = "codex"
MODEL = "codex-image_gen"
SCENE_MODEL = "claude-sonnet-5-5"
GUESS_MODEL = "claude-haiku-5-5"
STATUS_DONE = "ai_generated"
DEFAULT_MAX_COST = 5.0
DEFAULT_REPORT = Path("data/out/media-ai.html")
MAX_TRIES = 3
MIN_CONFIDENCE = 70  # multiple choice: the lemma must be picked with at least this confidence
CODEX_TIMEOUT = 15 * 60
IMAGE_WIDTH = 1536  # 3:2, like the OpenAI pictures (TASK-53); the app crops it to 16:9
GUESS_SIZE = 768  # the guesser sees the picture as a JPEG with this longest side

# US dollars per million tokens (input, output). From the Claude pricing table, checked 2026-10-16.
PRICES = {"claude-sonnet-5-5": (2.00, 10.00), "claude-haiku-5-5": (0.10, 0.50)}
# What one sense costs in Claude money, for the cap and the dry-run estimate only (the real cost comes from the
# usage of each answer). One try is about 1,500 tokens in and 1,500 out for the scene (the thinking counts as
# output) and two small guess calls; a sense needs about 1.3 tries. The first real run shows the true number.
ESTIMATE_PER_SENSE = 0.03
SECONDS_PER_PICTURE = 60  # what the developer saw with Codex; used for the dry-run time estimate

# --- Prompts (from the trial) --------------------------------------------------------------------------

STYLE = (
    "Style: cute cartoon vector illustration like a children's picture book, bold clean outlines, flat bright "
    "colours. People and animals have big expressive eyes and mouths; objects NEVER have faces, eyes, arms or legs. "
    "Clothes in soft sage green and deep teal (#1F6B5F). Plain light grey-white background. Subject in the centre, "
    "everything important inside the middle 16:9 area. No text, letters or numbers anywhere."
)
KID = "the kid (about 8 years old, short brown hair, sage green T-shirt, deep teal jeans, white sneakers)"

SCENE_SYSTEM = f"""You are the art director for a picture dictionary for English learners (B1-B2).
For ONE sense of a word you write the scene an illustrator will draw. The picture must make a learner guess
THIS sense of THIS word, and not a near word or another sense.

When the meaning needs a person (a need, an emotion, an action, a story), the main character is always the same
kid: {KID}. Write "the kid" in the scene. Do NOT add the kid or any person when the word is a thing or a place:
show only the thing. People appear in a thing or place only when they are part of what it is (customers and a
cashier at the counter inside a bank). Other people (an old man, a doctor) only when the word needs them.
People have expressive faces. Objects never have faces.

First pick the method:
- object: concrete noun, thing or place. The thing alone, in its most typical form, nothing distracting,
  no kid. For a building whose meaning is what happens inside (bank, post office, hospital), show the building
  cut open or through a big window so the typical activity inside is visible. For a part of a place (river bank,
  roof), show the whole place and make that part the clear focus.
- need: body need or state (thirsty, hungry, tired, cold, sick). The kid shows it clearly on face and body,
  with a thought bubble showing what he needs (water, food, a bed).
- emotion: feeling (ashamed, proud, angry, afraid). The kid's face and body show it, and the cause is in the
  scene (a broken vase for ashamed). No thought bubble.
- action: verb or behaviour (hello, carry, run). The kid in the middle of the action, with small motion lines;
  the object of the action visible.
- quality: adjective. ONE typical person or thing that clearly has the quality (an old man with a walking stick
  for old, an empty transparent box for empty). If one subject alone could mean another word, add one small
  hint or use a contrast with the opposite faded.
- relation: preposition or place word. Two simple, very familiar objects whose position shows the relation;
  exaggerate it.
- role: person or job (doctor, champion). A person with the clothes and tools of the role, doing the job.
- story: abstract word. One small, familiar moment where this word is the only natural description.
- repetition: count, frequency or time words. Identical side-by-side panels, one per time, or the sun's
  position for times of day (no clocks).
- none: the sense cannot be drawn so a learner would guess it (grammar words, very abstract senses).

Rules:
- One clear focus. Every element must help the meaning; remove the rest.
- Never rely on text, numbers, symbols that need reading, or speech bubbles with words.
- Avoid the other senses of the word (they are listed) and avoid things that would suggest the distractors.
- No violence, no brands, no real people.
- Distractors: 3 real English words a learner might guess from a weak picture of this sense
  (near synonyms, the opposite, the neighbouring idea). Not other senses of the same word.

Answer with JSON only:
{{"method": "...", "scene": "2-4 short sentences: what is in the picture and where", "focus": "the one thing
the eye must land on", "avoid": "what must NOT appear", "distractors": ["w1","w2","w3"]}}"""

METHODS = ("object", "need", "emotion", "action", "quality", "relation", "role", "story", "repetition", "none")

# The three prompts below are new in TASK-82 (the trial gave the guesser only the choices).
OPEN_GUESS_PROMPT = """This picture is from a picture dictionary for English learners.
Which single English word does this picture show?
Answer with JSON only: {"answer": "<one English word>", "confidence": <0-100>, "why": "<one short sentence>"}"""

CHOICE_GUESS_PROMPT = """This picture is from a picture dictionary. Which ONE English word does it show?
Choices: {choices}
Answer with JSON only: {{"answer": "<one of the choices>", "confidence": <0-100>, "why": "<one short sentence>"}}"""

# A separate call that never sees the scene, so the choices are not made to fit the picture.
DISTRACTOR_SYSTEM = """You help test a picture dictionary for English learners (B1-B2).
You get ONE meaning of a word. List 3 real English words that a learner could confuse with this meaning: near
synonyms, the opposite, or the neighbouring idea. Not other meanings of the same word, and not forms of the word.
Answer with JSON only: {"distractors": ["w1", "w2", "w3"]}"""


def codex_prompt(scene: dict) -> str:
    """The wrapper that makes Codex call its image tool exactly once with our prompt."""
    kid = re.sub(r"\bthe kid\b", lambda m: m.group(0) + " " + KID[8:], scene["scene"], count=1, flags=re.I)
    return f"""Use your built-in image generation tool exactly ONCE with the IMAGE PROMPT below, word for word.
Do not add, remove or reword anything. Landscape 3:2. One image only. Do not run any other command or edit files.
Then reply with only this JSON: {{"prompt_used": "<the exact prompt you sent>"}}
If the tool refuses or fails, reply {{"error": "<reason>"}}.

IMAGE PROMPT:
{kid} Main focus: {scene.get("focus", "")}. Must not appear: {scene.get("avoid", "")}.
{STYLE}"""


# --- Claude --------------------------------------------------------------------------------------------

@dataclass
class Reply:
    """What a Claude call gives back: the text and the tokens it cost."""

    text: str
    input_tokens: int
    output_tokens: int


# (model, system prompt or None, content) -> Reply. fetch_from_claude is the real one; tests use a fake.
Fetch = Callable[[str, str | None, "str | list[dict]"], Reply]


def cost_usd(model: str, input_tokens: int, output_tokens: int) -> float:
    """Dollars for one call. A model without a price here is counted at the highest price listed."""
    price_in, price_out = PRICES.get(model) or (max(p[0] for p in PRICES.values()), max(p[1] for p in PRICES.values()))
    return (input_tokens * price_in + output_tokens * price_out) / 1_000_000


def make_fetch(scene_effort: str | None = None) -> Fetch:
    """The real Claude call. scene_effort ("low" ... "max") is only for the scene model; None keeps the API default."""

    def fetch(model: str, system: str | None, content) -> Reply:
        return fetch_from_claude(model, system, content, scene_effort if model == SCENE_MODEL else None)

    return fetch


def fetch_from_claude(model: str, system: str | None, content, effort: str | None = None) -> Reply:
    """One real Claude call. The SDK client retries 429, 5xx and connection errors by itself (max_retries=5).
    A wrong key or a refused account stops the whole run (ProviderStop). No temperature: Sonnet 5.5 refuses it."""
    import anthropic

    from pipeline.generate import _client

    kwargs: dict = {"system": system} if system else {}
    if effort:
        kwargs["output_config"] = {"effort": effort}
    try:
        response = _client().messages.create(
            model=model,
            max_tokens=4000,  # room for the thinking that is on by default; the answer is about 200 tokens
            messages=[{"role": "user", "content": content}],
            **kwargs,
        )
    except (anthropic.AuthenticationError, anthropic.PermissionDeniedError) as error:
        raise ProviderStop(f"Claude: {error}") from error
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    # The first *text* block: the model may send a thinking block first.
    text = next((block.text for block in response.content if block.type == "text"), "")
    return Reply(text, response.usage.input_tokens, response.usage.output_tokens)


@dataclass
class Usage:
    """The Claude spend of one try."""

    input_tokens: int = 0
    output_tokens: int = 0
    cost: float = 0.0

    def add(self, model: str, reply: Reply) -> None:
        self.input_tokens += reply.input_tokens
        self.output_tokens += reply.output_tokens
        self.cost += cost_usd(model, reply.input_tokens, reply.output_tokens)


class BadAnswer(ValueError):
    """Claude's answer was not the JSON we asked for."""


def parse_json(text: str) -> dict:
    found = re.search(r"\{.*\}", text, re.S)
    if not found:
        raise BadAnswer(f"no JSON in the answer: {text[:120]!r}")
    try:
        data = json.loads(found.group(0))
    except ValueError as error:
        raise BadAnswer(f"not valid JSON: {text[:120]!r}") from error
    if not isinstance(data, dict):
        raise BadAnswer(f"not a JSON object: {text[:120]!r}")
    return data


def ask_json(fetch: Fetch, model: str, system: str | None, content, usage: Usage) -> dict:
    reply = fetch(model, system, content)
    usage.add(model, reply)  # the tokens were spent, also when the answer is bad
    return parse_json(reply.text)


# --- Scene ---------------------------------------------------------------------------------------------

def scene_message(sense: dict, feedback: str = "") -> str:
    message = (f'Word: "{sense["lemma"]}" ({sense["pos"]})\nThis sense: {sense["simple_definition"]}\n'
               f"Example: {easy_example(sense['examples_json'])}\n"
               f"Other senses of the word (do NOT show these): {json.dumps(sense.get('others') or [])}")
    if feedback:
        message += f"\n\nA picture from your last scene failed the blind test: {feedback}\nWrite a clearer scene."
    return message


def write_scene(sense: dict, feedback: str, fetch: Fetch, usage: Usage) -> dict:
    scene = ask_json(fetch, SCENE_MODEL, SCENE_SYSTEM, scene_message(sense, feedback), usage)
    method = str(scene.get("method", "")).strip().lower()
    if method not in METHODS:
        raise BadAnswer(f"unknown method {method!r}")
    if method != "none" and not str(scene.get("scene", "")).strip():
        raise BadAnswer("the scene is empty")
    scene["method"] = method
    return scene


# --- Codex ---------------------------------------------------------------------------------------------

class DrawError(RuntimeError):
    """Codex made no picture for this try."""


@dataclass
class Drawn:
    image: bytes  # a PNG as Codex made it
    seconds: float


class ImageDrawer(Protocol):
    """What make_sense needs from the picture maker. CodexDrawer is the real one; the tests use a fake."""

    def draw(self, prompt: str) -> Drawn: ...


class CodexDrawer:
    """`codex exec` with the image tool, through the developer's ChatGPT plan (log in once with `codex login`).

    stdin is DEVNULL, because without it Codex waits for input forever. Codex prints `session id: <uuid>` and
    saves its pictures in ~/.codex/generated_images/<uuid>/; with several workers the session id is the only
    way to know which picture is ours.
    """

    STOP_WORDS = ("usage limit", "not logged in", "please log in", "codex login")

    def __init__(self, timeout: int = CODEX_TIMEOUT, run: Callable = subprocess.run, home: Path | None = None):
        if run is subprocess.run and shutil.which("codex") is None:
            raise ProviderStop("codex is not installed (npm i -g @openai/codex), then run: codex login")
        self.timeout, self.run = timeout, run
        home = home or Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex")
        self.images = home / "generated_images"

    def draw(self, prompt: str) -> Drawn:
        started = time.time()
        with tempfile.TemporaryDirectory() as folder:  # the sandbox is read-only, the folder only keeps the cwd clean
            try:
                proc = self.run(["codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", prompt],
                                capture_output=True, text=True, timeout=self.timeout, cwd=folder,
                                stdin=subprocess.DEVNULL)
            except subprocess.TimeoutExpired as error:
                raise DrawError(f"codex did not finish in {self.timeout // 60} minutes") from error
        output = (proc.stdout or "") + (proc.stderr or "")
        session = re.search(r"session id:\s*([0-9a-f-]{36})", output)
        pictures = sorted((self.images / session.group(1)).glob("*.png"), key=lambda p: p.stat().st_mtime) if session else []
        if not pictures:
            if any(word in output.lower() for word in self.STOP_WORDS):
                raise ProviderStop(f"codex: {output.strip()[-300:]}")
            raise DrawError(f"codex made no picture (exit {proc.returncode}): {output.strip()[-300:]}")
        return Drawn(pictures[-1].read_bytes(), time.time() - started)


# --- Picture files -------------------------------------------------------------------------------------

def prepare_images(png: bytes) -> tuple[bytes, bytes]:
    """(WebP for the media folder, JPEG for the guesser) from Codex's PNG. The picture is cropped to 3:2 around the
    middle (the prompt keeps the subject there) and scaled down to IMAGE_WIDTH. WebP quality 80, like the others."""
    image = Image.open(io.BytesIO(png)).convert("RGB")
    wide = image.width / image.height
    if wide > 1.5:  # too wide: cut the sides
        width = round(image.height * 1.5)
        left = (image.width - width) // 2
        image = image.crop((left, 0, left + width, image.height))
    elif wide < 1.5:  # too tall (a square): cut the top and the bottom
        height = round(image.width / 1.5)
        top = (image.height - height) // 2
        image = image.crop((0, top, image.width, top + height))
    if image.width > IMAGE_WIDTH:
        image = image.resize((IMAGE_WIDTH, round(IMAGE_WIDTH / 1.5)), Image.Resampling.LANCZOS)
    webp = io.BytesIO()
    image.save(webp, "WEBP", quality=80)
    small = image.copy()
    small.thumbnail((GUESS_SIZE, GUESS_SIZE), Image.Resampling.LANCZOS)
    jpeg = io.BytesIO()
    small.save(jpeg, "JPEG", quality=85)
    return webp.getvalue(), jpeg.getvalue()


def file_name_for(sense: dict, image: bytes) -> str:
    """<word>_<sense number>_ai_<8 hex of sha256>.webp, like media_generate. A new picture always gets a new name,
    because Caddy and the phones cache every media file for a year."""
    digest = hashlib.sha256(image).hexdigest()[:8]
    return f"{sanitize_filename(sense['lemma'])}_{sense['position'] + 1}_ai_{digest}.webp"


# --- The blind guess -----------------------------------------------------------------------------------

def normalize(word) -> str:
    return re.sub(r"[^a-z' -]", "", str(word or "").lower()).strip()


def accepted_words(sense: dict) -> set[str]:
    """The lemma and its forms (dictionary_form): a guess of "ran" or "running" for "run" is right."""
    return {normalize(sense["lemma"]), *(normalize(form) for form in sense.get("forms") or [])} - {""}


def confidence_of(answer: dict) -> float:
    try:
        return float(answer.get("confidence", 0))
    except (TypeError, ValueError):
        return 0.0


def image_content(jpeg: bytes, text: str) -> list[dict]:
    return [
        {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg",
                                     "data": base64.standard_b64encode(jpeg).decode("ascii")}},
        {"type": "text", "text": text},
    ]


def pick_distractors(sense: dict, fetch: Fetch, usage: Usage) -> list[str]:
    """3 confusable words from a call that sees only the word and the sense, never the scene."""
    message = f'Word: "{sense["lemma"]}" ({sense["pos"]})\nMeaning: {sense["simple_definition"]}'
    answer = ask_json(fetch, GUESS_MODEL, DISTRACTOR_SYSTEM, message, usage)
    taken = accepted_words(sense)
    words: list[str] = []
    for word in answer.get("distractors") or []:
        word = normalize(word)
        if word and word not in taken and word not in words:
            words.append(word)
    if len(words) < 2:
        raise BadAnswer(f"not enough distractors: {answer.get('distractors')!r}")
    return words[:3]


def blind_guess(sense: dict, jpeg: bytes, fetch: Fetch, usage: Usage) -> dict:
    """Step a: an open guess with no choices; the lemma or one of its forms passes.
    Step b: otherwise multiple choice with 3 distractors; it passes only with the lemma at confidence >= 70."""
    opened = ask_json(fetch, GUESS_MODEL, None, image_content(jpeg, OPEN_GUESS_PROMPT), usage)
    guess = {"open": {"answer": str(opened.get("answer", "")), "confidence": confidence_of(opened),
                      "why": str(opened.get("why", ""))}, "choice": None, "passed": False, "by": None}
    if normalize(opened.get("answer")) in accepted_words(sense):
        guess.update(passed=True, by="open")
        return guess
    distractors = pick_distractors(sense, fetch, usage)
    choices = [sense["lemma"], *distractors]
    random.Random(sense["id"]).shuffle(choices)
    picked = ask_json(fetch, GUESS_MODEL, None,
                      image_content(jpeg, CHOICE_GUESS_PROMPT.format(choices=", ".join(choices))), usage)
    picked_lemma = normalize(picked.get("answer")) == normalize(sense["lemma"])
    guess["choice"] = {"choices": choices, "answer": str(picked.get("answer", "")), "picked_lemma": picked_lemma,
                       "confidence": confidence_of(picked), "why": str(picked.get("why", ""))}
    if picked_lemma and confidence_of(picked) >= MIN_CONFIDENCE:
        guess.update(passed=True, by="choice")
    return guess


def feedback_from(guess: dict) -> str:
    """What the guesser said, for the next scene."""
    text = (f"asked without choices which word the picture shows, the viewer said '{guess['open']['answer']}' "
            f"because: {guess['open']['why']}")
    if guess.get("choice"):
        c = guess["choice"]
        text += f". With the choices {c['choices']} the viewer picked '{c['answer']}' (confidence {c['confidence']:.0f})"
    return text


# --- One sense -----------------------------------------------------------------------------------------

@dataclass
class Attempt:
    number: int
    usage: Usage = field(default_factory=Usage)
    scene: dict | None = None
    prompt: str = ""
    file_name: str | None = None
    guess: dict | None = None
    seconds: float = 0.0  # Codex time

    @property
    def method(self) -> str | None:
        return self.scene["method"] if self.scene else None

    @property
    def passed(self) -> bool:
        return bool(self.guess and self.guess["passed"])

    @property
    def complete(self) -> bool:
        """A try worth saving: a scene that is "none", or a drawn and guessed picture."""
        return self.scene is not None and (self.method == "none" or self.guess is not None)

    def score(self) -> float:
        """For choosing the best picture of a sense that failed: the lemma picked in the multiple choice
        counts most, then the confidence."""
        choice = (self.guess or {}).get("choice")
        return float(choice["confidence"]) if choice and choice.get("picked_lemma") else 0.0


@dataclass
class SenseResult:
    sense: dict
    attempts: list[Attempt] = field(default_factory=list)
    error: str | None = None
    stop: bool = False  # the provider said every next call will fail too
    seconds: float = 0.0

    @property
    def cost(self) -> float:
        return sum(a.usage.cost for a in self.attempts)

    @property
    def outcome(self) -> str:
        if any(a.passed for a in self.attempts):
            return "passed"
        if self.error:
            return "error"
        if self.attempts and self.attempts[-1].method == "none":
            return "none"
        return "failed"

    def best(self) -> Attempt | None:
        """The attempt to link or to review: the passed one, else the picture closest to a pass."""
        drawn = [a for a in self.attempts if a.file_name]
        passed = [a for a in drawn if a.passed]
        return passed[-1] if passed else max(drawn, key=Attempt.score, default=None)


@dataclass
class Services:
    fetch: Fetch
    drawer: ImageDrawer
    media_dir: Path


def make_sense(sense: dict, services: Services) -> SenseResult:
    """Runs in a worker thread: Claude, Codex and the file only. Never raises; a failure is in result.error.
    Up to MAX_TRIES tries; every try after a fail writes a new scene with what the guesser said."""
    started = time.time()
    result = SenseResult(sense)
    feedback = ""
    try:
        for number in range(1, MAX_TRIES + 1):
            attempt = Attempt(number)
            result.attempts.append(attempt)  # added first, so the Claude money spent on a failed try is counted
            attempt.scene = write_scene(sense, feedback, services.fetch, attempt.usage)
            if attempt.method == "none":
                break
            attempt.prompt = codex_prompt(attempt.scene)
            drawn = services.drawer.draw(attempt.prompt)
            attempt.seconds = drawn.seconds
            webp, jpeg = prepare_images(drawn.image)
            name = file_name_for(sense, webp)
            services.media_dir.mkdir(parents=True, exist_ok=True)
            (services.media_dir / name).write_bytes(webp)
            attempt.file_name = name
            attempt.guess = blind_guess(sense, jpeg, services.fetch, attempt.usage)
            if attempt.passed:
                break
            feedback = feedback_from(attempt.guess)
    except ProviderStop as error:
        result.error, result.stop = str(error), True
    except Exception as error:  # this sense is tried again on the next run
        result.error = str(error) or repr(error)
    result.seconds = time.time() - started
    return result


# --- The run -------------------------------------------------------------------------------------------

class Store(Protocol):
    """What run() and link_passed() need from the database. PostgresStore is the real one."""

    def pending_senses(self, words: list[str] | None, limit: int, redo: bool) -> list[dict]: ...
    def count_pending(self, words: list[str] | None, redo: bool) -> int: ...
    def save_result(self, result: SenseResult) -> None: ...
    def still_wanted(self, sense_id: int) -> bool: ...
    def unlinked_passed(self) -> list[dict]: ...
    def link(self, generation: dict, facts: tuple) -> bool: ...


@dataclass
class RunSummary:
    planned: int = 0
    results: list[SenseResult] = field(default_factory=list)
    cost: float = 0.0
    estimate: float = 0.0  # dry run: the estimate for every planned sense
    remaining: int = 0  # senses still without a final answer after this run
    skipped: int = 0  # not started, because the cap (or a stop) came first
    stopped_by: str | None = None  # "cost", "interrupt" or the provider's message
    previews: list[tuple[dict, dict, str]] = field(default_factory=list)  # dry run: (sense, scene, codex prompt)

    def of(self, outcome: str) -> list[SenseResult]:
        return [r for r in self.results if r.outcome == outcome]

    def pictures(self) -> list[Attempt]:
        return [a for r in self.results for a in r.attempts if a.file_name]


def estimate_text(count: int, workers: int, max_cost: float) -> str:
    cost = count * ESTIMATE_PER_SENSE
    hours = count * SECONDS_PER_PICTURE * 1.3 / max(workers, 1) / 3600
    return (f"Estimate for {count} senses: Claude about ${cost:.2f} (${ESTIMATE_PER_SENSE:.3f} each; the ${max_cost:.2f} cap "
            f"covers about {int(max_cost // ESTIMATE_PER_SENSE)} senses). Codex has no per-picture cost; at about "
            f"{SECONDS_PER_PICTURE} s a picture, 1.3 tries a sense and {workers} workers that is about {hours:.1f} hours.")


def run(store: Store, services: Services, *, words: list[str] | None = None, limit: int = 100000, redo: bool = False,
        max_cost: float = DEFAULT_MAX_COST, workers: int = 4, dry_run: bool = False,
        log: Callable[[str], None] = print) -> RunSummary:
    """Makes a picture for each pending sense. A new sense starts only when the Claude money spent, plus the
    estimate of every sense in flight, plus its own estimate stays within max_cost (the estimate grows to the
    biggest overrun seen). Ctrl-C once: no new sense starts and the running ones finish; twice: quit now."""
    senses = store.pending_senses(words, limit, redo)
    summary = RunSummary(planned=len(senses))
    log(f"{len(senses)} senses to draw, prompt version {PROMPT_VERSION}, {workers} at a time, Claude cap ${max_cost:.2f}")
    if dry_run:
        for sense in senses[:3]:
            usage = Usage()
            scene = write_scene(sense, "", services.fetch, usage)
            summary.cost += usage.cost
            prompt = codex_prompt(scene) if scene["method"] != "none" else "(method none: nothing would be drawn)"
            summary.previews.append((sense, scene, prompt))
            log(f"\n--- {sense['lemma']} #{sense['position'] + 1} ({sense['pos']}): {scene['method']} ---\n"
                f"Scene: {json.dumps(scene, ensure_ascii=False)}\n\nCodex prompt:\n{prompt}")
        summary.estimate = len(senses) * ESTIMATE_PER_SENSE
        log(f"\nDry run: nothing was drawn or saved ({len(summary.previews)} scenes cost ${summary.cost:.4f}).")
        log(estimate_text(len(senses), workers, max_cost))
        return summary

    queue = iter(senses)
    in_flight: dict[Future, float] = {}
    worst = 0.0  # the biggest (real cost - estimate) seen, added to every next estimate
    stop_message: str | None = None
    pool = ThreadPoolExecutor(max_workers=workers)
    try:
        while True:
            while stop_message is None and len(in_flight) < workers:
                sense = next(queue, None)
                if sense is None:
                    break
                if not store.still_wanted(sense["id"]):  # its image_type or status changed in the admin
                    log(f"{sense['lemma']} #{sense['position'] + 1}: no longer wanted, skipped")
                    continue
                estimate = ESTIMATE_PER_SENSE + worst
                if summary.cost + sum(in_flight.values()) + estimate > max_cost:
                    stop_message = "cost"
                    summary.skipped += 1  # taken from the queue but not started
                    break
                in_flight[pool.submit(make_sense, sense, services)] = estimate
            if not in_flight:
                break
            try:
                finished, _ = wait(in_flight, return_when=FIRST_COMPLETED)
            except KeyboardInterrupt:
                if stop_message == "interrupt":
                    raise
                stop_message = "interrupt"
                log(f"\nStopping: {len(in_flight)} senses in flight will finish (Ctrl-C again to quit now).")
                continue
            for future in finished:
                estimate = in_flight.pop(future)
                result = future.result()
                summary.cost += result.cost
                worst = max(worst, result.cost - (estimate - worst))
                summary.results.append(result)
                if any(a.complete for a in result.attempts):
                    store.save_result(result)
                label = f"{result.sense['lemma']} #{result.sense['position'] + 1}"
                best = result.best()
                log(f"[{len(summary.results)}/{len(senses)}] {label}: {result.outcome}"
                    f"{' (' + result.error + ')' if result.error else ''}"
                    f"{', ' + best.file_name if best else ''}, {len(result.attempts)} tries, "
                    f"{result.seconds:.0f}s | ${summary.cost:.4f}")
                if result.stop:
                    stop_message = result.error
    finally:
        pool.shutdown(wait=False, cancel_futures=True)
    summary.skipped += sum(1 for _ in queue)
    if summary.skipped:
        summary.stopped_by = stop_message or "cost"
    summary.remaining = store.count_pending(words, redo)
    return summary


def print_summary(summary: RunSummary, workers: int, max_cost: float, log: Callable[[str], None] = print) -> None:
    passed, failed, none, errors = (summary.of(o) for o in ("passed", "failed", "none", "error"))
    pictures = summary.pictures()
    seconds = [a.seconds for a in pictures if a.seconds]
    average = sum(seconds) / len(seconds) if seconds else 0.0
    log("")
    log(f"Passed {len(passed)}, failed {len(failed)} (best picture kept, not linked), none {len(none)} (not drawn), "
        f"errors {len(errors)} (run again to retry those). Claude cost ${summary.cost:.4f} of the ${max_cost:.2f} cap.")
    log(f"{len(pictures)} pictures drawn, {average:.0f} s on average with Codex.")
    if summary.results and summary.remaining:
        per_sense = sum(r.seconds for r in summary.results) / len(summary.results)
        eta = summary.remaining * per_sense / max(workers, 1)
        log(f"{summary.remaining} senses are left. At this speed that is about {eta / 3600:.1f} hours with {workers} workers "
            f"and about ${summary.remaining * summary.cost / len(summary.results):.2f} of Claude money.")
    if summary.stopped_by == "cost":
        log(f"Stopped at the Claude cap: {summary.skipped} senses were not started. Run again to go on.")
    elif summary.stopped_by:
        log(f"Stopped: {summary.stopped_by}. {summary.skipped} senses were not started.")
    for title, group in (("Failed, review these", failed), ("Method none, decide if image_type should become none", none),
                         ("Errors", errors)):
        if group:
            log(f"\n{title}:")
            for r in group[:30]:
                log(f"  {r.sense['lemma']} #{r.sense['position'] + 1}: {r.sense['simple_definition'][:70]}"
                    f"{' | ' + r.error[:80] if r.error else ''}")
            if len(group) > 30:
                log(f"  ... and {len(group) - 30} more (see the report)")
    if passed:
        log("\nNothing in the app changed yet. Open the report, then copy the files to the server and link them:\n"
            "  rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/\n"
            "  uv run pipeline media-ai --link")


# --- Link (after rsync) --------------------------------------------------------------------------------

@dataclass
class LinkSummary:
    linked: int = 0
    not_on_server: list[str] = field(default_factory=list)
    missing_here: list[str] = field(default_factory=list)
    skipped: int = 0  # the sense changed meanwhile (image_type none, or removed)


def link_passed(store: Store, media_dir: Path, on_server: Callable[[str], bool],
                log: Callable[[str], None] = print) -> LinkSummary:
    """Links the newest picture of every sense whose newest try for PROMPT_VERSION passed, if its file is on the
    server. It replaces any picture (Pixabay, a clip, an upload, an OpenAI picture, an approved one). The old media
    row and file stay, so the change can be undone (doc-15)."""
    summary = LinkSummary()
    for generation in store.unlinked_passed():
        name = generation["file_name"]
        if not (media_dir / name).is_file():
            summary.missing_here.append(name)
            continue
        if not on_server(name):
            summary.not_on_server.append(name)
            continue
        if store.link(generation, file_facts(media_dir, name)):
            summary.linked += 1
            log(f"linked {generation['lemma']} #{generation['position'] + 1}: {name}")
        else:
            summary.skipped += 1
    log(f"\nLinked {summary.linked}. Skipped {summary.skipped} (the sense has no picture type now, or was removed).")
    if summary.not_on_server:
        log(f"{len(summary.not_on_server)} files are not on the server yet (rsync first): "
            + ", ".join(summary.not_on_server[:5]) + (" ..." if len(summary.not_on_server) > 5 else ""))
    if summary.missing_here:
        log(f"{len(summary.missing_here)} files are missing in {media_dir}: " + ", ".join(summary.missing_here[:5]))
    if summary.linked:
        log("Each linked sense shows up in the admin's \"Media to review\" again. The app shows the new pictures "
            "after the next export and app build (uv run pipeline export).")
    return summary


# --- Postgres ------------------------------------------------------------------------------------------

# A sense is finished for this PROMPT_VERSION when one try passed, or the method was none, or all MAX_TRIES failed.
# An unfinished sense (an error, a stop) is tried again on the next run. --redo ignores all of this.
_NOT_FINISHED = """AND NOT EXISTS (SELECT 1 FROM media_generation g WHERE g.sense_id = s.id AND g.prompt_version = %s
                       AND (g.passed OR g.method = 'none' OR g.attempt >= %s))"""


class PostgresStore:
    """Reads the senses, writes media_generation and links. Tables: backend Flyway V6, V8, V14, V15, V16."""

    def __init__(self, connection):
        self.pg = connection

    def _where(self, words: list[str] | None, redo: bool) -> tuple[str, list]:
        sql = "WHERE s.image_type <> 'none' AND (s.media_status IS NULL OR s.media_status <> 'removed')"
        params: list = []
        if not redo:
            sql += " " + _NOT_FINISHED
            params += [PROMPT_VERSION, MAX_TRIES]
        if words:
            sql += " AND w.lemma = ANY(%s)"
            params.append([word.lower() for word in words])
        return sql, params

    def pending_senses(self, words: list[str] | None, limit: int, redo: bool) -> list[dict]:
        """Bundled senses that may have a picture, most common words first."""
        where, params = self._where(words, redo)
        sql = f"""SELECT s.id, w.lemma, s.position, s.pos, s.simple_definition, s.examples_json, s.media_id,
                         (SELECT json_agg(json_build_object('pos', o.pos, 'meaning', o.simple_definition) ORDER BY o.position)
                            FROM dictionary_sense o WHERE o.word_id = s.word_id AND o.id <> s.id) AS others,
                         (SELECT coalesce(array_agg(f.form), '{{}}') FROM dictionary_form f WHERE f.word_id = s.word_id) AS forms
                  FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id
                  {where} ORDER BY w.word_rank, s.position LIMIT %s"""
        with self.pg.cursor() as cursor:
            cursor.execute(sql, [*params, limit])
            columns = [column.name for column in cursor.description]
            rows = [dict(zip(columns, row)) for row in cursor.fetchall()]
        self.pg.commit()  # end the read transaction, so a later pg.transaction() is a real one
        return rows

    def count_pending(self, words: list[str] | None, redo: bool) -> int:
        where, params = self._where(words, redo)
        with self.pg.cursor() as cursor:
            cursor.execute(f"SELECT count(*) FROM dictionary_sense s JOIN dictionary_word w ON w.id = s.word_id {where}", params)
            count = cursor.fetchone()[0]
        self.pg.commit()
        return count

    def still_wanted(self, sense_id: int) -> bool:
        """Asked again before each sense starts: the admin may have removed the picture or set the type to none."""
        with self.pg.cursor() as cursor:
            cursor.execute("SELECT 1 FROM dictionary_sense WHERE id = %s AND image_type <> 'none' "
                           "AND (media_status IS NULL OR media_status <> 'removed')", (sense_id,))
            found = cursor.fetchone() is not None
        self.pg.commit()
        return found

    def save_result(self, result: SenseResult) -> None:
        """One row per complete try. cost_usd is the Claude spend of that try only (Codex is in the plan)."""
        now = datetime.now(timezone.utc)
        with self.pg.cursor() as cursor:
            for attempt in result.attempts:
                if not attempt.complete:
                    continue
                cursor.execute(
                    """INSERT INTO media_generation (sense_id, file_name, provider, model, prompt, input_tokens,
                       output_tokens, cost_usd, created_at, old_media_id, prompt_version, method, scene_json,
                       guess_json, passed, attempt)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                    (result.sense["id"], attempt.file_name, PROVIDER, MODEL, attempt.prompt or "(not drawn)",
                     attempt.usage.input_tokens, attempt.usage.output_tokens, round(attempt.usage.cost, 6), now,
                     result.sense["media_id"], PROMPT_VERSION, attempt.method, Json(attempt.scene),
                     Json(attempt.guess) if attempt.guess else None, attempt.passed if attempt.file_name else None,
                     attempt.number),
                )
        self.pg.commit()  # saved sense by sense, so stopping the run loses nothing

    def unlinked_passed(self) -> list[dict]:
        """The newest try of each sense for PROMPT_VERSION, when it passed and is not linked yet."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT * FROM (
                       SELECT DISTINCT ON (g.sense_id) g.id, g.sense_id, g.file_name, g.passed, g.media_id, w.lemma, s.position
                       FROM media_generation g
                       JOIN dictionary_sense s ON s.id = g.sense_id
                       JOIN dictionary_word w ON w.id = s.word_id
                       WHERE g.prompt_version = %s
                       ORDER BY g.sense_id, g.created_at DESC, g.attempt DESC, g.id DESC) newest
                   WHERE passed AND media_id IS NULL AND file_name IS NOT NULL
                   ORDER BY sense_id""",
                (PROMPT_VERSION,),
            )
            columns = [column.name for column in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]

    def link(self, generation: dict, facts: tuple) -> bool:
        """One transaction: a new media row (source 'ai'), the sense points at it with media_status ai_generated, its
        picture review is cleared (so it is in "Media to review" again), and the generation is marked linked.
        The picture the sense had stays in `media` and is kept in old_media_id. False (nothing changed) when the sense
        has image_type 'none' now or was removed."""
        width, height, size = facts
        now = datetime.now(timezone.utc)
        # Commit first: the SELECT in unlinked_passed() opened a transaction, and inside an open transaction
        # pg.transaction() is only a savepoint that is never committed (the bug fixed in PR #74).
        self.pg.commit()
        with self.pg.transaction(), self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT media_id FROM dictionary_sense WHERE id = %s AND image_type <> 'none'
                   AND (media_status IS NULL OR media_status <> 'removed') FOR UPDATE""",
                (generation["sense_id"],),
            )
            row = cursor.fetchone()
            if row is None:
                return False
            old_media_id = row[0]
            cursor.execute(
                """INSERT INTO media (file_name, kind, width, height, bytes, license_note, source, created_at)
                   VALUES (%s, 'photo', %s, %s, %s, %s, 'ai', %s) RETURNING id""",
                (generation["file_name"], width, height, size, f"AI image ({PROVIDER} {MODEL})", now),
            )
            media_id = cursor.fetchone()[0]
            cursor.execute(
                """UPDATE dictionary_sense SET media_id = %s, media_status = %s, media_reviewed_at = NULL,
                   media_reviewed_media_id = NULL WHERE id = %s""",
                (media_id, STATUS_DONE, generation["sense_id"]),
            )
            cursor.execute(
                "UPDATE media_generation SET media_id = %s, linked_at = %s, old_media_id = %s WHERE id = %s",
                (media_id, now, old_media_id, generation["id"]),
            )
        return True

    def report_rows(self) -> list[dict]:
        """Every try for PROMPT_VERSION with the picture it replaces, in word order."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT g.id, g.sense_id, g.attempt, g.file_name, g.method, g.scene_json, g.guess_json, g.passed,
                          g.cost_usd, g.created_at, g.linked_at, w.lemma, s.position, s.pos, s.simple_definition,
                          old.file_name AS old_file_name, old.kind AS old_kind
                   FROM media_generation g
                   JOIN dictionary_sense s ON s.id = g.sense_id
                   JOIN dictionary_word w ON w.id = s.word_id
                   LEFT JOIN media old ON old.id = g.old_media_id
                   WHERE g.prompt_version = %s
                   ORDER BY w.word_rank, s.position, g.created_at, g.attempt, g.id""",
                (PROMPT_VERSION,),
            )
            columns = [column.name for column in cursor.description]
            rows = [dict(zip(columns, row)) for row in cursor.fetchall()]
        self.pg.commit()
        return rows


# --- The report ----------------------------------------------------------------------------------------

def sense_outcome(tries: list[dict]) -> str:
    if any(t["passed"] for t in tries):
        return "passed"
    if tries[-1]["method"] == "none":
        return "none"
    return "failed"


def write_report(rows: list[dict], report_path: Path, media_dir: Path, old_base_url: str = DEFAULT_MEDIA_BASE_URL) -> int:
    """data/out/media-ai.html: for each sense the old picture, every try (picture, method, scene, blind guess) and
    the result. Failed and none senses come first, because those need the developer's eye. Returns the number of senses."""
    report_path.parent.mkdir(parents=True, exist_ok=True)
    relative = Path(os.path.relpath(media_dir.resolve(), report_path.resolve().parent))
    senses: dict[int, list[dict]] = {}
    for row in rows:
        senses.setdefault(row["sense_id"], []).append(row)
    order = {"failed": 0, "none": 1, "passed": 2}
    ordered = sorted(senses.values(), key=lambda tries: order[sense_outcome(tries)])  # stable: word order inside
    counts = {name: sum(1 for t in ordered if sense_outcome(t) == name) for name in order}
    cost = sum(float(r["cost_usd"]) for r in rows)
    cards = []
    for tries in ordered:
        first, outcome = tries[0], sense_outcome(tries)
        old = ""
        if first.get("old_file_name"):
            src = html.escape(f"{old_base_url.rstrip('/')}/{first['old_file_name']}")
            old = (f'<video src="{src}" muted loop autoplay playsinline></video>' if first.get("old_kind") == "clip"
                   else f'<img src="{src}" loading="lazy">')
        cells = [f'<div class="try">{old or "<div class=none>no picture</div>"}<p>Now in the app</p></div>']
        for t in tries:
            guess = t.get("guess_json")
            verdict = "<b class=ok>PASS</b>" if t["passed"] else ("<b>not drawn</b>" if t["method"] == "none" else "<b class=bad>FAIL</b>")
            picture = (f'<img src="{html.escape((relative / t["file_name"]).as_posix())}" loading="lazy">'
                       if t.get("file_name") else "")
            lines = ""
            if guess:
                o = guess["open"]
                lines = f'<p>Open guess: <b>{html.escape(o["answer"])}</b> ({o["confidence"]:.0f}%) {html.escape(o["why"])}</p>'
                if guess.get("choice"):
                    c = guess["choice"]
                    lines += (f'<p>Choice from {html.escape(", ".join(c["choices"]))}: <b>{html.escape(c["answer"])}</b> '
                              f'({c["confidence"]:.0f}%) {html.escape(c["why"])}</p>')
            scene = t.get("scene_json") or {}
            cells.append(f'<div class="try">{picture}<p>Try {t["attempt"]} &middot; {html.escape(str(t["method"]))} &middot; {verdict}'
                         f'{" &middot; linked" if t.get("linked_at") else ""}</p>'
                         f'<p class=scene>{html.escape(str(scene.get("scene", "")))}</p>{lines}</div>')
        cards.append(f'<section class="{outcome}"><h2>{html.escape(first["lemma"])} <small>{html.escape(first["pos"])} '
                     f'#{first["position"] + 1} &middot; {outcome}</small></h2>'
                     f'<p class=mean>{html.escape(first["simple_definition"])}</p><div class="row">{"".join(cells)}</div></section>')
    page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>AI pictures</title><style>
:root{{--bg:#fafaf8;--fg:#1d1d1b;--mut:#6b6b66;--card:#fff;--line:#e4e4df}}
@media (prefers-color-scheme:dark){{:root{{--bg:#1b1b1a;--fg:#ededea;--mut:#a3a39c;--card:#262624;--line:#3a3a37}}}}
body{{background:var(--bg);color:var(--fg);font:15px/1.5 system-ui;margin:0 auto;max-width:1200px;padding:16px}}
section{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 16px;margin:16px 0}}
section.failed{{border-color:#c0392b}} h2{{margin:0;font-weight:600}} small,.mean,.scene{{color:var(--mut)}}
.row{{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}}
.try img,.try video{{width:100%;aspect-ratio:3/2;object-fit:cover;border-radius:8px;background:#eee}}
.none{{aspect-ratio:3/2;display:flex;align-items:center;justify-content:center;border:1px dashed var(--line);border-radius:8px;color:var(--mut)}}
.ok{{color:#2e7d4f}} .bad{{color:#c0392b}} p{{margin:4px 0}}
</style></head><body><h1>AI pictures, version {PROMPT_VERSION}</h1>
<p class=mean>{counts["passed"]} passed, {counts["failed"]} failed (best picture kept, not linked), {counts["none"]} not drawn
(method none). Claude cost ${cost:.4f} in all. Failed and not-drawn senses are first. Pictures are 3:2; the app crops them to 16:9.</p>
{"".join(cards)}</body></html>"""
    report_path.write_text(page, encoding="utf-8")
    return len(ordered)
