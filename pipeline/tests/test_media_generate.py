"""AI images for needs_ai senses (TASK-53). No real image API: a fake generator and a fake HTTP transport.

The Postgres tests at the end need TEST_DATABASE_URL (see conftest.py) and are skipped without it.
"""

import base64
import io
import json
from datetime import datetime, timezone

import httpx
import pytest
from PIL import Image

from pipeline import media_generate
from pipeline.media_generate import GeneratedImage, GenerationResult, OpenAIImageGenerator, ProviderStop

EXAMPLES = json.dumps([{"level": "medium", "text": "The bank is closed today."},
                       {"level": "easy", "text": "I put money in the bank."}])


def png(width=1536, height=1024, colour=(31, 107, 95)) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), colour).save(buffer, "PNG")
    return buffer.getvalue()


def sense(sense_id, lemma="bank", position=0):
    return {"id": sense_id, "lemma": lemma, "position": position, "pos": "noun",
            "simple_definition": "a place that keeps your money safe", "examples_json": EXAMPLES,
            "media_id": sense_id * 10, "old_file_name": f"{lemma}_{position + 1}.webp"}


class FakeGenerator:
    provider = "fake"
    model = "fake-image-1"

    def __init__(self, cost=0.01, estimate=0.01, fail_for=(), stop_for=()):
        self.cost, self.estimate = cost, estimate
        self.fail_for, self.stop_for = set(fail_for), set(stop_for)
        self.prompts: list[str] = []

    def estimate_cost(self, prompt):
        return self.estimate

    def generate(self, prompt):
        self.prompts.append(prompt)
        word = prompt.split('"')[1]
        if word in self.stop_for:
            raise ProviderStop("OpenAI 401 invalid_api_key")
        if word in self.fail_for:
            raise RuntimeError("OpenAI 400 moderation_blocked")
        return GeneratedImage(png(colour=(len(self.prompts), 0, 0)), 100, 400, self.cost)


class FakeStore:
    """Same methods as PostgresStore, in memory."""

    def __init__(self, senses):
        self.senses = senses
        self.saved: list[GenerationResult] = []
        self.linked: list[str] = []
        self.refuse: set[str] = set()
        self.approved: set[int] = set()

    def pending_senses(self, words, limit, regenerate):
        waiting = {r.sense["id"] for r in self.saved}
        rows = [s for s in self.senses if regenerate or s["id"] not in waiting]
        if words:
            rows = [s for s in rows if s["lemma"] in words]
        return rows[:limit]

    def save_generation(self, result, provider, model):
        self.saved.append(result)

    def still_needs_ai(self, sense_id):
        return sense_id not in self.approved

    def unlinked(self):
        return [{"id": i, "sense_id": r.sense["id"], "file_name": r.file_name, "provider": "fake",
                 "model": "fake-image-1", "lemma": r.sense["lemma"], "position": r.sense["position"]}
                for i, r in enumerate(self.saved) if r.file_name not in self.linked]

    def link(self, generation, provider, model, facts):
        if generation["file_name"] in self.refuse:
            return False
        self.linked.append(generation["file_name"])
        return True


@pytest.fixture
def media_dir(tmp_path):
    folder = tmp_path / "media"
    folder.mkdir()
    return folder


# --- Prompt and file name ------------------------------------------------------------------------------

def test_prompt_has_the_sense_and_the_fixed_style():
    prompt = media_generate.build_prompt(sense(1))
    assert '"bank" (noun)' in prompt
    assert "a place that keeps your money safe" in prompt
    assert "I put money in the bank." in prompt   # the easy example, not the medium one
    assert "No text, letters, numbers" in prompt and "sage green" in prompt and "teal" in prompt
    assert "No realistic human faces" in prompt


def test_prompt_without_examples_has_no_example_line():
    item = sense(1) | {"examples_json": "[]"}
    assert "Example sentence" not in media_generate.build_prompt(item)


def test_file_name_is_new_for_new_content():
    first = media_generate.file_name_for(sense(1, lemma="ice cream", position=1), b"one")
    assert first.startswith("ice_cream_2_ai_") and first.endswith(".webp") and len(first) == len("ice_cream_2_ai_") + 13
    assert media_generate.file_name_for(sense(1, lemma="ice cream", position=1), b"two") != first


# --- OpenAI generator (fake HTTP) ----------------------------------------------------------------------

def openai_answer(image=None, input_tokens=120, text_tokens=120, output_tokens=400):
    return {"data": [{"b64_json": base64.b64encode(image or png()).decode()}],
            "usage": {"input_tokens": input_tokens, "output_tokens": output_tokens,
                      "input_tokens_details": {"text_tokens": text_tokens, "image_tokens": 0}}}


def generator_with(*responses):
    """An OpenAIImageGenerator whose HTTP calls get these answers in order."""
    answers = list(responses)
    seen: list[httpx.Request] = []

    def handler(request):
        seen.append(request)
        return answers.pop(0)

    slept: list[float] = []
    generator = OpenAIImageGenerator("gpt-image-1-mini", api_key="test-key",
                                     client=httpx.Client(transport=httpx.MockTransport(handler)), sleep=slept.append)
    return generator, seen, slept


def test_openai_sends_the_request_and_counts_the_real_cost():
    generator, seen, _ = generator_with(httpx.Response(200, json=openai_answer()))
    result = generator.generate("a bank")
    body = json.loads(seen[0].content)
    assert body == {"model": "gpt-image-1-mini", "prompt": "a bank", "n": 1, "size": "1536x1024",
                    "quality": "low", "output_format": "webp"}
    assert seen[0].headers["authorization"] == "Bearer test-key"
    assert (result.input_tokens, result.output_tokens) == (120, 400)
    assert result.cost_usd == pytest.approx((120 * 2.00 + 400 * 8.00) / 1_000_000)
    assert Image.open(io.BytesIO(result.image)).width == 1536


def test_openai_estimate_is_not_below_a_real_answer():
    generator, _, _ = generator_with()
    prompt = media_generate.build_prompt(sense(1))
    real = generator.cost(len(prompt) // 4, 400)
    assert generator.estimate_cost(prompt) >= real


def test_openai_tries_again_after_429_with_retry_after():
    generator, seen, slept = generator_with(
        httpx.Response(429, headers={"retry-after": "7"}, json={"error": {"code": "rate_limit_exceeded", "message": "slow"}}),
        httpx.Response(200, json=openai_answer()),
    )
    generator.generate("a bank")
    assert len(seen) == 2 and slept == [7.0]


def test_openai_gives_up_after_five_tries():
    busy = httpx.Response(503, json={"error": {"message": "busy"}})
    generator, seen, slept = generator_with(*[busy] * 5)
    with pytest.raises(RuntimeError, match="503"):
        generator.generate("a bank")
    assert len(seen) == 5 and len(slept) == 4


def test_openai_wrong_key_or_no_credit_stops_the_run():
    generator, _, _ = generator_with(httpx.Response(401, json={"error": {"code": "invalid_api_key", "message": "no"}}))
    with pytest.raises(ProviderStop):
        generator.generate("a bank")
    generator, _, _ = generator_with(httpx.Response(429, json={"error": {"code": "insufficient_quota", "message": "pay"}}))
    with pytest.raises(ProviderStop):
        generator.generate("a bank")


def test_openai_blocked_prompt_fails_only_that_call():
    generator, seen, _ = generator_with(httpx.Response(400, json={"error": {"code": "moderation_blocked", "message": "no"}}))
    with pytest.raises(RuntimeError, match="moderation_blocked"):
        generator.generate("a bank")
    assert len(seen) == 1


def test_openai_refuses_unknown_model_and_missing_key(monkeypatch):
    with pytest.raises(ValueError, match="No price"):
        OpenAIImageGenerator("dall-e-9", api_key="k")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(ProviderStop, match="OPENAI_API_KEY"):
        OpenAIImageGenerator()


# --- The run -------------------------------------------------------------------------------------------

def test_run_saves_a_320px_webp_per_sense_and_records_it(media_dir):
    store = FakeStore([sense(1, "bank"), sense(2, "apple")])
    summary = media_generate.run(store, FakeGenerator(), media_dir, log=lambda _: None)
    assert len(summary.made) == 2 and summary.failed == [] and summary.skipped == 0
    assert summary.cost == pytest.approx(0.02)
    for result in store.saved:
        with Image.open(media_dir / result.file_name) as image:
            assert (image.format, image.width, image.height) == ("WEBP", 320, 213)
        assert result.prompt == media_generate.build_prompt(result.sense)
    # A second run has nothing to do: both senses wait to be linked.
    assert media_generate.run(store, FakeGenerator(), media_dir, log=lambda _: None).planned == 0


def test_run_never_passes_the_budget_cap(media_dir):
    store = FakeStore([sense(i, f"w{i}") for i in range(1, 11)])
    summary = media_generate.run(store, FakeGenerator(cost=0.01, estimate=0.01), media_dir, max_cost=0.035,
                                 workers=2, log=lambda _: None)
    assert len(summary.made) == 3 and summary.cost <= 0.035
    assert summary.skipped == 7 and summary.stopped_by == "cost"


def test_run_estimate_grows_when_a_call_costs_more_than_expected(media_dir):
    store = FakeStore([sense(i, f"w{i}") for i in range(1, 11)])
    summary = media_generate.run(store, FakeGenerator(cost=0.02, estimate=0.01), media_dir, max_cost=0.05,
                                 workers=1, log=lambda _: None)
    assert summary.cost <= 0.05 and len(summary.made) == 2


def test_failed_sense_is_not_saved_and_a_provider_stop_ends_the_run(media_dir):
    store = FakeStore([sense(1, "a"), sense(2, "b"), sense(3, "c"), sense(4, "d")])
    summary = media_generate.run(store, FakeGenerator(fail_for={"a"}, stop_for={"b"}), media_dir, workers=1,
                                 log=lambda _: None)
    assert [r.sense["lemma"] for r in summary.failed] == ["a", "b"]
    assert store.saved == [] and summary.skipped == 2
    assert "invalid_api_key" in summary.stopped_by
    assert list(media_dir.iterdir()) == []


def test_run_skips_a_sense_approved_in_the_admin_during_the_run(media_dir):
    store = FakeStore([sense(1, "a"), sense(2, "b")])
    store.approved.add(2)   # "Looks right" cleared needs_ai after the list was read
    generator = FakeGenerator()
    summary = media_generate.run(store, generator, media_dir, log=lambda _: None)
    assert [r.sense["lemma"] for r in summary.made] == ["a"] and len(generator.prompts) == 1


def test_dry_run_calls_and_saves_nothing(media_dir):
    store = FakeStore([sense(1), sense(2, "apple")])
    generator = FakeGenerator(estimate=0.003)
    lines: list[str] = []
    summary = media_generate.run(store, generator, media_dir, dry_run=True, log=lines.append)
    assert generator.prompts == [] and store.saved == [] and list(media_dir.iterdir()) == []
    assert summary.estimate == pytest.approx(0.006)
    assert "Estimate $0.0060 for 2 pictures" in lines[-1] and "covers about 1666 pictures" in lines[-1]


# --- Link ----------------------------------------------------------------------------------------------

def test_link_only_files_that_are_here_and_on_the_server(media_dir):
    store = FakeStore([sense(1, "a"), sense(2, "b"), sense(3, "c"), sense(4, "d")])
    media_generate.run(store, FakeGenerator(), media_dir, log=lambda _: None)
    names = [r.file_name for r in store.saved]
    (media_dir / names[2]).unlink()        # c: deleted on the Mac
    store.refuse.add(names[3])             # d: the admin picked another picture meanwhile
    summary = media_generate.link_generated(store, media_dir, lambda name: name != names[1], log=lambda _: None)
    assert store.linked == [names[0]]
    assert summary.not_on_server == [names[1]] and summary.missing_here == [names[2]] and summary.skipped == 1


def test_is_on_server_asks_with_head():
    seen: list[httpx.Request] = []

    def handler(request):
        seen.append(request)
        return httpx.Response(200 if request.url.path.endswith("here.webp") else 404)

    check = media_generate.is_on_server("https://api.example.test/media/", httpx.Client(transport=httpx.MockTransport(handler)))
    assert check("here.webp") and not check("gone.webp")
    assert seen[0].method == "HEAD" and str(seen[0].url) == "https://api.example.test/media/here.webp"


# --- Report --------------------------------------------------------------------------------------------

def test_report_shows_old_and_new_picture(tmp_path):
    media = tmp_path / "data" / "media"
    media.mkdir(parents=True)
    rows = [{"lemma": "bank", "position": 0, "pos": "noun", "simple_definition": "money <place>",
             "file_name": "bank_1_ai_1a2b3c4d.webp", "prompt": "Draw a bank & money", "provider": "openai",
             "model": "gpt-image-1-mini", "cost_usd": 0.0034, "linked_at": None, "old_file_name": "bank_1.webp",
             "old_kind": "photo"},
            {"lemma": "run", "position": 1, "pos": "verb", "simple_definition": "to move fast",
             "file_name": "run_2_ai_ffffffff.webp", "prompt": "p", "provider": "openai", "model": "gpt-image-1-mini",
             "cost_usd": 0.0030, "linked_at": datetime.now(timezone.utc), "old_file_name": "run_2.mp4", "old_kind": "clip"}]
    report = tmp_path / "data" / "out" / "media-generate.html"
    assert media_generate.write_report(rows, report, media) == 2
    page = report.read_text()
    assert '<img src="../media/bank_1.webp"' in page and '<img src="../media/bank_1_ai_1a2b3c4d.webp"' in page
    assert '<video src="../media/run_2.mp4"' in page
    assert "money &lt;place&gt;" in page and "Draw a bank &amp; money" in page
    assert "2 pictures, $0.0064 in all" in page and "not linked" in page and ">linked" not in page.replace("not linked", "")


# --- Postgres (needs TEST_DATABASE_URL) ----------------------------------------------------------------

def seed_pg(pg):
    """Word bank: position 0 needs_ai, 1 needs_ai but image_type none, 2 removed, 3 no status."""
    now = datetime.now(timezone.utc)
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO dictionary_word (lemma, word_rank, created_at, updated_at) VALUES ('bank', 1, %s, %s) RETURNING id", (now, now))
        word_id = cursor.fetchone()[0]
        for position, image_type, status in ((0, "photo", "needs_ai"), (1, "none", "needs_ai"), (2, "photo", "removed"), (3, "photo", None)):
            cursor.execute("INSERT INTO media (file_name, kind, created_at) VALUES (%s, 'photo', %s) RETURNING id",
                           (f"bank_{position + 1}.webp", now))
            media_id = cursor.fetchone()[0]
            cursor.execute(
                """INSERT INTO dictionary_sense (word_id, position, pos, cefr, simple_definition, vietnamese, examples_json,
                   usage_json, image_type, image_query, media_id, media_status)
                   VALUES (%s, %s, 'noun', 'A1', 'a place for money', 'ngân hàng', %s, '[]', %s, 'bank', %s, %s)""",
                (word_id, position, EXAMPLES, image_type, media_id, status),
            )
    pg.commit()


def made(item, file_name="bank_1_ai_00000000.webp"):
    return GenerationResult(item, media_generate.build_prompt(item), file_name, 120, 400, 0.0034)


def test_pg_pending_is_needs_ai_with_a_picture_type_only(pg):
    seed_pg(pg)
    store = media_generate.PostgresStore(pg)
    pending = store.pending_senses(None, 100, regenerate=False)
    assert [(s["lemma"], s["position"], s["old_file_name"]) for s in pending] == [("bank", 0, "bank_1.webp")]
    store.save_generation(made(pending[0]), "openai", "gpt-image-1-mini")
    assert store.pending_senses(None, 100, regenerate=False) == []
    assert len(store.pending_senses(None, 100, regenerate=True)) == 1
    assert store.still_needs_ai(pending[0]["id"]) is True
    pg.execute("UPDATE dictionary_sense SET media_status = NULL WHERE id = %s", (pending[0]["id"],))  # Looks right
    pg.commit()
    assert store.still_needs_ai(pending[0]["id"]) is False


def test_pg_link_makes_an_ai_media_row_and_sets_the_status(pg, tmp_path):
    seed_pg(pg)
    store = media_generate.PostgresStore(pg)
    item = store.pending_senses(None, 100, regenerate=False)[0]
    store.save_generation(made(item), "openai", "gpt-image-1-mini")
    [generation] = store.unlinked()
    assert store.link(generation, "openai", "gpt-image-1-mini", (320, 213, 9000)) is True
    row = pg.execute(
        """SELECT m.source, m.license_note, m.width, s.media_status, g.media_id = m.id, g.old_media_id <> m.id
           FROM dictionary_sense s JOIN media m ON m.id = s.media_id JOIN media_generation g ON g.sense_id = s.id
           WHERE s.position = 0""").fetchone()
    assert row == ("ai", "AI image (openai gpt-image-1-mini)", 320, "ai_generated", True, True)
    assert store.unlinked() == []
    assert pg.execute("SELECT count(*) FROM media WHERE file_name = 'bank_1.webp'").fetchone()[0] == 1  # old row kept
    rows = store.report_rows()
    assert rows[0]["old_file_name"] == "bank_1.webp" and rows[0]["linked_at"] is not None


def test_pg_link_does_nothing_when_the_admin_changed_the_sense(pg):
    seed_pg(pg)
    store = media_generate.PostgresStore(pg)
    item = store.pending_senses(None, 100, regenerate=False)[0]
    store.save_generation(made(item), "openai", "gpt-image-1-mini")
    [generation] = store.unlinked()
    pg.execute("UPDATE dictionary_sense SET media_status = NULL WHERE position = 0")  # Pick another
    pg.commit()
    assert store.link(generation, "openai", "gpt-image-1-mini", (320, 213, 9000)) is False
    assert pg.execute("SELECT count(*) FROM media WHERE source = 'ai'").fetchone()[0] == 0
