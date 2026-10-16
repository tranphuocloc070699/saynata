"""Claude vision check of the sense pictures and clips (TASK-78).

For every bundled sense that has media, Claude Haiku looks at the picture (for a clip: one frame from the
middle) together with the word, part of speech, simple explanation and easy example, and says whether the
picture clearly shows THIS meaning: "good", "wrong" or "unsure". Results go to the Postgres table media_check
(Flyway V12). With --apply the "wrong" senses get media_status = 'needs_ai', like the admin's "Mark for AI".

The flow, in the order of the functions below:
    PostgresStore.pending_senses  ->  check_one (worker thread: file, ffmpeg, Claude)  ->  run (main thread:
    cost stop, save)  ->  PostgresStore.apply_wrong  ->  write_report

Workers only read files and call Claude. Only the main thread talks to Postgres (a psycopg connection is
not thread-safe).
"""

import base64
import html
import json
import os
import re
import subprocess
from collections.abc import Callable
from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol

DEFAULT_MODEL = "claude-haiku-5-5"
DEFAULT_MAX_COST = 2.0
DEFAULT_REPORT = Path("data/out/media-check.html")
VERDICTS = ("good", "wrong", "unsure")

# US dollars per million tokens (input, output), for prompts up to 100K tokens. Same numbers as
# backend/src/main/resources/application.properties (app.claude.pricing.*). Haiku 5.5 checked 2026-10-15.
PRICES = {"claude-haiku-5-5": (0.10, 0.50)}

SYSTEM_PROMPT = """You check pictures for a learner's English dictionary. Each picture is shown next to ONE
meaning of a word. Decide if the picture helps a learner understand THIS meaning.

Answer with JSON only, no other text:
{"verdict": "good" | "wrong" | "unsure", "reason": "<one short sentence>"}

- "good": the picture clearly shows THIS meaning. A learner who sees it would guess the right meaning.
- "wrong": the picture shows a different meaning of the word, something unrelated, or something that would
  mislead a learner. Also use "wrong" when the picture is only loosely related to the meaning.
- "unsure": you cannot tell (the picture is too small, too abstract, or fits the meaning only in a way you
  cannot judge).
For a verb or an action, the action must be visible. A picture of a clip is one frame from a 3 second loop.
The reason is one short English sentence (at most 25 words) that says what you see."""


class Store(Protocol):
    """What run() needs from the database. PostgresStore is the real one; the tests use a small fake."""

    def pending_senses(self, words: list[str] | None, limit: int, recheck: bool) -> list[dict]: ...
    def save_check(self, result: "CheckResult", model: str) -> None: ...
    def apply_wrong(self) -> int: ...


@dataclass
class CheckResult:
    sense: dict
    verdict: str | None = None  # None when the check failed (nothing is saved)
    reason: str = ""
    input_tokens: int = 0
    output_tokens: int = 0
    cost_usd: float = 0.0
    error: str | None = None


@dataclass
class Reply:
    """What the Claude call gives back: the text and the tokens it cost."""

    text: str
    input_tokens: int
    output_tokens: int


class BadAnswer(ValueError):
    """Claude's answer was not the JSON we asked for."""


# --- Prompt and answer ---------------------------------------------------------------------------------

def easy_example(examples_json: str) -> str:
    """The easy example of the sense (the first one when no level is "easy")."""
    try:
        items = json.loads(examples_json)
    except (TypeError, ValueError):
        return ""
    for item in items:
        if item.get("level") == "easy":
            return item.get("text", "")
    return items[0].get("text", "") if items else ""


def build_prompt(sense: dict) -> str:
    picture = "one frame from a short clip" if sense["kind"] == "clip" else "a photo"
    return (
        f"Headword: {sense['lemma']}\n"
        f"Part of speech: {sense['pos']}\n"
        f"Meaning: {sense['simple_definition']}\n"
        f"Example: {easy_example(sense['examples_json'])}\n"
        f"The picture is {picture}. Does it clearly show this meaning?"
    )


def parse_answer(text: str) -> tuple[str, str]:
    """(verdict, reason) from Claude's text. Raises BadAnswer when it is not the JSON we asked for."""
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())  # some answers come in a code fence
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start == -1 or end <= start:
        raise BadAnswer(f"no JSON object in: {text[:80]!r}")
    try:
        data = json.loads(cleaned[start:end + 1])
    except ValueError as error:
        raise BadAnswer(f"not valid JSON: {text[:80]!r}") from error
    verdict = str(data.get("verdict", "")).strip().lower() if isinstance(data, dict) else ""
    if verdict not in VERDICTS:
        raise BadAnswer(f"verdict must be good, wrong or unsure: {text[:80]!r}")
    reason = str(data.get("reason", "")).strip()[:300]
    return verdict, reason or "(no reason given)"


def cost_usd(model: str, input_tokens: int, output_tokens: int) -> float:
    """Dollars for one call. A model without a price here is counted at the highest price listed."""
    price_in, price_out = PRICES.get(model) or (max(p[0] for p in PRICES.values()), max(p[1] for p in PRICES.values()))
    return (input_tokens * price_in + output_tokens * price_out) / 1_000_000


# --- The picture ---------------------------------------------------------------------------------------

def middle_frame(path: Path, run: Callable = subprocess.run) -> bytes:
    """One JPEG frame from the middle of a clip. Needs ffmpeg (ffprobe tells the length; 3 s if unknown)."""
    duration = 3.0
    try:
        probe = run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
            capture_output=True, text=True, check=True,
        )
        duration = float(probe.stdout.strip())
    except (OSError, ValueError, subprocess.CalledProcessError):
        pass  # ffprobe missing or odd output: our clips are 3 s long
    try:
        frame = run(
            ["ffmpeg", "-v", "error", "-ss", f"{duration / 2:.2f}", "-i", str(path), "-frames:v", "1",
             "-f", "image2", "-c:v", "mjpeg", "pipe:1"],
            capture_output=True, check=True,
        )
    except FileNotFoundError as error:
        raise RuntimeError("ffmpeg is not installed (brew install ffmpeg)") from error
    except subprocess.CalledProcessError as error:
        raise RuntimeError(f"ffmpeg could not read {path.name}") from error
    if not frame.stdout:
        raise RuntimeError(f"ffmpeg gave no frame for {path.name}")
    return frame.stdout


def load_picture(media_dir: Path, sense: dict, run: Callable = subprocess.run) -> tuple[bytes, str]:
    """(image bytes, media type) for Claude. A photo is sent as it is (320 px WebP); a clip as one JPEG frame."""
    path = media_dir / sense["file_name"]
    if not path.is_file():
        raise RuntimeError(f"file not found: {path}")
    if sense["kind"] == "clip":
        return middle_frame(path, run), "image/jpeg"
    return path.read_bytes(), "image/webp"


def build_content(image: bytes, media_type: str, prompt: str) -> list[dict]:
    return [
        {"type": "image", "source": {"type": "base64", "media_type": media_type,
                                     "data": base64.standard_b64encode(image).decode("ascii")}},
        {"type": "text", "text": prompt},
    ]


# --- Claude --------------------------------------------------------------------------------------------

def fetch_from_claude(model: str, content: list[dict]) -> Reply:
    """One real Claude call. The SDK client retries 429, 5xx and connection errors by itself (max_retries=5)."""
    from pipeline.generate import _client

    response = _client().messages.create(
        model=model,
        max_tokens=1000,  # room for the short thinking that is on by default; the answer is about 40 tokens
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": content}],
        output_config={"effort": "low"},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError(f"Claude refused: {response.stop_details}")
    text = "".join(block.text for block in response.content if block.type == "text")
    return Reply(text, response.usage.input_tokens, response.usage.output_tokens)


def check_one(sense: dict, media_dir: Path, model: str, fetch: Callable[[str, list[dict]], Reply],
              run: Callable = subprocess.run) -> CheckResult:
    """Runs in a worker thread: file, ffmpeg and Claude only. Never raises; a failure is in result.error."""
    result = CheckResult(sense)
    try:
        image, media_type = load_picture(media_dir, sense, run)
        reply = fetch(model, build_content(image, media_type, build_prompt(sense)))
    except Exception as error:  # a failed sense is left unchecked and tried again on the next run
        result.error = str(error) or repr(error)
        return result
    result.input_tokens, result.output_tokens = reply.input_tokens, reply.output_tokens
    result.cost_usd = cost_usd(model, reply.input_tokens, reply.output_tokens)  # the tokens were spent either way
    try:
        result.verdict, result.reason = parse_answer(reply.text)
    except BadAnswer as error:
        result.error = str(error)
    return result


# --- The run -------------------------------------------------------------------------------------------

@dataclass
class RunSummary:
    counts: dict[str, int]
    failed: int = 0
    cost: float = 0.0
    input_tokens: int = 0
    output_tokens: int = 0
    stopped_by_cost: bool = False
    skipped: int = 0  # senses left unchecked because the cost stop came first
    applied: int = 0
    wrong: list[CheckResult] = None

    def checked(self) -> int:
        return sum(self.counts.values())


def run(store: Store, media_dir: Path, model: str, fetch: Callable[[str, list[dict]], Reply], *,
        words: list[str] | None = None, limit: int = 100000, recheck: bool = False, apply: bool = False,
        max_cost: float = DEFAULT_MAX_COST, workers: int = 4, ffmpeg_run: Callable = subprocess.run,
        log: Callable[[str], None] = print) -> RunSummary:
    """Checks the pending senses. At most `workers` calls are in flight, and no new one starts once the
    running cost has reached max_cost (so the total can pass it by at most `workers` calls, a few cents less)."""
    senses = store.pending_senses(words, limit, recheck)
    summary = RunSummary(counts={verdict: 0 for verdict in VERDICTS}, wrong=[])
    log(f"{len(senses)} senses to check, model {model}, {workers} at a time, stop at ${max_cost:.2f}")
    queue = iter(senses)
    in_flight: set[Future] = set()
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        while True:
            while len(in_flight) < workers and summary.cost < max_cost:
                sense = next(queue, None)
                if sense is None:
                    break
                in_flight.add(pool.submit(check_one, sense, media_dir, model, fetch, ffmpeg_run))
            if not in_flight:
                break
            finished, in_flight = wait(in_flight, return_when=FIRST_COMPLETED)
            for future in finished:
                result = future.result()
                done += 1
                summary.cost += result.cost_usd
                summary.input_tokens += result.input_tokens
                summary.output_tokens += result.output_tokens
                label = f"{result.sense['lemma']} #{result.sense['position']}"
                if result.verdict is None:
                    summary.failed += 1
                    log(f"[{done}/{len(senses)}] {label}: failed ({result.error}) | ${summary.cost:.4f}")
                    continue
                store.save_check(result, model)
                summary.counts[result.verdict] += 1
                if result.verdict == "wrong":
                    summary.wrong.append(result)
                log(f"[{done}/{len(senses)}] {label}: {result.verdict} | ${summary.cost:.4f}")
    summary.skipped = sum(1 for _ in queue)
    summary.stopped_by_cost = summary.skipped > 0
    if apply:
        summary.applied = store.apply_wrong()
    return summary


def print_summary(summary: RunSummary, applied: bool, log: Callable[[str], None] = print) -> None:
    log("")
    log(f"Checked {summary.checked()}: " + ", ".join(f"{n} {verdict}" for verdict, n in summary.counts.items())
        + f"; {summary.failed} failed (run again to retry those)")
    log(f"Cost ${summary.cost:.4f} ({summary.input_tokens} input and {summary.output_tokens} output tokens)")
    if summary.stopped_by_cost:
        log(f"Stopped at the cost limit: {summary.skipped} senses are not checked yet. Run again to go on.")
    if summary.wrong:
        log(f"\nWrong ({len(summary.wrong)}):")
        for result in summary.wrong[:25]:
            log(f"- {result.sense['lemma']} ({result.sense['pos']}): {result.reason}")
        if len(summary.wrong) > 25:
            log(f"... and {len(summary.wrong) - 25} more, see the report")
    if applied:
        log(f"\n--apply: {summary.applied} senses are now marked needs_ai (wrong verdicts only, never unsure)")
    elif summary.counts["wrong"]:
        log("\nNothing was marked. After you review the report, run again with --apply.")


# --- Postgres ------------------------------------------------------------------------------------------

class PostgresStore:
    """Reads the senses to check and writes media_check. Table: backend Flyway V12."""

    def __init__(self, connection):
        self.pg = connection

    def pending_senses(self, words: list[str] | None, limit: int, recheck: bool) -> list[dict]:
        """Bundled senses with media, most common words first. Skips removed and needs_ai senses, and (unless
        recheck) senses that already have a check for the media they have now."""
        sql = """SELECT s.id, w.lemma, s.position, s.pos, s.simple_definition, s.examples_json,
                        s.media_id, m.file_name, m.kind
                 FROM dictionary_sense s
                 JOIN dictionary_word w ON w.id = s.word_id
                 JOIN media m ON m.id = s.media_id
                 WHERE s.image_type <> 'none'
                   AND (s.media_status IS NULL OR s.media_status NOT IN ('removed', 'needs_ai'))"""
        params: list = []
        if not recheck:
            sql += " AND NOT EXISTS (SELECT 1 FROM media_check c WHERE c.sense_id = s.id AND c.media_id = s.media_id)"
        if words:
            sql += " AND w.lemma = ANY(%s)"
            params.append([word.lower() for word in words])
        sql += " ORDER BY w.word_rank, s.position LIMIT %s"
        params.append(limit)
        with self.pg.cursor() as cursor:
            cursor.execute(sql, params)
            columns = [column.name for column in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]

    def save_check(self, result: CheckResult, model: str) -> None:
        with self.pg.cursor() as cursor:
            cursor.execute(
                """INSERT INTO media_check (sense_id, media_id, verdict, reason, model, checked_at, input_tokens,
                   output_tokens, cost_usd) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                (result.sense["id"], result.sense["media_id"], result.verdict, result.reason, model,
                 datetime.now(timezone.utc), result.input_tokens, result.output_tokens, round(result.cost_usd, 6)),
            )
        self.pg.commit()  # saved one by one, so stopping the run loses nothing

    def apply_wrong(self) -> int:
        """media_status = 'needs_ai' on every sense whose newest check of its current media says "wrong"
        (also checks from an earlier run) and that has no status yet. The media stays until an AI image
        replaces it. "unsure" is never applied."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """UPDATE dictionary_sense s SET media_status = 'needs_ai'
                   WHERE s.media_status IS NULL AND s.image_type <> 'none'
                     AND (SELECT c.verdict FROM media_check c
                          WHERE c.sense_id = s.id AND c.media_id = s.media_id
                          ORDER BY c.checked_at DESC, c.id DESC LIMIT 1) = 'wrong'"""
            )
            count = cursor.rowcount
        self.pg.commit()
        return count

    def latest_checks(self) -> list[dict]:
        """The newest check of every sense (for the report): wrong first, then unsure, then good."""
        with self.pg.cursor() as cursor:
            cursor.execute(
                """SELECT DISTINCT ON (c.sense_id) w.lemma, s.position, s.pos, s.simple_definition, m.file_name,
                          m.kind, c.verdict, c.reason, c.model, s.media_status, w.word_rank
                   FROM media_check c
                   JOIN dictionary_sense s ON s.id = c.sense_id
                   JOIN dictionary_word w ON w.id = s.word_id
                   JOIN media m ON m.id = c.media_id
                   ORDER BY c.sense_id, c.checked_at DESC, c.id DESC"""
            )
            columns = [column.name for column in cursor.description]
            rows = [dict(zip(columns, row)) for row in cursor.fetchall()]
        order = {"wrong": 0, "unsure": 1, "good": 2}
        return sorted(rows, key=lambda r: (order[r["verdict"]], r["word_rank"], r["position"]))


# --- The report ----------------------------------------------------------------------------------------

def write_report(rows: list[dict], report_path: Path, media_dir: Path) -> int:
    """data/out/media-check.html: thumbnail, word, meaning, verdict and reason, wrong first. Returns the rows."""
    report_path.parent.mkdir(parents=True, exist_ok=True)
    relative = Path(os.path.relpath(media_dir.resolve(), report_path.resolve().parent))
    counts = {verdict: sum(1 for r in rows if r["verdict"] == verdict) for verdict in VERDICTS}
    body = []
    for row in rows:
        source = html.escape((relative / row["file_name"]).as_posix())
        if row["kind"] == "clip":
            thumb = (f'<video src="{source}" muted loop playsinline preload="metadata" width="160" '
                     f'onmouseover="this.play()" onmouseout="this.pause()"></video>')
        else:
            thumb = f'<img src="{source}" width="160" loading="lazy">'
        marked = " (marked needs_ai)" if row.get("media_status") == "needs_ai" else ""
        body.append(
            f'<tr class="{row["verdict"]}"><td>{thumb}</td>'
            f'<td><b>{html.escape(row["lemma"])}</b> <small>{html.escape(row["pos"])} #{row["position"]}</small>'
            f'<br>{html.escape(row["simple_definition"])}</td>'
            f'<td class="v">{row["verdict"]}{marked}</td><td>{html.escape(row["reason"])}</td></tr>'
        )
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Media check</title>
<style>
body {{ font: 15px system-ui, sans-serif; margin: 24px; }}
table {{ border-collapse: collapse; width: 100%; }}
td {{ border-bottom: 1px solid #ddd; padding: 8px; vertical-align: top; }}
tr.wrong {{ background: #fdecea; }} tr.unsure {{ background: #fff6e0; }}
.v {{ font-weight: 600; white-space: nowrap; }} small {{ color: #666; }}
</style></head><body>
<h1>Media check</h1>
<p>{counts['wrong']} wrong, {counts['unsure']} unsure, {counts['good']} good. Wrong first. The newest check of each sense.</p>
<table>
{chr(10).join(body)}
</table></body></html>
"""
    report_path.write_text(page, encoding="utf-8")
    return len(rows)
