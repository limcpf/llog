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

1) Generator wrapper
```
bash scripts/llog --help
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
llog 0.3.8
Usage:
  init <dir> [--dry-run] [--verbose]
  build [--src dir] [--out dir] [--config path] [--import-src md_dir] [--dry-run] [--verbose]
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
- Page tokens: `{{SITE_NAME}}`, `{{DOMAIN}}`, `{{OG_DEFAULT}}`, `{{YEAR}}` etc., read from `site.json`.
- Page meta: overrides in `<file>.meta.json` (e.g., `PAGE_DESCRIPTION`, `OG_IMAGE`).

### Header / Footer customization
- Shared header/footer are provided as partials.
  - Header: `generator/src/main/resources/templates/partials/site-header.html`
  - Footer: `generator/src/main/resources/templates/partials/site-footer.html`
- To customize per project, place files with the same path under your project `partials/` and they will override the templates.
- Navigation labels come from `site.json` Extras: `nav_home_label`, `nav_about_label`, `nav_categories_label`, `nav_posts_label`.

### About page
- Built-in: an `about.html` template ships with the generator, but it is NOT copied automatically. If your source (`--src`) doesn’t contain `about.html`, the About menu item is hidden.
- Override: add your own `about.html` in the source root to show the menu and use your content. You can still use the shared includes.
  - Example skeleton
    ```html
    <!doctype html>
    <html lang="en">
      <head>
        <!-- @include partials/head-shared.html -->
        <title>About — {{SITE_NAME}}</title>
        <meta name="description" content="{{PAGE_DESCRIPTION}}" />
        <link rel="canonical" href="{{DOMAIN}}/about" />
      </head>
      <body>
        <a class="u-sr-only u-sr-only--focusable" href="#main">Skip to main</a>
        <!-- @include partials/site-header.html -->
        <main id="main" class="l-reading" role="main">
          <article class="c-article u-flow" aria-labelledby="about-title">
            <header>
              <h1 id="about-title">{{SITE_NAME}}</h1>
              <p class="c-article__meta">About</p>
            </header>
            <p>Contact: <a href="mailto:{{CONTACT_EMAIL}}">{{CONTACT_EMAIL}}</a></p>
          </article>
        </main>
        <!-- @include partials/site-footer.html -->
      </body>
    </html>
    ```
- Page meta override: put `about.html.meta.json` next to the page (e.g., `{ "PAGE_DESCRIPTION": "About this site", "OG_IMAGE": "/og/about.jpg" }`).
- Contact email: set `contact_email` in `site.json` Extras.

### Meta / Favicon / Theme Colors
- Global site description: `site_description` (default for page description)
- Favicon: `favicon_path` (default `/favicon.svg`)
- Theme colors: `theme_color_light`, `theme_color_dark`
- Twitter card: `twitter_card` (default `summary_large_image`)
- Page-level overrides via `.meta.json`

### Front Matter table (optional)
- Show Front Matter at the top of posts as a neat table (details/summary + table)
- site.json Extras:
  - `frontmatter_show`: `false|true` (default `false`)
  - `frontmatter_always_open`: `false|true` (default `false` → collapsed)

### Analytics
- Cloudflare Web Analytics (recommended): `cf_beacon_token`
- Google Analytics GA4 (optional): `ga_measurement_id`, `ga_send_page_view`

### External site.json (optional)
- `llog build --src . --out dist --config /path/to/site.json`
- or `SITE_JSON=/path/to/site.json llog build --src . --out dist`

### Cloudflare Pages example
- Build command: `./llog build --src . --out dist --import-src $VAULT_DIR`
- Artifact directory: `dist`

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
