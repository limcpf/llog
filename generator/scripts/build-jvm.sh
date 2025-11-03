#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Keep templates in sync with root before building
bash scripts/sync-templates.sh

SRC_MAIN="src/main/java"
RES_MAIN="src/main/resources"
BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_FILE="$BUILD_DIR/bloggen.jar"

echo "[build-jvm] Compiling sources..."
rm -rf "$CLASSES_DIR" "$JAR_FILE"
mkdir -p "$CLASSES_DIR"

find "$SRC_MAIN" -name '*.java' > "$BUILD_DIR/sources.list"

${JAVAC:-javac} --release 21 -d "$CLASSES_DIR" @"$BUILD_DIR/sources.list"

echo "[build-jvm] Copying resources..."
if [ -d "$RES_MAIN" ]; then
  rsync -a "$RES_MAIN/" "$CLASSES_DIR/" >/dev/null 2>&1 || cp -R "$RES_MAIN/"* "$CLASSES_DIR/" || true
fi

echo "[build-jvm] Creating jar..."
${JAR:-jar} --create \
  --file "$JAR_FILE" \
  --main-class io.site.bloggen.app.Main \
  -C "$CLASSES_DIR" .

cat > "$BUILD_DIR/bloggen" <<'RUN'
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec ${JAVA:-java} -jar "$DIR/bloggen.jar" "$@"
RUN
chmod +x "$BUILD_DIR/bloggen"

echo "[build-jvm] Done: $JAR_FILE"
echo "[build-jvm] Run: $BUILD_DIR/bloggen --help"
