# BlogGen (generator/) — Java 21 + GraalVM Native

CLI-first static blog generator that emits this repo’s HTML/CSS template as-is.

Quick start
- JVM run
  - `./gradlew run --args="--help"`
  - `./gradlew run --args="init ../site"`
  - `./gradlew run --args="new:post --title '첫 글' --date 2025-10-22 --root ../site"`
  - `./gradlew run --args="build --src ../site --out ../dist"`
- Native build (M4)
  - Install GraalVM (Java 21+) and Native Image
  - `./gradlew nativeCompile`
  - Run: `./build/native/nativeCompile/bloggen --help`

Flags
- `--dry-run`: log planned actions without writing files
- `--verbose`: show debug logs

Tokens
- Global (site.json): `DOMAIN`, `SITE_NAME`, `RSS_TITLE`, `OG_DEFAULT`, `OG_IMAGE`, `YEAR`, `SITE_DESCRIPTION`, `CONTACT_EMAIL`, `NAV_*`, `COPYRIGHT`
- Per-page: sidecar `<file>.meta.json` with flat JSON, e.g. `PAGE_DESCRIPTION`, `OG_IMAGE`, `OG_TITLE`, then use `{{KEY}}` in the page.

Docs
- Rules: `generator/AGENTS.md`
- Tasks: `generator/TASKS.md`
