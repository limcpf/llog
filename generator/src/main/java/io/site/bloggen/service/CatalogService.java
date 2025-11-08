package io.site.bloggen.service;

import io.site.bloggen.core.Post;
import io.site.bloggen.core.SiteConfig;
import io.site.bloggen.template.TemplateVars;
import io.site.bloggen.template.TokenEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CatalogService {
    public io.site.bloggen.util.Result<Void> generate(Path src, Path out, SiteConfig cfg, boolean dryRun) {
        try {
            var posts = new ContentScanner().scanPosts(src);
            var tokens = TemplateVars.from(cfg);
            // homepage (index.html) — ensure root has an index
            try {
                java.nio.file.Path homeOut = out.resolve("index.html");
                if (!java.nio.file.Files.exists(homeOut)) {
                    String homeTpl = loadResource("/templates/index.html");
                    String homeHtml = TokenEngine.apply(homeTpl, tokens);
                    write(homeOut, homeHtml, dryRun);
                }
            } catch (IOException e) {
                // If template missing, continue without failing build
            }
            // posts/index.html
            String postsTpl = loadResource("/templates/posts/index.html");
            int pageSize = postsPageSize(cfg);
            if (pageSize > 0 && posts.size() > pageSize) {
                int totalPages = (int) Math.ceil(posts.size() / (double) pageSize);
                for (int page = 1; page <= totalPages; page++) {
                    int from = (page - 1) * pageSize;
                    int to = Math.min(from + pageSize, posts.size());
                    List<Post> slice = posts.subList(from, to);
                    String postsSections = buildYearSections(slice);
                    String pagination = buildPaginationNav(page, totalPages, cfg, i -> pageHref("/posts", i));
                    String postsHtml = postsTpl.replace("{{POSTS_SECTIONS}}", postsSections)
                                               .replace("{{POSTS_PAGINATION}}", pagination);
                    var local = new LinkedHashMap<>(tokens);
                    local.put("POSTS_CANONICAL_PATH", pageHref("/posts", page));
                    postsHtml = TokenEngine.apply(postsHtml, local);
                    Path target = page == 1 ? out.resolve("posts/index.html")
                                             : out.resolve("posts/page/" + page + "/index.html");
                    write(target, postsHtml, dryRun);
                }
            } else {
                String postsSections = buildYearSections(posts);
                String postsHtml = postsTpl.replace("{{POSTS_SECTIONS}}", postsSections)
                                           .replace("{{POSTS_PAGINATION}}", "");
                var local = new LinkedHashMap<>(tokens);
                local.put("POSTS_CANONICAL_PATH", pageHref("/posts", 1));
                postsHtml = TokenEngine.apply(postsHtml, local);
                write(out.resolve("posts/index.html"), postsHtml, dryRun);
            }

            // archives.html
            String archTpl = loadResource("/templates/archives.html");
            int archPageSize = archivesPageSize(cfg);
            if (archPageSize > 0 && posts.size() > archPageSize) {
                int total = (int) Math.ceil(posts.size() / (double) archPageSize);
                for (int page = 1; page <= total; page++) {
                    int from = (page - 1) * archPageSize;
                    int to = Math.min(from + archPageSize, posts.size());
                    List<Post> slice = posts.subList(from, to);
                    String sect = buildYearSections(slice);
                    String pagination = buildPaginationNav(page, total, cfg, i -> archivesHref(i));
                    String html = archTpl.replace("{{ARCHIVE_SECTIONS}}", sect)
                                         .replace("{{ARCHIVE_PAGINATION}}", pagination);
                    var local = new LinkedHashMap<>(tokens);
                    local.put("ARCHIVES_CANONICAL_PATH", archivesHref(page));
                    html = TokenEngine.apply(html, local);
                    Path target = page == 1 ? out.resolve("archives.html") : out.resolve("archives/page/" + page + "/index.html");
                    write(target, html, dryRun);
                }
            } else {
                String sect = buildYearSections(posts);
                String html = archTpl.replace("{{ARCHIVE_SECTIONS}}", sect)
                                     .replace("{{ARCHIVE_PAGINATION}}", "");
                var local = new LinkedHashMap<>(tokens);
                local.put("ARCHIVES_CANONICAL_PATH", archivesHref(1));
                html = TokenEngine.apply(html, local);
                write(out.resolve("archives.html"), html, dryRun);
            }

            // tags index + pages
            Map<String, List<Post>> byTag = groupByTag(posts);
            String tagsList = buildTagsList(byTag.keySet(), cfg);
            String tagsIndexTpl = loadResource("/templates/tags/index.html");
            String tagsIndexHtml = TokenEngine.apply(tagsIndexTpl.replace("{{TAGS_LIST}}", tagsList), tokens);
            write(out.resolve("tags/index.html"), tagsIndexHtml, dryRun);
            // tag pages
            String tagTpl = loadResource("/templates/tags/tag-template.html");
            int tagPageSize = tagsPageSize(cfg);
            for (String tag : byTag.keySet()) {
                List<Post> tagged = byTag.get(tag);
                int total = tagPageSize > 0 ? (int) Math.ceil(tagged.size() / (double) tagPageSize) : 1;
                total = Math.max(total, 1);
                for (int page = 1; page <= total; page++) {
                    List<Post> slice = tagged;
                    if (tagPageSize > 0 && tagged.size() > tagPageSize) {
                        int from = (page - 1) * tagPageSize;
                        int to = Math.min(from + tagPageSize, tagged.size());
                        slice = tagged.subList(from, to);
                    }
                    String items = buildPostsList(slice);
                    String pagination = buildPaginationNav(page, total, cfg, i -> tagHref(tag, i));
                    var local = new LinkedHashMap<>(tokens);
                    local.put("TAG_NAME", tagLabel(tag, cfg));
                    local.put("TAG_SLUG", tag);
                    local.put("TAG_CANONICAL_PATH", tagHref(tag, page));
                    String html = tagTpl.replace("{{TAG_POSTS}}", items)
                                        .replace("{{TAG_PAGINATION}}", pagination);
                    html = TokenEngine.apply(html, local);
                    Path target = page == 1 ? out.resolve("tags/" + tag + ".html") : out.resolve("tags/" + tag + "/page/" + page + "/index.html");
                    write(target, html, dryRun);
                }
            }

            // feed.xml
            String feedTpl = loadResource("/templates/feed.xml");
            String feedItems = buildFeedItems(posts, cfg);
            String feedXml = TokenEngine.apply(feedTpl.replace("{{FEED_ITEMS}}", feedItems), tokens);
            write(out.resolve("feed.xml"), feedXml, dryRun);

            // sitemap.xml
            String smTpl = loadResource("/templates/sitemap.xml");
            String urls = buildSitemapUrls(posts, cfg);
            String smXml = TokenEngine.apply(smTpl.replace("{{SITEMAP_POST_URLS}}", urls), tokens);
            write(out.resolve("sitemap.xml"), smXml, dryRun);

            return io.site.bloggen.util.Result.ok(null);
        } catch (IOException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, e.getMessage());
        }
    }

    private static String loadResource(String path) throws IOException {
        try (var is = CatalogService.class.getResourceAsStream(path)) {
            if (is == null) throw new IOException("Missing resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(Path path, String content, boolean dryRun) throws IOException {
        if (dryRun) { io.site.bloggen.util.Log.info("[dry-run] would write " + path); return; }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String buildYearSections(List<Post> posts) {
        Map<String, List<Post>> byYear = new TreeMap<>(Comparator.reverseOrder());
        for (Post p : posts) byYear.computeIfAbsent(p.year(), k -> new ArrayList<>()).add(p);
        StringBuilder sb = new StringBuilder();
        for (var e : byYear.entrySet()) {
            String y = e.getKey();
            sb.append("        <section class=\"u-flow\" aria-labelledby=\"y").append(y).append("\">\n");
            sb.append("          <h2 id=\"y").append(y).append("\">").append(y).append("</h2>\n");
            sb.append("          <ul>\n");
            for (Post p : e.getValue()) {
                sb.append("            <li>\n");
                sb.append("              <time datetime=\"").append(p.date()).append("\">").append(p.date()).append("</time>\n");
                sb.append("              — <a href=\"").append(p.url()).append("\">").append(escape(p.title())).append("</a>\n");
                sb.append("            </li>\n");
            }
            sb.append("          </ul>\n");
            sb.append("        </section>\n");
        }
        return sb.toString();
    }

    private static String buildTagsList(Set<String> tags, SiteConfig cfg) {
        List<String> sorted = new ArrayList<>(tags);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (String t : sorted) {
            String label = tagLabel(t, cfg);
            sb.append("          <li><a href=\"/tags/").append(t).append(".html\">").append(escape(label)).append("</a></li>\n");
        }
        return sb.toString();
    }

    private static Map<String, List<Post>> groupByTag(List<Post> posts) {
        Map<String, List<Post>> by = new TreeMap<>();
        for (Post p : posts) {
            if (p.tags().isEmpty()) by.computeIfAbsent("untagged", k->new ArrayList<>()).add(p);
            for (String t : p.tags()) by.computeIfAbsent(t, k->new ArrayList<>()).add(p);
        }
        // sort each list by date desc
        for (var list : by.values()) list.sort(Comparator.comparing(Post::date).reversed());
        return by;
    }

    private static String buildPostsList(List<Post> posts) {
        StringBuilder sb = new StringBuilder();
        for (Post p : posts) {
            sb.append("          <li>\n");
            sb.append("            <time datetime=\"").append(p.date()).append("\">").append(p.date()).append("</time>\n");
            sb.append("            — <a href=\"").append(p.url()).append("\">").append(escape(p.title())).append("</a>\n");
            sb.append("          </li>\n");
        }
        return sb.toString();
    }

    private static String buildFeedItems(List<Post> posts, SiteConfig cfg) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter rfc822 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"));
        for (Post p : posts) {
            sb.append("    <item>\n");
            sb.append("      <title>").append(escape(p.title())).append("</title>\n");
            sb.append("      <link>").append(cfg.domain().replaceAll("/$", "")).append(p.url()).append("</link>\n");
            sb.append("      <guid isPermaLink=\"true\">").append(cfg.domain().replaceAll("/$", "")).append(p.url()).append("</guid>\n");
            sb.append("      <pubDate>").append(rfc822.format(p.date().atStartOfDay(ZoneId.of("Asia/Seoul")))).append("</pubDate>\n");
            if (p.description() != null && !p.description().isBlank())
                sb.append("      <description>").append(escape(p.description())).append("</description>\n");
            sb.append("    </item>\n");
        }
        return sb.toString();
    }

    private static String buildSitemapUrls(List<Post> posts, SiteConfig cfg) {
        String base = cfg.domain().replaceAll("/$", "");
        StringBuilder sb = new StringBuilder();
        for (Post p : posts) {
            sb.append("  <url><loc>").append(base).append(p.url()).append("</loc></url>\n");
        }
        return sb.toString();
    }

    private static int pageSize(SiteConfig cfg) {
        String v = cfg.extras().getOrDefault("posts_page_size", "10");
        try { return Math.max(0, Integer.parseInt(v.trim())); } catch (NumberFormatException e) { return 10; }
    }

    private static String buildPaginationNav(int page, int totalPages, SiteConfig cfg, java.util.function.IntFunction<String> linkFor) {
        if (totalPages <= 1) return "";
        String prevLabel = cfg.extras().getOrDefault("pagination_prev_label", "이전");
        String nextLabel = cfg.extras().getOrDefault("pagination_next_label", "다음");
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"c-pagination\" aria-label=\"페이지네이션\">");
        if (page > 1) {
            sb.append("<a class=\"c-pagination__prev\" rel=\"prev\" href=\"")
              .append(linkFor.apply(page - 1)).append("\">").append(escape(prevLabel)).append("</a>");
            sb.append(" ");
        }
        // Determine which page numbers to show: 1, last, current±1, and neighbors near edges
        java.util.LinkedHashSet<Integer> show = new java.util.LinkedHashSet<>();
        show.add(1);
        show.add(totalPages);
        for (int i = page - 1; i <= page + 1; i++) if (i >= 1 && i <= totalPages) show.add(i);
        if (page <= 3) show.add(2);
        if (page >= totalPages - 2) show.add(totalPages - 1);
        java.util.List<Integer> list = new java.util.ArrayList<>(show);
        java.util.Collections.sort(list);
        int prev = 0;
        for (int i : list) {
            if (prev != 0 && i - prev > 1) {
                sb.append("<span class=\"c-pagination__ellipsis\">…</span> ");
            }
            if (i == page) sb.append("<strong class=\"is-current\">").append(i).append("</strong>");
            else sb.append("<a href=\"").append(linkFor.apply(i)).append("\">").append(i).append("</a>");
            sb.append(" ");
            prev = i;
        }
        if (page < totalPages) {
            sb.append("<a class=\"c-pagination__next\" rel=\"next\" href=\"")
              .append(linkFor.apply(page + 1)).append("\">").append(escape(nextLabel)).append("</a>");
        }
        sb.append("</nav>");
        return sb.toString();
    }

    private static String pageHref(String base, int page) {
        if ("/archives".equals(base)) return page <= 1 ? "/archives.html" : "/archives/page/" + page + "/";
        return page <= 1 ? base + "/" : base + "/page/" + page + "/";
    }

    private static String archivesHref(int page) { return pageHref("/archives", page); }
    private static String tagHref(String tag, int page) { return page <= 1 ? "/tags/" + tag + ".html" : "/tags/" + tag + "/page/" + page + "/"; }

    private static int postsPageSize(SiteConfig cfg) { return pageSize(cfg); }
    private static int archivesPageSize(SiteConfig cfg) {
        String v = cfg.extras().getOrDefault("archives_page_size", cfg.extras().getOrDefault("posts_page_size", "0"));
        try { return Math.max(0, Integer.parseInt(v.trim())); } catch (NumberFormatException e) { return 0; }
    }
    private static int tagsPageSize(SiteConfig cfg) {
        String v = cfg.extras().getOrDefault("tags_page_size", cfg.extras().getOrDefault("posts_page_size", "0"));
        try { return Math.max(0, Integer.parseInt(v.trim())); } catch (NumberFormatException e) { return 0; }
    }

    private static String tagLabel(String tag, SiteConfig cfg) {
        String mapStr = cfg.extras().getOrDefault("tag_labels", "");
        if (!mapStr.isBlank()) {
            String[] pairs = mapStr.split(",");
            for (String p : pairs) {
                String[] kv = p.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().toLowerCase();
                    String val = kv[1].trim();
                    if (k.equals(tag)) return val.isEmpty() ? tag : val;
                }
            }
        }
        if ("untagged".equals(tag)) {
            String ut = cfg.extras().getOrDefault("untagged_label", "미지정");
            return ut.isBlank() ? tag : ut;
        }
        return tag;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
