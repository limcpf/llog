#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

SITE_ROOT="$(cd .. && pwd)"
TPL_DIR="src/main/resources/templates"

echo "[sync-templates] Regenerating .filelist in $TPL_DIR (templates are the source of truth)"
(
  cd "$TPL_DIR"
  # list directories with trailing slash, exclude root '.'
  find . -type d ! -path . -print \
    | sed 's#^./##; s#$#/#' \
    | sed '/^$/d'
  # list files, exclude .filelist itself
  find . -type f -print \
    | sed 's#^./##' \
    | grep -v '^\.filelist$' || true
) > "$TPL_DIR/.filelist"

echo "[sync-templates] Done"
