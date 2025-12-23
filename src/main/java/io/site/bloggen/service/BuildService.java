package io.site.bloggen.service;

import io.site.bloggen.core.SiteConfig;
import io.site.bloggen.infra.FS;
import io.site.bloggen.template.DomainUpdater;
import io.site.bloggen.template.TemplateVars;
import io.site.bloggen.template.TokenEngine;
import io.site.bloggen.util.FlatJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildService {
    public io.site.bloggen.util.Result<Void> build(Path src, Path out, SiteConfig cfg, boolean dryRun,
            boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        try {
            if (!Files.exists(src)) {
                return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO,
                        "source not found: " + src.toAbsolutePath());
            }
            // Copy all
            FS.deleteTree(out);
            if (dryRun) {
                io.site.bloggen.util.Log.info("[dry-run] would clean output: " + out.toAbsolutePath());
                try (var s = Files.walk(src)) {
                    long files = s.filter(Files::isRegularFile).count();
                    io.site.bloggen.util.Log.info("[dry-run] would copy ~" + files + " files from " + src + " to " + out
                            + ", excluding the output dir");
                }
            } else {
                Files.createDirectories(out);
                final Path absSrc = src.toAbsolutePath().normalize();
                final Path absOut = out.toAbsolutePath().normalize();
                java.nio.file.Files.walkFileTree(absSrc, new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir,
                            java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        // Skip output dir and generator dir, VCS dirs
                        if (dir.startsWith(absOut))
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        if (dir.equals(absSrc.resolve("generator")))
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        var name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.equals(".git") || name.equals(".idea") || name.equals(".svn")
                                || name.equals(".github") || name.equals("examples") || name.equals("sample-site")
                                || name.equals("scripts") || name.equals("dist") || name.equals("node_modules")) {
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        // Allowlist top-level site directories only: assets, posts, tags,
                        // partials(read-only for includes)
                        java.nio.file.Path rel = absSrc.relativize(dir);
                        if (rel.getNameCount() == 1) {
                            String top = rel.getName(0).toString();
                            boolean allow = top.equals("assets") || top.equals("posts") || top.equals("tags")
                                    || top.equals("partials");
                            if (!allow)
                                return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        java.nio.file.Path target = absOut.resolve(rel);
                        java.nio.file.Files.createDirectories(target);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file,
                            java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        if (file.startsWith(absOut))
                            return java.nio.file.FileVisitResult.CONTINUE;
                        var baseName = file.getFileName() != null ? file.getFileName().toString() : "";
                        // Skip repo/docs files not needed in final site
                        if (baseName.equals("AGENTS.md") || baseName.equals("DECISIONS.md")) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        // Determine location
                        java.nio.file.Path rel = absSrc.relativize(file);
                        String lower = baseName.toLowerCase();
                        boolean underAssets = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("assets");
                        boolean underPosts = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("posts");
                        boolean underTags = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("tags");
                        boolean underPartials = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("partials");

                        // Exclude markdown by default, but allow any format under assets/
                        if (!underAssets && lower.endsWith(".md")) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        // Only copy:
                        // - files under assets/posts/tags
                        // - root-level site entry files
                        // (html/xml/txt/ico/webmanifest/svg/png/jpg/jpeg/webp/avif)
                        boolean isRootLevel = rel.getNameCount() == 1;
                        boolean allowedRoot = false;
                        if (isRootLevel) {
                            allowedRoot = lower.endsWith(".html") || lower.endsWith(".xml") || lower.endsWith(".txt")
                                    || lower.endsWith(".ico") || lower.endsWith(".webmanifest")
                                    || lower.endsWith(".svg") || lower.endsWith(".png") || lower.endsWith(".jpg")
                                    || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".avif");
                        }

                        // Restrict file types inside posts/tags
                        if (underPosts) {
                            // allow only .html and .meta.json (sidecar removed later)
                            if (!(lower.endsWith(".html") || lower.endsWith(".html.meta.json")
                                    || lower.endsWith(".meta.json"))) {
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        }
                        if (underTags) {
                            if (!lower.endsWith(".html")) {
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        }

                        if (!(underAssets || underPosts || underTags || allowedRoot)) {
                            // Do not copy partials or any other directories/files
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        java.nio.file.Path target = absOut.resolve(rel);
                        java.nio.file.Files.createDirectories(target.getParent());
                        java.nio.file.Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
            // Ensure default template assets exist in output (overlay missing files)
            try (java.io.InputStream list = BuildService.class.getResourceAsStream("/templates/.filelist")) {
                if (list != null) {
                    String all = new String(list.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : all.split("\\R")) {
                        if (line == null || line.isBlank())
                            continue;
                        if (!line.startsWith("assets/"))
                            continue;
                        if (line.endsWith("/")) { // directory marker
                            java.nio.file.Files.createDirectories(out.resolve(line));
                            continue;
                        }
                        java.nio.file.Path target = out.resolve(line);
                        if (java.nio.file.Files.exists(target))
                            continue; // keep user's file
                        java.nio.file.Files.createDirectories(target.getParent());
                        try (java.io.InputStream is = BuildService.class.getResourceAsStream("/templates/" + line)) {
                            if (is != null)
                                java.nio.file.Files.copy(is, target);
                        }
                    }
                }
            } catch (java.io.IOException ignored) {
            }
            // Ensure common root icons/manifests exist if missing
            try {
                String[] roots = new String[] { "favicon.svg", "site.webmanifest" };
                for (String r : roots) {
                    Path target = out.resolve(r);
                    if (Files.exists(target))
                        continue;
                    try (java.io.InputStream is = BuildService.class.getResourceAsStream("/templates/" + r)) {
                        if (is != null) {
                            Files.copy(is, target);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            // Note: do not auto-create about page. Navigation will hide the link
            // when `about.html` is absent in the source root.
            // Extra safety: ensure project's src/assets is fully copied (in case earlier
            // traversal skipped for any reason)
            try {
                Path srcAssets = src.resolve("assets");
                Path outAssets = out.resolve("assets");
                if (Files.exists(srcAssets)) {
                    java.nio.file.Files.createDirectories(outAssets);
                    try (var s = Files.walk(srcAssets)) {
                        s.filter(Files::isRegularFile).forEach(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            Path rel = srcAssets.relativize(p);
                            Path target = outAssets.resolve(rel);
                            try {
                                java.nio.file.Files.createDirectories(target.getParent());
                                java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                }
            } catch (IOException ignored) {
            }

            // Ensure posts html files are present
            try {
                Path srcPosts = src.resolve("posts");
                Path outPosts = out.resolve("posts");
                if (Files.exists(srcPosts)) {
                    java.nio.file.Files.createDirectories(outPosts);
                    try (var s = Files.list(srcPosts)) {
                        for (Path p : (Iterable<Path>) s::iterator) {
                            if (!Files.isRegularFile(p))
                                continue;
                            String lower = p.getFileName().toString().toLowerCase();
                            if (!(lower.endsWith(".html") || lower.endsWith(".meta.json")))
                                continue;
                            Path target = outPosts.resolve(p.getFileName().toString());
                            try {
                                java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }

            // Prepare token map
            var tokens = new java.util.LinkedHashMap<>(TemplateVars.from(cfg));
            boolean hasAbout = java.nio.file.Files.exists(src.resolve("about.html"));
            String aboutLink = hasAbout ? "<a href=\"/about.html\" {{ABOUT_CURRENT_ATTR}}>{{NAV_ABOUT_LABEL}}</a>" : "";
            tokens.put("ABOUT_LINK_HTML", aboutLink);
            if (!hasAbout) {
                // Ensure any lingering label token renders as empty in custom headers
                tokens.put("NAV_ABOUT_LABEL", "");
            }

            // Precompute series contexts (prev/next) from output posts
            var seriesCtx = computeSeriesContext(out);

            // Post-process HTML/XML/TXT-like files: includes -> tokens -> domain updates
            try (var s = Files.walk(out)) {
                s.filter(p -> Files.isRegularFile(p))
                        .forEach(p -> {
                            try {
                                String name = p.getFileName().toString();
                                boolean isText = name.endsWith(".html") || name.endsWith(".xml")
                                        || name.endsWith(".txt") || name.endsWith(".webmanifest");
                                if (!isText)
                                    return;
                                String orig = Files.readString(p, StandardCharsets.UTF_8);
                                String t = expandIncludes(orig, src);
                                if (!hasAbout) {
                                    // Remove any hard-coded About anchors in included headers
                                    t = t.replaceAll(
                                            "(?is)\\s*<a[^>]+href=\\\"/?about(?:\\.html)?\\\"[^>]*>.*?</a>\\s*", " ");
                                }
                                // Front matter block visibility and behavior
                                boolean fmShow = Boolean
                                        .parseBoolean(cfg.extras().getOrDefault("frontmatter_show", "false"));
                                boolean fmOpen = Boolean
                                        .parseBoolean(cfg.extras().getOrDefault("frontmatter_always_open", "false"));
                                if (!fmShow) {
                                    t = t.replaceAll("(?s)<!--\\s*FM_BLOCK_START\\s*-->.*?<!--\\s*FM_BLOCK_END\\s*-->",
                                            "");
                                } else {
                                    t = t.replace("{{FM_OPEN_ATTR}}", fmOpen ? "open" : "");
                                }
                                var local = new java.util.LinkedHashMap<>(tokens);
                                // Page tokens
                                var rel = out.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                                String pagePath = "/" + rel;
                                local.put("PAGE_PATH", pagePath);
                                local.put("PAGE_URL", cfg.domain().replaceAll("/$", "") + pagePath);
                                // Default description
                                if (!local.containsKey("PAGE_DESCRIPTION") || local.get("PAGE_DESCRIPTION") == null
                                        || local.get("PAGE_DESCRIPTION").isBlank()) {
                                    local.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                                }
                                // Post-specific tokens: breadcrumb, article section, JSON-LD
                                if (pagePath.startsWith("/posts/") && pagePath.endsWith(".html")) {
                                    String cat = local.getOrDefault("CATEGORY_PATH", "");
                                    local.put("BREADCRUMB", buildBreadcrumb(cat, cfg));
                                    local.put("ARTICLE_SECTION", lastSegmentLabel(cat, cfg));
                                    String htmlForTitle = orig; // before token application contains <h1>
                                    local.put("POST_JSONLD", buildJsonLd(htmlForTitle, pagePath, local, cfg));
                                    // series badge + prev/next
                                    var sc = seriesCtx.get(pagePath);
                                    if (sc != null) {
                                        local.put("SERIES_BADGE", sc.badgeHtml);
                                        // Replace token if present
                                        if (t.contains("{{SERIES_NAV}}")) {
                                            t = t.replace("{{SERIES_NAV}}", sc.navHtml == null ? "" : sc.navHtml);
                                        } else {
                                            // Fallback removed to prevent header injection ghosting
                                        }
                                    } else {
                                        local.put("SERIES_BADGE", "");
                                        t = t.replace("{{SERIES_NAV}}", "");
                                    }
                                }
                                if (!local.containsKey("PAGE_DESCRIPTION") || local.get("PAGE_DESCRIPTION") == null
                                        || local.get("PAGE_DESCRIPTION").isBlank()) {
                                    local.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                                }
                                // Sidecar meta: <filename>.meta.json
                                var meta = p.resolveSibling(name + ".meta.json");
                                if (java.nio.file.Files.exists(meta)) {
                                    String mj = java.nio.file.Files.readString(meta, StandardCharsets.UTF_8);
                                    var mm = FlatJson.parse(mj);
                                    for (var e : mm.entrySet()) {
                                        String k = e.getKey() == null ? "" : e.getKey().trim();
                                        String v = sanitizeForOutput(e.getValue());
                                        if (!k.isEmpty() && v != null && !v.isBlank()) {
                                            local.put(k.toUpperCase(), v);
                                        }
                                    }
                                }
                                String nt = TokenEngine.apply(t, local);
                                nt = DomainUpdater.apply(nt, cfg);
                                if (!nt.equals(orig)) {
                                    if (dryRun)
                                        io.site.bloggen.util.Log.debug("[dry-run] would update: " + p);
                                    else
                                        Files.writeString(p, nt, StandardCharsets.UTF_8);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            // Remove sidecar meta files from output (only when not dry-run)
            if (!dryRun) {
                try (var s2 = Files.walk(out)) {
                    s2.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".meta.json"))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
                // Remove root docs and config not needed in dist
                try {
                    Files.deleteIfExists(out.resolve("AGENTS.md"));
                } catch (IOException ignored) {
                }
                try {
                    Files.deleteIfExists(out.resolve("DECISIONS.md"));
                } catch (IOException ignored) {
                }
                try {
                    Files.deleteIfExists(out.resolve("site.json"));
                } catch (IOException ignored) {
                }
                // Remove partials folder from output (includes are expanded at build time)
                try {
                    FS.deleteTree(out.resolve("partials"));
                } catch (Exception ignored) {
                }
            }
            // feed/sitemap/robots simple domain swap
            Path feed = out.resolve("feed.xml");
            if (Files.exists(feed)) {
                String t = Files.readString(feed, StandardCharsets.UTF_8);
                String nt = t.replaceAll("https?://[^<]*/posts/", cfg.domain().replaceAll("/$", "") + "/posts/")
                        .replaceFirst("(<link>)https?://[^<]*(</link>)",
                                "$1" + cfg.domain().replaceAll("/$", "") + "/$2");
                if (!nt.equals(t)) {
                    if (dryRun)
                        io.site.bloggen.util.Log.debug("[dry-run] would update feed.xml");
                    else
                        Files.writeString(feed, nt, StandardCharsets.UTF_8);
                }
            }
            Path sm = out.resolve("sitemap.xml");
            if (Files.exists(sm)) {
                String t = Files.readString(sm, StandardCharsets.UTF_8);
                Pattern p = Pattern.compile("https?://[^<]+");
                Matcher m = p.matcher(t);
                StringBuffer sb = new StringBuffer();
                String domain = cfg.domain().replaceAll("/$", "");
                while (m.find()) {
                    String url = m.group();
                    String replaced = domain + url.replaceFirst("^https?://[^/]+", "");
                    m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
                }
                m.appendTail(sb);
                String nt = sb.toString();
                if (!nt.equals(t)) {
                    if (dryRun)
                        io.site.bloggen.util.Log.debug("[dry-run] would update sitemap.xml");
                    else
                        Files.writeString(sm, nt, StandardCharsets.UTF_8);
                }
            }
            Path rb = out.resolve("robots.txt");
            if (Files.exists(rb)) {
                String t = Files.readString(rb, StandardCharsets.UTF_8);
                String nt = t.replaceAll("(?m)^(Sitemap: ).*$",
                        "$1" + cfg.domain().replaceAll("/$", "") + "/sitemap.xml");
                if (!nt.equals(t)) {
                    if (dryRun)
                        io.site.bloggen.util.Log.debug("[dry-run] would update robots.txt");
                    else
                        Files.writeString(rb, nt, StandardCharsets.UTF_8);
                }
            }
            // Generate catalogs (posts, archives, tags, feeds, sitemap)
            var cat = new CatalogService().generate(src, out, cfg, dryRun);
            if (cat instanceof io.site.bloggen.util.Result.Err<?> err) {
                return io.site.bloggen.util.Result.err(err.code(), err.message());
            }
            // Post-process again to expand includes/tokens in generated pages
            try (var s = Files.walk(out)) {
                s.filter(p -> Files.isRegularFile(p))
                        .forEach(p -> {
                            try {
                                String name = p.getFileName().toString();
                                boolean isText = name.endsWith(".html") || name.endsWith(".xml")
                                        || name.endsWith(".txt") || name.endsWith(".webmanifest");
                                if (!isText)
                                    return;
                                String orig = Files.readString(p, StandardCharsets.UTF_8);
                                String t = expandIncludes(orig, src);
                                if (!hasAbout) {
                                    t = t.replaceAll(
                                            "(?is)\\s*<a[^>]+href=\\\"/?about(?:\\.html)?\\\"[^>]*>.*?</a>\\s*", " ");
                                }
                                boolean fmShow2 = Boolean
                                        .parseBoolean(cfg.extras().getOrDefault("frontmatter_show", "false"));
                                boolean fmOpen2 = Boolean
                                        .parseBoolean(cfg.extras().getOrDefault("frontmatter_always_open", "false"));
                                if (!fmShow2) {
                                    t = t.replaceAll("(?s)<!--\\s*FM_BLOCK_START\\s*-->.*?<!--\\s*FM_BLOCK_END\\s*-->",
                                            "");
                                } else {
                                    t = t.replace("{{FM_OPEN_ATTR}}", fmOpen2 ? "open" : "");
                                }
                                var local2 = new java.util.LinkedHashMap<>(tokens);
                                var rel2 = out.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                                String pagePath2 = "/" + rel2;
                                local2.put("PAGE_PATH", pagePath2);
                                local2.put("PAGE_URL", cfg.domain().replaceAll("/$", "") + pagePath2);
                                if (!local2.containsKey("PAGE_DESCRIPTION") || local2.get("PAGE_DESCRIPTION") == null
                                        || local2.get("PAGE_DESCRIPTION").isBlank()) {
                                    local2.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                                }
                                if (pagePath2.startsWith("/posts/") && pagePath2.endsWith(".html")) {
                                    String cat2 = local2.getOrDefault("CATEGORY_PATH", "");
                                    local2.put("BREADCRUMB", buildBreadcrumb(cat2, cfg));
                                    local2.put("ARTICLE_SECTION", lastSegmentLabel(cat2, cfg));
                                    local2.put("POST_JSONLD", buildJsonLd(orig, pagePath2, local2, cfg));
                                    // series badge + prev/next
                                    var sc2 = seriesCtx.get(pagePath2);
                                    if (sc2 != null) {
                                        local2.put("SERIES_BADGE", sc2.badgeHtml);
                                        // Replace token if present
                                        if (t.contains("{{SERIES_NAV}}")) {
                                            t = t.replace("{{SERIES_NAV}}", sc2.navHtml == null ? "" : sc2.navHtml);
                                        } else {
                                            // Fallback injection if token missing (old templates)
                                            if (sc2.navHtml != null && !sc2.navHtml.isBlank()
                                                    && !t.contains("c-series-nav")) {
                                                if (t.contains("<!--FM_BLOCK_END-->")) {
                                                    t = t.replace("<!--FM_BLOCK_END-->",
                                                            "<!--FM_BLOCK_END-->\n" + sc2.navHtml + "\n");
                                                } else {
                                                    // inject inside main
                                                    if (t.contains("role=\"main\">")) {
                                                        t = t.replace("role=\"main\">",
                                                                "role=\"main\">\n" + sc2.navHtml + "\n");
                                                    } else {
                                                        t = t.replaceFirst("</header>",
                                                                "</header>\n" + sc2.navHtml + "\n");
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        local2.put("SERIES_BADGE", "");
                                        t = t.replace("{{SERIES_NAV}}", "");
                                    }
                                }
                                if (!local2.containsKey("PAGE_DESCRIPTION") || local2.get("PAGE_DESCRIPTION") == null
                                        || local2.get("PAGE_DESCRIPTION").isBlank()) {
                                    local2.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                                }
                                // nav current helpers
                                boolean isHome = "index.html".equals(rel2) || rel2.isEmpty();
                                boolean isAbout = "about.html".equals(rel2);
                                boolean isPosts = rel2.startsWith("posts/");
                                boolean isCats = rel2.startsWith("categories/");
                                local2.put("HOME_CURRENT_ATTR", isHome ? "aria-current=\"page\"" : "");
                                local2.put("ABOUT_CURRENT_ATTR", isAbout ? "aria-current=\"page\"" : "");
                                local2.put("POSTS_CURRENT_ATTR", isPosts ? "aria-current=\"page\"" : "");
                                local2.put("CATS_CURRENT_ATTR", isCats ? "aria-current=\"page\"" : "");
                                String nt2 = TokenEngine.apply(t, local2);
                                nt2 = DomainUpdater.apply(nt2, cfg);
                                if (!nt2.equals(orig)) {
                                    if (dryRun)
                                        io.site.bloggen.util.Log.debug("[dry-run] would update: " + p);
                                    else
                                        Files.writeString(p, nt2, StandardCharsets.UTF_8);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            return io.site.bloggen.util.Result.ok(null);
        } catch (IOException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, e.getMessage());
        } catch (RuntimeException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.UNKNOWN, e.getMessage());
        }
    }

    private static String expandIncludes(String text, Path srcRoot) {
        // Pattern: <!-- @include path --> or <!-- @include path="..." -->
        java.util.regex.Pattern pat = java.util.regex.Pattern
                .compile("<!--\\s*@include\\s+(?:path=)?\"?([^\"\s]+)\"?\\s*-->");
        java.util.regex.Matcher m = pat.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rel = m.group(1).trim();
            java.nio.file.Path inc = srcRoot.resolve(rel).normalize();
            String repl;
            try {
                if (java.nio.file.Files.exists(inc)) {
                    repl = java.nio.file.Files.readString(inc, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    // Fallback to packaged template resource: /templates/<rel>
                    String resourcePath = "/templates/" + rel.replace('\\', '/');
                    try (java.io.InputStream is = BuildService.class.getResourceAsStream(resourcePath)) {
                        if (is != null)
                            repl = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        else
                            repl = "<!-- include not found: " + rel + " -->";
                    }
                }
            } catch (java.io.IOException e) {
                repl = "<!-- include error: " + rel + " -->";
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static class SeriesEntry {
        String pagePath;
        String url;
        String title;
        String seriesTitle;
        String seriesSlug;
        int order;
        java.time.LocalDate date;
    }

    private static class SeriesCtx {
        String badgeHtml;
        String navHtml;
    }

    private static java.util.Map<String, SeriesCtx> computeSeriesContext(Path out) {
        java.util.Map<String, java.util.List<SeriesEntry>> bySeries = new java.util.LinkedHashMap<>();
        java.nio.file.Path posts = out.resolve("posts");
        if (!java.nio.file.Files.exists(posts))
            return java.util.Map.of();
        java.util.regex.Pattern filePat = java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-(.+)\\.html$");
        java.util.regex.Pattern h1Pat = java.util.regex.Pattern.compile("<h1[^>]*>(.*?)</h1>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        try (var s = java.nio.file.Files.list(posts)) {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s
                    .filter(f -> f.getFileName().toString().endsWith(".html"))::iterator) {
                String name = p.getFileName().toString();
                java.util.regex.Matcher fm = filePat.matcher(name);
                if (!fm.matches())
                    continue;
                java.time.LocalDate date;
                try {
                    date = java.time.LocalDate.parse(fm.group(1));
                } catch (Exception e) {
                    date = java.time.LocalDate.MIN;
                }
                java.nio.file.Path meta = p.resolveSibling(name + ".meta.json");
                if (!java.nio.file.Files.exists(meta))
                    continue;
                String mj = java.nio.file.Files.readString(meta, java.nio.charset.StandardCharsets.UTF_8);
                var mm = io.site.bloggen.util.FlatJson.parse(mj);
                String series = mm.getOrDefault("SERIES", "");
                if (series == null || series.isBlank())
                    continue;
                String ord = mm.getOrDefault("SERIES_ORDER", "");
                int order = Integer.MAX_VALUE;
                try {
                    if (ord != null && !ord.isBlank())
                        order = Integer.parseInt(ord.trim());
                } catch (Exception ignored) {
                }
                String html = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = h1Pat.matcher(html);
                String title = m.find() ? m.group(1).replaceAll("<[^>]+>", "").trim() : name;
                SeriesEntry se = new SeriesEntry();
                String rel = out.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                se.pagePath = "/" + rel;
                se.url = se.pagePath;
                se.title = title;
                se.seriesTitle = series;
                se.seriesSlug = io.site.bloggen.infra.Slug.of(series);
                se.order = order;
                se.date = date;
                bySeries.computeIfAbsent(se.seriesSlug, k -> new java.util.ArrayList<>()).add(se);
            }
        } catch (java.io.IOException ignored) {
        }
        // sort
        for (var e : bySeries.entrySet()) {
            e.getValue().sort((a, b) -> {
                int ao = a.order;
                int bo = b.order;
                if (ao != Integer.MAX_VALUE || bo != Integer.MAX_VALUE) {
                    if (ao != bo)
                        return Integer.compare(ao, bo);
                }
                return a.date.compareTo(b.date);
            });
        }
        // build ctx per page
        java.util.Map<String, SeriesCtx> ctx = new java.util.HashMap<>();
        for (var e : bySeries.entrySet()) {
            var list = e.getValue();
            String slug = e.getKey();
            String title = list.isEmpty() ? slug : list.get(0).seriesTitle;
            String badge = "<div class=\"c-series-badge\"><a href=\"/series/" + slug + "/\">" + escapeHtml(title)
                    + "</a></div>";
            for (int i = 0; i < list.size(); i++) {
                SeriesEntry cur = list.get(i);
                SeriesEntry prev = i > 0 ? list.get(i - 1) : null;
                SeriesEntry next = i < list.size() - 1 ? list.get(i + 1) : null;
                if (prev == null && next == null) {
                    SeriesCtx sc = new SeriesCtx();
                    sc.badgeHtml = badge;
                    sc.navHtml = "";
                    ctx.put(cur.pagePath, sc);
                    continue;
                }
                StringBuilder nav = new StringBuilder();
                nav.append("<nav class=\"c-series-nav\">");
                // Removed redundant label: <strong class="c-series-nav__label">...</strong>
                nav.append("<ul class=\"c-series-nav__list\">");
                if (prev != null)
                    nav.append("<li><a href=\"").append(prev.url).append("\">← ").append(escapeHtml(prev.title))
                            .append("</a></li>");
                if (next != null)
                    nav.append("<li><a href=\"").append(next.url).append("\">").append(escapeHtml(next.title))
                            .append(" →</a></li>");
                nav.append("</ul></nav>");
                SeriesCtx sc = new SeriesCtx();
                sc.badgeHtml = badge;
                sc.navHtml = nav.toString();
                ctx.put(cur.pagePath, sc);
            }
        }
        return ctx;
    }

    private static String buildBreadcrumb(String catPath, io.site.bloggen.core.SiteConfig cfg) {
        if (catPath == null || catPath.isBlank())
            return "";
        String[] parts = catPath.split("/");
        StringBuilder sb = new StringBuilder();
        // sb.append("<a href=\"/categories/\">카테고리</a> / "); // Removed root link
        String prefix = "";
        for (int i = 0; i < parts.length; i++) {
            if (!prefix.isEmpty())
                prefix += "/";
            String seg = parts[i];
            prefix += seg;
            String label = mapCategoryLabel(seg, cfg);
            sb.append("<a href=\"/categories/").append(prefix).append("/\">").append(escapeHtml(label)).append("</a>");
            if (i < parts.length - 1)
                sb.append(" / ");
        }
        return sb.toString();
    }

    private static String lastSegmentLabel(String catPath, io.site.bloggen.core.SiteConfig cfg) {
        if (catPath == null || catPath.isBlank())
            return "";
        String[] parts = catPath.split("/");
        String seg = parts[parts.length - 1];
        return mapCategoryLabel(seg, cfg);
    }

    private static String mapCategoryLabel(String seg, io.site.bloggen.core.SiteConfig cfg) {
        String mapStr = cfg.extras().getOrDefault("category_labels", "");
        if (!mapStr.isBlank()) {
            String[] pairs = mapStr.split(",");
            for (String p : pairs) {
                String[] kv = p.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().toLowerCase();
                    String val = kv[1].trim();
                    if (k.equals(seg))
                        return val.isEmpty() ? seg : val;
                }
            }
        }
        // default: prettify
        String s = seg.replace('-', ' ');
        if (s.isEmpty())
            return seg;
        return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }

    private static String buildJsonLd(String html, String pagePath, java.util.Map<String, String> local,
            io.site.bloggen.core.SiteConfig cfg) {
        // Extract h1
        String title = "";
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<h1[^>]*>(.*?)</h1>",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
            if (m.find())
                title = m.group(1).replaceAll("<[^>]+>", "").trim();
        } catch (Exception ignored) {
        }
        if (title.isBlank())
            title = cfg.siteName();
        String date = "";
        java.util.regex.Matcher dm = java.util.regex.Pattern.compile("/posts/(\\d{4}-\\d{2}-\\d{2})-")
                .matcher(pagePath);
        if (dm.find())
            date = dm.group(1);
        String section = lastSegmentLabel(local.getOrDefault("CATEGORY_PATH", ""), cfg);
        String desc = local.getOrDefault("PAGE_DESCRIPTION", cfg.extras().getOrDefault("site_description", ""));
        String url = local.getOrDefault("PAGE_URL", cfg.domain());
        String tags = local.getOrDefault("TAGS", "");
        StringBuilder kw = new StringBuilder();
        if (!tags.isBlank()) {
            String[] arr = tags.split(",");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0)
                    kw.append(", ");
                kw.append(arr[i].trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"application/ld+json\">{\n")
                .append("\"@context\":\"https://schema.org\",\n")
                .append("\"@type\":\"BlogPosting\",\n")
                .append("\"headline\":\"").append(escapeJson(title)).append("\",\n");
        if (!date.isBlank())
            sb.append("\"datePublished\":\"").append(escapeJson(date)).append("\",\n");
        if (!section.isBlank())
            sb.append("\"articleSection\":\"").append(escapeJson(section)).append("\",\n");
        if (kw.length() > 0)
            sb.append("\"keywords\":\"").append(escapeJson(kw.toString())).append("\",\n");
        sb.append("\"url\":\"").append(escapeJson(url)).append("\",\n")
                .append("\"description\":\"").append(escapeJson(desc)).append("\"\n}")
                .append("</script>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static String sanitizeForOutput(String s) {
        if (s == null)
            return "";
        String t = s.replace("\\n", " ").replace("\\r", " ").replace("\\t", " ").replace("\\b", " ");
        return t.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }
}
