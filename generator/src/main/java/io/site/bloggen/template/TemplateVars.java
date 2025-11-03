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
        String cr = ex.getOrDefault("copyright", "");
        if (cr == null || cr.isBlank()) {
            cr = "© " + m.get("YEAR") + " " + cfg.siteName();
        }
        m.put("COPYRIGHT", cr);
        return m;
    }
}
