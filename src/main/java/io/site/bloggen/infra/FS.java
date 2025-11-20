package io.site.bloggen.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Objects;

public final class FS {
    private FS() {}

    public static void ensureDir(Path dir) throws IOException {
        if (Files.notExists(dir)) Files.createDirectories(dir);
    }

    public static void copyTree(Path src, Path dst) throws IOException {
        Objects.requireNonNull(src); Objects.requireNonNull(dst);
        if (!Files.exists(src)) return;
        ensureDir(dst);
        try (var stream = Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    Path target = dst.resolve(rel);
                    if (Files.isDirectory(p)) {
                        ensureDir(target);
                    } else {
                        ensureDir(target.getParent());
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }

    public static void writeString(Path path, String content) throws IOException {
        ensureDir(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}

