package io.site.bloggen.template;

import io.site.bloggen.core.SiteConfig;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateVarsTest {
    @Test void buildsCoreTokens() {
        SiteConfig cfg = SiteConfig.ofDefaults();
        Map<String,String> m = TemplateVars.from(cfg);
        assertEquals("https://site.example", m.get("DOMAIN"));
        assertEquals(cfg.siteName(), m.get("SITE_NAME"));
        assertTrue(m.get("YEAR").matches("\\d{4}"));
        assertEquals("https://site.example" + cfg.ogDefault(), m.get("OG_IMAGE"));
    }
}

