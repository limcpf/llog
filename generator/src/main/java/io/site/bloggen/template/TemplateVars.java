package io.site.bloggen.template;

import io.site.bloggen.core.SiteConfig;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TemplateVars {
    private TemplateVars() {}

    public static Map<String, String> from(SiteConfig cfg) {
        String domain = cfg.domain().replaceAll("/$", "");
        Map<String,String> m = new LinkedHashMap<>();
        m.put("DOMAIN", domain);
        m.put("SITE_NAME", cfg.siteName());
        m.put("RSS_TITLE", cfg.rssTitle());
        m.put("OG_DEFAULT", cfg.ogDefault());
        m.put("OG_IMAGE", domain + cfg.ogDefault());
        m.put("YEAR", String.valueOf(Year.now().getValue()));
        // Optional site-wide tokens
        var ex = cfg.extras();
        m.put("SITE_DESCRIPTION", ex.getOrDefault("site_description", ""));
        m.put("CONTACT_EMAIL", ex.getOrDefault("contact_email", ""));
        m.put("NAV_HOME_LABEL", ex.getOrDefault("nav_home_label", "홈"));
        m.put("NAV_ABOUT_LABEL", ex.getOrDefault("nav_about_label", "소개"));
        m.put("NAV_POSTS_LABEL", ex.getOrDefault("nav_posts_label", "글"));
        m.put("FAVICON_PATH", ex.getOrDefault("favicon_path", "/favicon.svg"));
        m.put("THEME_COLOR_LIGHT", ex.getOrDefault("theme_color_light", "#f7f3e9"));
        m.put("THEME_COLOR_DARK", ex.getOrDefault("theme_color_dark", "#151311"));
        m.put("TWITTER_CARD", ex.getOrDefault("twitter_card", "summary_large_image"));
        // Optional: Google Analytics (GA4) snippet via extras.ga_measurement_id
        String gaId = ex.getOrDefault("ga_measurement_id", "").trim();
        if (!gaId.isBlank()) {
            // send_page_view defaults to true; allow override via extras.ga_send_page_view
            String spv = ex.getOrDefault("ga_send_page_view", "true").trim();
            if (!spv.equalsIgnoreCase("true") && !spv.equalsIgnoreCase("false")) spv = "true";
            StringBuilder ga = new StringBuilder();
            ga.append("<script async src=\"https://www.googletagmanager.com/gtag/js?id=")
              .append(escapeAttr(gaId)).append("\"></script>\n");
            ga.append("<script>\n")
              .append("  window.dataLayer = window.dataLayer || [];\n")
              .append("  function gtag(){dataLayer.push(arguments);}\n")
              .append("  gtag('js', new Date());\n")
              .append("  gtag('config','").append(escapeAttr(gaId)).append("', { 'send_page_view': ")
              .append(spv.equalsIgnoreCase("true") ? "true" : "false").append(" });\n")
              .append("</script>\n");
            m.put("GA_SNIPPET", ga.toString());
        } else {
            m.put("GA_SNIPPET", "");
        }
        // Optional: Cloudflare Web Analytics (privacy-friendly)
        String cfToken = ex.getOrDefault("cf_beacon_token", "").trim();
        if (!cfToken.isBlank()) {
            StringBuilder cf = new StringBuilder();
            cf.append("<script defer src=\"https://static.cloudflareinsights.com/beacon.min.js\" data-cf-beacon='{")
              .append("\"token\":\"").append(escapeAttr(cfToken)).append("\"}"></script>\n");
            m.put("CF_SNIPPET", cf.toString());
        } else {
            m.put("CF_SNIPPET", "");
        }
        String cr = ex.getOrDefault("copyright", "");
        if (cr == null || cr.isBlank()) {
            cr = "© " + m.get("YEAR") + " " + cfg.siteName();
        }
        m.put("COPYRIGHT", cr);
        return m;
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
