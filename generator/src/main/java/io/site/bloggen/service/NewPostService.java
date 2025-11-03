package io.site.bloggen.service;

import io.site.bloggen.infra.FS;
import io.site.bloggen.infra.Slug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class NewPostService {
    public io.site.bloggen.util.Result<Path> create(Path root, String title, LocalDate date, String slug, boolean dryRun, boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        try {
            if (date == null) date = LocalDate.now();
            if (slug == null || slug.isBlank()) slug = Slug.of(title);
            String fname = String.format("%s-%s.html", date, slug);
            Path dir = root.resolve("posts");
            FS.ensureDir(dir);
            Path path = dir.resolve(fname);
            String tpl = loadTemplate();
            String html = tpl.replace("{{TITLE}}", escape(title))
                    .replace("{{DATE}}", date.toString());
            if (dryRun) {
                io.site.bloggen.util.Log.info("[dry-run] would create post: " + path);
                io.site.bloggen.util.Log.info("[dry-run] would create meta:  " + dir.resolve(fname + ".meta.json"));
            } else {
                Files.writeString(path, html, StandardCharsets.UTF_8);
                // Optional sidecar meta
                Path meta = dir.resolve(fname + ".meta.json");
                String metaJson = "{\n  \"PAGE_DESCRIPTION\": \"\",\n  \"OG_IMAGE\": \"\"\n}\n";
                Files.writeString(meta, metaJson, StandardCharsets.UTF_8);
            }
            return io.site.bloggen.util.Result.ok(path);
        } catch (IOException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, e.getMessage());
        }
    }

    private String loadTemplate() throws IOException {
        try (var is = getClass().getResourceAsStream("/templates/posts/post-template.html")) {
            if (is == null) throw new IOException("Missing post template");
            return new String(is.readAllBytes());
        }
    }

    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
