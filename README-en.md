# llog — Static Blog Template + Generator (Generator as Source of Truth)

[한국어](README.md)

llog is a fast, accessible, and readable static blog template bundled with a Java-based CLI generator. The generator’s templates are the single source of truth — releases ship a site skeleton and native binaries for simple, reproducible builds.

- Templates (SoT): `generator/src/main/resources/templates/`
- Output: `dist/` (deployable static files)
- Examples: `examples/` (demo only)

## Features
- Typography: Pretendard (variable+static) self-hosted, optimized for Korean
- A11y: skip link, clear focus styles, semantic landmarks, WCAG AA contrast
- SEO/OG: canonical, og:url/image, Twitter card, sitemap/feed
- Structure: HTML `<!-- @include ... -->` + simple token replacement `{{KEY}}`
- Performance: minimal output, lazy images, clean defaults

## Quick Start (Local)
Prereqs: Java 21 for JVM; GraalVM native-image optional for native builds.

1) Build the generator (JVM)
```
bash generator/scripts/build-jvm.sh
```

2) Create a sample site (with sample MD → import → dist build)
```
generator/build/llog sample --out sample-site --build
python3 -m http.server -d sample-site/dist 8080
# http://localhost:8080
```

3) Build from a working directory (using the release skeleton)
```
# unpack site-skeleton.tar.gz (e.g., ./work)
mkdir -p work && tar -xzf site-skeleton.tar.gz -C work
# import markdown content
./llog import:md --src /path/to/vault --root work
# build
./llog build --src work --out dist --verbose
```

## CLI
```
llog 0.1.0
Usage:
  init <dir> [--dry-run] [--verbose]
  build [--src dir] [--out dir] [--dry-run] [--verbose]
  new:post --title "..." [--date YYYY-MM-DD] [--slug slug] [--root dir] [--dry-run] [--verbose]
  import:md --src <md_dir> [--root dir] [--dry-run] [--verbose]
  sample [--out dir] [--build] [--dry-run] [--verbose]
  --help | --version
```
- `sample`: creates an example site with two sample MD posts; builds `dist` when `--build` is provided.
- `import:md`: reads Front Matter (`title`, `createdDate`, `publish`) and converts to posts.
- `build`: expands includes → tokens → domain; generates index/tags/feed/sitemap.

## Templates, Tokens, Includes
- Single source (SoT): `generator/src/main/resources/templates/`
- Shared head: include `partials/head-shared.html` in each page’s `<head>`
  - Syntax: `<!-- @include partials/head-shared.html -->`
  - Build looks in project `src` first, then falls back to packaged templates
- Page tokens: `{{SITE_NAME}}`, `{{DOMAIN}}`, `{{OG_DEFAULT}}`, `{{YEAR}}` etc., read from `site.json`
- Page meta: put overrides in `<file>.meta.json` and they’re injected as tokens

## Repository Layout
- `generator/` — generator code/templates/scripts (source of truth)
  - `src/main/resources/templates/` — actual templates (assets/partials/pages)
  - `scripts/` — build/native/utility scripts
- `examples/` — demo workflows (not production content)
- `dist/` — built output for deployment (no docs/partials/config)

## Release & Deploy
- Template repo (this repo)
  - GitHub Actions: `.github/workflows/release.yml`
  - Tag push (`v*`) uploads native binaries and `site-skeleton.tar.gz` with checksums
- Content repo (separate)
  - See `examples/workflows/content-build-and-deploy.yml`
  - Flow: download release assets → checkout Vault (Markdown) → `import:md` → `build` → deploy to Pages

## A11y/SEO Checklist
- Provide a skip link: `<a class="u-sr-only u-sr-only--focusable" href="#main">Skip to content</a>`
- Respect heading hierarchy; use meaningful link text; `aria-current="page"`
- Include `<title>`, `meta description`, canonical, Open Graph/Twitter
- Images must have `alt`, fixed `width/height`, `loading="lazy"`, `decoding="async"`

## Dev Tips
- `examples/` is for demos. Operate/build from a skeleton-based working directory.
- `dist` only contains deploy essentials; put required assets under `templates/assets/`.
- Native binary reduces runtime dependencies for simpler CI/CD.

---
Issues and PRs are welcome. Prefer pinned release tags to keep templates and binaries in sync.
