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

# --wait blocks until every service passes its health check (or fails).
if ! $compose up -d --build --wait --wait-timeout 300; then
  echo "Backend did not become healthy. Last logs:" >&2
  $compose logs --tail 80 backend >&2
  exit 1
fi

$compose ps
# Old images from earlier builds fill the disk on a small VPS.
docker image prune -f >/dev/null
echo "Deployed $(git rev-parse --short HEAD)."
