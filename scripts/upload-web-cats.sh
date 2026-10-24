#!/usr/bin/env bash
# Copies the 7 Mochi sprite sheets the website uses from the paid CatMegaBundle on this Mac to the VPS, where Caddy
# serves them at https://saynata.com/cats/ (doc-11 section 14, doc-23). They are never in git or in the web build:
# the pack has no license to redistribute, and this repo is public. Without them the site shows its own pixel cat.
#
# Usage: scripts/upload-web-cats.sh [path-to-CatMegaBundle]   (default: ~/Downloads/CatMegaBundle)
# Env:   SSH_HOST (default vps_sieutoc), REMOTE_DIR (default /var/lib/saynata/web-cats)
set -euo pipefail

bundle="${1:-$HOME/Downloads/CatMegaBundle}"
host="${SSH_HOST:-vps_sieutoc}"
remote_dir="${REMOTE_DIR:-/var/lib/saynata/web-cats}"
sprites="$bundle/Mochi/Sprites/Classical/Individual"
# Same names as CAT_SHEETS in web/src/lib/mochi.ts.
files=(Dance.png Sleep.png Excited.png catsick1.png Box1.png Box2.png Box3.png)

if [ ! -d "$sprites" ]; then
  echo "Mochi sprites not found at: $sprites" >&2
  echo "Usage: scripts/upload-web-cats.sh [path-to-CatMegaBundle]" >&2
  exit 1
fi
for file in "${files[@]}"; do
  if [ ! -f "$sprites/$file" ]; then
    echo "Missing in the bundle: Mochi/Sprites/Classical/Individual/$file" >&2
    exit 1
  fi
done

# The folder belongs to the SSH user, so rsync needs no sudo; Caddy only reads it.
ssh "$host" "sudo mkdir -p '$remote_dir' && sudo chown \"\$(id -un)\" '$remote_dir' && chmod 755 '$remote_dir'"
(cd "$sprites" && rsync -av --chmod=F644 "${files[@]}" "$host:$remote_dir/")
echo "Done. Check: curl -I https://saynata.com/cats/Dance.png"
