---
id: doc-11
title: Deploy to a VPS
type: guide
created_date: '2026-10-11 16:33'
updated_date: '2026-10-14 12:19'
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
| `CLAUDE_API_KEY` | Your key. Required. |
| `DB_PASSWORD` | A long random password for Postgres. Required. Make one with `openssl rand -base64 24`. |
| `BACKEND_PORT` | Port on the server (default 8081). |
| `BACKEND_BIND` | `0.0.0.0` (open) or `127.0.0.1` (only Caddy on the same server can reach it). |
| `CLAUDE_MODEL` | Optional. Default is the model in `application.properties`. |
| `ADMIN_JWT_SECRET` | Signs the admin login cookie. 32+ characters. Make one with `openssl rand -base64 48`. **The backend does not start without it**, unless `ADMIN_ENABLED=false`. |
| `ADMIN_EMAIL` | Email of the first admin (for the admin web app). Used only when no admin exists yet. |
| `ADMIN_INITIAL_PASSWORD` | Password of the first admin, 12+ characters. Used only when no admin exists yet; remove it from `.env` after the first start. |
| `ADMIN_ENABLED` | Optional. `false` turns the admin API off. Default `true`. |
| `PIXABAY_API_KEY` | Optional. Free key from https://pixabay.com/api/docs/ . With it, a new word that is not in the bundle gets a photo or clip in the background (section 9). Empty = no pictures are fetched. |
| `MEDIA_BASE_URL` | Optional. Public address of the media folder. Default `https://api.loffy.me/media`. |
| `MEDIA_HOST_DIR` | Optional. Folder on the server with the media files. Default `/var/lib/saynata/media` (section 9). |
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
[backend/scripts/deploy.sh](../../../backend/scripts/deploy.sh). That script runs `docker compose up -d --build --wait`,
so the run turns red if the backend does not become healthy, and it prints the last backend logs.
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
2. Build (on the server, or on your Mac and copy `admin/dist`). It needs Node 20 or newer:

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

To update after a `git pull`, run step 2 again and nothing else: Caddy reads the new files at once, no restart.
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

**What puts files there.**

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
- `import-to-postgres` reads sizes from `--media-dir` (default: `media/` next to the sqlite file) and `export` copies
  the files named in Postgres from `--media-dir` (default `data/media`) into `data/out/media/`.

## Troubleshooting

| Problem | Fix |
|---|---|
| Compose says `Set CLAUDE_API_KEY in .env` | `.env` is missing or the value is empty. Run compose from the `backend/` folder. |
| Backend stays "unhealthy" | Read `logs backend`. Often a wrong `DB_PASSWORD` (the volume keeps the old one) or too little RAM. |
| Admin page is blank after a reload | The Caddy block is missing `try_files {path} /index.html`. |
| App cannot connect | Test the URL with `curl` from your laptop. Check DNS, the firewall, and that the URL starts with `https://`. |
| Deploy fails with `Not possible to fast-forward` | Someone changed files in `~/saynata` on the server. Run `git status` there, then `git checkout -- .` to drop the changes. |
| Deploy fails with `Host key verification failed` | `VPS_KNOWN_HOSTS` is wrong or the server was rebuilt. Run `ssh-keyscan` again and update the secret. |
| Pipeline cannot connect to `DATABASE_URL` | Is the SSH tunnel open? Does the port in the URL match `-L`? Is the compose file the new one (`docker compose -f docker-compose.prod.yml up -d` recreates Postgres with the loopback port; the data stays in the volume)? |
| Build is killed on a 1 GB VPS | Add swap: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`. |
