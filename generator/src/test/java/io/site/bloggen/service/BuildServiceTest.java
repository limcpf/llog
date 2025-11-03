package io.site.bloggen.service;

import io.site.bloggen.core.SiteConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildServiceTest {
    @Test void dryRunOk(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src");
        Path out = tmp.resolve("out");
        Files.createDirectories(src);
        Files.writeString(src.resolve("index.html"), "<link rel=\"canonical\" href=\"https://old/\"> {{SITE_NAME}}", StandardOpenOption.CREATE);
        Files.writeString(src.resolve("site.json"), SiteConfig.ofDefaults().toJson());
        var res = new BuildService().build(src, out, SiteConfig.ofDefaults(), true, false);
        assertTrue(res instanceof io.site.bloggen.util.Result.Ok<?>);
    }
}

