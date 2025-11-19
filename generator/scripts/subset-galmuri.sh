#!/usr/bin/env bash
set -euo pipefail

# Subset Galmuri fonts to English (Basic Latin + Punctuation) + Korean (Jamo/Compatibility/Syllables)
# Outputs overwrite the original WOFF2 files to keep CSS references stable.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FONTS_DIR="$ROOT_DIR/src/main/resources/templates/assets/fonts"

UNICODES="U+0000-007F,U+2000-206F,U+1100-11FF,U+3130-318F,U+AC00-D7A3"

echo "[subset] Using unicode ranges: $UNICODES"

python3 - <<'PY'
import importlib.util, sys
ok = importlib.util.find_spec('fontTools') is not None
sys.exit(0 if ok else 1)
PY
if [ $? -ne 0 ]; then
  echo "[subset] Installing fonttools + brotli (user)" >&2
  python3 -m pip install --user --quiet fonttools brotli
fi

subset() {
  local in_file="$1"; shift
  local tmp_file="$in_file.tmp.woff2"
  echo "[subset] -> $in_file"
  python3 -m fontTools.subset "$in_file" \
    --flavor=woff2 \
    --unicodes="$UNICODES" \
    --layout-features='*' \
    --no-hinting \
    --drop-tables+=DSIG \
    --output-file="$tmp_file" >/dev/null 2>&1
  if [ -f "$tmp_file" ]; then
    mv "$tmp_file" "$in_file"
  else
    echo "[subset] ERROR: failed to create subset for $in_file" >&2
    exit 1
  fi
}

subset "$FONTS_DIR/Galmuri11.woff2"
subset "$FONTS_DIR/Galmuri11-Bold.woff2"
subset "$FONTS_DIR/Galmuri11-Condensed.woff2"

echo "[subset] Done."
