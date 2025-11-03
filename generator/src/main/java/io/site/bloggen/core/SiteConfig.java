package io.site.bloggen.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SiteConfig(String domain, String siteName, String rssTitle, String ogDefault, Map<String,String> extras) {
    public static SiteConfig ofDefaults() {
        Map<String,String> extras = new HashMap<>();
        extras.put("site_description", "고전의 숨, 편안한 읽기");
        extras.put("contact_email", "hello@example.com");
        extras.put("nav_home_label", "홈");
        extras.put("nav_about_label", "소개");
        extras.put("nav_posts_label", "글");
        extras.put("posts_page_size", "10");
        extras.put("archive_more_label", "더 보기: 아카이브");
        extras.put("pagination_prev_label", "이전");
        extras.put("pagination_next_label", "다음");
        extras.put("copyright", ""); // empty -> computed as default
        return new SiteConfig("https://site.example", "고전서가", "고전서가", "/og/default.jpg", extras);
    }

    public static SiteConfig fromJson(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        Map<String,String> map = parseFlatJson(json);
        return new SiteConfig(
                map.getOrDefault("domain", "https://site.example"),
                map.getOrDefault("site_name", "고전서가"),
                map.getOrDefault("rss_title", "고전서가"),
                map.getOrDefault("og_default", "/og/default.jpg"),
                map
        );
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"domain\": \"").append(escape(domain)).append("\",\n");
        sb.append("  \"site_name\": \"").append(escape(siteName)).append("\",\n");
        sb.append("  \"rss_title\": \"").append(escape(rssTitle)).append("\",\n");
        sb.append("  \"og_default\": \"").append(escape(ogDefault)).append("\"\n");
        for (var e : extras.entrySet()) {
            sb.append(",\n  \"").append(escape(e.getKey())).append("\": \"").append(escape(e.getValue())).append("\"");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static Map<String,String> parseFlatJson(String json) {
        Map<String,String> out = new HashMap<>();
        Pattern p = Pattern.compile("\\\"(.*?)\\\"\\s*:\\s*\\\"(.*?)\\\"");
        Matcher m = p.matcher(json);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }
}
