#!/usr/bin/env bash
# Build the Rust/WASM editor with Trunk and copy it into the Android assets.
# Usage: scripts/sync-web.sh   (WORDCRAFT_SRC = Rust repo, default below)
set -euo pipefail

SRC="${WORDCRAFT_SRC:-/root/hermes_user_files/wordcraft-src}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/web"
APP="$SRC/apps/wordcraft-web"
DIST="$SRC/dist/web"   # Trunk.toml: dist = "../../dist/web"

[ -d "$APP" ] || { echo "sync-web: not a WordCraft Rust repo: $SRC" >&2; exit 1; }
command -v trunk >/dev/null || { echo "sync-web: trunk not found" >&2; exit 1; }

echo "sync-web: trunk build --release ($APP)"
(cd "$APP" && trunk build --release)

[ -f "$DIST/index.html" ] || { echo "sync-web: $DIST/index.html missing after build" >&2; exit 1; }

# Replace the whole directory so stale hashed files do not pile up.
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a "$DIST/." "$DEST/"
echo "sync-web: copied $DIST -> $DEST"
