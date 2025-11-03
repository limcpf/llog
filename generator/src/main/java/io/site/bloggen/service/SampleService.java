package io.site.bloggen.service;

import io.site.bloggen.core.SiteConfig;
import io.site.bloggen.infra.FS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SampleService {
    public io.site.bloggen.util.Result<Path> create(Path outDir, boolean build, boolean dryRun, boolean verbose) {
        io.site.bloggen.util.Log.setVerbose(verbose);
        try {
            // 1) Initialize template into outDir
            var initRes = new InitService().init(outDir, SiteConfig.ofDefaults(), dryRun, verbose);
            if (initRes instanceof io.site.bloggen.util.Result.Err<?> err) {
                return io.site.bloggen.util.Result.err(err.code(), err.message());
            }
            // 2) Add sample markdown posts under outDir/samples-md
            Path mdDir = outDir.resolve("samples-md");
            if (!dryRun) FS.ensureDir(mdDir);
            String md1 = """
                    ---
                    title: 첫 글
                    createdDate: 2025-01-01
                    publish: true
                    ---

                    안녕하세요. 이것은 샘플 포스트입니다. Pretendard로 렌더링되는 한글 가독성을 확인해 보세요.
                    
                    - 목록 항목 하나
                    - 목록 항목 둘
                    
                    코드:
                    
                    ```html
                    <p>Hello world</p>
                    ```
                    """;
            String md2 = """
                    ---
                    title: 타이포그래피 샘플
                    createdDate: 2025-01-02
                    publish: true
                    ---

                    본문 길이, 인용문, 강조 등 기본 스타일을 확인합니다.

                    > 인용문은 이렇게 보입니다.

                    **굵게**, *기울임*, `코드` 표시 등도 점검하세요.
                    """;
            if (dryRun) {
                io.site.bloggen.util.Log.info("[dry-run] would write sample md: " + mdDir.resolve("hello.md"));
                io.site.bloggen.util.Log.info("[dry-run] would write sample md: " + mdDir.resolve("typography.md"));
            } else {
                Files.writeString(mdDir.resolve("hello.md"), md1, StandardCharsets.UTF_8);
                Files.writeString(mdDir.resolve("typography.md"), md2, StandardCharsets.UTF_8);
            }

            // 3) Import markdown into posts
            var importRes = new MdImportService().importAll(mdDir, outDir, dryRun, verbose);
            if (importRes instanceof io.site.bloggen.util.Result.Err<?> err) {
                return io.site.bloggen.util.Result.err(err.code(), err.message());
            }

            // 4) Optional build to outDir/dist
            if (build) {
                var cfgPath = outDir.resolve("site.json");
                SiteConfig cfg = Files.exists(cfgPath) ? SiteConfig.fromJson(cfgPath) : SiteConfig.ofDefaults();
                var b = new BuildService().build(outDir, outDir.resolve("dist"), cfg, dryRun, verbose);
                if (b instanceof io.site.bloggen.util.Result.Err<?> err) {
                    return io.site.bloggen.util.Result.err(err.code(), err.message());
                }
            }
            return io.site.bloggen.util.Result.ok(outDir);
        } catch (Exception e) {
            return io.site.bloggen.util.Result.err(io.site.bloggen.util.Exit.UNKNOWN, e.getMessage());
        }
    }
}
