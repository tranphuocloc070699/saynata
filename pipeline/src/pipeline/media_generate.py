"""AI images for the senses marked needs_ai (TASK-53).

The Claude check (media_check.py) and the admin's "Mark for AI" set dictionary_sense.media_status = 'needs_ai'
on bundled senses whose Pixabay picture is wrong. This makes a new picture for each of them with an image model
(OpenAI GPT Image mini, chosen by the developer on 2026-10-16), saves it as a 320 px WebP in data/media/, and
records it in the Postgres table media_generation (Flyway V14). Nothing in the app changes yet: the old
picture stays until the new one is linked.

Two steps, because the file has to be on the server before a sense may point to it:
    1. generate:  PostgresStore.pending_senses -> generate_one (worker thread: model, resize, file)
                  -> run (main thread: budget cap, save) -> write_report
    2. --link:    after rsync, link_generated links every image whose file answers 200 at MEDIA_BASE_URL:
                  a media row with source 'ai', dictionary_sense.media_id = new, media_status = 'ai_generated'.

The prompt is built from the sense alone (headword, part of speech, simple explanation, easy example) plus one
fixed style rule, so there is no extra LLM call. Workers only call the model and write files. Only the main
thread talks to Postgres (a psycopg connection is not thread-safe).
"""

import base64
import hashlib
import html
import os
import random
import time
from collections.abc import Callable
from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol

import httpx

from pipeline.media import resize_and_save_webp, sanitize_filename
from pipeline.media_check import easy_example
from pipeline.postgres import file_facts

DEFAULT_PROVIDER = "openai"
DEFAULT_MODEL = "gpt-image-1-mini"
DEFAULT_MAX_COST = 5.0
DEFAULT_REPORT = Path("data/out/media-generate.html")
DEFAULT_MEDIA_BASE_URL = "https://api.loffy.me/media"
STATUS_DONE = "ai_generated"

# 3:2 is the closest size to the app's 16:9 frame (240 x 135, the picture is cropped to fill it). The prompt
# keeps the subject in the middle, so the crop does not cut it.
IMAGE_SIZE = "1536x1024"
IMAGE_QUALITY = "low"

# US dollars per million tokens: (text input, image output). From developers.openai.com/api/docs/pricing,
# checked 2026-10-16. A model that is not here is refused, so a run never spends at an unknown price.
PRICES = {
    "gpt-image-1-mini": (2.00, 8.00),
    "gpt-image-1.5": (5.00, 32.00),
    "gpt-image-2": (5.00, 30.00),
}
# Image output tokens per picture for GPT Image models (OpenAI image guide). Used only for the estimate; the
# real cost comes from the usage in each answer.
OUTPUT_TOKENS = {
    ("low", "1024x1024"): 272, ("low", "1536x1024"): 400, ("low", "1024x1536"): 408,
    ("medium", "1024x1024"): 1056, ("medium", "1536x1024"): 1568, ("medium", "1024x1536"): 1584,
}

STYLE = (
    "Style: a flat, simple vector illustration, like an icon in a children's dictionary. One clear subject in "
    "the centre, with empty space around it, inside the middle 16:9 area of the picture. Plain, very light "
    "off-white background. Soft sage green and deep teal (#1F6B5F) as the main colours, with a few small warm "
    "accents only where the subject needs them. Clean shapes, no shadows or texture. No text, letters, numbers "
    "or signs anywhere in the picture. No realistic human faces: people are simple figures without facial "
    "details."
)


# --- Prompt --------------------------------------------------------------------------------------------

def build_prompt(sense: dict) -> str:
    """One prompt per sense: what to show (from the dictionary), then the fixed style rule."""
    example = easy_example(sense["examples_json"])
    lines = [
        f'A picture that shows the meaning of the English word "{sense["lemma"]}" ({sense["pos"]}) for a '
        f"language learner.",
        f"Meaning: {sense['simple_definition']}",
    ]
    if example:
        lines.append(f'Example sentence (do not write it in the picture): "{example}"')
    lines.append("Show this meaning clearly, so a learner can guess the word from the picture alone.")
    lines.append(STYLE)
    return "\n".join(lines)


def file_name_for(sense: dict, image: bytes) -> str:
    """<word>_<sense number>_ai_<8 hex of sha256>.webp. A new picture always gets a new name, because Caddy and
    the phones cache every media file for a year."""
    digest = hashlib.sha256(image).hexdigest()[:8]
    return f"{sanitize_filename(sense['lemma'])}_{sense['position'] + 1}_ai_{digest}.webp"


# --- Generators ----------------------------------------------------------------------------------------

@dataclass
class GeneratedImage:
    image: bytes  # the picture as the provider sent it (any format Pillow can open)
    input_tokens: int
    output_tokens: int
    cost_usd: float


class ProviderStop(RuntimeError):
    """An error that will hit every sense the same way (wrong key, no credit left): the run stops."""


class ImageGenerator(Protocol):
    """A provider behind one small interface, so it can change. OpenAIImageGenerator is the real one; the
    tests use a fake."""

    provider: str
    model: str

    def estimate_cost(self, prompt: str) -> float: ...
    def generate(self, prompt: str) -> GeneratedImage: ...


class OpenAIImageGenerator:
    """POST https://api.openai.com/v1/images/generations. Key: OPENAI_API_KEY in pipeline/.env.

    429 and 5xx are tried again (5 tries in all), with the Retry-After header when OpenAI sends one: a new
    account may make only a few images a minute. A blocked prompt (moderation_blocked) fails only that sense.
    """

    provider = "openai"
    URL = "https://api.openai.com/v1/images/generations"
    TRIES = 5

    def __init__(self, model: str = DEFAULT_MODEL, api_key: str | None = None, client: httpx.Client | None = None,
                 sleep: Callable[[float], None] = time.sleep):
        if model not in PRICES:
            raise ValueError(f"No price for {model}. Add it to PRICES in media_generate.py first.")
        self.model = model
        self.api_key = api_key if api_key is not None else os.environ.get("OPENAI_API_KEY", "").strip()
        if not self.api_key:
            raise ProviderStop("OPENAI_API_KEY is not set in pipeline/.env (https://platform.openai.com/api-keys)")
        self.client = client or httpx.Client(timeout=180)
        self.sleep = sleep

    def cost(self, text_tokens: int, image_tokens: int) -> float:
        price_in, price_out = PRICES[self.model]
        return (text_tokens * price_in + image_tokens * price_out) / 1_000_000

    def estimate_cost(self, prompt: str) -> float:
        # About 4 characters per token in English; counted as 3 so the estimate is on the high side.
        return self.cost(len(prompt) // 3 + 20, OUTPUT_TOKENS[(IMAGE_QUALITY, IMAGE_SIZE)])

    def generate(self, prompt: str) -> GeneratedImage:
        body = {"model": self.model, "prompt": prompt, "n": 1, "size": IMAGE_SIZE, "quality": IMAGE_QUALITY,
                "output_format": "webp"}
        headers = {"Authorization": f"Bearer {self.api_key}"}
        for attempt in range(1, self.TRIES + 1):
            try:
                response = self.client.post(self.URL, json=body, headers=headers)
            except httpx.TransportError as error:
                if attempt == self.TRIES:
                    raise RuntimeError(f"OpenAI did not answer: {error}") from error
                self.sleep(2 ** attempt)
                continue
            if response.status_code == 200:
                return self._read(response.json())
            code, message = _error_of(response)
            if response.status_code == 401 or code in ("insufficient_quota", "billing_hard_limit_reached"):
                raise ProviderStop(f"OpenAI {response.status_code} {code}: {message}")
            if response.status_code in (429, 500, 502, 503, 504) and attempt < self.TRIES:
                self.sleep(_retry_after(response) or min(60, 5 * 2 ** (attempt - 1)) + random.random())
                continue
            raise RuntimeError(f"OpenAI {response.status_code} {code}: {message}")
        raise AssertionError("unreachable")

    def _read(self, data: dict) -> GeneratedImage:
        image = base64.b64decode(data["data"][0]["b64_json"])
        usage = data.get("usage") or {}
        details = usage.get("input_tokens_details") or {}
        input_tokens = int(usage.get("input_tokens", 0))
        output_tokens = int(usage.get("output_tokens", 0))
        text_tokens = int(details.get("text_tokens", input_tokens))
        return GeneratedImage(image, input_tokens, output_tokens, self.cost(text_tokens, output_tokens))


def _error_of(response: httpx.Response) -> tuple[str, str]:
    try:
        error = response.json().get("error") or {}
    except ValueError:
        return "", response.text[:200]
    return str(error.get("code") or error.get("type") or ""), str(error.get("message", ""))[:300]


def _retry_after(response: httpx.Response) -> float | None:
    try:
        seconds = float(response.headers.get("retry-after", ""))
    except ValueError:
        return None
    return min(seconds, 60.0) if seconds >= 0 else None


def make_generator(provider: str, model: str | None) -> ImageGenerator:
    if provider == "openai":
        return OpenAIImageGenerator(model or DEFAULT_MODEL)
    raise ValueError(f"Unknown provider {provider!r}. Known: openai")


# --- One sense -----------------------------------------------------------------------------------------

@dataclass
class GenerationResult:
    sense: dict
    prompt: str
    file_name: str | None = None  # None when it failed (nothing is saved)
    input_tokens: int = 0
    output_tokens: int = 0
    cost_usd: float = 0.0
    error: str | None = None
    stop: bool = False  # the provider said every next call will fail too


def generate_one(sense: dict, generator: ImageGenerator, media_dir: Path) -> GenerationResult:
    """Runs in a worker thread: the model and the file only. Never raises; a failure is in result.error."""
    result = GenerationResult(sense, build_prompt(sense))
    try:
        image = generator.generate(result.prompt)
    except ProviderStop as error:
        result.error, result.stop = str(error), True
        return result
    except Exception as error:  # this sense stays needs_ai and is tried again on the next run
        result.error = str(error) or repr(error)
        return result
    result.input_tokens, result.output_tokens, result.cost_usd = image.input_tokens, image.output_tokens, image.cost_usd
    try:
        name = file_name_for(sense, image.image)
        resize_and_save_webp(image.image, media_dir / name)  # 320 px wide, quality 80, like the Pixabay photos
        result.file_name = name
    except Exception as error:  # the money is spent, but the picture could not be read
        result.error = f"could not save the picture: {error}"
    return result


# --- The run -------------------------------------------------------------------------------------------

class Store(Protocol):
    """What run() and link_generated() need from the database. PostgresStore is the real one."""

    def pending_senses(self, words: list[str] | None, limit: int, regenerate: bool) -> list[dict]: ...
    def save_generation(self, result: GenerationResult, provider: str, model: str) -> None: ...
    def unlinked(self) -> list[dict]: ...
    def link(self, generation: dict, provider: str, model: str, facts: tuple) -> bool: ...


@dataclass
class RunSummary:
    planned: int = 0
    made: list[GenerationResult] = field(default_factory=list)
    failed: list[GenerationResult] = field(default_factory=list)
    cost: float = 0.0
    estimate: float = 0.0  # dry run: the estimate for every planned sense
    skipped: int = 0  # not started, because the budget cap (or a provider stop) came first
    stopped_by: str | None = None  # "cost" or the provider's message


def run(store: Store, generator: ImageGenerator, media_dir: Path, *, words: list[str] | None = None,
        limit: int = 100000, regenerate: bool = False, max_cost: float = DEFAULT_MAX_COST, workers: int = 2,
        dry_run: bool = False, log: Callable[[str], None] = print) -> RunSummary:
    """Makes a picture for each pending sense. A new call starts only when the money spent, plus the estimate
    of every call in flight, plus its own estimate stays within max_cost; so the cap is never passed, as long
    as no call costs more than its estimate (the estimate grows to the biggest real cost seen)."""
    senses = store.pending_senses(words, limit, regenerate)
    summary = RunSummary(planned=len(senses))
    log(f"{len(senses)} senses need an AI image. {generator.provider} {generator.model}, {IMAGE_QUALITY} quality, "
        f"{IMAGE_SIZE}, budget cap ${max_cost:.2f}")
    if dry_run:
        for index, sense in enumerate(senses):
            prompt = build_prompt(sense)
            summary.estimate += generator.estimate_cost(prompt)
            if index < 3:
                log(f"\n--- {sense['lemma']} #{sense['position'] + 1} ({sense['pos']}) ---\n{prompt}")
        each = summary.estimate / max(len(senses), 1)
        fits = int(max_cost // each) if each else 0
        log(f"\nDry run: nothing was called or saved. Estimate ${summary.estimate:.4f} for {len(senses)} pictures "
            f"(about ${each:.4f} each); the ${max_cost:.2f} cap covers about {fits} pictures.")
        return summary

    queue = iter(senses)
    in_flight: dict[Future, float] = {}
    worst = 0.0  # the biggest (real cost - estimate) seen, added to every next estimate
    stop_message: str | None = None
    with ThreadPoolExecutor(max_workers=workers) as pool:
        while True:
            while stop_message is None and len(in_flight) < workers:
                sense = next(queue, None)
                if sense is None:
                    break
                estimate = generator.estimate_cost(build_prompt(sense)) + worst
                if summary.cost + sum(in_flight.values()) + estimate > max_cost:
                    stop_message = "cost"
                    summary.skipped += 1  # this one was taken from the queue but not started
                    break
                in_flight[pool.submit(generate_one, sense, generator, media_dir)] = estimate
            if not in_flight:
                break
            finished, _ = wait(in_flight, return_when=FIRST_COMPLETED)
            for future in finished:
                estimate = in_flight.pop(future)
                result = future.result()
                summary.cost += result.cost_usd
                worst = max(worst, result.cost_usd - (estimate - worst))
                label = f"{result.sense['lemma']} #{result.sense['position'] + 1}"
                done = len(summary.made) + len(summary.failed) + 1
                if result.file_name is None:
                    summary.failed.append(result)
                    log(f"[{done}/{len(senses)}] {label}: failed ({result.error}) | ${summary.cost:.4f}")
                    if result.stop:
                        stop_message = result.error
                    continue
                store.save_generation(result, generator.provider, generator.model)
                summary.made.append(result)
                log(f"[{done}/{len(senses)}] {label}: {result.file_name} | ${summary.cost:.4f}")
    summary.skipped += sum(1 for _ in queue)
    if summary.skipped:
        summary.stopped_by = stop_message or "cost"
    return summary


def print_summary(summary: RunSummary, max_cost: float, log: Callable[[str], None] = print) -> None:
    log("")
    log(f"Made {len(summary.made)} pictures, {len(summary.failed)} failed (run again to retry those). "
        f"Cost ${summary.cost:.4f} of the ${max_cost:.2f} cap.")
    if summary.stopped_by == "cost":
        log(f"Stopped at the budget cap: {summary.skipped} senses have no picture yet. Run again to go on.")
    elif summary.stopped_by:
        log(f"Stopped: {summary.stopped_by}. {summary.skipped} senses were not started.")
    if summary.made:
        log("Nothing in the app changed yet. Open the report, then copy the files to the server and link them:\n"
            "  rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/\n"
            "  uv run pipeline media-generate --link")


# --- Link (after rsync) --------------------------------------------------------------------------------

def is_on_server(base_url: str, client: httpx.Client | None = None) -> Callable[[str], bool]:
    """A check that the file answers 200 at MEDIA_BASE_URL (Caddy), so no sense ever points to a missing file."""
    http = client or httpx.Client(timeout=20, follow_redirects=True)

    def check(file_name: str) -> bool:
        try:
            return http.head(f"{base_url.rstrip('/')}/{file_name}").status_code == 200
        except httpx.HTTPError:
            return False

    return check


@dataclass
class LinkSummary:
    linked: int = 0
    not_on_server: list[str] = field(default_factory=list)
    missing_here: list[str] = field(default_factory=list)
    skipped: int = 0  # the sense changed meanwhile (picked in the admin, removed, or already has an AI image)


def link_generated(store: Store, media_dir: Path, on_server: Callable[[str], bool],
                   log: Callable[[str], None] = print) -> LinkSummary:
    """Links the newest unlinked picture of every sense that is still needs_ai, with the link-media rules:
    a sense with image_type 'none' or media_status 'removed' is never touched. The old picture's row and file
    stay (the app build in people's phones still points to them until the next export)."""
    summary = LinkSummary()
    for generation in store.unlinked():
        name = generation["file_name"]
        if not (media_dir / name).is_file():
            summary.missing_here.append(name)
            continue
        if not on_server(name):
            summary.not_on_server.append(name)
            continue
        if store.link(generation, generation["provider"], generation["model"], file_facts(media_dir, name)):
            summary.linked += 1
            log(f"linked {generation['lemma']} #{generation['position'] + 1}: {name}")
        else:
            summary.skipped += 1
    log(f"\nLinked {summary.linked}. Skipped {summary.skipped} (the sense changed in the admin meanwhile).")
    if summary.not_on_server:
        log(f"{len(summary.not_on_server)} files are not on the server yet (rsync first): "
            + ", ".join(summary.not_on_server[:5]) + (" ..." if len(summary.not_on_server) > 5 else ""))
    if summary.missing_here:
        log(f"{len(summary.missing_here)} files are missing in {media_dir}: " + ", ".join(summary.missing_here[:5]))
    if summary.linked:
        log("The app shows the new pictures after the next export and app build (uv run pipeline export).")
    return summary


# --- Postgres ------------------------------------------------------------------------------------------

class PostgresStore:
    """Reads the needs_ai senses, writes media_generation and links. Tables: backend Flyway V6, V8, V14."""

    def __init__(self, connection):
        self.pg = connection

    def pending_senses(self, words: list[str] | None, limit: int, regenerate: bool) -> list[dict]:
        """Bundled needs_ai senses that may have a picture, most common words first. Unless regenerate, a sense
        that already has an unlinked AI picture (for example from the trial) is skipped."""
        sql = """SELECT s.id, w.lemma, s.position, s.pos, s.simple_definition, s.examples_json,
                        s.media_id, m.file_name AS old_file_name
                 FROM dictionary_sense s
                 JOIN dictionary_word w ON w.id = s.word_id
                 LEFT JOIN media m ON m.id = s.media_id
                 WHERE s.media_status = 'needs_ai' AND s.image_type <> 'none'"""
        params: list = []
        if not regenerate:
            sql += " AND NOT EXISTS (SELECT 1 FROM media_generation g WHERE g.sense_id = s.id AND g.media_id IS NULL)"
        if words:
            sql += " AND w.lemma = ANY(%s)"
            params.append([word.lower() for word in words])
        sql += " ORDER BY w.word_rank, s.position LIMIT %s"
        params.append(limit)
        with self.pg.cursor() as cursor:
            cursor.execute(sql, params)
            columns = [column.name for column in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]

    def save_generation(self, result: GenerationResult, provider: str, model: str) -> None:
        with self.pg.cursor() as cursor:
            cursor.execute(
                """INSERT INTO media_generation (sense_id, file_name, provider, model, prompt, input_tokens,
                   output_tokens, cost_usd, created_at, old_media_id) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (result.sense["id"], result.file_name, provider, model, result.prompt, result.input_tokens,
                 result.output_tokens, round(result.cost_usd, 6), datetime.now(timezone.utc), result.sense["media_id"]),
            )
        self.pg.commit()  # saved one by one, so stopping the run loses nothing

    def unlinked(self) -> list[dict]:
        """The newest unlinked picture of each sense that is still needs_ai."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT DISTINCT ON (g.sense_id) g.id, g.sense_id, g.file_name, g.provider, g.model, w.lemma, s.position
                   FROM media_generation g
                   JOIN dictionary_sense s ON s.id = g.sense_id
                   JOIN dictionary_word w ON w.id = s.word_id
                   WHERE g.media_id IS NULL AND s.media_status = 'needs_ai'
                   ORDER BY g.sense_id, g.created_at DESC, g.id DESC"""
            )
            columns = [column.name for column in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]

    def link(self, generation: dict, provider: str, model: str, facts: tuple) -> bool:
        """One transaction: the media row (source 'ai'), the sense, the generation. False (and nothing changed)
        when the sense is no longer needs_ai, or its image_type became 'none'."""
        width, height, size = facts
        now = datetime.now(timezone.utc)
        with self.pg.transaction(), self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT 1 FROM dictionary_sense WHERE id = %s AND media_status = 'needs_ai' AND image_type <> 'none'
                   FOR UPDATE""",
                (generation["sense_id"],),
            )
            if cursor.fetchone() is None:
                return False
            cursor.execute(
                """INSERT INTO media (file_name, kind, width, height, bytes, license_note, source, created_at)
                   VALUES (%s, 'photo', %s, %s, %s, %s, 'ai', %s) RETURNING id""",
                (generation["file_name"], width, height, size, f"AI image ({provider} {model})", now),
            )
            media_id = cursor.fetchone()[0]
            cursor.execute(
                "UPDATE dictionary_sense SET media_id = %s, media_status = %s WHERE id = %s",
                (media_id, STATUS_DONE, generation["sense_id"]),
            )
            cursor.execute(
                "UPDATE media_generation SET media_id = %s, linked_at = %s WHERE id = %s",
                (media_id, now, generation["id"]),
            )
        return True

    def report_rows(self) -> list[dict]:
        """Every AI picture made, newest first, with the picture it replaces (for the report)."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT w.lemma, s.position, s.pos, s.simple_definition, g.file_name, g.prompt, g.provider, g.model,
                          g.cost_usd, g.created_at, g.linked_at, old.file_name AS old_file_name, old.kind AS old_kind
                   FROM media_generation g
                   JOIN dictionary_sense s ON s.id = g.sense_id
                   JOIN dictionary_word w ON w.id = s.word_id
                   LEFT JOIN media old ON old.id = g.old_media_id
                   ORDER BY g.created_at DESC, g.id DESC"""
            )
            columns = [column.name for column in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]


# --- The report ----------------------------------------------------------------------------------------

def write_report(rows: list[dict], report_path: Path, media_dir: Path) -> int:
    """data/out/media-generate.html: the old picture next to the AI picture, with the word, meaning, prompt and
    cost. Newest first. Returns the number of rows."""
    report_path.parent.mkdir(parents=True, exist_ok=True)
    relative = Path(os.path.relpath(media_dir.resolve(), report_path.resolve().parent))
    total = sum(float(row["cost_usd"]) for row in rows)
    body = []
    for row in rows:
        new = html.escape((relative / row["file_name"]).as_posix())
        if row.get("old_file_name"):
            old_src = html.escape((relative / row["old_file_name"]).as_posix())
            old = (f'<video src="{old_src}" muted loop autoplay playsinline width="240"></video>'
                   if row.get("old_kind") == "clip" else f'<img src="{old_src}" width="240" loading="lazy">')
        else:
            old = "<small>(none)</small>"
        state = "linked" if row.get("linked_at") else "not linked"
        body.append(
            f'<tr><td>{old}</td><td><img src="{new}" width="240" loading="lazy"></td>'
            f'<td><b>{html.escape(row["lemma"])}</b> <small>{html.escape(row["pos"])} #{row["position"] + 1}</small>'
            f'<br>{html.escape(row["simple_definition"])}'
            f'<br><small>{html.escape(row["provider"])} {html.escape(row["model"])}, ${float(row["cost_usd"]):.4f}, '
            f'{state}</small>'
            f'<details><summary>Prompt</summary><pre>{html.escape(row["prompt"])}</pre></details></td></tr>'
        )
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>AI images</title>
<style>
body {{ font: 15px system-ui, sans-serif; margin: 24px; }}
table {{ border-collapse: collapse; width: 100%; }}
th {{ text-align: left; padding: 8px; }}
td {{ border-bottom: 1px solid #ddd; padding: 8px; vertical-align: top; }}
img, video {{ width: 240px; height: 135px; object-fit: cover; border-radius: 8px; border: 1px solid #ddd; }}
pre {{ white-space: pre-wrap; font-size: 12px; max-width: 520px; }} small {{ color: #666; }}
</style></head><body>
<h1>AI images</h1>
<p>{len(rows)} pictures, ${total:.4f} in all. Newest first. Pictures are cropped to 240 x 135, like in the app.</p>
<table>
<tr><th>Old picture</th><th>AI picture</th><th>Sense</th></tr>
{chr(10).join(body)}
</table></body></html>
"""
    report_path.write_text(page, encoding="utf-8")
    return len(rows)
