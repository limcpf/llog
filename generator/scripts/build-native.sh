#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Keep templates in sync with root before building
bash scripts/sync-templates.sh

BUILD_DIR="build"
BIN_OUT="$BUILD_DIR/bloggen"

if ! command -v native-image >/dev/null 2>&1; then
  echo "[build-native] ERROR: native-image not found. Install GraalVM (Java 21+) and native-image." >&2
  exit 2
fi

# Ensure JVM jar exists
bash "scripts/build-jvm.sh"

echo "[build-native] Building native image..."
native-image \
  --no-fallback \
  -H:Name=bloggen \
  -H:Path="$BUILD_DIR" \
  -H:Class=io.site.bloggen.app.Main \
  -H:IncludeResources=^templates/.* \
  -cp "$BUILD_DIR/bloggen.jar"

echo "[build-native] Done: $BIN_OUT"
echo "[build-native] Run: $BIN_OUT --help"
