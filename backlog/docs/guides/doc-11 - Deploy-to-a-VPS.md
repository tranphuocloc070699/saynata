---
id: doc-11
title: Deploy to a VPS
type: guide
created_date: '2026-10-11 16:33'
updated_date: '2026-10-20 03:13'
---
# Deploy to a VPS

How to run the Saynata backend on a small server (about 10 USD per month) so the iPhone app works anywhere.
You do each step yourself. No secrets are in git.

What you need:
- A VPS with Ubuntu 22.04 or newer, 2 GB RAM (1 GB works, but the first build is slow), and a public IP.
- Docker with the Compose plugin on the server (`curl -fsSL https://get.docker.com | sh`).
- A Claude API key.
- For HTTPS: a domain name that points to the server's IP (an `A` record).

## What runs

```mermaid
flowchart LR
    APP[iPhone app] -->|HTTPS 443| CADDY[Caddy, optional]
    CADDY -->|127.0.0.1:8081| BE[backend container]
    BE --> PG[(postgres container + volume)]
    BE --> CL[Claude API]
```

Files: [backend/Dockerfile](../../../backend/Dockerfile), [backend/docker-compose.prod.yml](../../../backend/docker-compose.prod.yml),
[backend/.env.example](../../../backend/.env.example).
Postgres has no public port. Its data lives in the named volume `pgdata`, so it survives restarts and rebuilds.
The backend has a health check on `/actuator/health` (it shows only `UP` or `DOWN`).

## 1. Copy the files to the server

The simplest way is to clone the repo on the server:

```bash
git clone https://github.com/tranphuocloc070699/saynata.git ~/saynata
cd ~/saynata/backend
```

Or copy only the `backend/` folder: `rsync -av --exclude target backend/ user@SERVER:~/saynata/`.

## 2. Set the .env file

```bash
cp .env.example .env
nano .env
```

| Name | Meaning |
|---|---|
| `CLAUDE_API_KEY` | Your Anthropic key. Required, unless you use a gateway (next rows). |
| `CLAUDE_BASE_URL`, `CLAUDE_AUTH_TOKEN` | Optional. An Anthropic-compatible gateway instead of Anthropic: its URL without `/v1` (for example `https://steyg.one/api`) and its key, sent as `Authorization: Bearer`. With a gateway the backend puts the JSON Schema in the prompt and parses the answer from the text (STEYG ignores the strict JSON setting), so non-Claude models work too: on STEYG use `CLAUDE_MODEL=deepseek-v4.1-flash` (the fastest model there in the 2026-10-20 test: about 2.6 s to the first words of a sentence, against about 6 s for `gpt-6-luna`), `CLAUDE_SCENE_MODEL=claude-opus-5-5`, `CLAUDE_EFFORT=` (empty). Then also set `CLAUDE_MODEL` and `CLAUDE_SCENE_MODEL` to model IDs the gateway sells, `CLAUDE_EFFORT=` (empty) for models before the 5 family, and check there is a price for each model in `application.properties`. |
| `CLAUDE_SCENE_MODEL` | Optional. The model that writes the AI picture scene (default `claude-sonnet-5-5`). |
| `CLAUDE_EFFORT` | Optional. `low` by default; empty = not sent (older models refuse it). |
| `DB_PASSWORD` | A long random password for Postgres. Required. Make one with `openssl rand -base64 24`. |
| `BACKEND_PORT` | Port on the server (default 8081). |
| `BACKEND_BIND` | `0.0.0.0` (open) or `127.0.0.1` (only Caddy on the same server can reach it). |
| `CLAUDE_MODEL` | Optional. Default is the model in `application.properties`. |
| `ADMIN_JWT_SECRET` | Signs the admin login cookie. 32+ characters. Make one with `openssl rand -base64 48`. **The backend does not start without it**, unless `ADMIN_ENABLED=false`. |
| `ADMIN_EMAIL` | Email of the first admin (for the admin web app). Used only when no admin exists yet. |
| `AI_PICTURES_ENABLED` | Optional, default `false`. `true` lets the admin draw AI pictures on the server with the Codex CLI (TASK-91). Needs the steps in section 12 first. Also `AI_PICTURES_PER_DAY` (30), `AI_SCENE_DAILY_USD` (1.00), `AI_CODEX_SANDBOX` (`read-only`), `CODEX_HOST_DIR` (`/var/lib/saynata/codex`). |
| `ADMIN_INITIAL_PASSWORD` | Password of the first admin, 12+ characters. Used only when no admin exists yet; remove it from `.env` after the first start. |
| `ADMIN_ENABLED` | Optional. `false` turns the admin API off. Default `true`. |
| `PIXABAY_API_KEY` | Optional. Free key from https://pixabay.com/api/docs/ . With it, a new word that is not in the bundle gets a photo or clip in the background (section 9), except words saved by the prefill job. Empty = no pictures are fetched. |
| `MEDIA_BASE_URL` | Optional. Public address of the media folder. Default `https://api.loffy.me/media`. |
| `MEDIA_HOST_DIR` | Optional. Folder on the server with the media files. Default `/var/lib/saynata/media` (section 9). |
| `USER_JWT_SECRET` | Optional. Signs the sign-in tokens of app users. 32+ characters, `openssl rand -base64 48`. If empty, a key is derived from `ADMIN_JWT_SECRET` (a warning is logged). Set it once and keep it: changing it signs everyone out. |
| `RESEND_API_KEY` | Optional until Resend is ready. A "Sending access" API key from resend.com (section 10). With `EMAIL_FROM` it makes the backend send sign-in emails with Resend. |
| `EMAIL_FROM` | The "From" address of the sign-in email, on your verified domain, for example `login@loffy.me`. **No key and no from address = no email is sent**: each code is only written to the backend log (section 10). |
| `SES_FROM_EMAIL` | Optional, for later. The older name of `EMAIL_FROM`, used for AWS SES when `RESEND_API_KEY` is empty (section 11). |
| `AWS_REGION` | Only for SES. The AWS region where you set up SES, for example `ap-southeast-1`. Must be the same region as the SES identity. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | The access key of the IAM user that may only send email (section 11). Read by the AWS SDK itself. |
| `GOOGLE_CLIENT_IDS` | Optional. The iOS OAuth client ID from Google Cloud (section 13), comma-separated for more. Empty = the Google button answers "This sign-in option is not set up yet". |
| `APPLE_CLIENT_IDS` | Optional. The bundle IDs Apple makes ID tokens for. Default `com.luke.EnglishTranslation,com.luke.EnglishTranslation.Mac`. |
| `PUBLIC_BASE_URL` | Optional. The public HTTPS address of the backend, default `https://api.loffy.me`. The sign-in link email points to `PUBLIC_BASE_URL/auth/link?token=...`. **Change it when the domain moves** (section 13). |
| `APP_URL_SCHEME` | Optional. The app's URL scheme the link page opens, default `saynata` (`saynata://sign-in?token=...`). |
| `APPLE_APP_IDS` | Optional, paid Apple account only. `TEAMID.com.luke.EnglishTranslation`. Set = `/.well-known/apple-app-site-association` is served, so iOS opens the emailed link in the app directly (section 13). |
| `REQUIRE_SIGN_IN` | Optional. `true` (default): a guest cannot cause a new Claude call (401). **Set `REQUIRE_SIGN_IN=false` until the app with sign-in is released**, because the current app sends no token. Rate limits and the daily budget cap still apply to guests' Claude calls while it is false (a guest IP may cause 30 new calls per hour, `LIMIT_CLAUDE_GUEST_IP_MAX`). Remove the line, or set `true`, when the new app is out. |
| `CLAUDE_DAILY_BUDGET_USD` | Optional. The most Claude may cost per day in US dollars (UTC day). Default `1.00`. At the cap, new explanations stop until midnight UTC; cached words still work. Prices are in `application.properties` (`app.claude.pricing.*`). |
| `PREFILL_CRON` | Optional. When the prefill job runs by itself (TASK-83.1, feature page "Prefill"): a six-field Spring cron (`second minute hour day month weekday`) in **UTC**. Empty (default) = it never runs by itself. A night run in Vietnam: `PREFILL_CRON=0 0 20 * * *` (20:00 UTC = 03:00). You can always start a run by hand with `POST /api/admin/prefill/run`. |
| `PREFILL_MAX_USD_PER_RUN` | Optional. The most **one** prefill run may spend on Claude, in US dollars. Default `0.50`. Separate from `CLAUDE_DAILY_BUDGET_USD`: prefill calls are saved as feature `PREFILL` and do not use the users' day budget. |
| `PREFILL_DELAY` | Optional. Pause after each new word, default `0s`. Prefilled words get no picture, so no pause is needed. |
| `LIMIT_*` | Optional. Rate limits, for example `LIMIT_CLAUDE_USER_HOUR_MAX` (default 60). The full list is in `backend/.env.example`. |
| `POSTGRES_PORT` | Optional. Port of Postgres on the server's own loopback (`127.0.0.1`), for the dictionary pipeline (section 8). Default 5432. It is never open to the internet. |

The admin API (`/api/admin`, see [Admin API](../features/doc-13%20-%20Admin-API.md)) is for the admin web app.
Its login cookie is `SameSite=Strict`, so the admin site and the API must share a site, for example
`admin.loffy.me` and `api.loffy.me`. Behind Caddy keep `BACKEND_BIND=127.0.0.1`: the login rate limit reads the
client IP from `X-Forwarded-For`, which Caddy sets.

`.env` is git-ignored. Never commit it. Set `DB_PASSWORD` before the first start: Postgres keeps the first one
in the volume.

## 3. Start it

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

The first build downloads Maven dependencies and takes a few minutes. Then check:

```bash
docker compose -f docker-compose.prod.yml ps        # backend should become "healthy"
curl http://localhost:8081/actuator/health          # {"status":"UP"}
curl http://localhost:8081/api/words/run            # a bundled word, no Claude call
docker compose -f docker-compose.prod.yml logs -f backend
```

Useful commands:

| Job | Command |
|---|---|
| Update after `git pull` | `./scripts/deploy.sh` (rebuilds, waits until healthy, prints logs if not) |
| Restart | `docker compose -f docker-compose.prod.yml restart backend` |
| Stop (keeps data) | `docker compose -f docker-compose.prod.yml down` |
| Back up the database | `docker compose -f docker-compose.prod.yml exec postgres pg_dump -U app english_translation > backup.sql` |

Never use `down -v` unless you want to delete the database: `-v` removes the volume.

## 4. Firewall

Allow SSH first, or you lock yourself out.

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp     # only if you use Caddy
sudo ufw allow 443/tcp    # only if you use Caddy
sudo ufw enable
```

Warning: Docker opens ports by editing iptables itself, and that skips ufw. A port in `ports:` is open to the
internet even if ufw says no. This is why, with Caddy, you set `BACKEND_BIND=127.0.0.1`. Also check your VPS
provider's own firewall panel.

## 5. HTTPS (needed for the iPhone app)

iOS App Transport Security (ATS) blocks plain `http://` to any host except local-network addresses. Our Info.plist
only has `NSAllowsLocalNetworking`. So a real server needs an `https://` URL with a valid certificate. Do not turn
ATS off.

Option A, recommended: Caddy in front of the backend. Caddy gets and renews a free Let's Encrypt certificate by itself.

1. In `.env` set `BACKEND_BIND=127.0.0.1`, then run `docker compose -f docker-compose.prod.yml up -d`.
2. Install Caddy (https://caddyserver.com/docs/install#debian-ubuntu-raspbian) and put this in `/etc/caddy/Caddyfile`:

```caddyfile
api.example.com {
    reverse_proxy 127.0.0.1:8081
}
```

3. Run `sudo systemctl reload caddy`, then `curl https://api.example.com/actuator/health`.
   The same block also serves the sense photos and clips at `/media/`, see section 9.

Option B: a tunnel such as Cloudflare Tunnel or Tailscale Funnel. You open no ports and need no certificate setup,
but it adds an account and a service you depend on.

## 6. Point the app at the server

In `EnglishTranslation/EnglishTranslation/Info.plist`, change the value of `BackendBaseURL`:

```xml
<key>BackendBaseURL</key>
<string>https://api.example.com</string>
```

Build and run on your iPhone. A word that is not in the bundled `dictionary.sqlite`, and every sentence, goes to the
server. Watch `docker compose -f docker-compose.prod.yml logs -f backend` while you test.

The Claude key stays on the server. The app never sends one.

## 7. Auto deploy (GitHub Actions)

After CI passes on `main`, [.github/workflows/deploy.yml](../../../.github/workflows/deploy.yml) logs in to the VPS
over SSH, moves `~/saynata` to the commit CI tested (`git merge --ff-only`), and runs
[backend/scripts/deploy.sh](../../../backend/scripts/deploy.sh). That script builds the backend image
(`docker compose build backend`), then runs `docker compose up -d --wait`, so the run turns red if the backend does not
become healthy, and it prints the last backend logs.
Each build try is stopped after 6 minutes (`DEPLOY_BUILD_TIMEOUT`, in seconds, set it on the server to change it) and
tried once more; if both tries fail, the old backend keeps running. The GitHub jobs stop after 20 minutes (backend)
and 10 minutes (admin). A normal deploy takes about 3 minutes.
A second job, `deploy-admin`, then builds the admin web app on GitHub (Node 22, `VITE_API_URL` from the
`ADMIN_API_URL` repository variable, default `https://api.loffy.me`) and copies `admin/dist` to the server with
`rsync --delete` (section 8). The VPS needs `rsync`, but no Node.
You can also start it by hand: Actions tab, **Deploy**, **Run workflow**.
Until the secrets below exist, the workflow does nothing and stays green.

```mermaid
flowchart LR
    PUSH[push to main] --> CI[CI workflow]
    CI -->|success| DEP[Deploy workflow]
    DEP -->|SSH, deploy key| VPS[VPS: git merge + deploy.sh]
```

One-time setup (steps 1-3 above must be done first; the repo can live in `~/saynata` or another folder such as `/var/www/saynata`, see `VPS_APP_DIR`):

1. On your Mac, make a key only for deploys (no passphrase):
   `ssh-keygen -t ed25519 -f ~/.ssh/saynata_deploy -N "" -C "github-deploy"`
2. Allow it on the VPS: `ssh-copy-id -i ~/.ssh/saynata_deploy.pub vps_sieutoc`
3. Get the server's host key, so GitHub can check it is talking to your server:
   `ssh-keyscan -p <port> <server IP>` (copy all output lines).
4. On GitHub: repo **Settings**, **Secrets and variables**, **Actions**, **New repository secret**:

| Secret | Value |
|---|---|
| `VPS_HOST` | The server IP or domain (the `HostName` in your `~/.ssh/config` for `vps_sieutoc`) |
| `VPS_USER` | The SSH user (`User` in `~/.ssh/config`). Default `root`. |
| `VPS_PORT` | Optional. Default 22. |
| `VPS_SSH_KEY` | The whole private key: `cat ~/.ssh/saynata_deploy` |
| `VPS_KNOWN_HOSTS` | The `ssh-keyscan` output from step 3 |
| `VPS_APP_DIR` | Optional. Repo folder on the server: an absolute path such as `/var/www/saynata`, or a path relative to the user's home. Default `saynata` (`~/saynata`). |

5. Test: Actions tab, **Deploy**, **Run workflow**. Then `curl https://<your domain>/actuator/health`.

Rules: never edit files inside `~/saynata` on the server (except `backend/.env`, which git ignores), or the
fast-forward fails. The private key lives only in GitHub secrets and on your Mac, never in git or in a chat.
The deploy user must be able to run `docker` (`sudo usermod -aG docker <user>`).

## 8. Admin web app (admin.loffy.me)

The admin CMS ([Admin CMS](../features/doc-14%20-%20Admin-CMS.md)) is only static files. Caddy serves `admin/dist`; the backend
at `api.loffy.me` stays the same (its `/api/admin/**` login and CORS for `https://admin.loffy.me` come from TASK-49.1).

1. DNS: add an `A` record `admin.loffy.me` that points to the server, like `api.loffy.me`.
2. Build. The Deploy workflow does this for you after every push to main (section 7). By hand, on the server or on your Mac (then copy `admin/dist`), with Node 20 or newer:

```bash
cd ~/saynata/admin
npm ci
VITE_API_URL=https://api.loffy.me npm run build     # writes admin/dist
```

3. Add this to `/etc/caddy/Caddyfile` next to the `api.loffy.me` block (`root` is the absolute path to `admin/dist`;
   `try_files` sends every unknown path to `index.html`, so a reload on `/words` works):

```caddyfile
admin.loffy.me {
    root * /home/youruser/saynata/admin/dist
    encode gzip
    try_files {path} /index.html
    file_server
}
```

4. Run `sudo systemctl reload caddy`, then open `https://admin.loffy.me` and sign in.

Updates are automatic: the `deploy-admin` job replaces `admin/dist` after each push to main, and Caddy reads the new files at once, no restart. By hand, run step 2 again.
The Caddy user must be able to read the folder (`chmod -R o+rX ~/saynata/admin/dist`, and `chmod o+x` on each parent folder).
CI builds and tests the admin app on every pull request (`admin` job in `.github/workflows/ci.yml`).

## 9. Dictionary pipeline: Postgres and media (TASK-49.3)

The bundled dictionary (2,750 words) lives in this server's Postgres (tables `dictionary_word`, `dictionary_sense`,
`dictionary_form`, `media`; made by Flyway when the backend starts). The pipeline on your Mac imports into it once and
exports `dictionary.sqlite` from it, see [Dictionary pipeline](../features/doc-6%20-%20Dictionary-pipeline.md).

**`DATABASE_URL` for the pipeline.** Postgres has no public port. Compose publishes it only on the server's loopback
(`127.0.0.1:5432`), so open an SSH tunnel from your Mac and keep it open while the pipeline runs:

```bash
ssh -N -L 55432:127.0.0.1:5432 vps_sieutoc      # change 5432 if you set POSTGRES_PORT
```

Then in `pipeline/.env` (git-ignored):

```
DATABASE_URL=postgresql://app:YOUR_DB_PASSWORD@localhost:55432/english_translation
```

`app` and `english_translation` are the user and database in `docker-compose.prod.yml`; the password is `DB_PASSWORD`
from `backend/.env`. Start the backend once before the first import (it creates the tables). Then, in `pipeline/`:

```bash
uv run pipeline import-to-postgres   # one time; safe to run again
uv run pipeline export               # read-only; builds data/out/dictionary.sqlite and data/out/media/
```

**The media folder (TASK-49.4).** Media files (photos and short clips) are not stored in Postgres. Postgres keeps only a row per
file (`media`: file name, photo or clip, size, Pixabay author and page link). The files live on disk in
`/var/lib/saynata/media` on the server. `docker-compose.prod.yml` mounts that folder into the backend container as
`/app/media` (`MEDIA_DIR`), and Caddy serves it read-only at `https://api.loffy.me/media/<file>`.

1. Make the folder once. The backend runs as user id 10001 inside the container, so that user must own it:

```bash
sudo mkdir -p /var/lib/saynata/media
sudo chown 10001:10001 /var/lib/saynata/media
sudo chmod 755 /var/lib/saynata/media     # Caddy only needs to read it
```

2. Add this inside the existing `api.loffy.me` block of `/etc/caddy/Caddyfile`, **before** `reverse_proxy`.
   `handle_path` removes `/media` from the path, so `root` is the folder itself. File names contain a hash of the
   content (for example `run_1_3fa9c2d1.mp4`), so a name never changes its file and the cache can be one year:

```caddyfile
api.loffy.me {
    handle_path /media/* {
        root * /var/lib/saynata/media
        header Cache-Control "public, max-age=31536000, immutable"
        file_server
    }
    reverse_proxy 127.0.0.1:8081
}
```

3. `sudo systemctl reload caddy`, then redeploy the backend (`./scripts/deploy.sh`). The image now has ffmpeg
   (about 100 MB more). Check: `curl -I https://api.loffy.me/media/<a file name>` shows `Cache-Control: public, max-age=31536000, immutable`.

**Uploads from the admin (TASK-79).** The admin web app can upload a picture or clip for a sense (`POST /api/admin/words/{id}/senses/{n}/media/upload`, doc-15). The file is converted on the server with ffmpeg (already in the image) to a small WebP or MP4, so the stored file is small. The limits are two optional variables of the backend:

| Variable | Default | Meaning |
|---|---|---|
| `UPLOAD_MAX_FILE_SIZE` | `25MB` | The biggest file the backend accepts (`spring.servlet.multipart.max-file-size`). A bigger one gets 413 "That file is too big." |
| `UPLOAD_MAX_REQUEST_SIZE` | `26MB` | The biggest whole request (`spring.servlet.multipart.max-request-size`). Keep it a little above the file limit. |

**Does Caddy need a change? No.** Caddy's `reverse_proxy` has no request-body size limit by default (the `request_body { max_size }` option exists, but it is off). So a 25 MB upload passes to the backend and the backend answers 413 if it is too big. Only add `request_body { max_size 30MB }` to the `api.loffy.me` block if you want Caddy to refuse big bodies earlier; keep it above `UPLOAD_MAX_REQUEST_SIZE`. The new `.webp`/`.mp4` file is written to `MEDIA_DIR` like any other, so no new folder or permission is needed.

**What puts files there.**

- *The admin's own files (TASK-79).* An upload is converted and saved in the same folder. After you upload, copy the folder to your Mac (`rsync -av vps_sieutoc:/var/lib/saynata/media/ data/media/`) before `uv run pipeline export`, so the new file reaches the next app build.
- *New backend words.* With `PIXABAY_API_KEY` set, the backend saves a photo (320 px WebP) or a 3 s muted clip (MP4) for the
  first 3 senses of a word it just created, after the answer was sent. The next lookup returns the address in
  `imageUrl`. Pixabay is asked once per sense and at most 40 times a minute. Without a key nothing is fetched.
- *The bundled 2,750 words (pipeline media pass).* Run the pass on your Mac (it uses your Pixabay key and takes a long time;
  start with `--words run walk` to try it), then copy the files and the rows to the server:

```bash
cd pipeline
uv run pipeline media --words run walk          # or without --words for the full pass (many Pixabay requests)
uv run pipeline export-local                    # builds data/out/dictionary.sqlite from the media pass (image_url per sense)
uv run pipeline link-media --media-dir data/media   # media rows + links into the server Postgres, through the SSH tunnel
rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/
```

  `link-media` only adds `media` rows (file name, size, photographer, Pixabay page) and sets `media_id` on senses that have none.
  It never changes text and never replaces media a sense already has, so admin edits are safe (`import-to-postgres` skips
  words that are already there, so it cannot do this job). The files can be copied before or after the rows; an address works as soon as both exist. File names from the pipeline
  are like `run_1.mp4` (no hash). Do not replace a file under the same name later, because the browser and the
  app may keep the old one for a year; use a new name.
- *AI pictures for `needs_ai` senses (TASK-53).* Made on your Mac by OpenAI, then copied and linked (details below).
- `import-to-postgres` reads sizes from `--media-dir` (default: `media/` next to the sqlite file) and `export` copies
  the files named in Postgres from `--media-dir` (default `data/media`) into `data/out/media/`.

**AI pictures (TASK-53).** `uv run pipeline media-generate` makes a picture for every bundled sense marked `needs_ai`
(doc-15, doc-6 section 13). It needs two values in `pipeline/.env`:

| Variable | Meaning |
|---|---|
| `OPENAI_API_KEY` | Your OpenAI API key (https://platform.openai.com/api-keys). It is billed per picture (gpt-image-1-mini, low quality: about $0.0035), separate from a ChatGPT subscription. Also set a monthly budget in the OpenAI dashboard (Settings, Limits) as a second guard next to `--max-cost`. Never commit it. |
| `MEDIA_BASE_URL` | Optional, default `https://api.loffy.me/media`. `--link` asks this address for each new file (`HEAD`) and links only files that answer 200, so copy the files first. |

```bash
cd pipeline
uv run pipeline media-generate --limit 20       # trial, about $0.08; open data/out/media-generate.html
uv run pipeline media-generate                  # the rest, stops at the $5 cap (all 743: about $2.80)
rsync -av data/media/ vps_sieutoc:/var/lib/saynata/media/
uv run pipeline media-generate --link
uv run pipeline export
```

The backend needs no change and no key for this: the files are served by Caddy like the Pixabay ones.

**Provider terms (commercial use), checked 2026-10-16.** Saynata is a public app, so every picture source must allow
use in it:

| Source | Terms | What it means for Saynata |
|---|---|---|
| Pixabay | [Pixabay Content License](https://pixabay.com/service/license-summary/) | Free for commercial use, no credit needed; resized copies may be stored and shown. Not allowed: selling the files as they are, or showing people in a bad light. |
| OpenAI (API, `gpt-image-1-mini`) | [Services Agreement](https://openai.com/policies/services-agreement/), [Usage policies](https://openai.com/policies/usage-policies/) | The customer owns the Output, and OpenAI assigns any rights it has in it, so the pictures may be used in the app and the admin. API data is not used to train OpenAI models unless you opt in. You are responsible for how the pictures are used, and the usage policies apply (the prompt rule "no realistic faces, no text" also keeps us away from people and brands). AI pictures may not get copyright protection in some countries, so others could copy them. Not legal advice. |
| Admin uploads (TASK-79) | Whoever made the file | Upload only pictures you made or may use. A picture made in ChatGPT falls under OpenAI's consumer [Terms of Use](https://openai.com/policies/terms-of-use/), which also give the user ownership of the Output. |
| OpenAI ChatGPT login with the Codex CLI (TASK-91) | OpenAI [Terms of Use](https://openai.com/policies/terms-of-use/), [Codex authentication](https://learn.chatgpt.com/docs/auth) | The pictures belong to the user under the consumer terms (like the uploads above). **Known risk, accepted by the developer on 2026-10-18:** the consumer terms forbid extracting Output "automatically or programmatically", and OpenAI's docs say an API key is "the recommended default for automation". The server draws on request, one at a time, at most 30 a day, but the account could still be limited. If that happens, the other choice is the OpenAI image API (about $0.004 a picture). |

## 10. Resend setup (sign-in emails)

Saynata sends one kind of email: the 6-digit sign-in code. We send it with [Resend](https://resend.com) on your own
domain. Resend is simpler than AWS SES: you only add DNS records and make one API key. The free plan is enough
for now. **Merging does not need any of this to start**: with no `RESEND_API_KEY` the backend runs and each code is
only written to its log (a line that starts with `[fake email]`; a warning at start-up says so). You can read a code
with `docker compose -f docker-compose.prod.yml logs backend | grep "fake email"`.

Which sender is used (decided at start-up, and logged): `RESEND_API_KEY` and `EMAIL_FROM` set, then **Resend**;
else a from address set (`EMAIL_FROM`, or the older `SES_FROM_EMAIL`), then **AWS SES** (section 11); else the log.

Below the domain is `loffy.me`. Use yours if it is different.

**1. Sign up and add the domain**

1. Sign up at https://resend.com (free).
2. Open **Domains**, then **Add Domain**, type `loffy.me`. Pick a region near your server.
3. Resend shows DNS records. Their exact names and values are on that page, so copy them from there. You should see:
   - **DKIM**: a TXT record at `resend._domainkey` (a long value that starts with `p=`). It proves the email
     really comes from your domain.
   - **SPF**: a TXT record at `send` (so `send.loffy.me`), value like `v=spf1 include:amazonses.com ~all`.
   - **MX**: an MX record at `send` (so `send.loffy.me`), priority 10, pointing to a Resend mail server.

**2. Add the records in Cloudflare**

1. Cloudflare, your domain, **DNS**, **Records**, **Add record**. Add each record from Resend: the same type, name
   and value.
2. Set **Proxy status** to **DNS only** (grey cloud) for every one of them. Email records cannot be proxied.
3. Cloudflare adds the domain name for you. Type `resend._domainkey` and `send`, not the full names.
4. These records do not clash with your SES records. They use `resend._domainkey` and `send.loffy.me`, while SES
   uses `loffy.me`, `mail.loffy.me` and its own DKIM CNAMEs. Leave the SES records as they are. Keep your existing
   `_dmarc` record (`p=quarantine`): it covers Resend mail too.

**3. Wait for Verified**

Back in Resend, click **Verify DNS Records**. The domain changes to **Verified**, usually in a few minutes (it
can take longer). If a record stays red, check the name and value, and that the cloud is grey.

**4. Make an API key**

1. Resend, **API Keys**, **Create API Key**.
2. Name: `saynata-backend`. Permission: **Sending access**. Domain: `loffy.me`.
3. Copy the key now (it starts with `re_`). Resend shows it once.

**5. Put the values in `.env` on the server**

```bash
nano /var/www/saynata/backend/.env
```

```bash
RESEND_API_KEY=re_...
EMAIL_FROM=login@loffy.me
```

`EMAIL_FROM` must be on the verified domain. `.env` is git-ignored: do not commit it. Then deploy:
`cd /var/www/saynata/backend && ./scripts/deploy.sh` (or merge to main, which deploys by itself, see section 7).
The log must now say `Sign-in codes are sent with Resend from login@loffy.me.` and no longer `No email service is set`.

**How to test**

1. Ask for a code (use your own address):

```bash
curl -i -X POST https://api.loffy.me/api/auth/email-code \
  -H 'Content-Type: application/json' -d '{"email":"you@gmail.com"}'
# HTTP/2 204. The answer is 204 whether the send worked or not.
```

2. The email "Your Saynata sign-in code" should arrive in a minute. Check spam if not.
3. In Gmail open the mail, then the three dots, **Show original**. You want `DKIM: PASS`, `SPF: PASS` and
   `DMARC: PASS`. If one fails, fix the DNS record from step 2.
4. Sign in with the code:

```bash
curl -s -X POST https://api.loffy.me/api/auth/verify \
  -H 'Content-Type: application/json' -d '{"email":"you@gmail.com","code":"123456"}'
# {"accessToken":"...","refreshToken":"...","expiresIn":3600,"user":{"id":1,"email":"you@gmail.com"}}
```

5. No email, and the answer was still 204? The backend never tells the caller about a send error (so nobody can
   learn which emails exist). Read the log:
   `docker compose -f docker-compose.prod.yml logs backend | grep "Resend"`. The log shows the HTTP status, never
   the key or the code. Common causes: `401` (wrong or missing API key), `403` (the domain is not verified yet, or
   `EMAIL_FROM` is not on the verified domain), `429` (too many sends; the backend tries once more), `5xx` (Resend
   problem; the backend tries once more). Resend's **Logs** page also lists every email.

Resend free plan limits change, so check the pricing page. Our code endpoint is rate limited (5 per hour per IP, 3
per 15 minutes per email), which also limits how much mail strangers can make us send.

## 11. AWS SES setup (sign-in emails, optional, for later)

The backend uses Resend (section 10) when `RESEND_API_KEY` is set. This section is the other choice. SES is used only
when `RESEND_API_KEY` is empty and `EMAIL_FROM` (or `SES_FROM_EMAIL`) is set. We did not choose it for now because
it needs a sandbox exit with a website review, an IAM user and a region setup.

Saynata sends one kind of email: the 6-digit sign-in code. It goes through Amazon SES on your own domain, so it
reaches the inbox and not the spam folder. **Merging the accounts code does not need any of this to start**: with
no `SES_FROM_EMAIL` the backend runs, and each code is only written to its log (a line that starts with
`[fake email]`; a warning at start-up says so). Sign-in emails really go out only after you finish this section.
In the meantime, you can read a code on the server with
`docker compose -f docker-compose.prod.yml logs backend | grep "fake email"` to test sign-in with curl.

Below, `yourdomain.com` is your domain and `REGION` is the AWS region you pick (for example `ap-southeast-1` or
`us-east-1`). SES is separate in every region: the identity, the sandbox status, the IAM policy and `AWS_REGION`
must all use the same one. Cost is small: about 0.10 USD per 1,000 emails (check the SES pricing page).

**1. Verify your domain (once)**

1. AWS Console, region `REGION`, open **Amazon SES**, then **Configured identities**, then **Create identity**.
2. Choose **Domain** and type `yourdomain.com` (or a subdomain such as `mail.yourdomain.com`; a subdomain keeps this
   mail apart from your normal email). Keep **Easy DKIM** with **RSA_2048_BIT**. Create.
3. SES shows three **CNAME** records (`xxxx._domainkey.yourdomain.com`). This is **DKIM**: it proves the email really
   comes from your domain. Add all three at your DNS provider. Copy the name and the value exactly. Some providers add
   the domain name for you, so do not type it twice.
4. Wait. The identity changes to **Verified** and DKIM to **Successful**, usually in a few minutes, up to 72 hours.

**2. SPF and DMARC (so Gmail and others trust the mail)**

SPF says which servers may send for your domain. To make SPF line up with your domain, set a **custom MAIL FROM**
domain. It is optional, but it gives you SPF and DMARC alignment:

1. In the identity, open **Custom MAIL FROM domain** and choose a subdomain, for example `bounce.yourdomain.com`.
2. Add the two DNS records SES shows: an **MX** record (`10 feedback-smtp.REGION.amazonses.com`) and a **TXT** record
   for the subdomain: `v=spf1 include:amazonses.com ~all`.
3. Add a **DMARC** TXT record at `_dmarc.yourdomain.com`. Start gently, and read the reports before you tighten it:
   `v=DMARC1; p=none; rua=mailto:you@yourdomain.com`. After a few weeks of good mail you can change `p=none` to
   `p=quarantine`.

If your domain already has an SPF record (`v=spf1 ...`), do not add a second one on the same name. Add
`include:amazonses.com` to the existing one. (The records above are on subdomains, so this only matters if you put
SES mail on the main domain.)

**3. Leave the SES sandbox**

A new SES account is in the **sandbox**: it may send only to addresses you verified yourself (and about 200 emails a
day). Real learners need production access.

1. SES, **Account dashboard**, **Request production access**.
2. Mail type: **Transactional**. Website: your site or the App Store page. Describe the use: "Saynata is an English
   learning app. We send one email per sign-in: a 6-digit code the user asked for. No marketing. Volume is low (under
   1,000 a day). Codes expire in 10 minutes, and the endpoint is rate limited. We watch bounces and complaints."
3. AWS usually answers within a day. Until then, test with your own verified address (see "How to test").

**4. An IAM user that can only send email**

Never put your root keys or an admin key on the server.

1. IAM, **Policies**, **Create policy**, JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ses:SendEmail",
      "Resource": "arn:aws:ses:REGION:YOUR_ACCOUNT_ID:identity/yourdomain.com"
    }
  ]
}
```

   (The backend uses the SES v2 `SendEmail` call, which needs this single permission. If SES shows another identity
   in the error, add it to `Resource`.)
2. IAM, **Users**, **Create user** (for example `saynata-ses`), attach only that policy. No console access.
3. Open the user, **Security credentials**, **Create access key**, use case **Application running outside AWS**.
   Copy the access key id and the secret now: AWS shows the secret once.

**5. Put the values in `backend/.env` on the server**

```bash
USER_JWT_SECRET=...            # openssl rand -base64 48
SES_FROM_EMAIL=login@yourdomain.com
AWS_REGION=REGION
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

`SES_FROM_EMAIL` must be on the verified domain (or be a verified address). Then run `./scripts/deploy.sh` (or
`docker compose -f docker-compose.prod.yml up -d`). The log must now say `Sign-in codes are sent with AWS SES from
...` and no longer the warning `SES_FROM_EMAIL is not set`. `.env` is git-ignored: do not commit it.

**How to test**

1. In the sandbox, first verify your own address: SES, **Create identity**, **Email address**, click the link in the
   email AWS sends. Then ask for a code for that address.
2. Ask for a code:

```bash
curl -i -X POST https://api.yourdomain.com/api/auth/email-code \
  -H 'Content-Type: application/json' -d '{"email":"you@example.com"}'
# HTTP/2 204. The answer is the same whether the email exists or not.
```

3. The email should arrive in a minute ("Your Saynata sign-in code"). Then sign in with it:

```bash
curl -s -X POST https://api.yourdomain.com/api/auth/verify \
  -H 'Content-Type: application/json' -d '{"email":"you@example.com","code":"123456"}'
# {"accessToken":"...","refreshToken":"...","expiresIn":3600,"user":{"id":1,"email":"you@example.com"}}
```

4. Use the token: `curl -H "Authorization: Bearer <accessToken>" https://api.yourdomain.com/api/me`.
5. In Gmail open the mail, then **Show original**: you want `DKIM: PASS`, `SPF: PASS` and `DMARC: PASS`. If one fails,
   fix the DNS record from step 1 or 2.
6. No email, and the answer was still 204? The backend never tells the caller about a send error (so nobody can learn
   which emails exist). Read the log: `docker compose -f docker-compose.prod.yml logs backend | grep "sign-in code"`.
   Common causes: `Email address is not verified` (sandbox, or `SES_FROM_EMAIL` is not on the verified domain),
   `not authorized to perform ses:SendEmail` (wrong policy or region), `security token invalid` (wrong keys).
   You can also use the SES mailbox simulator address `success@simulator.amazonses.com` in the sandbox.

**Keep your sender reputation good.** SES pauses accounts with many bounces or spam complaints. The code endpoint is
rate limited (5 per hour per IP, 3 per 15 minutes per email), which also limits how much stranger mail it can send.
Watch **Reputation metrics** in SES now and then. Later you can add a configuration set with bounce notifications.

## 12. AI pictures with Codex (TASK-91)

The admin web app can draw an AI picture for one sense: Sparkles button, edit the prompt, OK. The backend then runs the **Codex CLI** inside its container and links the picture to the sense (doc-15, "AI picture from the admin"). Codex uses **your ChatGPT login** (`auth.json`), so a picture has no per-picture cost and takes about 1 minute. It is off until you finish these steps (`AI_PICTURES_ENABLED=false` by default; then the admin gets "AI pictures are not set up on this server").

**Know the risks first** (the developer chose this on 2026-10-18; the other choice was the OpenAI image API, about $0.004 a picture):

- `auth.json` is like a **password**. Anyone who has it can use your ChatGPT account. Never put it in git, in chat or in a backup you do not control. It is `chmod 600` and owned by user 10001 on the server.
- Your ChatGPT plan has its own limits. When it is used up, the admin shows "The ChatGPT plan limit is reached". The backend also stops at `AI_PICTURES_PER_DAY` (default 30) pictures a day.
- OpenAI's consumer terms forbid taking Output "automatically or programmatically", and their docs recommend an API key for automation ([source](https://learn.chatgpt.com/docs/auth)). The account could be limited. See the provider table in section 9.
- Codex starts with an empty environment (only `PATH`, `HOME`, `CODEX_HOME`, `LANG`), so it never sees `CLAUDE_API_KEY` or `DB_PASSWORD`.

**Steps** (you do these; the Docker image already has Codex, `CODEX_VERSION` in the [Dockerfile](../../../backend/Dockerfile)):

1. On your Mac, sign in with ChatGPT and check it:
   ```bash
   codex login
   codex login status        # Logged in using ChatGPT
   ```
2. On the server, make the folder for the login. The container user has the fixed id 10001, and the folder must be **writable** (Codex refreshes the tokens inside `auth.json`):
   ```bash
   sudo mkdir -p /var/lib/saynata/codex
   sudo chown 10001:10001 /var/lib/saynata/codex
   ```
3. Copy `auth.json` from your Mac:
   ```bash
   scp ~/.codex/auth.json vps_sieutoc:~/auth.json
   ssh vps_sieutoc 'sudo mv ~/auth.json /var/lib/saynata/codex/auth.json && sudo chown 10001:10001 /var/lib/saynata/codex/auth.json && sudo chmod 600 /var/lib/saynata/codex/auth.json'
   ```
4. In `~/saynata/backend/.env` on the server set `AI_PICTURES_ENABLED=true`. Then deploy again (`./scripts/deploy.sh`, or push to main). The first build downloads Codex from GitHub.
5. Test that Codex is signed in inside the container, then draw one picture from the admin:
   ```bash
   docker compose -f docker-compose.prod.yml exec backend codex login status    # Logged in using ChatGPT
   ```
   In the admin: open a word, press the Sparkles button on a sense, press OK, wait about a minute. The new picture shows with the caption "AI image (codex)" and the sense is in "Media to review".

**If something goes wrong**

| Problem | Fix |
|---|---|
| Admin says "AI pictures are not set up" | `AI_PICTURES_ENABLED` is not `true` in `.env`, or the backend was not redeployed. |
| "Codex is not signed in on the server" | `auth.json` is missing, not owned by 10001, or the login ended (you logged out on the Mac, or the token was revoked). Copy it again (step 3). The other way is a login made on the server itself: `docker compose -f docker-compose.prod.yml exec backend codex login --device-auth`, then open the address it prints and enter the code. That keeps a separate login for the server. |
| Drawing fails with a sandbox error (for example `bwrap` or a namespace message in `docker compose logs backend`) | The `read-only` sandbox may not start inside Docker. Set `AI_CODEX_SANDBOX=workspace-write` in `.env` and redeploy. **Never** use a "danger" or "bypass" flag. |
| "The ChatGPT plan limit is reached" | Wait. The plan limit comes back by itself. |
| Job stays "drawing" for a long time | After `AI_CODEX_TIMEOUT` (10 minutes) Codex is stopped and the job fails. A restart also marks it failed. |

To turn it off again: `AI_PICTURES_ENABLED=false` and redeploy. To remove the login: `sudo rm /var/lib/saynata/codex/auth.json`.


## 13. Sign in with Google, Apple and a link (TASK-92)

The app can sign in four ways: the email code (sections 10 and 11), an emailed **link**, **Google** and **Apple**. How it works: doc-17, "Other ways to sign in". The link works as soon as email works. Google and Apple need these steps by hand.

**Google (free):**

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), pick or make a project. Set up the **OAuth consent screen** (External, app name Saynata, your email, scopes `openid` and `email`). While it is in "Testing", add your Gmail as a test user.
2. **Create credentials, OAuth client ID, type iOS**. Bundle ID: `com.luke.EnglishTranslation`. Copy the client ID (`123-abc.apps.googleusercontent.com`). An iOS client has no secret.
3. Server: `GOOGLE_CLIENT_IDS=<that client ID>` in `backend/.env`, then `./scripts/deploy.sh`.
4. App: put the same ID in `EnglishTranslation/EnglishTranslation/Info.plist`, key `GoogleIOSClientID`, and build again. Nothing else: the app uses the system web sign-in sheet, no Google SDK.

**Apple (needs the paid Apple Developer account):**

1. developer.apple.com, Certificates, Identifiers & Profiles, Identifiers, `com.luke.EnglishTranslation`: turn on **Sign in with Apple**. The app's `EnglishTranslation.entitlements` already asks for it.
2. Install with `PAID_ACCOUNT=1 scripts/install-on-phone.sh`. A free Apple ID cannot sign this capability, so on a free build the Apple button shows "Sign in with Apple did not work".
3. Nothing on the server: `APPLE_CLIENT_IDS` already has the bundle IDs.

**The emailed link:** the email has `PUBLIC_BASE_URL/auth/link?token=...`. That page (served by the backend; Caddy already forwards every path) opens `saynata://sign-in?token=...`, so it works with a free Apple ID. Mail apps do not open a custom scheme straight from an email, so the page in between is needed. Later, with the paid account, it can become a **universal link** (iOS opens the app directly, no page and no "Open in Saynata?" question):

1. Set `APPLE_APP_IDS=<TEAMID>.com.luke.EnglishTranslation` on the server. Check `curl https://api.loffy.me/.well-known/apple-app-site-association`.
2. Add the **Associated Domains** capability to the app with `applinks:api.loffy.me` (the host of `PUBLIC_BASE_URL`). The app already reads `https://.../auth/link?token=...`.

**When the domain moves** (for example to the new domain you plan to buy; check its exact spelling first), change these:

| Where | What |
|---|---|
| `backend/.env` | `PUBLIC_BASE_URL=https://api.<new domain>` (link emails), `MEDIA_BASE_URL`, `EMAIL_FROM` (verify the new domain in Resend first) |
| Caddy | a site block for the new host (section 5) |
| App `Info.plist` | `BackendBaseURL` |
| Associated Domains (paid only) | `applinks:api.<new domain>`; `APPLE_APP_IDS` stays the same |
| Google Cloud | nothing for the iOS client. Add the domain to the consent screen's authorized domains only if a web client comes later. |
| Admin web app | `VITE_API_URL` at build time, its Caddy block (section 8), and `app.admin.allowed-origins` in `application.properties` (CORS) |
| Browser extension | the backend address in `extension/src/shared/config.ts` |

Links already sent keep the old address, so keep the old domain working for 15 minutes (the link lifetime), or users ask for a new link.

**How to test** (with your own email):

```bash
# 1. Ask for a link. codeChallenge is the SHA-256 (hex) of a secret you keep.
SECRET=my-test-secret-$(date +%s)
CHALLENGE=$(printf %s "$SECRET" | shasum -a 256 | cut -d' ' -f1)
curl -i -X POST https://api.loffy.me/api/auth/email-link -H 'Content-Type: application/json' \
  -d "{\"email\":\"you@gmail.com\",\"codeChallenge\":\"$CHALLENGE\"}"
# HTTP/2 204. Copy the token from the link in the email.
curl -s -X POST https://api.loffy.me/api/auth/email-link/verify -H 'Content-Type: application/json' \
  -d "{\"token\":\"<token>\",\"codeVerifier\":\"$SECRET\"}"
# {"accessToken":"...","refreshToken":"...","expiresIn":3600,"user":{...}}
# 2. Google before GOOGLE_CLIENT_IDS is set: {"code":"provider_not_configured",...}; after: {"code":"invalid_id_token",...}
curl -s -X POST https://api.loffy.me/api/auth/google -H 'Content-Type: application/json' -d '{"idToken":"x","nonce":"y"}'
```

## Troubleshooting

| Problem | Fix |
|---|---|
| The backend stops at start with `Set CLAUDE_API_KEY, or CLAUDE_AUTH_TOKEN for a gateway` | `.env` is missing or both values are empty. Run compose from the `backend/` folder. |
| Every new explanation fails, the log says `401 invalid x-api-key` | The Claude key is wrong or was revoked. Put a new one in `.env` (or switch to a gateway), then `docker compose -f docker-compose.prod.yml up -d backend`. |
| Backend stays "unhealthy" | Read `logs backend`. Often a wrong `DB_PASSWORD` (the volume keeps the old one) or too little RAM. |
| Admin page is blank after a reload | The Caddy block is missing `try_files {path} /index.html`. |
| App cannot connect | Test the URL with `curl` from your laptop. Check DNS, the firewall, and that the URL starts with `https://`. |
| Deploy fails with `Not possible to fast-forward` | Someone changed files in `~/saynata` on the server. Run `git status` there, then `git checkout -- .` to drop the changes. |
| Deploy fails with `Host key verification failed` | `VPS_KNOWN_HOSTS` is wrong or the server was rebuilt. Run `ssh-keyscan` again and update the secret. |
| Pipeline cannot connect to `DATABASE_URL` | Is the SSH tunnel open? Does the port in the URL match `-L`? Is the compose file the new one (`docker compose -f docker-compose.prod.yml up -d` recreates Postgres with the loopback port; the data stays in the volume)? |
| Sign-in works but no email arrives | `RESEND_API_KEY` or `EMAIL_FROM` is empty (the code is only in the log), the Resend domain is not Verified, or the key is wrong. See section 10, "How to test". With SES: sandbox or unverified address, see section 11. |
| App says it needs a sign-in for a new sentence (401 `sign_in_required`) | The app has no sign-in screen yet (it waits for a mockup). Set `REQUIRE_SIGN_IN=false` in `.env` and redeploy until the app with sign-in is released. Cached sentences and bundled words work either way. |
| New explanations stop with 503 `daily_limit_reached` | Today's Claude spend reached `CLAUDE_DAILY_BUDGET_USD` (UTC day). See the spend: `GET /api/admin/usage?days=1` with the admin cookie. Raise the cap in `.env` if you want. |
| Prefill run does nothing | `GET /api/admin/prefill` shows `running`, the counts by status and the last runs (`stoppedBecause`: `queue-empty`, `cap`, `limit`, `failures`). Items are added by the pipeline collector (TASK-83.2) or by an admin; `PREFILL_CRON` empty means no night run. A run that stops with `cap` is normal: the next run continues. |
| Deploy hangs (the build log stops, the server is idle) | Seen on 2026-10-17: dockerd logged `session healthcheck failed fatally` and the BuildKit build waited forever. `deploy.sh` now stops a build after 6 minutes and tries once more, and the job stops after 20 minutes. If a run still hangs: 1. Cancel it (Actions tab, the run, **Cancel workflow**). 2. On the server, look for leftover build processes: `pgrep -af 'docker compose\|buildx'`, and stop them: `pkill -f 'buildx.*bake'; pkill -f 'docker compose -f docker-compose.prod.yml'`. 3. If builds still hang, restart Docker: `sudo systemctl restart docker` (the backend and Postgres restart too, a short outage). 4. Re-run the workflow (**Re-run all jobs**). The old backend keeps running until a new build succeeds. |
| Build is killed on a 1 GB VPS | Add swap: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`. |
| AI picture fails (TASK-91) | See section 12, "If something goes wrong": the three usual causes are a missing `AI_PICTURES_ENABLED=true`, an `auth.json` that is not owned by 10001 or has ended, and a sandbox that needs `AI_CODEX_SANDBOX=workspace-write`. |
