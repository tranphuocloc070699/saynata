"""Tests for media_ai.py (TASK-82): fake Claude, fake Codex, in-memory store; the Postgres tests need TEST_DATABASE_URL."""

import io
import json
import subprocess
import threading
from datetime import datetime, timezone
from types import SimpleNamespace

import psycopg
import pytest
from PIL import Image

from pipeline import media_ai, media_generate
from pipeline.media_ai import (
    Attempt, CodexDrawer, DrawError, Drawn, Reply, Services, SenseResult, Usage,
)
from pipeline.media_generate import ProviderStop

EXAMPLES = json.dumps([{"level": "hard", "text": "The bank approved the loan."},
                       {"level": "easy", "text": "I put money in the bank."}])


def png(width=1536, height=1024, colour=(31, 107, 95)) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), colour).save(buffer, "PNG")
    return buffer.getvalue()


def sense(sense_id=1, lemma="bank", position=0, forms=("banks", "banked", "banking")):
    return {"id": sense_id, "lemma": lemma, "position": position, "pos": "noun",
            "simple_definition": "a place that keeps your money safe", "examples_json": EXAMPLES,
            "media_id": sense_id * 10, "forms": list(forms),
            "others": [{"pos": "noun", "meaning": "the land beside a river"}]}


def scene_json(method="object", text="A bank building cut open. A cashier serves a customer at the counter."):
    return {"method": method, "scene": text, "focus": "the counter", "avoid": "a river",
            "distractors": ["shop", "office", "school"]}


class FakeClaude:
    """A fetch function. Each kind of call has a queue of answers (dicts, or a callable of the call text); when the
    queue is empty the last answer repeats. Every call is recorded in self.calls as (kind, text)."""

    def __init__(self, scenes=None, opens=None, choices=None, distractors=None, fail_scene=None):
        self.scenes = list(scenes or [scene_json()])
        self.opens = list(opens or [{"answer": "bank", "confidence": 90, "why": "a bank counter"}])
        self.choices = list(choices or [{"answer": "bank", "confidence": 90, "why": "money"}])
        self.distractors = list(distractors or [{"distractors": ["shop", "office", "school"]}])
        self.calls: list[tuple[str, str]] = []
        self.lock = threading.Lock()
        self.fail_scene = fail_scene

    def __call__(self, model, system, content):
        text = content if isinstance(content, str) else content[-1]["text"]
        if model == media_ai.SCENE_MODEL:
            kind, queue = "scene", self.scenes
            if self.fail_scene:
                raise self.fail_scene
        elif system == media_ai.DISTRACTOR_SYSTEM:
            kind, queue = "distractors", self.distractors
        elif "Choices:" in text:
            kind, queue = "choice", self.choices
        else:
            kind, queue = "open", self.opens
        with self.lock:
            self.calls.append((kind, text))
            answer = queue.pop(0) if len(queue) > 1 else queue[0]
        answer = answer(text) if callable(answer) else answer
        return Reply(json.dumps(answer), 1000, 500)

    def kinds(self):
        return [kind for kind, _ in self.calls]


class FakeDrawer:
    def __init__(self, fail=None, size=(1536, 1024)):
        self.prompts: list[str] = []
        self.fail, self.size = fail, size
        self.lock = threading.Lock()

    def draw(self, prompt):
        with self.lock:
            self.prompts.append(prompt)
            number = len(self.prompts)
        if self.fail:
            raise self.fail
        return Drawn(png(*self.size, colour=(number % 250, 50, 90)), 2.0)


class FakeStore:
    """Same methods as PostgresStore, in memory."""

    def __init__(self, senses):
        self.senses = senses
        self.saved: list[SenseResult] = []
        self.linked: list[str] = []
        self.refuse: set[str] = set()
        self.unwanted: set[int] = set()

    def _pending(self, words, redo):
        done = {r.sense["id"] for r in self.saved if r.outcome in ("passed", "none") or len(r.attempts) >= media_ai.MAX_TRIES}
        rows = [s for s in self.senses if redo or s["id"] not in done]
        return [s for s in rows if not words or s["lemma"] in words]

    def pending_senses(self, words, limit, redo):
        return self._pending(words, redo)[:limit]

    def count_pending(self, words, redo):
        return len(self._pending(words, redo))

    def save_result(self, result):
        self.saved.append(result)

    def still_wanted(self, sense_id):
        return sense_id not in self.unwanted

    def unlinked_passed(self):
        return [{"id": i, "sense_id": r.sense["id"], "file_name": r.best().file_name, "lemma": r.sense["lemma"],
                 "position": r.sense["position"]} for i, r in enumerate(self.saved)
                if r.outcome == "passed" and r.best().file_name not in self.linked]

    def link(self, generation, facts):
        if generation["file_name"] in self.refuse:
            return False
        self.linked.append(generation["file_name"])
        return True


@pytest.fixture
def media_dir(tmp_path):
    folder = tmp_path / "media"
    folder.mkdir()
    return folder


def services(media_dir, claude=None, drawer=None):
    return Services(claude or FakeClaude(), drawer or FakeDrawer(), media_dir)


# --- Prompts and Claude --------------------------------------------------------------------------------

def test_the_locked_scene_rules_are_in_the_prompt():
    text = media_ai.SCENE_SYSTEM
    assert "short brown hair, sage green T-shirt" in text           # one green cartoon kid
    assert "Objects never have faces" in text and "People have expressive faces" in text
    assert "Do NOT add the kid or any person when the word is a thing or a place" in text
    assert "cut open" in text and "river bank" in text              # buildings cut open, a part of a place
    assert "NEVER have faces" in media_ai.STYLE


def test_codex_prompt_describes_the_kid_once_and_adds_style_focus_and_avoid():
    scene = scene_json(text="The kid drinks water. The kid smiles.")
    prompt = media_ai.codex_prompt(scene)
    assert prompt.count("short brown hair") == 1
    assert "Main focus: the counter. Must not appear: a river." in prompt
    assert prompt.endswith(media_ai.STYLE)
    assert "exactly ONCE" in prompt and "Landscape 3:2" in prompt


def test_scene_message_has_the_sense_the_easy_example_and_the_other_senses():
    message = media_ai.scene_message(sense())
    assert 'Word: "bank" (noun)' in message and "I put money in the bank." in message
    assert "the land beside a river" in message and "failed the blind test" not in message
    assert "failed the blind test: it was wrong" in media_ai.scene_message(sense(), "it was wrong")


def test_scene_method_must_be_known_and_the_scene_not_empty():
    with pytest.raises(media_ai.BadAnswer):
        media_ai.write_scene(sense(), "", FakeClaude(scenes=[{"method": "magic", "scene": "x"}]), Usage())
    with pytest.raises(media_ai.BadAnswer):
        media_ai.write_scene(sense(), "", FakeClaude(scenes=[{"method": "object", "scene": " "}]), Usage())
    assert media_ai.write_scene(sense(), "", FakeClaude(scenes=[{"method": "none"}]), Usage())["method"] == "none"


def test_json_is_found_inside_text_and_a_bad_answer_is_an_error():
    assert media_ai.parse_json('Here: {"a": 1}\nbye') == {"a": 1}
    with pytest.raises(media_ai.BadAnswer):
        media_ai.parse_json("no json here")


def test_real_claude_call_reads_the_first_text_block_and_sends_no_temperature(monkeypatch):
    sent = {}

    class Messages:
        def create(self, **kwargs):
            sent.update(kwargs)
            blocks = [SimpleNamespace(type="thinking", thinking="hmm"), SimpleNamespace(type="text", text='{"ok": 1}')]
            return SimpleNamespace(content=blocks, stop_reason="end_turn", usage=SimpleNamespace(input_tokens=7, output_tokens=9))

    monkeypatch.setattr("pipeline.generate._client", lambda: SimpleNamespace(messages=Messages()))
    reply = media_ai.fetch_from_claude(media_ai.SCENE_MODEL, "system", "hello", effort="low")
    assert (reply.text, reply.input_tokens, reply.output_tokens) == ('{"ok": 1}', 7, 9)
    assert sent["max_tokens"] == 4000 and sent["system"] == "system" and sent["output_config"] == {"effort": "low"}
    assert "temperature" not in sent
    sent.clear()
    media_ai.fetch_from_claude(media_ai.GUESS_MODEL, None, "hello")
    assert "system" not in sent and "output_config" not in sent


def test_cost_uses_the_price_table():
    assert media_ai.cost_usd("claude-sonnet-5-5", 1_000_000, 1_000_000) == pytest.approx(12.0)
    assert media_ai.cost_usd("claude-haiku-5-5", 1_000_000, 1_000_000) == pytest.approx(0.6)
    assert media_ai.cost_usd("claude-unknown", 1_000_000, 0) == pytest.approx(2.0)  # the highest price listed


# --- Codex ---------------------------------------------------------------------------------------------

SESSION = "019a1b2c-3d4e-7f80-9a1b-2c3d4e5f6a7b"


def fake_codex(tmp_path, *, output=f"OpenAI Codex\nsession id: {SESSION}\n", write=True, error=None):
    calls = []

    def run(command, **kwargs):
        calls.append((command, kwargs))
        if error:
            raise error
        if write:
            folder = tmp_path / "generated_images" / SESSION
            folder.mkdir(parents=True, exist_ok=True)
            (folder / "a.png").write_bytes(png(10, 10, (1, 1, 1)))
            newest = folder / "b.png"
            newest.write_bytes(png(20, 20, (2, 2, 2)))
            import os
            os.utime(folder / "a.png", (1, 1))
        return subprocess.CompletedProcess(command, 0, output, "")

    return CodexDrawer(run=run, home=tmp_path), calls


def test_codex_runs_with_no_stdin_a_timeout_and_takes_the_newest_picture_of_its_session(tmp_path):
    drawer, calls = fake_codex(tmp_path)
    drawn = drawer.draw("PROMPT")
    command, kwargs = calls[0]
    assert command == ["codex", "exec", "--skip-git-repo-check", "--sandbox", "read-only", "PROMPT"]
    assert kwargs["stdin"] == subprocess.DEVNULL and kwargs["timeout"] == 15 * 60
    assert Image.open(io.BytesIO(drawn.image)).size == (20, 20)


def test_codex_without_a_session_id_or_a_picture_is_a_draw_error(tmp_path):
    with pytest.raises(DrawError, match="no picture"):
        fake_codex(tmp_path / "other", output="something else")[0].draw("p")
    with pytest.raises(DrawError, match="no picture"):
        fake_codex(tmp_path / "empty", write=False)[0].draw("p")


def test_codex_timeout_is_a_draw_error_and_a_plan_limit_stops_the_run(tmp_path):
    with pytest.raises(DrawError, match="15 minutes"):
        fake_codex(tmp_path / "slow", error=subprocess.TimeoutExpired("codex", 900))[0].draw("p")
    with pytest.raises(ProviderStop):
        fake_codex(tmp_path / "limit", output="You've hit your usage limit.", write=False)[0].draw("p")


# --- Picture files -------------------------------------------------------------------------------------

def test_a_picture_becomes_a_1536_wide_3_2_webp_and_a_768_jpeg():
    webp, jpeg = media_ai.prepare_images(png(2048, 1365))
    image = Image.open(io.BytesIO(webp))
    assert image.format == "WEBP" and image.size == (1536, 1024)
    assert max(Image.open(io.BytesIO(jpeg)).size) == 768 and Image.open(io.BytesIO(jpeg)).format == "JPEG"


def test_a_square_or_a_wide_picture_is_cropped_to_3_2_in_the_middle():
    assert Image.open(io.BytesIO(media_ai.prepare_images(png(1024, 1024))[0])).size == (1024, 683)
    assert Image.open(io.BytesIO(media_ai.prepare_images(png(1800, 600))[0])).size == (900, 600)


def test_file_name_has_the_word_the_sense_number_and_a_hash_of_the_picture():
    name = media_ai.file_name_for(sense(position=2), b"abc")
    assert name == "bank_3_ai_ba7816bf.webp"
    assert media_ai.file_name_for(sense(), b"abd") != media_ai.file_name_for(sense(), b"abc")


# --- The blind guess -----------------------------------------------------------------------------------

def test_an_open_guess_of_the_word_or_a_form_passes_without_choices():
    for word in ("bank", "Banks.", "banking"):
        claude = FakeClaude(opens=[{"answer": word, "confidence": 40, "why": "x"}])
        guess = media_ai.blind_guess(sense(), b"jpeg", claude, Usage())
        assert guess["passed"] and guess["by"] == "open" and guess["choice"] is None
        assert claude.kinds() == ["open"]


def test_a_wrong_open_guess_goes_to_multiple_choice_and_passes_at_70():
    claude = FakeClaude(opens=[{"answer": "money", "confidence": 80, "why": "coins"}],
                        choices=[{"answer": "Bank", "confidence": 70, "why": "counter"}])
    guess = media_ai.blind_guess(sense(), b"jpeg", claude, Usage())
    assert guess["passed"] and guess["by"] == "choice"
    assert claude.kinds() == ["open", "distractors", "choice"]
    assert sorted(guess["choice"]["choices"]) == ["bank", "office", "school", "shop"]


def test_multiple_choice_fails_with_low_confidence_or_the_wrong_word():
    low = FakeClaude(opens=[{"answer": "money", "confidence": 80, "why": "x"}],
                     choices=[{"answer": "bank", "confidence": 69, "why": "x"}])
    assert media_ai.blind_guess(sense(), b"jpeg", low, Usage())["passed"] is False
    wrong = FakeClaude(opens=[{"answer": "money", "confidence": 80, "why": "x"}],
                       choices=[{"answer": "shop", "confidence": 95, "why": "x"}])
    guess = media_ai.blind_guess(sense(), b"jpeg", wrong, Usage())
    assert guess["passed"] is False and guess["choice"]["picked_lemma"] is False


def test_distractors_come_from_a_call_that_never_sees_the_scene_and_never_include_the_word():
    claude = FakeClaude(opens=[{"answer": "money", "confidence": 80, "why": "x"}],
                        distractors=[{"distractors": ["Bank", "banks", "shop", "shop", "shore", "office"]}])
    media_ai.blind_guess(sense(), b"jpeg", claude, Usage())
    distractor_calls = [text for kind, text in claude.calls if kind == "distractors"]
    assert len(distractor_calls) == 1
    assert "a place that keeps your money safe" in distractor_calls[0] and "cashier" not in distractor_calls[0]
    choice_text = [text for kind, text in claude.calls if kind == "choice"][0]
    assert "Choices:" in choice_text
    assert media_ai.pick_distractors(sense(), claude, Usage()) == ["shop", "shore", "office"]


def test_too_few_distractors_is_an_error_not_a_free_pass():
    claude = FakeClaude(opens=[{"answer": "money", "confidence": 80, "why": "x"}],
                        distractors=[{"distractors": ["bank", "shop"]}])
    with pytest.raises(media_ai.BadAnswer):
        media_ai.blind_guess(sense(), b"jpeg", claude, Usage())


# --- One sense -----------------------------------------------------------------------------------------

def test_a_sense_that_passes_on_the_first_try_saves_a_webp_and_counts_claude_cost(media_dir):
    claude, drawer = FakeClaude(), FakeDrawer()
    result = media_ai.make_sense(sense(), services(media_dir, claude, drawer))
    assert result.outcome == "passed" and result.error is None and len(result.attempts) == 1
    attempt = result.attempts[0]
    assert attempt.file_name.startswith("bank_1_ai_") and attempt.file_name.endswith(".webp")
    assert Image.open(media_dir / attempt.file_name).size == (1536, 1024)
    assert claude.kinds() == ["scene", "open"]
    assert result.cost == pytest.approx(0.007 + 0.00035)
    assert drawer.prompts == [attempt.prompt]


def test_a_failed_try_is_redrawn_with_feedback_from_the_guesser(media_dir):
    claude = FakeClaude(
        scenes=[scene_json(), scene_json(text="A bigger bank with a sign-free front and a queue.")],
        opens=[{"answer": "shop", "confidence": 60, "why": "it has a counter"},
               {"answer": "bank", "confidence": 90, "why": "money"}],
        choices=[{"answer": "shop", "confidence": 80, "why": "counter"}])
    result = media_ai.make_sense(sense(), services(media_dir, claude))
    assert result.outcome == "passed" and [a.passed for a in result.attempts] == [False, True]
    second_scene_request = [text for kind, text in claude.calls if kind == "scene"][1]
    assert "failed the blind test" in second_scene_request and "'shop'" in second_scene_request
    assert result.attempts[0].file_name != result.attempts[1].file_name
    assert result.best() is result.attempts[1]


def test_three_failed_tries_keep_the_best_picture_and_stop(media_dir):
    claude = FakeClaude(
        opens=[{"answer": "shop", "confidence": 60, "why": "x"}],
        choices=[{"answer": "shop", "confidence": 90, "why": "x"},
                 {"answer": "bank", "confidence": 65, "why": "x"},   # right word, but under 70: the closest
                 {"answer": "office", "confidence": 90, "why": "x"}])
    result = media_ai.make_sense(sense(), services(media_dir, claude))
    assert result.outcome == "failed" and len(result.attempts) == media_ai.MAX_TRIES
    assert claude.kinds().count("scene") == 3
    assert result.best() is result.attempts[1]
    assert all((media_dir / a.file_name).is_file() for a in result.attempts)


def test_method_none_is_recorded_and_nothing_is_drawn(media_dir):
    drawer = FakeDrawer()
    result = media_ai.make_sense(sense(), services(media_dir, FakeClaude(scenes=[{"method": "none"}]), drawer))
    assert result.outcome == "none" and drawer.prompts == [] and result.attempts[0].complete
    assert result.best() is None


def test_a_codex_failure_is_an_error_that_keeps_the_cost_and_the_finished_tries(media_dir):
    drawer = FakeDrawer()
    claude = FakeClaude(opens=[{"answer": "shop", "confidence": 60, "why": "x"}],
                        choices=[{"answer": "shop", "confidence": 90, "why": "x"}])
    ok = media_ai.make_sense(sense(), services(media_dir, claude, drawer))
    assert ok.outcome == "failed"
    result = media_ai.make_sense(sense(), services(media_dir, FakeClaude(), FakeDrawer(fail=DrawError("no picture"))))
    assert result.outcome == "error" and "no picture" in result.error and not result.stop
    assert result.cost == pytest.approx(0.007) and not result.attempts[0].complete  # the scene was paid, not saved


def test_a_provider_stop_is_marked_so_the_run_can_end(media_dir):
    result = media_ai.make_sense(sense(), services(media_dir, FakeClaude(fail_scene=ProviderStop("bad key"))))
    assert result.stop and result.outcome == "error"


# --- The run -------------------------------------------------------------------------------------------

def several(count):
    return [sense(i, position=i - 1) for i in range(1, count + 1)]  # the fake guesser always says "bank"


def run_it(store, media_dir, claude=None, drawer=None, **kwargs):
    lines: list[str] = []
    summary = media_ai.run(store, services(media_dir, claude, drawer), log=lines.append, **kwargs)
    return summary, lines


def test_run_draws_and_saves_each_sense(media_dir):
    store = FakeStore(several(3))
    summary, lines = run_it(store, media_dir, workers=2)
    assert [r.outcome for r in summary.results] == ["passed"] * 3 and len(store.saved) == 3
    assert summary.remaining == 0 and summary.stopped_by is None
    assert summary.cost == pytest.approx(3 * (0.007 + 0.00035))


def test_a_second_run_continues_with_the_senses_that_are_not_finished(media_dir):
    store = FakeStore(several(4))
    run_it(store, media_dir, limit=2, workers=1)
    assert len(store.saved) == 2
    summary, _ = run_it(store, media_dir, workers=1)
    assert [r.sense["id"] for r in summary.results] == [3, 4]
    summary, _ = run_it(store, media_dir, workers=1)
    assert summary.planned == 0
    summary, _ = run_it(store, media_dir, workers=1, redo=True)
    assert summary.planned == 4


def test_run_never_passes_the_claude_cap(media_dir, monkeypatch):
    monkeypatch.setattr(media_ai, "ESTIMATE_PER_SENSE", 0.02)
    store = FakeStore(several(10))
    summary, _ = run_it(store, media_dir, workers=2, max_cost=0.05)
    assert summary.cost <= 0.05 and summary.stopped_by == "cost"
    assert len(summary.results) < 10 and len(summary.results) + summary.skipped == 10
    assert summary.remaining == 10 - len(summary.results)


def test_run_skips_a_sense_that_changed_in_the_admin(media_dir):
    store = FakeStore(several(3))
    store.unwanted = {2}
    summary, lines = run_it(store, media_dir, workers=1)
    assert [r.sense["id"] for r in summary.results] == [1, 3]
    assert any("no longer wanted" in line for line in lines)


def test_a_provider_stop_ends_the_run_and_saves_nothing_for_that_sense(media_dir):
    store = FakeStore(several(5))
    summary, _ = run_it(store, media_dir, FakeClaude(fail_scene=ProviderStop("Claude: bad key")), workers=1)
    assert len(summary.results) == 1 and store.saved == [] and summary.skipped == 4
    assert summary.stopped_by == "Claude: bad key"


def test_a_failed_sense_is_not_saved_when_nothing_was_finished_so_the_next_run_retries_it(media_dir):
    store = FakeStore(several(2))
    summary, _ = run_it(store, media_dir, drawer=FakeDrawer(fail=DrawError("no picture")), workers=1)
    assert [r.outcome for r in summary.results] == ["error", "error"] and store.saved == []
    assert summary.remaining == 2


def test_dry_run_shows_three_scenes_and_prompts_and_draws_and_saves_nothing(media_dir):
    store = FakeStore(several(5))
    drawer = FakeDrawer()
    claude = FakeClaude()
    summary, lines = run_it(store, media_dir, claude, drawer, dry_run=True, workers=4)
    text = "\n".join(lines)
    assert claude.kinds() == ["scene"] * 3 and drawer.prompts == [] and store.saved == []
    assert text.count("Codex prompt:") == 3 and "Use your built-in image generation tool" in text
    assert "Estimate for 5 senses" in text and "nothing was drawn or saved" in text
    assert list(media_dir.iterdir()) == []


def test_summary_counts_the_outcomes_lists_failures_and_estimates_the_rest(media_dir):
    store = FakeStore(several(3))
    claude = FakeClaude(scenes=[scene_json(), {"method": "none"}, scene_json()],
                        opens=[{"answer": "bank", "confidence": 90, "why": "x"}])
    summary, _ = run_it(store, media_dir, claude, workers=1, limit=2)
    summary.remaining = 40
    lines: list[str] = []
    media_ai.print_summary(summary, 1, 5.0, lines.append)
    text = "\n".join(lines)
    assert "Passed 1, failed 0" in text and "none 1" in text and "errors 0" in text
    assert "1 pictures drawn, 2 s on average" in text
    assert "40 senses are left" in text and "hours with 1 workers" in text
    assert "Method none" in text and "bank #2" in text
    assert "rsync" in text and "media-ai --link" in text


def test_link_only_files_that_are_here_and_on_the_server(media_dir):
    store = FakeStore(several(3))
    run_it(store, media_dir, workers=1)
    names = [r.best().file_name for r in store.saved]
    (media_dir / names[2]).unlink()                      # missing here
    store.refuse.add(names[1])                           # the sense changed meanwhile
    lines: list[str] = []
    on_server = {names[1], names[2]}
    summary = media_ai.link_passed(store, media_dir, lambda name: name in on_server, lines.append)
    assert summary.not_on_server == [names[0]] and summary.missing_here == [names[2]]
    assert summary.skipped == 1 and summary.linked == 0


def test_the_report_puts_failed_senses_first_and_shows_scene_and_guess(tmp_path):
    media = tmp_path / "media"
    media.mkdir()
    now = datetime.now(timezone.utc)

    def row(sense_id, lemma, passed, method="object", attempt=1, file_name="x.webp", guess=True):
        return {"id": sense_id * 10 + attempt, "sense_id": sense_id, "attempt": attempt, "file_name": file_name,
                "method": method, "scene_json": {"scene": "A <b>scene</b>"},
                "guess_json": {"open": {"answer": "shop", "confidence": 60, "why": "counter"},
                               "choice": {"choices": ["bank", "shop"], "answer": "shop", "confidence": 80, "why": "w"}}
                if guess else None, "passed": passed, "cost_usd": 0.0074, "created_at": now, "linked_at": None,
                "lemma": lemma, "position": 0, "pos": "noun", "simple_definition": f"meaning of {lemma}",
                "old_file_name": f"{lemma}_1.jpg", "old_kind": "photo"}

    rows = [row(1, "good", True, file_name="good_1_ai_00000000.webp"),
            row(2, "bad", False, file_name="bad_1_ai_00000000.webp"), row(2, "bad", False, attempt=2, file_name="bad_1_ai_11111111.webp"),
            row(3, "hard", None, method="none", file_name=None, guess=False)]
    report = tmp_path / "out" / "r.html"
    assert media_ai.write_report(rows, report, media) == 3
    page = report.read_text()
    assert page.index("bad ") < page.index("hard ") < page.index("good ")
    assert '<img src="../media/bad_1_ai_11111111.webp"' in page and "https://api.loffy.me/media/bad_1.jpg" in page
    assert "A &lt;b&gt;scene&lt;/b&gt;" in page and "Open guess: <b>shop</b>" in page and "Choice from bank, shop" in page
    assert "1 passed, 1 failed" in page and "1 not drawn" in page


# --- Postgres (needs TEST_DATABASE_URL) ----------------------------------------------------------------

def seed_pg(pg):
    """Word bank (forms banks, banked): 0 Pixabay photo, approved in the admin; 1 clip; 2 image_type none; 3 removed;
    4 needs_ai, no media. Word old (rank 2): 0 photo."""
    now = datetime.now(timezone.utc)
    with pg.cursor() as cursor:
        ids = {}
        for lemma, rank in (("bank", 1), ("old", 2)):
            cursor.execute("INSERT INTO dictionary_word (lemma, word_rank, created_at, updated_at) VALUES (%s, %s, %s, %s) RETURNING id",
                           (lemma, rank, now, now))
            ids[lemma] = cursor.fetchone()[0]
        for form in ("banks", "banked"):
            cursor.execute("INSERT INTO dictionary_form (word_id, form, form_type) VALUES (%s, %s, 'plural')", (ids["bank"], form))
        specs = [("bank", 0, "photo", "photo", None), ("bank", 1, "photo", "clip", None), ("bank", 2, "none", "photo", None),
                 ("bank", 3, "photo", "photo", "removed"), ("bank", 4, "photo", None, "needs_ai"), ("old", 0, "photo", "photo", None)]
        for lemma, position, image_type, kind, status in specs:
            media_id = None
            if kind:
                cursor.execute("INSERT INTO media (file_name, kind, created_at) VALUES (%s, %s, %s) RETURNING id",
                               (f"{lemma}_{position + 1}.{'mp4' if kind == 'clip' else 'jpg'}", kind, now))
                media_id = cursor.fetchone()[0]
            cursor.execute(
                """INSERT INTO dictionary_sense (word_id, position, pos, cefr, simple_definition, vietnamese, examples_json,
                   usage_json, image_type, image_query, media_id, media_status, media_reviewed_at, media_reviewed_media_id)
                   VALUES (%s, %s, 'noun', 'A1', %s, 'x', %s, '[]', %s, 'q', %s, %s, %s, %s)""",
                (ids[lemma], position, f"meaning {lemma} {position}", EXAMPLES, image_type, media_id, status,
                 now if (lemma, position) == ("bank", 0) else None, media_id if (lemma, position) == ("bank", 0) else None),
            )
    pg.commit()


def pg_sense(store, lemma, position):
    return next(s for s in store.pending_senses([lemma], 100, True) if s["position"] == position)


def finished(item, *, passed=True, name="bank_1_ai_aaaaaaaa.webp", method="object", attempts=1):
    result = SenseResult(item)
    for number in range(1, attempts + 1):
        a = Attempt(number, usage=Usage(1000, 500, 0.0074))
        a.scene = scene_json(method)
        if method != "none":
            a.prompt, a.file_name = "codex prompt", name if attempts == 1 else f"{name[:-5]}{number}.webp"
            a.guess = {"open": {"answer": "x", "confidence": 1, "why": "w"}, "choice": None, "passed": passed and number == attempts, "by": "open"}
        result.attempts.append(a)
    return result


def test_pg_pending_is_every_picture_sense_that_is_not_removed_in_word_order_with_forms(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    pending = store.pending_senses(None, 100, False)
    assert [(s["lemma"], s["position"]) for s in pending] == [("bank", 0), ("bank", 1), ("bank", 4), ("old", 0)]
    assert sorted(pending[0]["forms"]) == ["banked", "banks"] and pending[3]["forms"] == []
    assert pending[0]["others"][0]["meaning"].startswith("meaning bank")
    assert [s["position"] for s in store.pending_senses(["bank"], 2, False)] == [0, 1]
    assert store.count_pending(None, False) == 4 and store.count_pending(["old"], False) == 1


def test_pg_a_passed_none_or_three_times_failed_sense_is_not_pending_again_unless_redo(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    store.save_result(finished(pg_sense(store, "bank", 0), passed=True, name="bank_1_ai_aaaaaaaa.webp"))
    store.save_result(finished(pg_sense(store, "bank", 1), method="none"))
    store.save_result(finished(pg_sense(store, "bank", 4), passed=False, name="bank_5_ai_bbbbbbbb.webp", attempts=3))
    store.save_result(finished(pg_sense(store, "old", 0), passed=False, name="old_1_ai_cccccccc.webp", attempts=2))
    assert [(s["lemma"], s["position"]) for s in store.pending_senses(None, 100, False)] == [("old", 0)]  # 2 of 3 tries
    assert store.count_pending(None, True) == 4 and len(store.pending_senses(None, 100, True)) == 4


def test_pg_save_result_keeps_every_try_with_scene_guess_and_claude_cost_only(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item, passed=True, attempts=2))
    rows = pg.execute("""SELECT attempt, passed, method, provider, model, prompt_version, cost_usd, scene_json->>'method',
                         guess_json->'open'->>'answer', old_media_id = %s FROM media_generation ORDER BY attempt""",
                      (item["media_id"],)).fetchall()
    assert [r[:2] for r in rows] == [(1, False), (2, True)]
    assert rows[0][2:6] == ("object", "codex", "codex-image_gen", "ai-v1") and float(rows[0][6]) == pytest.approx(0.0074)
    assert rows[0][7] == "object"
    assert rows[0][8:] == ("x", True)


def test_pg_method_none_is_saved_without_a_file_and_never_linked(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    store.save_result(finished(pg_sense(store, "bank", 1), method="none"))
    row = pg.execute("SELECT file_name, passed, method, prompt FROM media_generation").fetchone()
    assert row == (None, None, "none", "(not drawn)") and store.unlinked_passed() == []


def test_pg_link_replaces_a_pixabay_photo_that_was_approved_and_keeps_the_old_row(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item, name="bank_1_ai_aaaaaaaa.webp"))
    [generation] = store.unlinked_passed()
    assert store.link(generation, (1536, 1024, 90000)) is True
    sense_row = pg.execute("""SELECT m.source, m.file_name, m.width, m.license_note, s.media_status, s.media_reviewed_at,
                              s.media_reviewed_media_id FROM dictionary_sense s JOIN media m ON m.id = s.media_id
                              WHERE s.id = %s""", (item["id"],)).fetchone()
    assert sense_row == ("ai", "bank_1_ai_aaaaaaaa.webp", 1536, "AI image (codex codex-image_gen)", "ai_generated", None, None)
    assert pg.execute("SELECT count(*) FROM media WHERE file_name = 'bank_1.jpg'").fetchone()[0] == 1   # old row kept
    g = pg.execute("SELECT media_id IS NOT NULL, linked_at IS NOT NULL, old_media_id FROM media_generation").fetchone()
    assert g == (True, True, item["media_id"]) and store.unlinked_passed() == []


def test_pg_link_also_replaces_a_clip_and_a_sense_without_media(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    clip, empty = pg_sense(store, "bank", 1), pg_sense(store, "bank", 4)
    store.save_result(finished(clip, name="bank_2_ai_aaaaaaaa.webp"))
    store.save_result(finished(empty, name="bank_5_ai_bbbbbbbb.webp"))
    for generation in store.unlinked_passed():
        assert store.link(generation, (1536, 1024, 1)) is True
    statuses = pg.execute("SELECT position, media_status, media_id IS NOT NULL FROM dictionary_sense WHERE position IN (1, 4) AND word_id = "
                          "(SELECT id FROM dictionary_word WHERE lemma = 'bank') ORDER BY position").fetchall()
    assert statuses == [(1, "ai_generated", True), (4, "ai_generated", True)]
    assert pg.execute("SELECT old_media_id IS NULL FROM media_generation WHERE sense_id = %s", (empty["id"],)).fetchone()[0] is True


def test_pg_link_does_nothing_when_the_sense_became_none_or_removed(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item))
    [generation] = store.unlinked_passed()
    pg.execute("UPDATE dictionary_sense SET media_status = 'removed' WHERE id = %s", (item["id"],))
    pg.commit()
    assert store.link(generation, (1, 1, 1)) is False
    pg.execute("UPDATE dictionary_sense SET media_status = NULL, image_type = 'none' WHERE id = %s", (item["id"],))
    pg.commit()
    assert store.link(generation, (1, 1, 1)) is False
    assert pg.execute("SELECT count(*) FROM media WHERE source = 'ai'").fetchone()[0] == 0


def test_pg_only_the_newest_try_counts_and_only_when_it_passed(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item, passed=True, name="bank_1_ai_aaaaaaaa.webp"))
    store.save_result(finished(item, passed=False, name="bank_1_ai_bbbbbbbb.webp"))   # a --redo that failed
    assert store.unlinked_passed() == []
    store.save_result(finished(item, passed=True, name="bank_1_ai_cccccccc.webp"))
    assert [g["file_name"] for g in store.unlinked_passed()] == ["bank_1_ai_cccccccc.webp"]


def test_pg_link_is_committed_and_seen_from_another_connection(pg, database_url):
    """The bug of PR #74: unlinked_passed() opened a transaction, link() only made a savepoint inside it, and nothing was
    committed. On the same connection it looked linked; another connection (the backend) saw no change."""
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item))
    [generation] = store.unlinked_passed()
    assert store.link(generation, (1536, 1024, 1)) is True
    with psycopg.connect(database_url) as other:
        status = other.execute("SELECT media_status FROM dictionary_sense WHERE id = %s", (item["id"],)).fetchone()[0]
        ai_rows = other.execute("SELECT count(*) FROM media WHERE source = 'ai'").fetchone()[0]
        reviewed = other.execute("SELECT media_reviewed_media_id FROM dictionary_sense WHERE id = %s", (item["id"],)).fetchone()[0]
        linked = other.execute("SELECT count(*) FROM media_generation WHERE linked_at IS NOT NULL").fetchone()[0]
    assert (status, ai_rows, reviewed, linked) == ("ai_generated", 1, None, 1)


def test_pg_undo_with_the_sql_from_doc_15_brings_the_old_picture_back(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item))
    store.link(store.unlinked_passed()[0], (1536, 1024, 1))
    pg.execute("""UPDATE dictionary_sense s SET media_id = g.old_media_id, media_status = NULL
                  FROM media_generation g WHERE g.sense_id = s.id AND g.prompt_version = 'ai-v1'
                  AND g.linked_at IS NOT NULL AND s.media_id = g.media_id AND g.old_media_id IS NOT NULL""")
    pg.commit()
    assert pg.execute("SELECT media_id, media_status FROM dictionary_sense WHERE id = %s", (item["id"],)).fetchone() == (item["media_id"], None)


def test_pg_report_rows_have_the_old_picture_and_only_media_ai_tries(pg):
    seed_pg(pg)
    store = media_ai.PostgresStore(pg)
    item = pg_sense(store, "bank", 0)
    store.save_result(finished(item, passed=False, attempts=2))
    pg.execute("""INSERT INTO media_generation (sense_id, file_name, provider, model, prompt, input_tokens, output_tokens,
                  cost_usd, created_at) VALUES (%s, 'bank_1_ai_openai00.webp', 'openai', 'gpt-image-1-mini', 'p', 1, 1, 0.003, now())""",
               (item["id"],))
    pg.commit()
    rows = store.report_rows()
    assert [(r["attempt"], r["old_file_name"]) for r in rows] == [(1, "bank_1.jpg"), (2, "bank_1.jpg")]
    assert rows[0]["scene_json"]["method"] == "object" and rows[0]["guess_json"]["passed"] is False


def test_pg_the_openai_command_ignores_media_ai_tries(pg):
    """media-generate --link and its report must not touch the Codex tries (TASK-82 added prompt_version)."""
    seed_pg(pg)
    ai = media_ai.PostgresStore(pg)
    item = pg_sense(ai, "bank", 4)               # needs_ai, so media-generate would pick it up
    ai.save_result(finished(item, name="bank_5_ai_aaaaaaaa.webp"))
    old = media_generate.PostgresStore(pg)
    assert old.unlinked() == [] and old.report_rows() == []
