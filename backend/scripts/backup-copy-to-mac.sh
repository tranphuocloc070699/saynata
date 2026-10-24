#!/usr/bin/env bash
# Monthly copy to the developer's Mac (TASK-107). Run it ON THE MAC, not on the VPS (doc-11, section 17):
#   brew install restic
#   export RESTIC_FROM_REPOSITORY='s3:https://<account>.r2.cloudflarestorage.com/<bucket>'
#   export RESTIC_FROM_PASSWORD='<the same RESTIC_PASSWORD as the server>'
#   export AWS_ACCESS_KEY_ID='<R2 key>' AWS_SECRET_ACCESS_KEY='<R2 secret>'   # a read-only R2 token is enough
#   export RESTIC_PASSWORD='<a password for the Mac copy>'                    # may differ from the server one
#   backend/scripts/backup-copy-to-mac.sh [target-folder]                     # default: ~/Backups/saynata-restic
#
# It runs "restic copy": only the data the Mac does not have yet is downloaded, so a month costs a few MB to a few GB,
# not the full size again. The copy is a normal restic repository: "restic -r <folder> snapshots" lists it.
set -euo pipefail

target="${1:-$HOME/Backups/saynata-restic}"
log() { echo "[$(date +%Y-%m-%dT%H:%M:%S)] $*"; }

command -v restic >/dev/null || { log "Install restic first: brew install restic"; exit 1; }
for key in RESTIC_FROM_REPOSITORY RESTIC_FROM_PASSWORD RESTIC_PASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
  [ -n "${!key:-}" ] || { log "$key is not set. See the header of this script."; exit 1; }
done
export AWS_DEFAULT_REGION=auto

mkdir -p "$target"
if [ ! -f "$target/config" ]; then
  log "Creating the Mac repository in $target"
  # --copy-chunker-params makes the new repository cut files the same way, so copies share data (dedup).
  restic init --repo "$target" --from-repo "$RESTIC_FROM_REPOSITORY" --copy-chunker-params
fi

log "Copying new snapshots from R2 to $target"
restic copy --repo "$target" --from-repo "$RESTIC_FROM_REPOSITORY" --tag saynata

log "Snapshots on the Mac now:"
restic --repo "$target" snapshots
log "Done. Keep this folder in your normal Mac backup too (Time Machine), or on an external disk."
