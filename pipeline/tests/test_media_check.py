"""The Claude vision check (TASK-78). No real Claude call and no real ffmpeg: both are fakes.

The two Postgres tests at the end need TEST_DATABASE_URL (see conftest.py) and are skipped without it.
"""

import json
import subprocess
from datetime import datetime, timezone
from types import SimpleNamespace

import pytest

from pipeline import media_check
from pipeline.media_check import BadAnswer, CheckResult, Reply

EXAMPLES = json.dumps([{"level": "medium", "text": "She runs fast."}, {"level": "easy", "text": "I run."}])


def sense(sense_id, lemma="run", kind="photo", file_name=None, position=0):
    return {"id": sense_id, "lemma": lemma, "position": position, "pos": "verb",
            "simple_definition": "to move fast on your feet", "examples_json": EXAMPLES,
            "media_id": sense_id * 10, "file_name": file_name or f"{lemma}_{sense_id}.webp", "kind": kind}


class FakeStore:
    """Same three methods as PostgresStore, in memory."""

    def __init__(self, senses):
        self.senses = senses
        self.saved: list[tuple[int, str]] = []
        self.status: dict[int, str] = {}

    def pending_senses(self, words, limit, recheck):
        checked = {sense_id for sense_id, _ in self.saved}
        rows = [s for s in self.senses if recheck or s["id"] not in checked]
        return rows[:limit]

    def save_check(self, result, model):
        self.saved.append((result.sense["id"], result.verdict))

    def apply_wrong(self):
        wrong = [i for i, v in self.saved if v == "wrong" and i not in self.status]
        self.status.update({i: "needs_ai" for i in wrong})
        return len(wrong)


def reply(verdict="good", reason="A person runs.", input_tokens=500, output_tokens=40):
    return Reply(json.dumps({"verdict": verdict, "reason": reason}), input_tokens, output_tokens)


@pytest.fixture
def media_dir(tmp_path):
    folder = tmp_path / "media"
    folder.mkdir()
    return folder


def put_files(media_dir, senses):
    for item in senses:
        (media_dir / item["file_name"]).write_bytes(b"fake-bytes")


# --- prompt --------------------------------------------------------------------------------------------

def test_prompt_has_word_pos_meaning_and_the_easy_example():
    prompt = media_check.build_prompt(sense(1))
    assert "Headword: run" in prompt
    assert "Part of speech: verb" in prompt
    assert "Meaning: to move fast on your feet" in prompt
    assert "Example: I run." in prompt  # the easy one, not the first one in the list
    assert "a photo" in prompt


def test_prompt_says_when_the_picture_is_a_clip_frame():
    assert "one frame from a short clip" in media_check.build_prompt(sense(1, kind="clip"))


def test_content_is_a_base64_image_then_the_text():
    content = media_check.build_content(b"abc", "image/webp", "hello")
    assert content[0] == {"type": "image", "source": {"type": "base64", "media_type": "image/webp", "data": "YWJj"}}
    assert content[1] == {"type": "text", "text": "hello"}


# --- answer --------------------------------------------------------------------------------------------

def test_parse_a_clean_answer():
    assert media_check.parse_answer('{"verdict": "wrong", "reason": "It shows a bank."}') == ("wrong", "It shows a bank.")


def test_parse_an_answer_in_a_code_fence_with_a_capital_letter():
    text = '```json\n{"verdict": "Good", "reason": "Clear."}\n```'
    assert media_check.parse_answer(text) == ("good", "Clear.")


@pytest.mark.parametrize("text", [
    "I think it is fine.",                       # no JSON
    '{"verdict": "good", "reason": ',            # cut off
    '{"verdict": "maybe", "reason": "?"}',       # not one of the three
    '{"reason": "no verdict"}',
    "[1, 2]",
    "",
])
def test_a_malformed_answer_raises(text):
    with pytest.raises(BadAnswer):
        media_check.parse_answer(text)


def test_cost_uses_the_haiku_price_and_the_highest_price_for_an_unknown_model():
    assert media_check.cost_usd("claude-haiku-5-5", 1_000_000, 1_000_000) == pytest.approx(0.60)
    assert media_check.cost_usd("some-new-model", 1_000_000, 0) == pytest.approx(0.10)


# --- clip frame ----------------------------------------------------------------------------------------

def make_fake_ffmpeg(calls, duration="4.0\n", frame=b"JPEGDATA"):
    def run(command, **kwargs):
        calls.append(command)
        if command[0] == "ffprobe":
            return SimpleNamespace(stdout=duration)
        return SimpleNamespace(stdout=frame)
    return run


def test_a_clip_is_checked_on_one_frame_from_the_middle(tmp_path):
    clip = tmp_path / "run_1.mp4"
    clip.write_bytes(b"x")
    calls = []
    frame = media_check.middle_frame(clip, make_fake_ffmpeg(calls))
    assert frame == b"JPEGDATA"
    ffmpeg = calls[1]
    assert ffmpeg[0] == "ffmpeg"
    assert ffmpeg[ffmpeg.index("-ss") + 1] == "2.00"  # half of 4 seconds
    assert ffmpeg[ffmpeg.index("-frames:v") + 1] == "1"


def test_the_middle_is_1_5_seconds_when_ffprobe_is_not_there(tmp_path):
    clip = tmp_path / "run_1.mp4"
    clip.write_bytes(b"x")
    calls = []

    def run(command, **kwargs):
        calls.append(command)
        if command[0] == "ffprobe":
            raise FileNotFoundError("ffprobe")
        return SimpleNamespace(stdout=b"JPEG")

    media_check.middle_frame(clip, run)
    assert calls[1][calls[1].index("-ss") + 1] == "1.50"


def test_missing_ffmpeg_gives_a_clear_error(tmp_path):
    clip = tmp_path / "run_1.mp4"
    clip.write_bytes(b"x")

    def run(command, **kwargs):
        raise FileNotFoundError(command[0])

    with pytest.raises(RuntimeError, match="ffmpeg is not installed"):
        media_check.middle_frame(clip, run)


def test_a_failing_ffmpeg_and_an_empty_frame_are_errors(tmp_path):
    clip = tmp_path / "run_1.mp4"
    clip.write_bytes(b"x")

    def broken(command, **kwargs):
        if command[0] == "ffprobe":
            return SimpleNamespace(stdout="3")
        raise subprocess.CalledProcessError(1, command)

    with pytest.raises(RuntimeError, match="could not read"):
        media_check.middle_frame(clip, broken)
    with pytest.raises(RuntimeError, match="no frame"):
        media_check.middle_frame(clip, make_fake_ffmpeg([], frame=b""))


def test_load_picture_sends_a_photo_as_webp_and_a_clip_as_a_jpeg_frame(media_dir):
    photo, clip = sense(1), sense(2, kind="clip", file_name="run_2.mp4")
    put_files(media_dir, [photo, clip])
    assert media_check.load_picture(media_dir, photo) == (b"fake-bytes", "image/webp")
    assert media_check.load_picture(media_dir, clip, make_fake_ffmpeg([])) == (b"JPEGDATA", "image/jpeg")


# --- one check -----------------------------------------------------------------------------------------

def test_check_one_sends_the_image_and_the_prompt_to_claude(media_dir):
    item = sense(1)
    put_files(media_dir, [item])
    seen = {}

    def fetch(model, content):
        seen["model"], seen["content"] = model, content
        return reply("good")

    result = media_check.check_one(item, media_dir, "claude-haiku-5-5", fetch)
    assert result.verdict == "good" and result.error is None
    assert seen["model"] == "claude-haiku-5-5"
    assert seen["content"][0]["source"]["media_type"] == "image/webp"
    assert "Headword: run" in seen["content"][1]["text"]
    assert result.cost_usd == pytest.approx((500 * 0.10 + 40 * 0.50) / 1_000_000)


def test_check_one_keeps_the_cost_of_a_malformed_answer_but_gives_no_verdict(media_dir):
    item = sense(1)
    put_files(media_dir, [item])
    result = media_check.check_one(item, media_dir, "claude-haiku-5-5", lambda m, c: Reply("not json", 500, 40))
    assert result.verdict is None and "no JSON" in result.error
    assert result.cost_usd > 0


def test_check_one_reports_a_missing_file_and_a_failing_call(media_dir):
    missing = media_check.check_one(sense(1), media_dir, "m", lambda m, c: reply())
    assert "file not found" in missing.error

    item = sense(2)
    put_files(media_dir, [item])

    def boom(model, content):
        raise RuntimeError("503 overloaded")

    assert media_check.check_one(item, media_dir, "m", boom).error == "503 overloaded"


# --- the run -------------------------------------------------------------------------------------------

def test_run_saves_every_verdict_and_counts_them(media_dir):
    senses = [sense(i) for i in (1, 2, 3, 4)]
    put_files(media_dir, senses)
    verdicts = iter(["good", "wrong", "unsure", "wrong"])  # one worker, so the calls come in order
    fetch = lambda model, content: reply(next(verdicts))
    store = FakeStore(senses)
    summary = media_check.run(store, media_dir, "claude-haiku-5-5", fetch, workers=1, log=lambda line: None)
    assert summary.counts == {"good": 1, "wrong": 2, "unsure": 1}
    assert len(store.saved) == 4 and summary.failed == 0
    assert len(summary.wrong) == 2
    assert summary.cost == pytest.approx(4 * (500 * 0.10 + 40 * 0.50) / 1_000_000)


def test_run_does_not_save_a_failed_check(media_dir):
    senses = [sense(1), sense(2)]
    put_files(media_dir, senses[:1])  # the second file is missing
    store = FakeStore(senses)
    summary = media_check.run(store, media_dir, "m", lambda m, c: reply("good"), log=lambda line: None)
    assert summary.failed == 1 and summary.counts["good"] == 1
    assert [i for i, _ in store.saved] == [1]


def test_run_skips_senses_that_are_already_checked_unless_recheck(media_dir):
    senses = [sense(1), sense(2)]
    put_files(media_dir, senses)
    store = FakeStore(senses)
    store.saved.append((1, "good"))
    calls = []

    def fetch(model, content):
        calls.append(1)
        return reply()

    media_check.run(store, media_dir, "m", fetch, log=lambda line: None)
    assert len(calls) == 1  # only sense 2
    media_check.run(store, media_dir, "m", fetch, recheck=True, log=lambda line: None)
    assert len(calls) == 3  # both again


def test_run_stops_starting_checks_at_the_cost_limit(media_dir):
    senses = [sense(i) for i in range(1, 11)]
    put_files(media_dir, senses)
    store = FakeStore(senses)
    # One call costs 0.001 dollars (10,000 input tokens at $0.10 per million). The limit is 0.0025.
    summary = media_check.run(store, media_dir, "claude-haiku-5-5", lambda m, c: reply(input_tokens=10_000, output_tokens=0),
                              max_cost=0.0025, workers=1, log=lambda line: None)
    assert summary.stopped_by_cost
    assert len(store.saved) == 3  # 0.001, 0.002, 0.003: the third check starts below the limit
    assert summary.skipped == 7
    assert summary.cost == pytest.approx(0.003)


def test_apply_marks_only_the_wrong_senses(media_dir):
    senses = [sense(i) for i in (1, 2, 3)]
    put_files(media_dir, senses)
    verdicts = iter(["good", "wrong", "unsure"])
    store = FakeStore(senses)
    summary = media_check.run(store, media_dir, "m", lambda m, c: reply(next(verdicts)), workers=1, apply=True,
                              log=lambda line: None)
    assert store.status == {2: "needs_ai"}
    assert summary.applied == 1


def test_without_apply_nothing_is_marked(media_dir):
    senses = [sense(1)]
    put_files(media_dir, senses)
    store = FakeStore(senses)
    media_check.run(store, media_dir, "m", lambda m, c: reply("wrong"), log=lambda line: None)
    assert store.status == {}


# --- report --------------------------------------------------------------------------------------------

def test_the_report_has_thumbnails_words_verdicts_and_reasons(tmp_path):
    media = tmp_path / "data" / "media"
    media.mkdir(parents=True)
    rows = [
        {"lemma": "bank", "position": 0, "pos": "noun", "simple_definition": "a place for money <b>", "file_name": "bank_1.webp",
         "kind": "photo", "verdict": "wrong", "reason": "It shows a river bank.", "media_status": "needs_ai"},
        {"lemma": "run", "position": 0, "pos": "verb", "simple_definition": "to move fast", "file_name": "run_1.mp4",
         "kind": "clip", "verdict": "good", "reason": "A person runs.", "media_status": None},
    ]
    report = tmp_path / "data" / "out" / "media-check.html"
    assert media_check.write_report(rows, report, media) == 2
    page = report.read_text()
    assert '<img src="../media/bank_1.webp"' in page
    assert '<video src="../media/run_1.mp4"' in page
    assert "It shows a river bank." in page and "marked needs_ai" in page
    assert "a place for money &lt;b&gt;" in page  # text is escaped
    assert "1 wrong, 0 unsure, 1 good" in page


# --- Postgres (needs TEST_DATABASE_URL) ----------------------------------------------------------------

def seed_pg(pg):
    now = datetime.now(timezone.utc)
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO dictionary_word (lemma, word_rank, created_at, updated_at) VALUES ('run', 1, %s, %s) RETURNING id", (now, now))
        word_id = cursor.fetchone()[0]
        ids = []
        for name, kind in (("a.webp", "photo"), ("b.webp", "photo"), ("c.mp4", "clip"), ("d.webp", "photo")):
            cursor.execute("INSERT INTO media (file_name, kind, created_at) VALUES (%s, %s, %s) RETURNING id", (name, kind, now))
            ids.append(cursor.fetchone()[0])
        # position 0 ok, 1 removed, 2 needs_ai already, 3 no media type none
        rows = [(0, "photo", None, ids[0]), (1, "photo", "removed", ids[1]), (2, "photo", "needs_ai", ids[2]), (3, "none", None, ids[3])]
        for position, image_type, status, media_id in rows:
            cursor.execute(
                """INSERT INTO dictionary_sense (word_id, position, pos, cefr, simple_definition, vietnamese, examples_json,
                   usage_json, image_type, image_query, media_id, media_status)
                   VALUES (%s, %s, 'verb', 'A1', 'to move', 'chạy', '[]', '[]', %s, 'run', %s, %s)""",
                (word_id, position, image_type, media_id, status),
            )
    pg.commit()


def test_pg_pending_skips_removed_needs_ai_none_and_checked(pg):
    seed_pg(pg)
    store = media_check.PostgresStore(pg)
    pending = store.pending_senses(None, 100, recheck=False)
    assert [(s["lemma"], s["position"]) for s in pending] == [("run", 0)]
    store.save_check(CheckResult(pending[0], "good", "ok", 500, 40, 0.0001), "claude-haiku-5-5")
    assert store.pending_senses(None, 100, recheck=False) == []
    assert len(store.pending_senses(None, 100, recheck=True)) == 1
    assert store.pending_senses(["nothing"], 100, recheck=True) == []


def test_pg_apply_wrong_marks_wrong_only_and_a_new_picture_needs_a_new_check(pg):
    seed_pg(pg)
    store = media_check.PostgresStore(pg)
    item = store.pending_senses(None, 100, recheck=False)[0]
    store.save_check(CheckResult(item, "unsure", "?", 1, 1, 0.0), "m")
    assert store.apply_wrong() == 0
    store.save_check(CheckResult(item, "wrong", "bank", 1, 1, 0.0), "m")  # newest check wins
    assert store.apply_wrong() == 1
    with pg.cursor() as cursor:
        cursor.execute("SELECT media_status FROM dictionary_sense WHERE position = 0")
        assert cursor.fetchone()[0] == "needs_ai"
    latest = store.latest_checks()
    assert [(r["lemma"], r["verdict"]) for r in latest] == [("run", "wrong")]
