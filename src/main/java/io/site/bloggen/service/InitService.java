package io.site.bloggen.service;

import io.site.bloggen.core.SiteConfig;
import io.site.bloggen.infra.FS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InitService {
    public io.site.bloggen.util.Result<Void> init(Path dest, SiteConfig cfg, boolean dryRun, boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        try {
            if (dryRun) {
                io.site.bloggen.util.Log.info("[dry-run] would initialize template at " + dest.toAbsolutePath());
            }
            FS.ensureDir(dest);
        // Copy classpath templates (using packaged file list)
            if (dryRun) {
                int count = countFileList();
                io.site.bloggen.util.Log.info("[dry-run] would copy ~" + count + " template files");
            } else {
                copyResourceTree("templates", dest);
                // Write site.json
                Files.writeString(dest.resolve("site.json"), cfg.toJson());
            }
            return io.site.bloggen.util.Result.ok(null);
        } catch (IOException e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.IO, e.getMessage());
        }
    }

    private void copyResourceTree(String root, Path dest) throws IOException {
        // Known paths list packaged at build-time. For simplicity, maintain a file list resource.
        try (InputStream is = getClass().getResourceAsStream("/templates/.filelist")) {
            if (is == null) throw new IOException("Missing templates file list");
            String list = new String(is.readAllBytes());
            for (String line : list.split("\n")) {
                if (line.isBlank()) continue;
                String path = "/templates/" + line.strip();
                Path target = dest.resolve(line.strip());
                if (line.endsWith("/")) {
                    FS.ensureDir(target);
                } else {
                    try (InputStream rs = getClass().getResourceAsStream(path)) {
                        if (rs == null) throw new IOException("Missing resource: " + path);
                        FS.ensureDir(target.getParent());
                        Files.write(target, rs.readAllBytes());
                    }
                }
            }
        }
    }
    private int countFileList() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/.filelist")) {
            if (is == null) return 0;
            String list = new String(is.readAllBytes());
            int c = 0;
            for (String line : list.split("\n")) {
                if (!line.isBlank() && !line.endsWith("/")) c++;
            }
            return c;
        }
    }
}
