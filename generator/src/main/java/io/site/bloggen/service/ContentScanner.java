package io.site.bloggen.service;

import io.site.bloggen.core.Post;
import io.site.bloggen.util.FlatJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentScanner {
    private static final Pattern FILE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-(.+)\\.html$");
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public List<Post> scanPosts(Path srcRoot) throws IOException {
        Path dir = srcRoot.resolve("posts");
        if (!Files.exists(dir)) return List.of();
        List<Post> posts = new ArrayList<>();
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.getFileName().toString().endsWith(".html"))::iterator) {
                String name = p.getFileName().toString();
                Matcher fm = FILE.matcher(name);
                if (!fm.matches()) continue;
                LocalDate date = LocalDate.parse(fm.group(1), DateTimeFormatter.ISO_LOCAL_DATE);
                String slug = fm.group(2);
                String url = "/posts/" + name;
                String html = Files.readString(p, StandardCharsets.UTF_8);
                String title = extractTitle(html);
                // sidecar meta
                String desc = "";
                List<String> tags = new ArrayList<>();
                Path meta = p.resolveSibling(name + ".meta.json");
                if (Files.exists(meta)) {
                    String mj = Files.readString(meta, StandardCharsets.UTF_8);
                    var mm = FlatJson.parse(mj);
                    desc = mm.getOrDefault("PAGE_DESCRIPTION", "");
                    var tagStr = mm.getOrDefault("TAGS", "");
                    if (tagStr != null && !tagStr.isBlank()) {
                        for (String t : tagStr.split(",")) {
                            var tt = t.trim();
                            if (!tt.isEmpty()) tags.add(slugify(tt));
                        }
                    }
                }
                posts.add(new Post(name, url, date, title, List.copyOf(tags), desc));
            }
        }
        posts.sort(Comparator.comparing(Post::date).reversed());
        return posts;
    }

    private static String extractTitle(String html) {
        Matcher m = H1.matcher(html);
        if (m.find()) return stripTags(m.group(1)).trim();
        m = TITLE.matcher(html);
        if (m.find()) return stripTags(m.group(1)).trim();
        return "Untitled";
    }
    private static String stripTags(String s) { return s.replaceAll("<[^>]+>", ""); }
    private static String slugify(String s) { return s.toLowerCase().replaceAll("[^a-z0-9\\-\\s]", "").trim().replaceAll("\\s+", "-"); }
}

