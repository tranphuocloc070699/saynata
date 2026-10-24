#!/usr/bin/env bash
# Rebuild and restart the backend on the VPS, then wait until it is healthy.
# Run on the server from anywhere: ~/saynata/backend/scripts/deploy.sh
# The GitHub "Deploy" workflow calls this after it has pulled the new commit.
set -euo pipefail

cd "$(dirname "$0")/.."    # the backend/ folder, where .env and the compose file live

if [ ! -f .env ]; then
  echo "backend/.env is missing. Copy .env.example and fill it in first (guide: doc-11)." >&2
  exit 1
fi

compose="docker compose -f docker-compose.prod.yml"
# A normal build takes 2-3 minutes. A stuck BuildKit session can hang forever with an idle server
# (2026-10-17, "session healthcheck failed fatally"), so every Docker step has a time limit.
build_timeout="${DEPLOY_BUILD_TIMEOUT:-360}"   # seconds per build try

# shellcheck source=SCRIPTDIR/disk.sh
. scripts/disk.sh
check_free_space

# timeout sends its signal to the whole process group, so docker compose and its buildx bake child stop together.
# --kill-after sends SIGKILL if they ignore SIGTERM.
build() {
  timeout --kill-after=30 "$build_timeout" $compose build backend
}

if ! build; then
  echo "Build failed or took longer than ${build_timeout}s. Cleaning up and trying once more." >&2
  # A build process that escaped the process group must not hold BuildKit for the second try.
  pkill -KILL -u "$(id -u)" -f 'buildx.*bake' 2>/dev/null || true
  if ! build; then
    echo "Build failed twice. Nothing was restarted; the old backend is still running." >&2
    exit 1
  fi
fi

# The image is built, so up only starts it. --wait blocks until every service passes its health check (or fails).
# Only postgres and backend: the web app has its own script (web/scripts/deploy-web.sh).
if ! timeout --kill-after=30 360 $compose up -d --wait --wait-timeout 300 postgres backend; then
  echo "Backend did not become healthy. Last logs:" >&2
  $compose logs --tail 80 backend >&2
  exit 1
fi

$compose ps
# Build cache and old images from earlier builds filled the 16 GB disk on 2026-10-24 (doc-11, Troubleshooting).
prune_docker
df -h /
echo "Deployed $(git rev-parse --short HEAD)."
