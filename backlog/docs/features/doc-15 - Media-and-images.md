---
id: doc-15
title: Media and images
type: specification
created_date: '2026-10-15 04:02'
updated_date: '2026-10-15 07:25'
---
# Media and images

> Status: **working for photos and clips from Pixabay** (TASK-23, 49.3, 49.4, 52). AI images are not built yet (TASK-53). The iOS side on this branch only shows files inside the app; loading from the server comes with PR #48 (TASK-47 and 51). Last traced from code: 2026-10-15 (the admin words list can filter by media with `media=with|without`, TASK-60; a bundled word is answered from Postgres with its media, TASK-58).
> Why: a small picture or a short clip helps a learner remember a meaning (doc-1, Vision and spec). Postgres on the server is the source of truth for content (doc-7).

## What it does

Each sense of a word can have one **photo** (a 320 px WebP) or one **clip** (a 3 second, muted MP4). Nouns and adjectives get photos. Verbs get clips. The files come from Pixabay. There are two ways a sense gets media: the pipeline on the developer's Mac fills it for the 2,750 bundled words, and the backend fills it in the background for every **new** word that Claude writes. An admin can fix any sense in the admin web app: pick another picture, remove it, or mark it for an AI image. The iOS app shows the picture under the explanation of the sense.

This page follows the files from Pixabay to the screen. It does not repeat the pages for the parts: see [Word lookup](doc-5%20-%20Word-lookup.md), [Dictionary pipeline](doc-6%20-%20Dictionary-pipeline.md), [Admin API](doc-13%20-%20Admin-API.md), [Admin CMS](doc-14%20-%20Admin-CMS.md) and the [VPS guide, section 9](../guides/doc-11%20-%20Deploy-to-a-VPS.md).

## Big picture

```mermaid
flowchart LR
    PX[Pixabay]
    subgraph Mac["Developer Mac"]
        PIPE["pipeline media<br/>data/media/"]
        SQL["dictionary.sqlite<br/>(image_url: media/file)"]
    end
    subgraph VPS["VPS"]
        PG[("Postgres<br/>media, dictionary_sense,<br/>word_lookup_media")]
        DIR[("/var/lib/saynata/media")]
        CADDY[Caddy<br/>/media/*]
        BE["Backend<br/>BackendWordMediaService<br/>MediaStore"]
    end
    ADMIN[Admin web app]
    APP[iOS app]

    PX -->|"photos, clips"| PIPE
    PIPE -->|"link-media (rows)"| PG
    PIPE -->|"rsync (files)"| DIR
    PG -->|"export"| SQL
    SQL -->|"bundled in the app"| APP
    PX -->|"new word, background job"| BE
    BE -->|"rows"| PG
    BE -->|"files"| DIR
    ADMIN -->|"search, pick, remove, mark"| BE
    PX -->|"search results"| ADMIN
    DIR --> CADDY
    CADDY -->|"https://api.loffy.me/media/file"| APP
    BE -->|"GET /api/words: imageUrl"| APP
```

The database keeps only a row per file (`media`: name, kind, size, credit). The files themselves live on disk in one folder. The same folder is written by the backend and read by Caddy.

## 1. Pipeline: the bundled words

The bundled words are made on the Mac. The order matters, because the media pass works on `data/pipeline.db` (the old working database) and Postgres is the source of truth.

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant Pass as pipeline media
    participant PX as Pixabay
    participant Files as data/media/
    participant Local as data/out/dictionary.sqlite
    participant PG as Postgres (SSH tunnel)
    participant VPS as /var/lib/saynata/media

    Dev->>Pass: uv run pipeline media
    Pass->>PX: search (verbs: video first, then photo)
    PX-->>Pass: top hit + credit
    Pass->>Files: word_N.webp or word_N.mp4
    Pass->>Pass: save the credit in pipeline.db
    Dev->>Local: uv run pipeline export-local
    Dev->>PG: uv run pipeline link-media --media-dir data/media
    PG-->>PG: media rows + dictionary_sense.media_id (only where empty)
    Dev->>VPS: rsync data/media/ to the server
    Dev->>PG: uv run pipeline export (reads media from Postgres)
```

| Step | File:line | What happens |
|---|---|---|
| Which senses need media | [needs_media:60](../../../pipeline/src/pipeline/media.py#L60) | A sense with `image_type` other than `none` and no media yet. This makes the pass safe to run again. |
| Choose what to search | [fetch_media_for_sense:196](../../../pipeline/src/pipeline/media.py#L196) | Verbs: video first. Icons: illustrations first. Everything else: a photo. If the sense's `image_query` finds nothing, it tries `lemma + pos`. |
| Search Pixabay | [search_pixabay_photo:69](../../../pipeline/src/pipeline/media.py#L69), [search_pixabay_video:101](../../../pipeline/src/pipeline/media.py#L101) | `safesearch`, `order=popular`, top 3 hits, take the first. Waits 0.8 s between calls ([_wait_for_rate_limit:46](../../../pipeline/src/pipeline/media.py#L46)). |
| Resize | [resize_and_save_webp:140](../../../pipeline/src/pipeline/media.py#L140), [transcode_and_save_mp4:151](../../../pipeline/src/pipeline/media.py#L151) | Photo: 320 px wide WebP, quality 80, with Pillow. Clip: short muted MP4 loop, with ffmpeg. |
| Save the file | [process_word_media:256](../../../pipeline/src/pipeline/media.py#L256) | File name is `<word>_<sense number>.webp` or `.mp4` in `data/media/`. The credit (photographer, page) goes into `pipeline.db`. |
| The pass | [run_pass:347](../../../pipeline/src/pipeline/media.py#L347), command in [cli.py:144](../../../pipeline/src/pipeline/cli.py#L144) | `uv run pipeline media [--words run walk] [--workers 4] [--dry-run]`. Uses your Pixabay key (`PIXABAY_API_KEY` in `pipeline/.env`). A full pass is many Pixabay requests and takes a long time. |
| Old export | [export_from_pipeline_db:52](../../../pipeline/src/pipeline/export.py#L52), command at [cli.py:274](../../../pipeline/src/pipeline/cli.py#L274) | `export-local` builds `dictionary.sqlite` from `pipeline.db`. Each sense gets `image_url = "media/<file>"` ([export.py:78-80](../../../pipeline/src/pipeline/export.py#L78-L80)). |
| Link into Postgres | [link_media:195](../../../pipeline/src/pipeline/postgres.py#L195), command at [cli.py:177](../../../pipeline/src/pipeline/cli.py#L177) | Reads those `image_url` values and adds `media` rows. It sets `media_id` only on senses that have none, and never changes text. So admin edits are safe. |
| Copy the files | rsync, see [doc-11 section 9](../guides/doc-11%20-%20Deploy-to-a-VPS.md) | `rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/`. The rows say a file exists; rsync puts it there. |
| Import (one time) | [import_dictionary:89](../../../pipeline/src/pipeline/postgres.py#L89), command at [cli.py:157](../../../pipeline/src/pipeline/cli.py#L157) | The first copy of the bundled dictionary into Postgres. It also creates `media` rows. It reads file sizes from `--media-dir`. |
| Export from Postgres | [export_from_postgres:117](../../../pipeline/src/pipeline/export.py#L117), [copy_media:191](../../../pipeline/src/pipeline/export.py#L191), command at [cli.py:189](../../../pipeline/src/pipeline/cli.py#L189) | Builds `data/out/dictionary.sqlite` and copies the files that senses use into `data/out/media/`. Files missing from `--media-dir` are listed as a warning. |

Then the developer copies `dictionary.sqlite` and the media folder into the app (`EnglishTranslation/EnglishTranslation/Resources`). The bundled words use these files without the network. How the run works from the start is in [Dictionary pipeline](doc-6%20-%20Dictionary-pipeline.md).

## 2. Backend: new words

When a learner looks up a word that is not in the bundle and not in the cache, the backend asks Claude, saves the answer, replies, and **then** looks for pictures. The first reply has no `imageUrl`. The next lookup has it.

```mermaid
sequenceDiagram
    participant App as iOS app
    participant WS as WordService
    participant Job as BackendWordMediaService (thread media-1)
    participant PX as Pixabay
    participant Store as MediaStore
    participant DB as Postgres
    participant FS as MEDIA_DIR

    App->>WS: GET /api/words/dance
    WS->>DB: save word_lookup (Claude answer)
    WS--)Job: fetchInBackground (async)
    WS-->>App: entry, no imageUrl yet
    loop first 3 senses (noun, adjective, verb only)
        Job->>DB: claim: insert word_lookup_media
        Job->>PX: findClip (verb) or findPhoto
        PX-->>Job: hit + credit
        Job->>Store: save(word, position, hit)
        Store->>PX: download
        Store->>FS: ffmpeg: 320 px WebP or 3 s MP4
        Store->>DB: insert media row (credit, size)
        Job->>DB: word_lookup_media.media_id = new id
    end
    App->>WS: GET /api/words/dance (next time)
    WS->>DB: read word_lookup_media + media
    WS-->>App: senses with imageUrl
    App->>FS: GET https://api.loffy.me/media/file (through Caddy)
```

| Step | File:line | What happens |
|---|---|---|
| Start the job | [WordService.java:100](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L100), [startMediaJob:152](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L152) | Called after the new word is saved. If it fails to start, it is logged and the answer is still sent. |
| One worker thread | [MediaConfig.mediaExecutor:21](../../../backend/src/main/java/com/englishtranslation/backend/media/MediaConfig.java#L21) | One thread, queue of 50. A full queue drops the job: the word works, it only has no picture. |
| The job | [fetchInBackground:46](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L46), [fetch:55](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L55) | Does nothing when `PIXABAY_API_KEY` is empty. Takes the first 3 senses. Every error is caught, so the word answer never breaks. |
| Photo or clip | [kindFor:75](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L75) | Verb: clip (photo if there is no clip). Noun or adjective: photo. Other types (adverb, "the") get nothing. |
| Claim first | [claim:88](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L88) | A row in `word_lookup_media` is inserted before Pixabay is asked. So Pixabay is asked once per sense, even when it finds nothing. To try again, delete the word in the admin. |
| Rate limit | [MIN_GAP_MILLIS:28](../../../backend/src/main/java/com/englishtranslation/backend/media/HttpPixabayClient.java#L28), [waitForTurn:172](../../../backend/src/main/java/com/englishtranslation/backend/media/HttpPixabayClient.java#L172) | At least 1.5 s between Pixabay requests (40 per minute; Pixabay allows 100). With one worker thread we stay far below the limit. Downloads only come from `pixabay.com` over https. |
| Download, convert, save | [MediaStore.save:44](../../../backend/src/main/java/com/englishtranslation/backend/media/MediaStore.java#L44), [FfmpegMediaConverter:16-27](../../../backend/src/main/java/com/englishtranslation/backend/media/FfmpegMediaConverter.java#L16-L27) | Photo: WebP, max 320 px wide, quality 80. Clip: 3 s, no sound, H.264 MP4, `+faststart`. The name is `<word>_<n>_<8 hex of sha256>.<ext>`, so a new picture is always a new name. If the row cannot be saved, the file is removed. |
| Link to the sense | [fetchOneSense:100](../../../backend/src/main/java/com/englishtranslation/backend/media/BackendWordMediaService.java#L100) | Sets `word_lookup_media.media_id`. |
| Tables | [V7](../../../backend/src/main/resources/db/migration/V7__create_word_lookup_media.sql), [V8](../../../backend/src/main/resources/db/migration/V8__add_media_status.sql) | `word_lookup_media (word_lookup_id, sense_position, media_id, media_status)`. A deleted word deletes its rows. Bundled words use `dictionary_sense.media_id` and `media_status` instead. |
| Read for the answer | [SenseMediaReader.forBackendWords:26](../../../backend/src/main/java/com/englishtranslation/backend/media/SenseMediaReader.java#L26), [WordService.mediaOf:142](../../../backend/src/main/java/com/englishtranslation/backend/word/WordService.java#L142) | Joins `word_lookup_media` to `media`. A read problem is logged and ignored. |
| The address | [MediaProperties.urlFor:18](../../../backend/src/main/java/com/englishtranslation/backend/media/MediaProperties.java#L18), [application.properties:41-45](../../../backend/src/main/resources/application.properties#L41-L45) | `imageUrl` = `app.media.base-url` + `/` + file name. Default base URL: `https://api.loffy.me/media`. |
| The JSON | [WordEntryView.of:17](../../../backend/src/main/java/com/englishtranslation/backend/word/WordEntryView.java#L17) | Adds `imageUrl` to a sense that has media. It is left out when there is none, so old app versions see nothing new. |
| Serve the file | Caddy, see [doc-11 section 9](../guides/doc-11%20-%20Deploy-to-a-VPS.md); folder mount in [docker-compose.prod.yml:49-55](../../../backend/docker-compose.prod.yml#L49-L55) | `handle_path /media/*` reads `/var/lib/saynata/media` and adds `Cache-Control: public, max-age=31536000, immutable` (one year). The backend writes to the folder; Caddy only reads it. |

Env: `MEDIA_DIR` (folder), `MEDIA_BASE_URL` (public address), `PIXABAY_API_KEY` (empty means no job). Details of the word flow are in [Word lookup](doc-5%20-%20Word-lookup.md).

## 3. Admin: fix one sense (TASK-52)

In a word's detail, each sense has a media row with three icon buttons: **Pick another**, **No image** and **Mark for AI**. A sense is named by the word id and the position: `b-12:0` is the first sense of bundled word 12, `w-45:2` is the third sense of backend word 45. The screens are described in [Admin CMS](doc-14%20-%20Admin-CMS.md), the login and rules in [Admin API](doc-13%20-%20Admin-API.md).

```mermaid
sequenceDiagram
    participant Web as Admin web app
    participant C as AdminMediaController
    participant S as AdminMediaService
    participant PX as Pixabay
    participant Store as MediaStore
    participant DB as Postgres

    Web->>C: GET /api/admin/media/search?sense=b-12:0&q=
    C->>S: search
    S->>S: rate limit: 30 per 5 minutes per admin
    S->>PX: photos + clips for image_query (or the word)
    PX-->>Web: up to 6 results (small previews)
    Web->>C: PUT /words/b-12/senses/0/media (the chosen result)
    C->>S: replace
    S->>S: check: kind, only Pixabay https addresses
    S->>Store: save (download, convert, file, media row)
    S->>DB: dictionary_sense.media_id = new, media_status = null
    S-->>Web: the changed word
```

| Button | Endpoint | File:line | What happens |
|---|---|---|---|
| (search) | `GET /api/admin/media/search?sense=b-12:0&q=` | [controller:33](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaController.java#L33), [AdminMediaService.search:77](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L77) | Up to 6 results (usually 2 clips and the rest photos). With an empty `q`, it uses the sense's `image_query`, or the headword. The answer says which words it used. |
| Pick another | `PUT /api/admin/words/{id}/senses/{n}/media` | [controller:51](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaController.java#L51), [replace:100](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L100) | The result comes from the browser, so [checked:260](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L260) only accepts Pixabay https addresses. Then `MediaStore.save`, then the link. A sense that was "no image" gets `image_type = photo` again. |
| No image | `DELETE .../media` | [controller:57](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaController.java#L57), [clear:123](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L123) | `media_id` becomes null. For a bundled sense, `image_type = none` tells the pipeline not to fetch again. For a backend word the claim row stays, so the job does not fetch again. |
| Mark for AI | `POST .../media/mark-for-ai` | [controller:62](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaController.java#L62), [markForAi:136](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L136) | Sets `media_status = needs_ai`. The old picture stays until an AI image replaces it (TASK-53). Picking another picture clears the status. |
| (list) | `GET /api/admin/media?status=needs_ai` | [controller:44](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaController.java#L44), [listByStatus:154](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L154) | All marked senses, from both tables. Any other status is a 400. |

Shared details:

- **Search rate limit:** [MediaSearchRateLimiter:19-20](../../../backend/src/main/java/com/englishtranslation/backend/admin/MediaSearchRateLimiter.java#L19-L20) allows 30 searches in 5 minutes per admin, kept in memory (a restart clears it). One search is two Pixabay calls, and Pixabay allows 100 per minute for our whole key.
- **Backend words without a claim row:** [ensureBackendRow:205](../../../backend/src/main/java/com/englishtranslation/backend/admin/AdminMediaService.java#L205) makes the `word_lookup_media` row when it is missing (the background job only makes rows for 3 senses).
- **Old files stay on disk.** A replaced picture is not deleted; only its link changes.

## 4. iOS: how the app shows media

On this branch the app only shows files that are **inside the app**. The word card calls the view for each sense that has an `imageUrl`.

| Step | File:line | What happens |
|---|---|---|
| Read the field | [WordEntry.swift:48](../../../EnglishTranslation/EnglishTranslation/Dictionary/WordEntry.swift#L48) | `Sense.imageUrl` is decoded from the backend JSON (`imageUrl`) and from the bundled file (`image_url`). |
| Bundled words | [DictionaryStore.swift:40](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L40), [:106](../../../EnglishTranslation/EnglishTranslation/Dictionary/DictionaryStore.swift#L106) | Reads the `senses.image_url` column (for example `media/run_1.mp4`) when the file has it. |
| Show it | [WordCardView.swift:68](../../../EnglishTranslation/EnglishTranslation/Views/Word/WordCardView.swift#L68) | Under the sense title row, `SenseMediaView(path:)` is shown if the sense has an `imageUrl`. |
| Find the file | [SenseMediaView.swift:41](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L41) | `resolveMediaURL` looks in the app bundle only: the path as written, then the `media` folder, then the bundle root. |
| Photo or clip | [SenseMediaView.swift:13](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L13) | A path ending in `.mp4` plays in a looping, muted player ([LoopingPlayerUIView:68](../../../EnglishTranslation/EnglishTranslation/Views/Word/SenseMediaView.swift#L68)). Anything else is a 240 x 135 photo. |

A web address (`https://api.loffy.me/media/...`) is not a bundled file, so on this branch nothing is shown for words from the backend. The view then shows no error and no empty box.

### Coming in PR #48 (TASK-47 + 51)

These files are **not on this branch yet**, so they are named as plain text, not links. They were read from `origin/fix/task-47-51-media`. Real links will be added when PR #48 merges.

- `SenseMediaSource.swift`: reads `Sense.imageUrl` as either a bundled path or an absolute **https** address. Plain `http` is refused. It also tells a clip from a photo by the file extension.
- `MediaFileCache.swift`: a disk cache in `Caches/SenseMedia`, **50 MB**, least recently used files go first. A file bigger than a quarter of the cache is not kept. Two views that ask for the same address share one download. iOS may delete Caches, which is fine: files are downloaded again.
- `SenseMediaView.swift` (new version): a server photo is downloaded once and saved, so it shows again offline. A server clip plays from the network at once, and is saved in the background for next time. While loading, the frame keeps its size but is invisible, so nothing jumps. If loading fails, nothing is shown.
- **Clip looper fix:** the player now starts from an empty `AVQueuePlayer` and lets `AVPlayerLooper` add the items. This stops the stutter at the loop point.
- **Play only when visible:** a clip plays only when about a third of it is on screen and the app is active (`onScrollVisibilityChange` and `scenePhase`). Otherwise it pauses.
- Tests: `SenseMediaTests.swift`.

## Errors

| Cause | Where | What happens |
|---|---|---|
| `PIXABAY_API_KEY` empty | Background job, admin | Job does nothing and claims nothing. Admin search, pick: 503 "Pixabay is not set up". |
| Pixabay has no result | Background job | The claim row stays with no `media_id`. Pixabay is not asked again for that sense. |
| Download or ffmpeg fails | `MediaStore.save` | No file and no row are left. Job: logged, next sense runs. Admin pick: 502 "Could not download or convert that file", the old picture stays. |
| Pixabay does not answer | Admin search | 502 "Pixabay did not answer". |
| More than 30 searches in 5 minutes | Admin search | 429 "Too many searches". |
| Chosen file is not from Pixabay, or `kind` is wrong | Admin pick | 400, nothing is downloaded. |
| Sense does not exist | Admin | 404. |
| Job queue is full (50 waiting) | `mediaExecutor` | Job dropped, the word has no picture. |
| File missing in `/var/lib/saynata/media` | Caddy | 404 for the file. The app shows nothing for that sense. |
| Bundled file missing in the app | iOS | Nothing is shown. |

## Tests

| Test file | What it covers |
|---|---|
| [BackendWordMediaServiceTest](../../../backend/src/test/java/com/englishtranslation/backend/media/BackendWordMediaServiceTest.java) | Noun gets a photo, verb gets a clip (photo fallback), other types skipped, a failure is not thrown and not retried, no leftover file after a failed conversion. |
| [NoPixabayKeyTest](../../../backend/src/test/java/com/englishtranslation/backend/media/NoPixabayKeyTest.java) | Without a key, Pixabay is never called and nothing is claimed. |
| [WordImageUrlTest](../../../backend/src/test/java/com/englishtranslation/backend/media/WordImageUrlTest.java) | `imageUrl` is absolute, the field is missing when there is no media, a new word is answered first. |
| [FfmpegMediaConverterTest](../../../backend/src/test/java/com/englishtranslation/backend/media/FfmpegMediaConverterTest.java) | Big photo becomes a small WebP, video becomes a short MP4, bad input fails with a clear message. Needs ffmpeg on the machine. |
| [HttpPixabayClientTest](../../../backend/src/test/java/com/englishtranslation/backend/media/HttpPixabayClientTest.java) | Only Pixabay hosts over https are accepted. |
| [AdminMediaTest](../../../backend/src/test/java/com/englishtranslation/backend/admin/AdminMediaTest.java) | Login and role, search (6 results, default words, rate limit, errors), pick, remove, mark for AI, list, 404. |
| [test_media.py](../../../pipeline/tests/test_media.py) | `needs_media`, file names, WebP resize, dry run, the pass with a fake fetcher. |
| [mediaRow.test.tsx](../../../admin/src/test/mediaRow.test.tsx) | The admin buttons and the Pick another panel. |

Run: `cd backend && ./mvnw -B test`, `cd pipeline && uv run pytest`, `cd admin && npm test`. There is no iOS test for media on this branch; PR #48 adds `SenseMediaTests.swift`.

## Change checklist

- Change the picture size, quality or clip length: [FfmpegMediaConverter](../../../backend/src/main/java/com/englishtranslation/backend/media/FfmpegMediaConverter.java) **and** `resize_and_save_webp` / `transcode_and_save_mp4` in the pipeline. Keep both the same.
- Change the file name rule: `MediaStore.save`, the pipeline file name, `link_media`, and the Caddy cache note (a name must change when the content changes).
- Add a new media `kind`: `kindFor`, `MediaStore`, `SenseMedia`, the admin `checked`, the iOS source type.
- Change `MEDIA_BASE_URL` or the folder: [doc-11](../guides/doc-11%20-%20Deploy-to-a-VPS.md), `docker-compose.prod.yml` and the Caddyfile together.
- Change Pixabay limits: `HttpPixabayClient` (backend), `_wait_for_rate_limit` (pipeline), `MediaSearchRateLimiter` (admin).

## TASK-53: AI images (not built)

AI images plug in at `media_status = needs_ai` (set by Mark for AI, listed by `GET /api/admin/media?status=needs_ai`). A future job reads those senses, makes an image, saves it through `MediaStore` with `source = ai` (plus provider, prompt and cost) on the `media` row, links it, and clears the status.

## Finding words by media (TASK-60)

`GET /api/admin/words?media=with` keeps the words where any sense has a media file, `media=without` the words where no sense has one (doc-13). A bundled word counts through `dictionary_sense.media_id`, a backend word through `word_lookup_media.media_id`. A claim row with no file does not count. The admin has two chips for it, **Has media** and **No media** (doc-14). This is the way to see which bundled words the Pixabay pass has not reached yet: press "No media", and the count line shows how many are left.

## Known gaps

- Flyway V9 (TASK-58) deleted the `word_lookup` rows of words that are in the bundled dictionary, with their `word_lookup_media` links. The media rows and files of those words stay on disk (same gap as the next lines).

- A new word's first answer has no `imageUrl`. The picture shows from the second lookup (on purpose: the answer must not wait for Pixabay).
- If the server restarts while jobs wait in the queue, those words get no picture until an admin deletes the word.
- A sense whose Pixabay search found nothing is never tried again by itself.
- Pipeline file names have no hash (`run_1.mp4`). Caddy caches every file for a year as `immutable`. If you rsync a different picture under the same name, phones may keep the old one.
- Bundled words read pictures from the app bundle. A picture an admin picks for a bundled sense changes Postgres, but reaches the app only after the next `export` and a new app build.
- Old files are never deleted from `/var/lib/saynata/media` (replaced pictures, removed words).
- On this branch, iOS cannot show server `https` media (see PR #48 above).
- Pixabay credit (photographer, page) is stored and sent to the admin, but the iOS card does not show it yet.
- The Media page of the admin web app is still a placeholder (see doc-14).
