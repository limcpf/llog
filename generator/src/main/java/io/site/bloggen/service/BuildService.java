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
    public io.site.bloggen.util.Result<Void> build(Path src, Path out, SiteConfig cfg, boolean dryRun, boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        try {
            if (!Files.exists(src)) {
                return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, "source not found: " + src.toAbsolutePath());
            }
            // Copy all
            FS.deleteTree(out);
            if (dryRun) {
                io.site.bloggen.util.Log.info("[dry-run] would clean output: " + out.toAbsolutePath());
                try (var s = Files.walk(src)) {
                    long files = s.filter(Files::isRegularFile).count();
                    io.site.bloggen.util.Log.info("[dry-run] would copy ~" + files + " files from " + src + " to " + out + ", excluding the output dir");
                }
            } else {
                Files.createDirectories(out);
                final Path absSrc = src.toAbsolutePath().normalize();
                final Path absOut = out.toAbsolutePath().normalize();
                java.nio.file.Files.walkFileTree(absSrc, new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        // Skip output dir and generator dir, VCS dirs
                        if (dir.startsWith(absOut)) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        if (dir.equals(absSrc.resolve("generator"))) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        var name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.equals(".git") || name.equals(".idea") || name.equals(".svn")
                                || name.equals(".github") || name.equals("examples") || name.equals("sample-site")
                                || name.equals("scripts") || name.equals("dist") || name.equals("node_modules")) {
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        // Allowlist top-level site directories only: assets, posts, tags, partials(read-only for includes)
                        java.nio.file.Path rel = absSrc.relativize(dir);
                        if (rel.getNameCount() == 1) {
                            String top = rel.getName(0).toString();
                            boolean allow = top.equals("assets") || top.equals("posts") || top.equals("tags") || top.equals("partials");
                            if (!allow) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        java.nio.file.Path target = absOut.resolve(rel);
                        java.nio.file.Files.createDirectories(target);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        if (file.startsWith(absOut)) return java.nio.file.FileVisitResult.CONTINUE;
                        var baseName = file.getFileName() != null ? file.getFileName().toString() : "";
                        // Skip repo/docs files not needed in final site
                        if (baseName.equals("AGENTS.md") || baseName.equals("DECISIONS.md")) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        // Exclude markdown and other source notes by default
                        String lower = baseName.toLowerCase();
                        if (lower.endsWith(".md")) return java.nio.file.FileVisitResult.CONTINUE;

                        java.nio.file.Path rel = absSrc.relativize(file);
                        boolean underAssets = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("assets");
                        boolean underPosts  = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("posts");
                        boolean underTags   = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("tags");
                        boolean underPartials = rel.getNameCount() >= 1 && rel.getName(0).toString().equals("partials");

                        // Only copy:
                        //  - files under assets/posts/tags
                        //  - root-level site entry files (html/xml/txt/ico/webmanifest/svg/png/jpg/jpeg/webp/avif)
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
                            if (!(lower.endsWith(".html") || lower.endsWith(".html.meta.json") || lower.endsWith(".meta.json"))) {
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
                        java.nio.file.Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
        // Ensure default template assets exist in output (overlay missing files)
        try (java.io.InputStream list = BuildService.class.getResourceAsStream("/templates/.filelist")) {
            if (list != null) {
                String all = new String(list.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String line : all.split("\\R")) {
                    if (line == null || line.isBlank()) continue;
                    if (!line.startsWith("assets/")) continue;
                    if (line.endsWith("/")) { // directory marker
                        java.nio.file.Files.createDirectories(out.resolve(line));
                        continue;
                    }
                    java.nio.file.Path target = out.resolve(line);
                    if (java.nio.file.Files.exists(target)) continue; // keep user's file
                    java.nio.file.Files.createDirectories(target.getParent());
                    try (java.io.InputStream is = BuildService.class.getResourceAsStream("/templates/" + line)) {
                        if (is != null) java.nio.file.Files.copy(is, target);
                    }
                }
            }
        } catch (java.io.IOException ignored) {}
        // Ensure common root icons/manifests exist if missing
        try {
            String[] roots = new String[] { "favicon.svg", "site.webmanifest" };
            for (String r : roots) {
                Path target = out.resolve(r);
                if (Files.exists(target)) continue;
                try (java.io.InputStream is = BuildService.class.getResourceAsStream("/templates/" + r)) {
                    if (is != null) {
                        Files.copy(is, target);
                    }
                }
            }
        } catch (IOException ignored) {}
        // Extra safety: ensure project's src/assets is fully copied (in case earlier traversal skipped for any reason)
        try {
            Path srcAssets = src.resolve("assets");
            Path outAssets = out.resolve("assets");
            if (Files.exists(srcAssets)) {
                java.nio.file.Files.createDirectories(outAssets);
                try (var s = Files.walk(srcAssets)) {
                    s.filter(Files::isRegularFile).forEach(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        if (name.endsWith(".md")) return; // do not copy markdown under assets
                        Path rel = srcAssets.relativize(p);
                        Path target = outAssets.resolve(rel);
                        try {
                            java.nio.file.Files.createDirectories(target.getParent());
                            java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (IOException ignored) {}
                    });
                }
            }
        } catch (IOException ignored) {}

        // Ensure posts html files are present
        try {
            Path srcPosts = src.resolve("posts");
            Path outPosts = out.resolve("posts");
            if (Files.exists(srcPosts)) {
                java.nio.file.Files.createDirectories(outPosts);
                try (var s = Files.list(srcPosts)) {
                    for (Path p : (Iterable<Path>) s::iterator) {
                        if (!Files.isRegularFile(p)) continue;
                        String lower = p.getFileName().toString().toLowerCase();
                        if (!(lower.endsWith(".html") || lower.endsWith(".meta.json"))) continue;
                        Path target = outPosts.resolve(p.getFileName().toString());
                        try {
                            java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException ignored) {}

        // Prepare token map
        var tokens = TemplateVars.from(cfg);

        // Post-process HTML/XML/TXT-like files: includes -> tokens -> domain updates
        try (var s = Files.walk(out)) {
            s.filter(p -> Files.isRegularFile(p))
             .forEach(p -> {
                 try {
                     String name = p.getFileName().toString();
                     boolean isText = name.endsWith(".html") || name.endsWith(".xml") || name.endsWith(".txt") || name.endsWith(".webmanifest");
                     if (!isText) return;
                     String orig = Files.readString(p, StandardCharsets.UTF_8);
                     String t = expandIncludes(orig, src);
                     var local = new java.util.LinkedHashMap<>(tokens);
                     // Page tokens
                     var rel = out.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                     String pagePath = "/" + rel;
                     local.put("PAGE_PATH", pagePath);
                     local.put("PAGE_URL", cfg.domain().replaceAll("/$", "") + pagePath);
                     if (!local.containsKey("PAGE_DESCRIPTION") || local.get("PAGE_DESCRIPTION") == null || local.get("PAGE_DESCRIPTION").isBlank()) {
                         local.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                     }
                     // Sidecar meta: <filename>.meta.json
                     var meta = p.resolveSibling(name + ".meta.json");
                     if (java.nio.file.Files.exists(meta)) {
                         String mj = java.nio.file.Files.readString(meta, StandardCharsets.UTF_8);
                         var mm = FlatJson.parse(mj);
                         for (var e : mm.entrySet()) {
                             String k = e.getKey() == null ? "" : e.getKey().trim();
                             String v = e.getValue();
                             if (!k.isEmpty() && v != null && !v.isBlank()) {
                                 local.put(k.toUpperCase(), v);
                             }
                         }
                     }
                     String nt = TokenEngine.apply(t, local);
                     nt = DomainUpdater.apply(nt, cfg);
                     if (!nt.equals(orig)) {
                         if (dryRun) io.site.bloggen.util.Log.debug("[dry-run] would update: " + p);
                         else Files.writeString(p, nt, StandardCharsets.UTF_8);
                     }
                 } catch (IOException e) { throw new RuntimeException(e); }
             });
        }
        // Remove sidecar meta files from output (only when not dry-run)
        if (!dryRun) {
            try (var s2 = Files.walk(out)) {
                s2.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".meta.json")).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
            // Remove root docs and config not needed in dist
            try { Files.deleteIfExists(out.resolve("AGENTS.md")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(out.resolve("DECISIONS.md")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(out.resolve("site.json")); } catch (IOException ignored) {}
            // Remove partials folder from output (includes are expanded at build time)
            try { FS.deleteTree(out.resolve("partials")); } catch (Exception ignored) {}
        }
        // feed/sitemap/robots simple domain swap
        Path feed = out.resolve("feed.xml");
        if (Files.exists(feed)) {
            String t = Files.readString(feed, StandardCharsets.UTF_8);
            String nt = t.replaceAll("https?://[^<]*/posts/", cfg.domain().replaceAll("/$", "") + "/posts/")
                         .replaceFirst("(<link>)https?://[^<]*(</link>)", "$1" + cfg.domain().replaceAll("/$", "") + "/$2");
            if (!nt.equals(t)) { if (dryRun) io.site.bloggen.util.Log.debug("[dry-run] would update feed.xml"); else Files.writeString(feed, nt, StandardCharsets.UTF_8); }
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
            if (!nt.equals(t)) { if (dryRun) io.site.bloggen.util.Log.debug("[dry-run] would update sitemap.xml"); else Files.writeString(sm, nt, StandardCharsets.UTF_8); }
        }
        Path rb = out.resolve("robots.txt");
        if (Files.exists(rb)) {
            String t = Files.readString(rb, StandardCharsets.UTF_8);
            String nt = t.replaceAll("(?m)^(Sitemap: ).*$", "$1" + cfg.domain().replaceAll("/$", "") + "/sitemap.xml");
            if (!nt.equals(t)) { if (dryRun) io.site.bloggen.util.Log.debug("[dry-run] would update robots.txt"); else Files.writeString(rb, nt, StandardCharsets.UTF_8); }
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
                         boolean isText = name.endsWith(".html") || name.endsWith(".xml") || name.endsWith(".txt") || name.endsWith(".webmanifest");
                         if (!isText) return;
                         String orig = Files.readString(p, StandardCharsets.UTF_8);
                         String t = expandIncludes(orig, src);
                         var local2 = new java.util.LinkedHashMap<>(tokens);
                         var rel2 = out.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                         String pagePath2 = "/" + rel2;
                         local2.put("PAGE_PATH", pagePath2);
                         local2.put("PAGE_URL", cfg.domain().replaceAll("/$", "") + pagePath2);
                         if (!local2.containsKey("PAGE_DESCRIPTION") || local2.get("PAGE_DESCRIPTION") == null || local2.get("PAGE_DESCRIPTION").isBlank()) {
                             local2.put("PAGE_DESCRIPTION", tokens.getOrDefault("SITE_DESCRIPTION", ""));
                         }
                        // nav current helpers
                        boolean isHome = "index.html".equals(rel2) || rel2.isEmpty();
                        boolean isAbout = "about.html".equals(rel2);
                        boolean isPosts = rel2.startsWith("posts/");
                        local2.put("HOME_CURRENT_ATTR", isHome ? "aria-current=\"page\"" : "");
                        local2.put("ABOUT_CURRENT_ATTR", isAbout ? "aria-current=\"page\"" : "");
                        local2.put("POSTS_CURRENT_ATTR", isPosts ? "aria-current=\"page\"" : "");
                         String nt2 = TokenEngine.apply(t, local2);
                         nt2 = DomainUpdater.apply(nt2, cfg);
                         if (!nt2.equals(orig)) {
                             if (dryRun) io.site.bloggen.util.Log.debug("[dry-run] would update: " + p);
                             else Files.writeString(p, nt2, StandardCharsets.UTF_8);
                         }
                     } catch (IOException e) { throw new RuntimeException(e); }
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
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile("<!--\\s*@include\\s+(?:path=)?\"?([^\"\s]+)\"?\\s*-->");
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
                        if (is != null) repl = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        else repl = "<!-- include not found: " + rel + " -->";
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
}
