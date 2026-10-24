#!/usr/bin/env bash
# Nightly backup (TASK-107): dump Postgres, then send the dump and the media folder to Cloudflare R2 with restic.
# Run on the VPS (a systemd timer does it; guide: doc-11, section 16):  ~/saynata/backend/scripts/backup.sh
#
# Steps: 1. pg_dump -Fc from the Docker Postgres into BACKUP_DIR/db, keep the last 3 dumps
#        2. restic backup of the newest dump + MEDIA_HOST_DIR to the R2 repository
#        3. restic forget --prune: keep 7 daily, 4 weekly, 6 monthly snapshots
#        4. ping HEALTHCHECK_URL (start, success or fail) when it is set
set -euo pipefail

cd "$(dirname "$0")/.."    # the backend/ folder, where .env and the compose file live

log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }

# Read one KEY from the environment first, then from .env. We do not "source" .env: its values may hold
# characters that the shell would read as code.
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

HEALTHCHECK_URL="$(env_value HEALTHCHECK_URL)"
BACKUP_DIR="$(env_value BACKUP_DIR /var/backups/saynata)"
MEDIA_HOST_DIR="$(env_value MEDIA_HOST_DIR /var/lib/saynata/media)"
KEEP_LOCAL_DUMPS=3

# The R2 keys have their own names in .env (R2_*) because AWS_ACCESS_KEY_ID there is already used for AWS SES.
# restic reads the standard AWS_* names, so we set them for this script only.
RESTIC_REPOSITORY="$(env_value RESTIC_REPOSITORY)"
RESTIC_PASSWORD="$(env_value RESTIC_PASSWORD)"
AWS_ACCESS_KEY_ID="$(env_value R2_ACCESS_KEY_ID)"
AWS_SECRET_ACCESS_KEY="$(env_value R2_SECRET_ACCESS_KEY)"
export RESTIC_REPOSITORY RESTIC_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
export AWS_DEFAULT_REGION=auto   # R2 uses the region name "auto"

ping_health() {   # ping_health "" | /start | /fail
  [ -n "$HEALTHCHECK_URL" ] || return 0
  curl -fsS -m 10 --retry 3 -o /dev/null "${HEALTHCHECK_URL}${1:-}" || log "Could not reach HEALTHCHECK_URL (ignored)."
}

# Any failure ends the script (set -e); this trap then tells the health check.
on_exit() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    log "BACKUP FAILED (exit code $status)."
    ping_health /fail
  fi
}
trap on_exit EXIT

# --- Check the setup before we start -------------------------------------------------------------------
for tool in docker restic curl; do
  command -v "$tool" >/dev/null || { log "Missing tool: $tool. See doc-11, section 16."; exit 1; }
done
for key in RESTIC_REPOSITORY RESTIC_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  [ -n "${!key}" ] || { log "$key is empty. Set RESTIC_REPOSITORY, RESTIC_PASSWORD, R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY in backend/.env."; exit 1; }
done
[ -d "$MEDIA_HOST_DIR" ] || { log "Media folder not found: $MEDIA_HOST_DIR"; exit 1; }

ping_health /start
compose="docker compose -f docker-compose.prod.yml"
dump_dir="$BACKUP_DIR/db"
mkdir -p "$dump_dir"

# --- 1. Database dump ----------------------------------------------------------------------------------
stamp="$(date -u +%Y%m%d-%H%M%S)"
dump_file="$dump_dir/saynata-$stamp.dump"
log "Dumping Postgres to $dump_file"
# The container has POSTGRES_USER and POSTGRES_DB, so we read them there. -T: no terminal (needed under systemd).
# We write to a .part file first, so a broken dump is never taken for a good one.
# shellcheck disable=SC2016  # the single quotes are on purpose: the variables must expand inside the container
if ! $compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$dump_file.part"; then
  rm -f "$dump_file.part"
  log "pg_dump failed."
  exit 1
fi
[ -s "$dump_file.part" ] || { rm -f "$dump_file.part"; log "The dump is empty."; exit 1; }
mv "$dump_file.part" "$dump_file"
log "Dump size: $(du -h "$dump_file" | cut -f1)"

# Keep the last N dumps on the VPS for a fast restore.
# shellcheck disable=SC2012  # file names are ours (saynata-DATE.dump), so ls is safe here
ls -1t "$dump_dir"/saynata-*.dump | tail -n +"$((KEEP_LOCAL_DUMPS + 1))" | while read -r old; do
  log "Removing old local dump $old"
  rm -f "$old"
done

# restic matches a new snapshot to the previous one by its paths, so the dump needs the same path every night.
# A hard link gives it a fixed name without copying the file.
latest_dir="$BACKUP_DIR/latest"
mkdir -p "$latest_dir"
ln -f "$dump_file" "$latest_dir/saynata.dump"

# --- 2. Send to R2 -------------------------------------------------------------------------------------
if ! restic cat config >/dev/null 2>&1; then
  log "Cannot open the restic repository $RESTIC_REPOSITORY."
  log "First time? Run 'restic init' once (doc-11, section 16, step 5). Otherwise check the keys and the password."
  exit 1
fi

log "restic backup: dump + $MEDIA_HOST_DIR"
restic backup --tag saynata --host saynata-vps "$latest_dir/saynata.dump" "$MEDIA_HOST_DIR"

# --- 3. Retention --------------------------------------------------------------------------------------
log "restic forget --prune (7 daily, 4 weekly, 6 monthly)"
restic forget --prune --tag saynata --host saynata-vps --keep-daily 7 --keep-weekly 4 --keep-monthly 6

# On Sundays also check the repository structure (cheap: it reads no data blocks).
if [ "$(date -u +%u)" = "7" ]; then
  log "restic check (Sunday)"
  restic check
fi

log "Backup finished."
ping_health ""
