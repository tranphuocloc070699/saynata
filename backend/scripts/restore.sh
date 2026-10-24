#!/usr/bin/env bash
# Restore test (TASK-107): take a restic snapshot from R2, load its database dump into a SCRATCH Postgres
# (a temporary container, never the live database) and print the row count of every table.
# Run on the VPS, once a month (doc-11, section 16):
#   ~/saynata/backend/scripts/restore.sh            # newest snapshot
#   ~/saynata/backend/scripts/restore.sh 1a2b3c4d   # a snapshot ID from: restic snapshots
#   KEEP_FILES=1 ~/saynata/backend/scripts/restore.sh   # keep the restored files in the printed folder
set -euo pipefail

cd "$(dirname "$0")/.."    # the backend/ folder, where .env lives

snapshot="${1:-latest}"
log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }

# Same .env reader as backup.sh: environment first, then .env, never "source".
env_value() {
  local key="$1" fallback="${2:-}" value
  if [ -n "${!key:-}" ]; then
    printf '%s' "${!key}"
    return
  fi
  value=""
  if [ -f .env ]; then
    value="$(grep -E "^${key}=" .env | tail -n 1 | cut -d= -f2- || true)"
    value="${value%\"}"; value="${value#\"}"
    value="${value%\'}"; value="${value#\'}"
  fi
  printf '%s' "${value:-$fallback}"
}

RESTIC_REPOSITORY="$(env_value RESTIC_REPOSITORY)"
RESTIC_PASSWORD="$(env_value RESTIC_PASSWORD)"
AWS_ACCESS_KEY_ID="$(env_value R2_ACCESS_KEY_ID)"
AWS_SECRET_ACCESS_KEY="$(env_value R2_SECRET_ACCESS_KEY)"
export RESTIC_REPOSITORY RESTIC_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
export AWS_DEFAULT_REGION=auto

for tool in docker restic; do
  command -v "$tool" >/dev/null || { log "Missing tool: $tool. See doc-11, section 16."; exit 1; }
done
for key in RESTIC_REPOSITORY RESTIC_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  [ -n "${!key}" ] || { log "$key is empty. Fill RESTIC_REPOSITORY, RESTIC_PASSWORD, R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY in backend/.env."; exit 1; }
done

work_dir="$(mktemp -d)"
container="saynata-restore-test-$$"

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  if [ "${KEEP_FILES:-0}" = "1" ]; then
    log "Restored files kept in $work_dir"
  else
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT

# --- 1. Get the dump from R2 ---------------------------------------------------------------------------
log "Snapshot to restore: $snapshot"
restic snapshots --tag saynata || true
# Only the dump is restored (the media folder can be large). Its path inside the snapshot is fixed by backup.sh.
dump_in_snapshot="$(restic ls "$snapshot" 2>/dev/null | grep -E '/latest/saynata\.dump$' | head -n 1 || true)"
[ -n "$dump_in_snapshot" ] || { log "No saynata.dump in snapshot '$snapshot'."; exit 1; }
log "Restoring $dump_in_snapshot"
restic restore "$snapshot" --target "$work_dir" --include "$dump_in_snapshot"
dump_file="$work_dir$dump_in_snapshot"
[ -s "$dump_file" ] || { log "Restored dump is missing or empty."; exit 1; }
log "Dump size: $(du -h "$dump_file" | cut -f1)"

# How many media files are in the snapshot (listing only, nothing is downloaded).
media_count="$(restic ls "$snapshot" 2>/dev/null | grep -c -E '/media/.+\.[A-Za-z0-9]+$' || true)"
log "Media files in the snapshot: $media_count"

# --- 2. Load it into a scratch Postgres ----------------------------------------------------------------
# A new container with its own empty data. It publishes no port and has a throw-away password.
# Same major version as docker-compose.prod.yml (postgres:17), because pg_restore must not be older than pg_dump.
log "Starting scratch Postgres container $container"
docker run -d --name "$container" -e POSTGRES_PASSWORD=scratch -e POSTGRES_DB=scratch postgres:17 >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U postgres -d scratch >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container" pg_isready -U postgres -d scratch >/dev/null || { log "Scratch Postgres did not start."; exit 1; }

docker cp "$dump_file" "$container:/tmp/restore.dump"
log "pg_restore into the scratch database"
# --no-owner/--no-privileges: the dump names the role "app", which does not exist in the scratch container.
docker exec "$container" pg_restore -U postgres -d scratch --no-owner --no-privileges --exit-on-error /tmp/restore.dump

# --- 3. Row counts -------------------------------------------------------------------------------------
log "Row counts (tables in schema public):"
docker exec "$container" psql -U postgres -d scratch -P pager=off -c "
  SELECT table_name AS \"table\",
         (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', table_name), false, true, '')))[1]::text::bigint AS \"rows\"
  FROM information_schema.tables
  WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  ORDER BY table_name;"

log "Restore test finished. The scratch container is removed now; the live database was not touched."
