package io.site.bloggen.app;

import io.site.bloggen.core.SiteConfig;
import io.site.bloggen.service.BuildService;
import io.site.bloggen.service.InitService;
import io.site.bloggen.service.NewPostService;
import io.site.bloggen.service.MdImportService;

import java.nio.file.Path;
import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp();
            System.exit(0);
        }
        String cmd = args[0];
        // global flags (best-effort per command as well)
        try {
            switch (cmd) {
                case "--version" -> { System.out.println("llog 0.2.2"); System.exit(0);} 
                case "init" -> doInit(args);
                case "build" -> doBuild(args);
                case "new:post" -> doNewPost(args);
                case "import:md" -> doImportMd(args);
                case "sample" -> doSample(args);
                default -> { System.err.println("Unknown command: " + cmd); printHelp(); System.exit(2);}    
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static void doInit(String[] args) throws Exception {
        boolean dry = false, verb = false; Path dest = null;
        for (int i=1;i<args.length;i++) {
            switch (args[i]) {
                case "--dry-run" -> dry = true;
                case "--verbose" -> verb = true;
                default -> { if (dest == null) dest = Path.of(args[i]); }
            }
        }
        if (dest == null) { System.err.println("usage: init <dir> [--dry-run] [--verbose]"); System.exit(2);} 
        var svc = new InitService();
        var res = svc.init(dest, SiteConfig.ofDefaults(), dry, verb);
        if (res instanceof io.site.bloggen.util.Result.Err<?> err) {
            io.site.bloggen.util.Log.error(err.message());
            System.exit(err.code());
        } else {
            System.out.println((dry?"[dry-run] ":"") + "Initialized template at " + dest.toAbsolutePath());
        }
    }

    static void doBuild(String[] args) throws Exception {
        Path src = Path.of(".");
        Path out = Path.of("dist");
        Path config = null; // optional explicit site.json path
        boolean dry = false, verb = false;
        for (int i=1;i<args.length;i++) {
            switch (args[i]) {
                case "--src" -> src = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--config" -> config = Path.of(args[++i]);
                case "--dry-run" -> dry = true;
                case "--verbose" -> verb = true;
            }
        }
        if (config == null) {
            String env = System.getenv("SITE_JSON");
            if (env != null && !env.isBlank()) config = Path.of(env);
        }
        Path srcCfg = src.resolve("site.json");
        var cfg = config != null && java.nio.file.Files.exists(config) ? SiteConfig.fromJson(config)
                 : java.nio.file.Files.exists(srcCfg) ? SiteConfig.fromJson(srcCfg)
                 : SiteConfig.ofDefaults();
        var svc = new BuildService();
        var res = svc.build(src, out, cfg, dry, verb);
        if (res instanceof io.site.bloggen.util.Result.Err<?> err) {
            io.site.bloggen.util.Log.error(err.message());
            System.exit(err.code());
        } else {
            System.out.println((dry?"[dry-run] ":"") + "Built site to " + out.toAbsolutePath());
        }
    }

    static void doNewPost(String[] args) throws Exception {
        String title = null; String date = null; String slug = null; Path root = Path.of("."); boolean dry=false, verb=false;
        for (int i=1;i<args.length;i++) {
            switch (args[i]) {
                case "--title" -> title = args[++i];
                case "--date" -> date = args[++i];
                case "--slug" -> slug = args[++i];
                case "--root" -> root = Path.of(args[++i]);
                case "--dry-run" -> dry = true;
                case "--verbose" -> verb = true;
            }
        }
        if (title == null || title.isBlank()) { System.err.println("--title is required"); System.exit(2);} 
        var svc = new NewPostService();
        var res = svc.create(root, title, date != null ? LocalDate.parse(date) : null, slug, dry, verb);
        if (res instanceof io.site.bloggen.util.Result.Err<?> err) {
            io.site.bloggen.util.Log.error(err.message());
            System.exit(err.code());
        } else {
            var p = ((io.site.bloggen.util.Result.Ok<java.nio.file.Path>)res).value();
            System.out.println((dry?"[dry-run] ":"") + "Created post " + p);
        }
    }

    static void printHelp() {
        System.out.println("llog 0.2.2");
        System.out.println("Usage:");
        System.out.println("  init <dir> [--dry-run] [--verbose]");
        System.out.println("  build [--src dir] [--out dir] [--config path] [--dry-run] [--verbose]");
        System.out.println("  new:post --title \"...\" [--date YYYY-MM-DD] [--slug slug] [--root dir] [--dry-run] [--verbose]");
        System.out.println("  import:md --src <md_dir> [--root dir] [--dry-run] [--verbose]");
        System.out.println("  sample [--out dir] [--build] [--dry-run] [--verbose]");
        System.out.println("  --help | --version");
    }

    static void doImportMd(String[] args) throws Exception {
        Path md = null; Path root = Path.of("."); boolean dry=false, verb=false;
        for (int i=1;i<args.length;i++) {
            switch (args[i]) {
                case "--src" -> md = Path.of(args[++i]);
                case "--root" -> root = Path.of(args[++i]);
                case "--dry-run" -> dry = true;
                case "--verbose" -> verb = true;
            }
        }
        if (md == null) { System.err.println("usage: import:md --src <md_dir> [--root dir] [--dry-run] [--verbose]"); System.exit(2);} 
        var svc = new MdImportService();
        var res = svc.importAll(md, root, dry, verb);
        if (res instanceof io.site.bloggen.util.Result.Err<?> err) {
            io.site.bloggen.util.Log.error(err.message());
            System.exit(err.code());
        } else {
            int n = ((io.site.bloggen.util.Result.Ok<Integer>)res).value();
            System.out.println((dry?"[dry-run] ":"") + "Imported " + n + " posts from markdown");
        }
    }

    static void doSample(String[] args) throws Exception {
        Path out = Path.of("sample-site"); boolean build=false, dry=false, verb=false;
        for (int i=1;i<args.length;i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(args[++i]);
                case "--build" -> build = true;
                case "--dry-run" -> dry = true;
                case "--verbose" -> verb = true;
            }
        }
        var svc = new io.site.bloggen.service.SampleService();
        var res = svc.create(out, build, dry, verb);
        if (res instanceof io.site.bloggen.util.Result.Err<?> err) {
            io.site.bloggen.util.Log.error(err.message());
            System.exit(err.code());
        } else {
            var p = ((io.site.bloggen.util.Result.Ok<java.nio.file.Path>)res).value();
            System.out.println((dry?"[dry-run] ":"") + "Created sample site at " + p.toAbsolutePath());
        }
    }
}
