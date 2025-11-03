plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories { mavenCentral() }

dependencies {
    // Keep runtime deps to zero for native friendliness
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("io.site.bloggen.app.Main")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("bloggen")
            buildArgs.addAll(listOf("--no-fallback"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Unify templates with root site: copy shared assets/partials into resources before packaging
val syncTemplates by tasks.registering {
    group = "build"
    description = "Sync shared assets/partials from repo root into resources/templates and regenerate .filelist"
    doLast {
        val templatesDir = project.layout.projectDirectory.dir("src/main/resources/templates").asFile
        // Regenerate .filelist only (templates are source of truth)
        val list = StringBuilder()
        templatesDir.walkTopDown().forEach { f ->
            val rel = f.relativeTo(templatesDir).path.replace('\\', '/')
            if (rel.isEmpty()) return@forEach
            if (f.isDirectory) list.append(rel).append('/')
            else list.append(rel)
            list.append('\n')
        }
        java.io.File(templatesDir, ".filelist").writeText(list.toString())
    }
}

tasks.named("processResources") {
    dependsOn(syncTemplates)
}

// Package templates into site-skeleton tarball for releases
val makeSiteSkeleton by tasks.registering(Tar::class) {
    group = "distribution"
    description = "Create site-skeleton.tar.gz from templates"
    archiveFileName.set("site-skeleton.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    compression = Compression.GZIP
    from("src/main/resources/templates") {
        exclude(".filelist")
    }
}
