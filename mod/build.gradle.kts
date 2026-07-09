import net.fabricmc.loom.task.RemapJarTask
import proguard.gradle.ProGuardTask

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        // ProGuard runs as a post-remap step to obfuscate/strip the shipped player jar.
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    id("fabric-loom") version "1.17.12"
    java
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Development-only source set: headless test harnesses + the /lethalspawn load-test command. It compiles
// against main but is NEVER packaged into the shipped/remapped jar (see below), so a production jar contains
// zero test/dev code and stays as light as possible. It is added to the runClient/runServer classpath so the
// harnesses run under `gradlew runServer` / start.bat only.
val devSourceSet: SourceSet = sourceSets.create("dev") {
    java.srcDir("src/dev/java")
    // Compile/run against everything main sees (Minecraft, Fabric loader/API, JOCL) plus main's own classes.
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // GPU compute (OpenCL via JOCL). Self-contained: JOCL bundles its native libs and loads them at
    // runtime. `include` packages it into the remapped jar so a dedicated server needs no extra dep.
    implementation("org.jocl:jocl:2.0.5")
    include("org.jocl:jocl:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

// ---- Kernel obfuscation: XOR-encrypt the OpenCL source in the packaged resources ----
// The plaintext bellman_ford.cl (the flow-field pathfinding algorithm) is never shipped. This task
// runs inside processResources: it reads the source .cl, XORs it with the same 32-byte key baked into
// GpuContext.K, writes bellman_ford.clx, and drops the original so only the encrypted form is in the jar.
val kernelKey = intArrayOf(
    0x9E, 0x2C, 0xB7, 0x41, 0xD3, 0x6A, 0x1F, 0x88,
    0x53, 0xE0, 0x0D, 0xAA, 0x74, 0xC5, 0x36, 0xF1,
    0x2B, 0x9D, 0x60, 0x18, 0xBE, 0x47, 0xD2, 0x05,
    0x8C, 0x33, 0xE7, 0x7A, 0xA1, 0x1C, 0xF8, 0x49,
).map { it.toByte() }.toByteArray()

tasks.processResources {
    // Exclude the plaintext kernel from the output; the encrypted .clx is written by doLast below.
    exclude("kernels/*.cl")
    doLast {
        val src = file("src/main/resources/kernels/bellman_ford.cl").readBytes()
        val enc = ByteArray(src.size) { i -> (src[i].toInt() xor kernelKey[i and 31].toInt()).toByte() }
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        outDir.resolve("bellman_ford.clx").writeBytes(enc)
        logger.lifecycle("[kernel] encrypted bellman_ford.cl -> .clx (${enc.size} bytes)")
    }
}

// ---- Two build flavours ----
// The default `remapJar` (→ `build`) packages ONLY the main source set: the shipped PLAYER jar, with zero
// dev/test code. The `remapDevJar` task below packages main + the dev source set (harnesses + /lethaldev &
// /lethalspawn commands): the DEVELOPER jar. Use build-player.bat / build-dev.bat to produce each.
val devJar = tasks.register<Jar>("devJar") {
    archiveClassifier.set("dev-unmapped")
    from(sourceSets.main.get().output)
    from(devSourceSet.output)
}

val remapDevJar = tasks.register<RemapJarTask>("remapDevJar") {
    dependsOn(devJar)
    inputFile.set(devJar.flatMap { it.archiveFile })
    archiveClassifier.set("dev")
    addNestedDependencies.set(true)
}

loom {
    runs {
        named("client") {
            runDir("run") // primary client keeps the default run dir
            source(devSourceSet) // dev harnesses on the client run classpath (dev env only)
            // Optimized JVM args for Liberica NIK 23 (GraalVM JIT) + tuned G1GC.
            // Conservative set: forces Graal as top-tier JIT, G1 with aikar-style tuning.
            vmArgs(
                "-Xms2G",
                "-Xmx4G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50",
                "-XX:G1HeapWastePercent=5",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:+AlwaysActAsServerClassMachine"
            )
            // Auto-load the test world on launch (skip menus) — like StormCore.
            // World dir must exist under run/saves/ with this exact name; if absent,
            // MC drops to the menu (no crash). Create it once, then it auto-enters.
            programArgs("--quickPlaySingleplayer", "Nouveau monde")
        }
        // Second dev client for local multiplayer tests: its OWN run dir (so it never fights the
        // primary client / server over run/.fabric/processedMods) and it auto-connects to the local
        // dedicated server on launch. Username is overridden so the two offline clients don't collide.
        create("client2") {
            client()
            runDir("run/client2")
            configName = "Minecraft Client 2"
            source(devSourceSet)
            vmArgs("-Xms2G", "-Xmx4G", "-XX:+UseG1GC")
            programArgs("--username", "Tester2", "--quickPlayMultiplayer", "localhost:25565")
        }
        // First multiplayer test client (own run dir, auto-joins the local server as "Tester1").
        create("client1") {
            client()
            runDir("run/client1")
            configName = "Minecraft Client 1"
            source(devSourceSet)
            vmArgs("-Xms2G", "-Xmx4G", "-XX:+UseG1GC")
            programArgs("--username", "Tester1", "--quickPlayMultiplayer", "localhost:25565")
        }
        named("server") {
            runDir("run/server") // dedicated server gets its own run dir under run/ — no lock war with the clients
            source(devSourceSet) // dev harnesses on the dedicated-server run classpath (start.bat / runServer)
            vmArgs(
                "-Xms2G",
                "-Xmx6G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:+AlwaysActAsServerClassMachine"
            )
        }
    }
}

// ---- Obfuscation: strip debug + rename internals on the shipped PLAYER jar ----
// Runs after Loom's remapJar. Input = the remapped named jar; output = an obfuscated jar that
// replaces build/libs/<name>-<ver>.jar. Keep rules in proguard-rules.pro preserve everything the
// Fabric loader, Mixin, reflection (ConfigSchema) and serialization reference by name.
val proguardJar = tasks.register<ProGuardTask>("proguardJar") {
    val remap = tasks.named<RemapJarTask>("remapJar")
    dependsOn(remap)

    val inJar = remap.flatMap { it.archiveFile }
    val outJar = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}-obf.jar")

    injars(inJar)
    outjars(outJar)

    // Library jars: the JDK modules + every jar on the runtime classpath (Minecraft, Fabric, JOCL).
    // ProGuard needs these to resolve supertypes it must NOT rename.
    val javaHome = System.getProperty("java.home")
    libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        "$javaHome/jmods/java.base.jmod")
    libraryjars(configurations.named("runtimeClasspath"))

    configuration("proguard-rules.pro")

    doLast {
        // Overwrite the canonical player jar with the obfuscated one so build-player.bat ships it.
        val canonical = layout.buildDirectory.file(
            "libs/${base.archivesName.get()}-${project.version}.jar").get().asFile
        val obf = outJar.get().asFile
        canonical.delete()
        obf.copyTo(canonical, overwrite = true)
        obf.delete()
        logger.lifecycle("[proguard] obfuscated player jar -> ${canonical.name}")
    }
}

// `gradlew build` (and build-player.bat) now produce the obfuscated player jar.
tasks.named("build") {
    dependsOn(proguardJar)
}
