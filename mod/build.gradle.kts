import net.fabricmc.loom.task.RemapJarTask

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

// ---- Kernel packaging: copy the OpenCL source to the .clx resource name expected by GpuContext ----
// The loader reads /kernels/bellman_ford.clx. We simply copy the plaintext .cl to that name (no
// transformation) and exclude the raw .cl so there is exactly one copy in the jar.
tasks.processResources {
    // Exclude the raw .cl from the output; the .clx copy is written by doLast below.
    exclude("kernels/*.cl")
    doLast {
        val src = file("src/main/resources/kernels/bellman_ford.cl").readBytes()
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        outDir.resolve("bellman_ford.clx").writeBytes(src)
        logger.lifecycle("[kernel] copied bellman_ford.cl -> .clx (${src.size} bytes)")
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
            // Optimized JVM args for Liberica NIK 23 (GraalVM JIT) + aggressive G1GC (Aikar-style).
            // Fixed 8G heap (no resize pauses), tight G1 pause target, pretouch + NUMA-aware — tuned for
            // max/steady FPS on a beefy dev box (62G RAM / 16 cores) rather than a small/shared machine.
            vmArgs(
                "-Xms8G",
                "-Xmx8G",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=25",
                "-XX:G1NewSizePercent=40",
                "-XX:G1MaxNewSizePercent=50",
                "-XX:G1HeapRegionSize=16M",
                "-XX:G1ReservePercent=15",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:InitiatingHeapOccupancyPercent=10",
                "-XX:SurvivorRatio=32",
                "-XX:MaxTenuringThreshold=1",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+PerfDisableSharedMem",
                "-XX:+AlwaysActAsServerClassMachine",
                "-XX:+AlwaysPreTouch",
                "-XX:+DisableExplicitGC",
                "-XX:+UseNUMA"
            )
            // Auto-load the test world on launch (skip menus) — like StormCore.
            // World dir must exist under run/saves/ with this exact name; if absent,
            // MC drops to the menu (no crash). Create it once, then it auto-enters.
            // Greenfield (huge 1:1-scale city, run/saves/Greenfield v0.5.4) makes a good stress test
            // for zombie pathing/climbing across dense multi-story buildings.
            programArgs("--quickPlaySingleplayer", "Greenfield v0.5.4")
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

// The shipped player jar is Loom's plain remapJar (no obfuscation): the source is MIT and lives in a
// private repo, so there is nothing to hide. `gradlew build` / build-player.bat produce it directly.
