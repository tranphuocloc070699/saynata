"""The Claude Batch writer of the local collection (TASK-83.9). A fake client stands in for the Batches API."""

import json
from types import SimpleNamespace as NS

import pytest

from pipeline import collection as c
from pipeline import collection_batch as cb
from test_collection import GOOD, HARD, VOCAB, add, sense, status_of


@pytest.fixture
def db(tmp_path):
    connection = c.connect(tmp_path / "pipeline.db")
    cb.prepare(connection)
    return connection


def message(senses=None, text=None, stop_reason="end_turn", tokens=(1000, 500, 0, 0)):
    body = text if text is not None else json.dumps({"senses": senses})
    usage = NS(input_tokens=tokens[0], output_tokens=tokens[1], cache_creation_input_tokens=tokens[2],
               cache_read_input_tokens=tokens[3])
    return NS(content=[NS(type="text", text=body)], stop_reason=stop_reason, usage=usage)


class FakeBatches:
    """Answers each request from `answers[text]` (a list of senses, a list per round, or a result type string)."""

    def __init__(self, answers):
        self.answers, self.sent, self.ended = answers, {}, set()
        self.round = {}

    def create(self, requests):
        batch_id = f"msgbatch_{len(self.sent) + 1}"
        self.sent[batch_id] = requests
        return NS(id=batch_id, processing_status="in_progress")

    def retrieve(self, batch_id):
        counts = NS(processing=0 if batch_id in self.ended else len(self.sent[batch_id]), succeeded=0, errored=0,
                    canceled=0, expired=0)
        return NS(id=batch_id, processing_status="ended" if batch_id in self.ended else "in_progress",
                  request_counts=counts)

    def results(self, batch_id):
        for request in self.sent[batch_id]:
            text = request["params"]["messages"][0]["content"].split("</word>")[0][len("<word>"):]
            answer = self.answers[text]
            if isinstance(answer, tuple):
                n = self.round.get(text, 0)
                self.round[text] = n + 1
                answer = answer[n]
            if isinstance(answer, str):
                yield NS(custom_id=request["custom_id"], result=NS(type=answer))
            else:
                yield NS(custom_id=request["custom_id"], result=NS(type="succeeded", message=message(answer)))


def client(answers):
    return NS(messages=NS(batches=FakeBatches(answers)))


def run_round(db, fake, **kwargs):
    rows = cb.rows_to_submit(db, None, None, None)
    ids = cb.submit(db, rows, fake, word_prompt="PROMPT", **kwargs)
    fake.messages.batches.ended.update(ids)
    return cb.collect(db, fake, vocabulary=VOCAB, prompt_version="v7")


def test_request_is_the_backend_word_call():
    add_row = {"id": 7, "kind": "word", "text": "a<b", "status": "pending", "attempts": 0, "problems": None}
    request = cb.request_for(add_row, "PROMPT")
    assert request["custom_id"] == "c7-a1"
    params = request["params"]
    assert params["model"] == "claude-haiku-5-5" and params["max_tokens"] == 8000
    assert params["system"] == [{"type": "text", "text": "PROMPT", "cache_control": {"type": "ephemeral"}}]
    assert params["messages"] == [{"role": "user", "content": "<word>a&lt;b</word>"}]
    assert params["output_config"]["effort"] == "low"
    assert params["output_config"]["format"]["schema"]["required"] == ["senses"]
    phrase = cb.request_for({**add_row, "kind": "idiom", "text": "break the ice"}, "PROMPT")
    assert c.PHRASE_NOTE in phrase["params"]["system"][0]["text"]


def test_retry_request_carries_the_backend_feedback():
    row = {"id": 3, "kind": "word", "text": "abide", "status": "retry", "attempts": 1,
           "problems": json.dumps(["sense 1: the definition uses hard words [x]", "sense 2: y"])}
    content = cb.request_for(row, "P")["params"]["messages"][0]["content"]
    assert content == ("<word>abide</word>\nYour last answer used words that are too hard. Write the whole entry "
                       "again. Use only very common words in definitions and in easy examples. Problems: "
                       "sense 1: the definition uses hard words [x]; sense 2: y")
    assert cb.request_for(row, "P")["custom_id"] == "c3-a2"


def test_submit_marks_rows_and_never_sends_them_twice(db):
    add(db, "give up", "go on", "make up")
    fake = client({})
    ids = cb.submit(db, cb.rows_to_submit(db, None, None, None), fake, word_prompt="P", chunk=2)
    assert ids == ["msgbatch_1", "msgbatch_2"]
    assert [len(r) for r in fake.messages.batches.sent.values()] == [2, 1]
    assert cb.rows_to_submit(db, None, None, None) == []
    summary = cb.collect(db, fake, vocabulary=VOCAB, prompt_version="v7")
    assert summary.waiting == 2 and summary.batches == 0  # still running: nothing changes


def test_collect_saves_done_retry_skipped_and_failed(db):
    add(db, "give up", "go on", "zzz up", "make up")
    fake = client({"give up": GOOD, "go on": HARD, "zzz up": [], "make up": "errored"})
    summary = run_round(db, fake)
    assert summary.counts == {"done": 1, "retry": 1, "skipped": 1, "failed": 1}
    assert summary.cost == pytest.approx(3 * (1000 * 0.10 + 500 * 0.50) / 1e6 * 0.5)
    done = status_of(db, "give up")
    assert (done["status"], done["model"], done["prompt_version"], done["attempts"], done["batch_id"]) == \
        ("done", "claude-haiku-5-5", "v7", 1, None)
    assert status_of(db, "go on")["entry"] is not None  # the first answer is kept for the retry
    batch = db.execute("SELECT * FROM collection_batch").fetchone()
    assert batch["status"] == "collected" and batch["cost_usd"] == pytest.approx(summary.cost, abs=1e-4)
    # the next submit sends the retry (with feedback) and the failed row again
    nxt = {row["text"]: row for row in cb.rows_to_submit(db, None, None, None)}
    assert set(nxt) == {"go on", "make up"}
    assert "Problems: sense 1: the definition uses hard words [relinquish]" in \
        cb.request_for(nxt["go on"], "P")["params"]["messages"][0]["content"]


def test_second_round_ends_in_done_or_needs_review(db):
    add(db, "give up", "go on", "make up")
    fake = client({"give up": (HARD, GOOD), "go on": (HARD, HARD), "make up": (HARD, "expired")})
    run_round(db, fake)
    summary = run_round(db, fake)
    assert summary.counts == {"done": 1, "needs_review": 2}
    assert status_of(db, "give up")["attempts"] == 2
    kept = status_of(db, "make up")  # the retry broke: the first entry stays, marked for review
    assert kept["status"] == "needs_review" and "relinquish" in kept["entry"]
    assert cb.rows_to_submit(db, None, None, None) == []


def test_refusal_and_bad_json_are_failed(db):
    add(db, "give up", "go on")
    fake = client({})
    rows = cb.rows_to_submit(db, None, None, None)
    cb.submit(db, rows, fake, word_prompt="P")
    batches = fake.messages.batches
    batches.ended.add("msgbatch_1")
    batches.results = lambda batch_id: iter([
        NS(custom_id=f"c{rows[0]['id']}-a1", result=NS(type="succeeded", message=message(text="", stop_reason="refusal"))),
        NS(custom_id=f"c{rows[1]['id']}-a1", result=NS(type="succeeded", message=message(text="{not json")))])
    assert cb.collect(db, fake, vocabulary=VOCAB, prompt_version="v7").counts == {"failed": 2}
    assert "refused" in status_of(db, "give up")["problems"]


def test_usage_cost_counts_cache_writes_and_reads_at_half_price():
    usage = NS(input_tokens=100, output_tokens=1000, cache_creation_input_tokens=2000, cache_read_input_tokens=4000)
    expected = ((100 + 2000 * 1.25 + 4000 * 0.1) * 0.10 + 1000 * 0.50) / 1e6 * 0.5
    assert cb.usage_cost(usage) == pytest.approx(expected)


def test_status_and_estimate(db):
    assert "No batches yet" in cb.status_lines(db)[0]
    assert cb.estimate_usd(1000) == pytest.approx(0.25)
    add(db, "give up")
    run_round(db, client({"give up": GOOD}))
    assert "Spent so far" in cb.status_lines(db)[-1]
    assert "retry" in "\n".join(c.report_lines(db))


def test_codex_writer_leaves_retry_and_batched_rows_alone(db):
    add(db, "give up", "go on")
    db.execute("UPDATE collection SET status = 'retry' WHERE text = 'give up'")
    db.execute("UPDATE collection SET batch_id = 'msgbatch_9' WHERE text = 'go on'")
    assert c.rows_to_write(db, None, None, None, None) == []
    assert [r["text"] for r in cb.rows_to_submit(db, None, None, None)] == ["give up"]
    _ = sense  # imported for parity with test_collection's helpers
