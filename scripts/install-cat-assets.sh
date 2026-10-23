#!/usr/bin/env bash
# Copies the cat pictures the app uses from the paid CatMegaBundle into
# EnglishTranslation/PaidAssets/Cats/ (git-ignored: the pack has no license to redistribute, and this repo is public).
# The Xcode "Copy cat assets" build phase then puts them in the app. Without them the app still builds and shows a
# still fallback (an SF Symbol cat).
#
# Usage: scripts/install-cat-assets.sh [path-to-CatMegaBundle]   (default: ~/Downloads/CatMegaBundle)
set -euo pipefail

bundle="${1:-$HOME/Downloads/CatMegaBundle}"
root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/EnglishTranslation/PaidAssets/Cats"

if [ ! -d "$bundle" ]; then
  echo "CatMegaBundle not found at: $bundle" >&2
  echo "Usage: scripts/install-cat-assets.sh [path-to-CatMegaBundle]" >&2
  exit 1
fi

mkdir -p "$dest"

copy() { # copy <path inside the bundle> <new simple name>
  if [ ! -f "$bundle/$1" ]; then
    echo "Missing in the bundle: $1" >&2
    exit 1
  fi
  cp "$bundle/$1" "$dest/$2"
  echo "  $2  <-  $1"
}

echo "Copying cat assets to $dest"
copy "Mochi/ExampleRooms/ExampleRoom 2.png"          cat-room.png    # 512x512 room
copy "Mochi/Sprites/Classical/Individual/Sleep.png"  cat-sleep.png   # 4 frames of 32x32
copy "Mochi/Sprites/Classical/Individual/Idle.png"   cat-idle.png    # 10 frames of 32x32 (Settings, TASK-98)
copy "CatItems/CatToys/BlueBall-Sheet.png"           cat-ball.png    # 5 frames of 24x16
echo "Done. Build the app again so the files are copied into it."
