#!/usr/bin/env python3
"""Measure how fast the streaming endpoint shows the meaning (TASK-65).

For each NEW sentence it prints the time until the `meaning` event and the time until `done`.
It also calls the old endpoint (POST /api/translations) with a second new sentence, so you can
compare "time until the app shows something" before and after.

Needs a running backend with a real Claude key. Each sentence is a real Claude call (about 1 cent in total).

    BASE_URL=http://localhost:8081 python3 backend/scripts/measure_stream.py
"""
import http.client
import json
import os
import sys
import time
import urllib.parse
import uuid

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8081")
LANG = os.environ.get("LANG_CODE", "vi")

# A random word makes every sentence new, so it is never served from the cache.
TEMPLATES = [
    "The committee was reluctant to approve the plan, although it looked promising ({tag}).",
    "She decided to postpone the trip because the weather had turned unpredictable ({tag}).",
    "Although he was exhausted, he kept working until the report was finished ({tag}).",
]


def open_connection():
    url = urllib.parse.urlparse(BASE_URL)
    cls = http.client.HTTPSConnection if url.scheme == "https" else http.client.HTTPConnection
    return cls(url.hostname, url.port or (443 if url.scheme == "https" else 80), timeout=60)


def post(path, text, accept):
    connection = open_connection()
    body = json.dumps({"text": text, "targetLang": LANG})
    started = time.perf_counter()
    connection.request("POST", path, body, {"Content-Type": "application/json", "Accept": accept})
    return connection, connection.getresponse(), started


def measure_stream(text):
    """Returns (seconds to meaning or None, seconds to done or None)."""
    connection, response, started = post("/api/translations/stream", text, "text/event-stream")
    if response.status != 200:
        print(f"  stream answered {response.status}: {response.read()[:200]!r}")
        return None, None
    meaning_at = done_at = None
    event = None
    while True:
        line = response.readline()
        if not line:
            break
        line = line.decode("utf-8").rstrip("\r\n")
        if line.startswith("event:"):
            event = line[6:].strip()
        elif line == "" and event:
            now = time.perf_counter() - started
            if event == "meaning" and meaning_at is None:
                meaning_at = now
            if event in ("done", "error"):
                done_at = now if event == "done" else None
                break
            event = None
    connection.close()
    return meaning_at, done_at


def measure_old(text):
    connection, response, started = post("/api/translations", text, "application/json")
    response.read()
    seconds = time.perf_counter() - started
    connection.close()
    return seconds if response.status == 200 else None


def fmt(seconds):
    return "   n/a" if seconds is None else f"{seconds:5.2f}s"


def main():
    print(f"Backend: {BASE_URL}   language: {LANG}\n")
    print("Streaming endpoint (3 new sentences):")
    print("  sentence  meaning  done")
    results = []
    for index, template in enumerate(TEMPLATES, start=1):
        meaning_at, done_at = measure_stream(template.format(tag=uuid.uuid4().hex[:6]))
        results.append((meaning_at, done_at))
        print(f"  {index:>8}  {fmt(meaning_at)}  {fmt(done_at)}")

    print("\nOld endpoint (3 new sentences): the app waits for this whole time before it shows anything.")
    old = []
    for index, template in enumerate(TEMPLATES, start=1):
        seconds = measure_old(template.format(tag=uuid.uuid4().hex[:6]))
        old.append(seconds)
        print(f"  {index:>8}  {fmt(seconds)}")

    meanings = [m for m, _ in results if m is not None]
    dones = [d for _, d in results if d is not None]
    olds = [o for o in old if o is not None]
    if meanings and dones and olds:
        print(f"\nAverage: first text on screen {sum(meanings) / len(meanings):.2f}s (was {sum(olds) / len(olds):.2f}s), "
              f"complete answer {sum(dones) / len(dones):.2f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
