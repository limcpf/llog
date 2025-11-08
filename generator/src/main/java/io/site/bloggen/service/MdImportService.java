package io.site.bloggen.service;

import io.site.bloggen.infra.FS;
import io.site.bloggen.infra.Slug;
import io.site.bloggen.util.FrontMatter;
import io.site.bloggen.util.Markdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Map;

public final class MdImportService {
    public io.site.bloggen.util.Result<Integer> importAll(Path mdDir, Path siteRoot, boolean dryRun, boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        int count = 0;
        try {
            if (!Files.exists(mdDir)) return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, "md path not found: " + mdDir);
            try (var walk = Files.walk(mdDir)) {
                for (Path p : (Iterable<Path>) walk.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".md"))::iterator) {
                    String md = Files.readString(p, StandardCharsets.UTF_8);
                    // detect presence of front matter block
                    String s = md.stripLeading();
                    boolean hasFM = s.startsWith("---") && s.indexOf('\n') >= 0 && s.indexOf("\n---", s.indexOf('\n')) >= 0;
                    Map<String,String> fm = FrontMatter.parse(md);
                    String publish = fm.getOrDefault("publish", "false");
                    if (!"true".equalsIgnoreCase(publish)) { if (hasFM) io.site.bloggen.util.Log.info("skip: publish!=true " + p); continue; }
                    String title = fm.getOrDefault("title", "");
                    String dateStr = fm.getOrDefault("createdDate", "");
                    if (title.isBlank() || dateStr.isBlank()) { if (hasFM) io.site.bloggen.util.Log.warn("skip: missing title/date: " + p); continue; }
                    LocalDate date;
                    try { date = LocalDate.parse(dateStr); }
                    catch (Exception ex) { if (hasFM) io.site.bloggen.util.Log.warn("skip: invalid createdDate (YYYY-MM-DD): " + p); continue; }
                    String slug = slugFromFile(p.getFileName().toString(), title);
                    String htmlContent = Markdown.toHtml(md);
                    String excerpt = Markdown.firstParagraphText(md);
                    Path out = siteRoot.resolve("posts").resolve(date + "-" + slug + ".html");
                    String tpl = loadTemplate();
                    String html = tpl.replace("{{TITLE}}", escape(title)).replace("{{DATE}}", date.toString()).replace("{{CONTENT_HTML}}", htmlContent);
                    if (dryRun) {
                        io.site.bloggen.util.Log.info("[dry-run] import: " + p + " -> " + out);
                    } else {
                        FS.ensureDir(out.getParent());
                        Files.writeString(out, html, StandardCharsets.UTF_8);
                        Path meta = out.resolveSibling(out.getFileName().toString() + ".meta.json");
                        String metaJson = "{\n  \"PAGE_DESCRIPTION\": \"" + escape(excerpt) + "\"\n}\n";
                        Files.writeString(meta, metaJson, StandardCharsets.UTF_8);
                        io.site.bloggen.util.Log.info("imported: " + p + " -> " + out);
                    }
                    count++;
                }
            }
            return io.site.bloggen.util.Result.ok(count);
        } catch (IOException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, e.getMessage());
        }
    }

    private static String loadTemplate() throws IOException {
        try (var is = MdImportService.class.getResourceAsStream("/templates/posts/post-md-template.html")) {
            if (is == null) throw new IOException("Missing post-md template");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String slugFromFile(String fileName, String titleFallback) {
        String base = fileName.replaceAll("\\.md$", "");
        String s1 = Slug.of(base);
        if (!"post".equals(s1)) return s1;
        String s2 = Slug.of(titleFallback);
        if (!"post".equals(s2)) return s2;
        return base.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
