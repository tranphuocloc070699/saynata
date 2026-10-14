"""Fetches, resizes, and caches stock media (photos & action video loops) from Pixabay.

Phase 2 of TASK-23:
- Nouns & adjectives (photo): 320 px WebP via Pillow (~15-25 KB).
- Action verbs (video): 2-3 s muted MP4 loop via ffmpeg (~50-100 KB).
- Stores photographer attribution (user, pageURL).
- Resume-safe: senses with media or image_type 'none' are skipped.
"""

import io
import json
import os
import re
import sqlite3
import subprocess
import tempfile
import threading
import time
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

import httpx
from PIL import Image

from pipeline import store

MEDIA_DIR = Path("data/media")
PIXABAY_PHOTO_URL = "https://pixabay.com/api/"
PIXABAY_VIDEO_URL = "https://pixabay.com/api/videos/"

# Rate limiting: Pixabay free tier is 100 requests/minute.
# Pacing at max ~60/min (0.8s between calls) prevents 429 rate limit errors.
_last_call_at = 0.0
_rate_limit_lock = threading.Lock()


def get_pixabay_api_key() -> str:
    key = os.environ.get("PIXABAY_API_KEY", "").strip()
    if not key:
        raise RuntimeError("PIXABAY_API_KEY is not set in pipeline/.env")
    return key


def _wait_for_rate_limit(min_gap: float = 0.8) -> None:
    global _last_call_at
    with _rate_limit_lock:
        now = time.monotonic()
        gap = min_gap - (now - _last_call_at)
        if gap > 0:
            time.sleep(gap)
        _last_call_at = time.monotonic()


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_\-]", "_", name.lower())


def needs_media(entry: dict) -> bool:
    """True if any sense with image_type != 'none' is missing media."""
    for sense in entry.get("senses", []):
        image_type = sense.get("image_type", "none")
        if image_type != "none" and "media" not in sense:
            return True
    return False


def search_pixabay_photo(
    query: str,
    api_key: str,
    client: httpx.Client,
    image_type: str = "photo",
) -> dict[str, Any] | None:
    """Searches Pixabay for photos or illustrations. Returns top result metadata or None."""
    _wait_for_rate_limit()
    params = {
        "key": api_key,
        "q": query,
        "image_type": image_type,
        "safesearch": "true",
        "order": "popular",
        "per_page": 3,
    }
    response = client.get(PIXABAY_PHOTO_URL, params=params, timeout=15)
    response.raise_for_status()
    data = response.json()
    hits = data.get("hits", [])
    if not hits:
        return None
    top = hits[0]
    return {
        "media_type": "photo",
        "download_url": top.get("webformatURL") or top.get("largeImageURL"),
        "author": top.get("user", "Unknown"),
        "page_url": top.get("pageURL", ""),
        "id": top.get("id"),
    }


def search_pixabay_video(
    query: str,
    api_key: str,
    client: httpx.Client,
) -> dict[str, Any] | None:
    """Searches Pixabay for short action video clips. Returns top result metadata or None."""
    _wait_for_rate_limit()
    params = {
        "key": api_key,
        "q": query,
        "safesearch": "true",
        "order": "popular",
        "per_page": 3,
    }
    response = client.get(PIXABAY_VIDEO_URL, params=params, timeout=15)
    response.raise_for_status()
    data = response.json()
    hits = data.get("hits", [])
    if not hits:
        return None
    top = hits[0]
    videos = top.get("videos", {})
    # Prefer tiny (360p) or small (480p) for fast download and lightweight transcoding
    download_url = (
        videos.get("tiny", {}).get("url")
        or videos.get("small", {}).get("url")
        or videos.get("medium", {}).get("url")
    )
    if not download_url:
        return None
    return {
        "media_type": "video",
        "download_url": download_url,
        "author": top.get("user", "Unknown"),
        "page_url": top.get("pageURL", ""),
        "id": top.get("id"),
    }


def resize_and_save_webp(image_bytes: bytes, target_path: Path, max_width: int = 320) -> int:
    """Downsamples image to max_width preserving aspect ratio and saves as WebP. Returns file size."""
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    if img.width > max_width:
        new_height = max(1, round(img.height * (max_width / img.width)))
        img = img.resize((max_width, new_height), Image.Resampling.LANCZOS)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(target_path, "WEBP", quality=80)
    return target_path.stat().st_size


def transcode_and_save_mp4(
    video_bytes: bytes,
    target_path: Path,
    duration: float = 3.0,
    max_width: int = 320,
) -> int:
    """Trims video to duration seconds, removes audio, scales to max_width, and encodes as H.264 MP4."""
    target_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as temp_in:
        temp_in.write(video_bytes)
        temp_in_path = temp_in.name

    try:
        cmd = [
            "ffmpeg",
            "-y",
            "-i",
            temp_in_path,
            "-t",
            str(duration),
            "-an",  # no audio
            "-vf",
            f"scale={max_width}:-2",  # scale width to 320, keep even height
            "-c:v",
            "libx264",
            "-crf",
            "28",
            "-preset",
            "fast",
            "-movflags",
            "+faststart",
            str(target_path),
        ]
        result = subprocess.run(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=True,
        )
        return target_path.stat().st_size
    finally:
        if os.path.exists(temp_in_path):
            os.remove(temp_in_path)


def fetch_media_for_sense(
    lemma: str,
    sense: dict,
    api_key: str,
    client: httpx.Client,
) -> dict[str, Any] | None:
    """Determines media strategy and queries Pixabay. Falls back to lemma+pos if image_query fails."""
    image_type = sense.get("image_type", "none")
    if image_type == "none":
        return None

    pos = sense.get("pos", "")
    query = sense.get("image_query", "").strip() or f"{lemma} {pos}"
    fallback_query = f"{lemma} {pos}".strip()

    # Strategy 1: Action verbs -> try Video first
    if pos == "verb":
        video_meta = search_pixabay_video(query, api_key, client)
        if not video_meta and query != fallback_query:
            video_meta = search_pixabay_video(fallback_query, api_key, client)
        if video_meta:
            return video_meta

    # Strategy 2: Icons/symbols -> try illustration first
    if image_type == "icon" and pos != "verb":
        illust_meta = search_pixabay_photo(query, api_key, client, image_type="illustration")
        if not illust_meta and query != fallback_query:
            illust_meta = search_pixabay_photo(fallback_query, api_key, client, image_type="illustration")
        if illust_meta:
            return illust_meta

    # Strategy 3: Concrete photos (default for photo or video fallback)
    photo_meta = search_pixabay_photo(query, api_key, client, image_type="photo")
    if not photo_meta and query != fallback_query:
        photo_meta = search_pixabay_photo(fallback_query, api_key, client, image_type="photo")
    return photo_meta


def _download_asset_bytes(client: httpx.Client, url: str, max_retries: int = 3) -> bytes:
    for attempt in range(max_retries):
        _wait_for_rate_limit(min_gap=1.0)
        try:
            resp = client.get(url, timeout=30)
            if resp.status_code == 429:
                time.sleep((attempt + 1) * 2.0)
                continue
            resp.raise_for_status()
            return resp.content
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 429 and attempt < max_retries - 1:
                time.sleep((attempt + 1) * 2.0)
                continue
            raise
    raise RuntimeError(f"Failed to download after {max_retries} attempts: {url}")


# Type for custom fetcher callback (used in tests)
MediaFetcher = Callable[[str, dict], dict[str, Any] | None]


def process_word_media(
    lemma: str,
    entry: dict,
    fetcher: MediaFetcher,
    media_dir: Path,
    dry_run: bool = False,
    client: httpx.Client | None = None,
) -> tuple[dict, list[str]]:
    """Fetches and saves media for all eligible senses of a word.

    Returns (updated_entry, log_messages).
    """
    new_entry = {**entry, "senses": []}
    logs: list[str] = []
    own_client = client is None

    http_client = client or httpx.Client(follow_redirects=True)
    try:
        for idx, sense in enumerate(entry.get("senses", [])):
            sense_copy = dict(sense)
            image_type = sense_copy.get("image_type", "none")

            if image_type == "none":
                sense_copy["media"] = None
                new_entry["senses"].append(sense_copy)
                continue

            # If already processed in an earlier run, preserve it
            if "media" in sense_copy and sense_copy["media"]:
                new_entry["senses"].append(sense_copy)
                continue

            metadata = fetcher(lemma, sense_copy)
            if not metadata:
                sense_copy["media"] = None
                logs.append(f"sense {idx + 1}: no media found for query '{sense_copy.get('image_query')}'")
                new_entry["senses"].append(sense_copy)
                continue

            media_type = metadata["media_type"]
            download_url = metadata["download_url"]
            ext = "mp4" if media_type == "video" else "webp"
            filename = f"{sanitize_filename(lemma)}_{idx + 1}.{ext}"
            target_file = media_dir / filename

            if dry_run:
                sense_copy["media"] = {
                    "type": media_type,
                    "filename": filename,
                    "author": metadata["author"],
                    "page_url": metadata["page_url"],
                    "preview_url": download_url,
                }
                logs.append(f"sense {idx + 1} ({media_type}): {filename} from {metadata['author']}")
                new_entry["senses"].append(sense_copy)
                continue

            # Download asset
            try:
                raw_bytes = _download_asset_bytes(http_client, download_url)

                if media_type == "video":
                    try:
                        size = transcode_and_save_mp4(raw_bytes, target_file)
                    except Exception as err:
                        # Video transcode failed: fall back to downloading photo
                        logs.append(f"sense {idx + 1}: video transcode failed ({err}), keeping photo fallback")
                        raise err
                else:
                    size = resize_and_save_webp(raw_bytes, target_file)

                sense_copy["media"] = {
                    "type": media_type,
                    "filename": filename,
                    "author": metadata["author"],
                    "page_url": metadata["page_url"],
                    "size_bytes": size,
                }
                logs.append(f"sense {idx + 1} ({media_type}): saved {filename} ({size // 1024} KB) by {metadata['author']}")
            except Exception as e:
                logs.append(f"sense {idx + 1}: download/process failed: {e}")
                raise e

            new_entry["senses"].append(sense_copy)
    finally:
        if own_client:
            http_client.close()

    return new_entry, logs


def run_pass(
    db: sqlite3.Connection,
    *,
    limit: int = 100000,
    workers: int = 4,
    words: list[str] | None = None,
    dry_run: bool = False,
    media_dir: Path = MEDIA_DIR,
    api_key: str | None = None,
    fetcher: MediaFetcher | None = None,
) -> dict[str, int]:
    """Runs the media pass across done words. Returns summary counts."""
    key = api_key or (get_pixabay_api_key() if not fetcher else "")

    rows = db.execute("SELECT lemma, entry FROM words WHERE status = 'done' ORDER BY rank").fetchall()
    todo = [(r["lemma"], json.loads(r["entry"])) for r in rows]
    todo = [
        (lemma, entry)
        for lemma, entry in todo
        if needs_media(entry) and (not words or lemma in words)
    ][:limit]

    counts = {"saved": 0, "failed": 0, "media_found": 0, "skipped": 0}
    if not todo:
        print("No words require media processing.")
        return counts

    media_dir.mkdir(parents=True, exist_ok=True)

    def default_fetcher(lemma: str, sense: dict) -> dict[str, Any] | None:
        with httpx.Client(follow_redirects=True) as client:
            return fetch_media_for_sense(lemma, sense, key, client)

    active_fetcher = fetcher or default_fetcher

    def _worker(lemma: str, entry: dict) -> dict[str, Any]:
        with httpx.Client(follow_redirects=True) as client:
            updated_entry, logs = process_word_media(
                lemma,
                entry,
                active_fetcher,
                media_dir,
                dry_run=dry_run,
                client=client,
            )
            return {"entry": updated_entry, "logs": logs}

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(_worker, lemma, entry): lemma for lemma, entry in todo}
        for number, future in enumerate(as_completed(futures), start=1):
            lemma = futures[future]
            try:
                res = future.result()
                entry_result = res["entry"]
                logs = res["logs"]

                media_count = sum(1 for s in entry_result.get("senses", []) if s.get("media"))
                counts["media_found"] += media_count

                if not dry_run:
                    store.save_entry(db, lemma, json.dumps(entry_result, ensure_ascii=False))
                    counts["saved"] += 1
                    print(f"[{number}/{len(todo)}] {lemma}: {media_count} media files saved", flush=True)
                else:
                    print(f"[{number}/{len(todo)}] {lemma} (dry run): {media_count} media matches", flush=True)

                for line in logs:
                    print(f"    {line}", flush=True)
            except Exception as err:
                counts["failed"] += 1
                print(f"[{number}/{len(todo)}] {lemma}: failed ({err})", flush=True)

    return counts
