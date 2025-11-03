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
                        java.nio.file.Path rel = absSrc.relativize(dir);
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
                        java.nio.file.Path rel = absSrc.relativize(file);
                        java.nio.file.Path target = absOut.resolve(rel);
                        java.nio.file.Files.createDirectories(target.getParent());
                        java.nio.file.Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
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
