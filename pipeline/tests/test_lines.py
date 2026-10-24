"""Language lines written by Codex (TASK-106.5). No test calls Codex: they use fakes.
The collect and push tests need a Postgres (TEST_DATABASE_URL, see conftest.py) and are skipped without one."""

import json
import re
import sqlite3
import subprocess
from pathlib import Path

import psycopg
import pytest

from pipeline import lines as ln

VERSION = "v1"


# ---------- the key: the same hash as the backend ----------


# Computed with the backend's own code (LineTranslationService.normalize + CacheKeys.key), see the task notes:
# strip() then replaceAll("\\s+", " ") then sha256 of the UTF-8 bytes.
JAVA_HASHES = [
    ("  a place   that keeps\tyour money\n safe  ", "7cb85d916b13ae09defb63d34205d3f9e3e72f207a8b1759a85258daeb575ea8"),
    ("a place", "426a85a7479437086bc38698a7079ed4501d73636b8bb6c4f90ef205dd7f50fb"),  # no-break space stays
    (" a place ", "13c76d4aaaaf2d7483fdecea93c8b958ccd07ccfce105301ccfd12545d460516"),  # and is not stripped
    ("a place", "2d242035f4ea462cdc29b7b64f0309fd01a972a539430eca55d471f9d7aac0a3"),  # em space is not collapsed
    ("　a place　", "d081d5ea16923825155ad866dbf7e0bb7fa326746ee432c2b37e71aebdbcc1c4"),  # but is stripped
    ("tiếng Việt   xin chào", "6fb14cf844a2ae60ca103108544f6e039a344f963e6eaecc4a64053d801b8619"),
    ("銀行 は　です", "49bf965a0a77f5ef13deab3c755866127d6974f16e1e7f1ff4443c45df43207d"),
    ("line1\r\nline2\u000bx\fy", "4c24a8ea2a1cd22a6d73dd9d43344a984f83f3b75b4e4fbeb7ac00d96454f3c9"),
    ("\u001cabc\u001f", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),  # = sha256("abc")
    ("emoji \U0001f600  ok", "21f070bfc29420afdc03d39927d748b1e66a414beb70ec41de7ce0932794dfb3"),
    (" a ", "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"),  # = sha256("a")
    ("\u0085a\u0085", "48bc918cd547e701f07ae56fe020508a584c179a67bfec31ff71955e0212919f"),  # NEL is not whitespace
    ("​a​", "5d6af4c0e6efe06f1ed2496f5123f1485f5ecbc5cf74b730fcf1fe140ddee454"),  # zero-width space neither
    ("﻿a", "1951c7860e968e742658b3af34e60741eb4aaf2a8d2ecc3993727016b12e81e8"),
]


@pytest.mark.parametrize("text,expected", JAVA_HASHES)
def test_hash_is_the_one_the_backend_computes(text, expected):
    assert ln.text_hash(ln.normalize(text)) == expected


def test_normalize_keeps_case_and_only_collapses_ascii_whitespace():
    assert ln.normalize("  Hello   World \n") == "Hello World"
    assert ln.normalize("US  us") == "US us"
    assert ln.normalize("a  b") == "a  b"
    assert ln.normalize(None) == "" and ln.normalize("   ") == ""


def test_usable_text_counts_utf16_units_like_java():
    assert ln.usable_text("x" * 500) == "x" * 500
    assert ln.usable_text("x" * 501) is None
    assert ln.usable_text("\U0001f600" * 250) == "\U0001f600" * 250  # 500 units
    assert ln.usable_text("\U0001f600" * 251) is None  # 502 units, 251 characters
    assert ln.usable_text("  ") is None


# ---------- the backend files ----------


def test_languages_are_the_backend_languages_without_english_and_thai():
    source = (ln.BACKEND / "src/main/java/com/englishtranslation/backend/translation/TargetLanguage.java").read_text()
    in_java = dict(re.findall(r'^\s*[A-Z]+\("([a-z]+)", "([^"]+)"\)', source, re.M))
    assert in_java.pop("en") == "English"
    in_java.pop("th", None)  # Thai is removed by TASK-106.1
    assert ln.LANGUAGES == in_java


def test_line_prompt_and_version_are_read_from_the_backend():
    prompt = ln.backend_line_prompt()
    assert prompt.startswith("You help a person learning English. You turn short English texts")
    assert "exactly one line per text" in prompt and '"""' not in prompt
    assert ln.backend_line_prompt_version() == VERSION


def test_prompt_has_the_shape_of_the_backend_message():
    prompt = ln.build_prompt("zh", ["a <b> & c", "second"], "PROMPT\n")
    assert prompt.startswith("PROMPT\n")
    assert prompt.endswith('<target_language>Simplified Chinese</target_language>\n<texts>\n'
                           '<text id="1">a &lt;b&gt; &amp; c</text>\n<text id="2">second</text>\n</texts>')
    assert '"lines"' in prompt


# ---------- the checks ----------


GOOD = {
    "vi": "một nơi giữ tiền của bạn an toàn", "ja": "お金を安全に預けておく場所", "ko": "돈을 안전하게 보관하는 곳",
    "zh": "存放你的钱的安全地方", "fr": "un endroit qui garde votre argent en sécurité",
    "es": "un lugar que guarda tu dinero seguro", "de": "ein Ort, der dein Geld sicher aufbewahrt",
}
TEXT = "a place that keeps your money safe"


@pytest.mark.parametrize("lang", list(GOOD))
def test_a_good_line_passes(lang):
    assert ln.line_problems(TEXT, GOOD[lang], lang) == []


@pytest.mark.parametrize("lang", list(GOOD))
def test_empty_english_and_two_line_answers_fail(lang):
    assert ln.line_problems(TEXT, "", lang) == ["empty line"]
    assert ln.line_problems(TEXT, None, lang) == ["empty line"]
    assert "same as the English text" in ln.line_problems(TEXT, TEXT.upper(), lang)
    assert "more than one line" in ln.line_problems(TEXT, GOOD[lang] + "\n" + GOOD[lang], lang)


def test_the_script_must_fit_the_language():
    assert "does not look like Japanese" in ln.line_problems(TEXT, "a safe place for money", "ja")
    assert "has Korean letters" in ln.line_problems(TEXT, GOOD["ko"], "ja")
    assert "does not look like Korean" in ln.line_problems(TEXT, GOOD["zh"], "ko")
    assert "has Japanese letters" in ln.line_problems(TEXT, GOOD["ja"], "ko")
    assert "has Japanese or Korean letters" in ln.line_problems(TEXT, GOOD["ja"], "zh")
    assert "does not look like Chinese" in ln.line_problems(TEXT, "bank place", "zh")
    assert "has Asian letters" in ln.line_problems(TEXT, GOOD["ja"], "fr")
    assert "does not look like Vietnamese (no tone marks)" in ln.line_problems(TEXT, "mot noi giu tien an toan", "vi")
    # a name or a bold English word inside a real line is fine
    assert ln.line_problems("Tom visits **Hanoi** today", "トムは今日**Hanoi**を訪れる", "ja") == []


def test_the_length_must_be_sensible():
    long = "つ" * 80
    assert any(p.startswith("too long") for p in ln.line_problems("a bank", long, "ja"))
    assert any(p.startswith("too long") for p in ln.line_problems("a bank", "un banco " * 20, "es"))
    assert any(p.startswith("too short") for p in ln.line_problems("x" * 100, "sí", "es"))
    assert ln.line_problems("bank", "銀行", "ja") == []  # a short text, a short line


# ---------- write ----------


@pytest.fixture
def db(tmp_path) -> sqlite3.Connection:
    return ln.connect(tmp_path / "pipeline.db")


def add(db, *texts, lang="vi", source=ln.SOURCE_BUNDLED, version=VERSION):
    for n, text in enumerate(texts):
        db.execute("INSERT INTO lines (text_hash, text, target_lang, prompt_version, source, priority) "
                   "VALUES (?, ?, ?, ?, ?, ?)", (ln.text_hash(text), text, lang, version, source, 100 - n))
    db.commit()


def row(db, text, lang="vi"):
    return db.execute("SELECT * FROM lines WHERE text = ? AND target_lang = ?", (text, lang)).fetchone()


class FakeWriter:
    """Answers each text from a dict; a tuple is one answer per call; an exception is raised; a list replaces the
    whole answer (to test a wrong count)."""

    name = "codex:fake"

    def __init__(self, answers, whole=None):
        self.answers, self.whole, self.prompts, self.calls = answers, whole, [], {}

    def texts_in(self, prompt):
        return [m.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                for m in re.findall(r'<text id="\d+">(.*?)</text>', prompt)]

    def write(self, prompt):
        self.prompts.append(prompt)
        if self.whole is not None:
            answer = self.whole.pop(0) if isinstance(self.whole, list) else self.whole
            if isinstance(answer, Exception):
                raise answer
            return answer
        out = []
        for text in self.texts_in(prompt):
            answer = self.answers[text]
            if isinstance(answer, tuple):
                n = self.calls.get(text, 0)
                self.calls[text] = n + 1
                answer = answer[n]
            if isinstance(answer, Exception):
                raise answer
            out.append(answer)
        return out


def run(db, writer, lang=None, **kwargs):
    by_language = ln.rows_to_write(db, VERSION, [lang] if lang else None)
    kwargs.setdefault("line_prompt", "P")
    return ln.write_rows(db, by_language, writer, **kwargs)


def test_good_lines_are_saved_and_a_second_run_finds_nothing_to_do(db):
    add(db, "a bank", "to run")
    writer = FakeWriter({"a bank": "ngân hàng", "to run": "chạy"})
    summary = run(db, writer)
    assert summary.counts == {"done": 2} and summary.calls == 1
    assert row(db, "a bank")["translation"] == "ngân hàng" and row(db, "a bank")["model"] == "codex:fake"
    assert row(db, "a bank")["attempts"] == 1
    assert ln.rows_to_write(db, VERSION) == {}


def test_one_call_holds_texts_of_one_language_only(db):
    add(db, "a bank", "to run", lang="vi")
    add(db, "a bank", "to run", lang="ko")
    writer = FakeWriter({"a bank": "은행", "to run": "달리다"})
    by_language = ln.rows_to_write(db, VERSION, ["ko", "vi"])
    ln.write_rows(db, by_language, writer, line_prompt="P", workers=1)
    assert len(writer.prompts) == 2
    for prompt in writer.prompts:
        assert prompt.count("<target_language>") == 1
    # vi got Korean text, so the check failed: the script check works on every row of a batch
    assert row(db, "a bank", "ko")["status"] == "done"
    assert row(db, "a bank", "vi")["status"] == "pending" and "tone marks" in row(db, "a bank", "vi")["problems"]


def test_batches_are_cut_to_the_batch_size_and_ordered_by_priority(db):
    add(db, *[f"text {n}" for n in range(5)])
    writer = FakeWriter({f"text {n}": "văn bản" for n in range(5)})
    run(db, writer, batch_size=2, workers=1)
    assert [writer.texts_in(p) for p in writer.prompts] == [["text 0", "text 1"], ["text 2", "text 3"], ["text 4"]]


def test_limit_is_per_language_and_sources_filter(db):
    add(db, "a", "b", "c", lang="vi")
    add(db, "a", "b", "c", lang="ja", source=ln.SOURCE_SENTENCE)
    got = ln.rows_to_write(db, VERSION, limit=2)
    assert [r["text"] for r in got["vi"]] == ["a", "b"] and len(got["ja"]) == 2
    only = ln.rows_to_write(db, VERSION, sources=[ln.SOURCE_SENTENCE])
    assert list(only) == ["ja"]


def test_a_bad_line_is_tried_again_and_after_three_tries_needs_review(db):
    add(db, "a bank")
    bad = "a bank"  # the English text again
    run(db, FakeWriter({"a bank": bad}))
    first = row(db, "a bank")
    assert (first["status"], first["attempts"]) == ("pending", 1)
    assert "same as the English text" in json.loads(first["problems"])
    run(db, FakeWriter({"a bank": bad}))
    run(db, FakeWriter({"a bank": bad}))
    last = row(db, "a bank")
    assert (last["status"], last["attempts"]) == ("needs_review", 3)
    assert ln.rows_to_write(db, VERSION) == {}
    assert run(db, FakeWriter({"a bank": "ngân hàng"})).calls == 0  # nothing pending: no call
    # --retry-failed gives it three more tries
    by_language = ln.rows_to_write(db, VERSION, retry_failed=True)
    ln.write_rows(db, by_language, FakeWriter({"a bank": "ngân hàng"}), line_prompt="P")
    assert row(db, "a bank")["status"] == "done"


def test_a_good_retry_after_a_bad_try_is_done(db):
    add(db, "a bank")
    writer = FakeWriter({"a bank": ("", "ngân hàng")})
    run(db, writer)
    assert row(db, "a bank")["status"] == "pending"
    run(db, writer)
    assert (row(db, "a bank")["status"], row(db, "a bank")["attempts"]) == ("done", 2)


def test_a_wrong_number_of_lines_splits_the_batch(db):
    add(db, "a", "b", "c", "d")
    # first call: 3 lines for 4 texts -> halves of 2; the first half answers well, the second half is wrong again
    writer = FakeWriter({}, whole=[["x1", "x2", "x3"], ["một", "hải"], ["bà"], ["bà"], ["bốn"]])
    ln.write_rows(db, ln.rows_to_write(db, VERSION), writer, line_prompt="P", workers=1)
    assert [row(db, t)["translation"] for t in "abcd"] == ["một", "hải", "bà", "bốn"]
    assert [row(db, t)["status"] for t in "abcd"] == ["done"] * 4


def test_a_failed_call_keeps_the_rows_pending_and_then_failed(db):
    add(db, "a bank")
    for _ in range(3):
        summary = run(db, FakeWriter({}, whole=ln.WriteError("no answer")))
    assert summary.counts == {"failed": 1}
    r = row(db, "a bank")
    assert (r["status"], r["attempts"]) == ("failed", 3) and "no answer" in r["problems"]


def test_the_plan_limit_stops_the_run_and_keeps_what_is_done(db):
    add(db, "a", "b", "c", "d")
    writer = FakeWriter({}, whole=[["một", "hải"], ln.ProviderStop("usage limit")])
    summary = ln.write_rows(db, ln.rows_to_write(db, VERSION), writer, batch_size=2, workers=1, line_prompt="P")
    assert summary.stopped == "usage limit" and summary.counts == {"done": 2}
    assert [row(db, t)["status"] for t in "abcd"] == ["done", "done", "pending", "pending"]


def test_codex_writer_uses_the_lines_schema_and_answer(tmp_path):
    calls = []

    def fake_run(command, **kwargs):
        calls.append(command)
        schema = json.loads(Path(command[command.index("--output-schema") + 1]).read_text())
        assert schema["properties"]["lines"]["items"] == {"type": "string"}
        Path(command[command.index("-o") + 1]).write_text(json.dumps({"lines": ["một", "hai"]}))
        return subprocess.CompletedProcess(command, 0, "", "")

    writer = ln.CodexWriter("gpt-x", "low", run=fake_run)
    assert writer.write("PROMPT") == ["một", "hai"] and writer.name == "codex:gpt-x"
    assert "read-only" in calls[0] and calls[0][-1] == "PROMPT"


def test_report_counts_and_the_estimate(db):
    assert "no lines yet" in ln.report_lines(db)[0]
    add(db, "a", "b", "c")
    add(db, "a", lang="ja")
    run(db, FakeWriter({"a": "một", "b": "hải", "c": "bà"}), lang="vi")
    text = "\n".join(ln.report_lines(db))
    assert re.search(r"vi\s+0\s+3\s+0\s+0\s+0\s+3", text) and re.search(r"ja\s+1\s+0", text)
    assert "To write: 1 lines" in text and "Ready to push: 3 lines" in text


# ---------- collect and push (Postgres) ----------


def seed(pg):
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO dictionary_word (id, lemma, word_rank, created_at, updated_at) "
                       "VALUES (1, 'bank', 2, now(), now()), (2, 'run', 1, now(), now())")
        for word_id, position, definition in [(1, 0, "a place that keeps your money safe"),
                                              (1, 1, "the side of a river"), (2, 0, "to move fast on foot"),
                                              (2, 1, "a place that keeps your money safe")]:  # a repeat
            cursor.execute("INSERT INTO dictionary_sense (word_id, position, pos, cefr, simple_definition, vietnamese, "
                           "examples_json, usage_json, image_type, image_query) "
                           "VALUES (%s, %s, 'noun', 'A1', %s, 'x', '[]', '[]', 'photo', 'q')",
                           (word_id, position, definition))
        entry = {"senses": [{"simpleDefinition": "a very big dog"}, {"simpleDefinition": "  to  move  fast on foot "},
                            {"simpleDefinition": ""}]}
        cursor.execute("INSERT INTO word_lookup (cache_key, word, entry_json, model, prompt_version, created_at) "
                       "VALUES ('k1', 'giraffe', %s, 'claude', 'v7', now()), ('k2', 'bad', 'not json', 'claude', 'v7', now())",
                       (json.dumps(entry),))
        for n, text in enumerate(["How are you?", "x" * 501, "How are you?"]):
            cursor.execute("INSERT INTO sentence_translation (cache_key, source_text, translation, model, prompt_version, "
                           "created_at) VALUES (%s, %s, 't', 'm', 'v9', now())", (f"s{n}", text[:500] if n == 1 else text))
    pg.commit()


def test_collect_reads_three_sources_without_duplicates_and_is_safe_to_run_again(db, pg, database_url):
    seed(pg)
    readonly = psycopg.connect(database_url)
    readonly.read_only = True
    found = ln.collect(db, readonly, VERSION)
    assert found.texts == {ln.SOURCE_BUNDLED: 3, ln.SOURCE_BACKEND: 1, ln.SOURCE_SENTENCE: 2}
    # "x"*501 was cut to 500 by the seed, so it is usable; the empty definition is not
    assert found.too_long == 1 and sum(found.added.values()) == 6 * 7 and found.existing == 0
    assert db.execute("SELECT count(*) FROM lines").fetchone()[0] == 42
    # order: run (rank 1) first, then bank, then the backend words, then sentences
    order = [r[0] for r in db.execute("SELECT text FROM lines WHERE target_lang = 'vi' ORDER BY priority DESC, id")]
    assert order == ["to move fast on foot", "a place that keeps your money safe", "the side of a river",
                     "a very big dog", "How are you?", "x" * 500]
    assert row(db, "to move fast on foot")["text_hash"] == ln.text_hash("to move fast on foot")
    again = ln.collect(db, readonly, VERSION)
    assert sum(again.added.values()) == 0 and again.existing == 42
    with pytest.raises(psycopg.errors.ReadOnlySqlTransaction):
        readonly.execute("DELETE FROM word_lookup")
    readonly.close()


def test_collect_skips_what_the_server_has_and_the_dry_run_saves_nothing(db, pg):
    seed(pg)
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO line_translation (text_hash, text, target_lang, prompt_version, translation, "
                       "source, created_at) VALUES (%s, 'the side of a river', 'ko', 'v1', 'x', 'llm', now()), "
                       "(%s, 'the side of a river', 'ja', 'v0', 'old', 'llm', now())",
                       (ln.text_hash("the side of a river"), ln.text_hash("the side of a river")))
    pg.commit()
    dry = ln.collect(db, pg, VERSION, languages=["ko", "ja"], dry_run=True)
    assert dry.on_server == 1 and sum(dry.added.values()) == 6 * 2 - 1
    assert db.execute("SELECT count(*) FROM lines").fetchone()[0] == 0
    ln.collect(db, pg, VERSION, sources=[ln.SOURCE_BUNDLED], languages=["ko", "ja"])
    assert row(db, "the side of a river", "ko") is None and row(db, "the side of a river", "ja") is not None


def test_collect_and_push_work_before_the_table_exists(db, pg):
    seed(pg)
    pg.execute("DROP TABLE line_translation")
    pg.commit()
    assert ln.server_lines(pg, VERSION) == set()
    assert sum(ln.collect(db, pg, VERSION, languages=["vi"]).added.values()) == 6


def test_push_adds_new_lines_and_never_overwrites(db, pg):
    add(db, "a bank", "to run", "a dog")
    add(db, "a bank", lang="ja")
    with pg.cursor() as cursor:
        cursor.execute("INSERT INTO line_translation (text_hash, text, target_lang, prompt_version, translation, source, "
                       "created_at) VALUES (%s, 'a bank', 'vi', 'v1', 'Claude wrote this', 'llm', now())",
                       (ln.text_hash("a bank"),))
    pg.commit()
    writer = FakeWriter({"a bank": "ngân hàng", "to run": "chạy", "a dog": "con chó"})
    run(db, writer, lang="vi")
    ln.write_rows(db, ln.rows_to_write(db, VERSION, ["ja"]), FakeWriter({"a bank": "銀行"}), line_prompt="P")
    rows, stale = ln.push_rows(db, VERSION)
    assert len(rows) == 4 and stale == 0
    plan = ln.plan_push(pg, rows, VERSION)
    assert [r["text"] for r in plan.exists] == ["a bank"] and len(plan.to_insert) == 3
    assert ln.push(db, pg, plan, VERSION) == 3
    with pg.cursor() as cursor:
        cursor.execute("SELECT text, target_lang, translation, source, prompt_version FROM line_translation "
                       "ORDER BY target_lang, text")
        saved = cursor.fetchall()
    assert saved == [("a bank", "ja", "銀行", "codex", "v1"), ("a bank", "vi", "Claude wrote this", "llm", "v1"),
                     ("a dog", "vi", "con chó", "codex", "v1"), ("to run", "vi", "chạy", "codex", "v1")]
    assert ln.push_rows(db, VERSION) == ([], 0)  # all marked pushed, also the one that was already there
    assert row(db, "a bank")["pushed_at"] is not None
    plan = ln.plan_push(pg, [row(db, "a dog")], VERSION)  # pushing again changes nothing
    assert ln.push(db, pg, plan, VERSION) == 0


def test_push_leaves_out_lines_of_an_older_prompt_version(db, pg):
    add(db, "a bank", version="v0")
    add(db, "a dog")
    run(db, FakeWriter({"a dog": "con chó"}))
    db.execute("UPDATE lines SET status = 'done', translation = 'ngân hàng' WHERE text = 'a bank'")
    db.commit()
    rows, stale = ln.push_rows(db, VERSION)
    assert [r["text"] for r in rows] == ["a dog"] and stale == 1
    assert ln.push_rows(db, VERSION, languages=["ja"]) == ([], 1)
    assert [r["text"] for r in ln.push_rows(db, VERSION, limit=1)[0]] == ["a dog"]
