#!/usr/bin/env bash
# Build the small web image from web/dist and restart only the web container, then wait until it is healthy.
# web/dist is copied to the server by the GitHub "Deploy" workflow (job deploy-web). It is not in git.
# Run on the server from anywhere: ~/saynata/web/scripts/deploy-web.sh
set -euo pipefail

web_dir="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -f "$web_dir/dist/server.js" ]; then
  echo "web/dist/server.js is missing. The Deploy workflow copies the build there first (doc-11, section 14)." >&2
  exit 1
fi

# The compose file and backend/.env (for the ${...} values) live in backend/.
cd "$web_dir/../backend"
compose="docker compose -f docker-compose.prod.yml"

# shellcheck source=SCRIPTDIR/../../backend/scripts/disk.sh
. scripts/disk.sh
check_free_space

# This build only copies files, so it takes seconds. The limit stops a stuck BuildKit session (see deploy.sh).
timeout --kill-after=30 180 $compose build web

# --no-deps: do not touch postgres or the backend. --wait: block until the health check passes.
if ! timeout --kill-after=30 180 $compose up -d --no-deps --wait --wait-timeout 120 web; then
  echo "The web app did not become healthy. Last logs:" >&2
  $compose logs --tail 80 web >&2
  exit 1
fi

$compose ps web
prune_docker
df -h /
echo "Web app deployed."
