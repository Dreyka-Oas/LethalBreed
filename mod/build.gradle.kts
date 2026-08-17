plugins {
    id("fabric-loom") version "1.17.12"
    java
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Development-only source set. It now holds everything that used to live scattered across main behind
// isDevelopmentEnvironment() checks: the headless verification harnesses, every profiler/counter/debug-trace
// consumer, the dev config holder (DevTestConfig/DevBounds/ConfigOverride) and its "Dev / Debug" config tab,
// and the three dev commands (/lethaldev, /lethalspawn, /lethalspecial — /lethalphase is a MAIN command, see
// command/PhaseCommand). It compiles against main but is never packaged into the shipped/remapped jar, and
// nothing here enforces that: Gradle's `jar` task packages sourceSets.main.output only, and no line in this
// file adds devSourceSet.output to it. Exclusion by omission, verified by
// `unzip -l build/libs/*.jar | grep lethalbreed/dev/` → nothing. It is added to the runClient/runServer
// classpath, so all of the above runs under `gradlew runServer` / `gradlew runClient` only.
//
// main keeps exactly one seam back into this source set: com.dreykaoas.lethalbreed.probe.DevProbe. Timings,
// counters and traces measure things that happen INSIDE main-source code (a tick, a stage, a hit), so they
// cannot be observed purely from dev — main has to call out to record them. DevProbe is that single call-out:
// a volatile sink field plus cheap gate checks, wired up by DevBootstrap (in dev) through the existing
// devHook("install") reflective call, so a player jar pays only an idle volatile read per gated call site and
// none of the actual measurement code, which never ships.
val devSourceSet: SourceSet = sourceSets.create("dev") {
    java.srcDir("src/dev/java")
    // Compile/run against everything main sees (Minecraft, Fabric loader/API, JOCL) plus main's own classes.
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// The dev source set holds the headless harnesses plus everything they exercise (instrumentation, dev config,
// dev commands), and until now nothing could unit-test any of it: every rule in there (how long to wait for a
// chunk, when a measurement is vacuous, when a counter or trace gate should fire) was only ever exercised by
// starting a real dedicated server, which takes minutes and cannot force the interesting timings. Putting
// dev's output on the test classpath makes its PURE logic — the classes that take no Minecraft type — reachable
// from plain JUnit. It changes nothing about packaging: the shipped jar is still built from `main` alone.
sourceSets.test {
    compileClasspath += devSourceSet.output
    runtimeClasspath += devSourceSet.output
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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Only the MAIN source set ships, so only it is stripped: dev and test keep full -g for IDE debugging.
// Gradle's default is `debug = true` with a null debugLevel, which hands javac a bare `-g`
// (= source,lines,vars). Dropping `vars` removes LocalVariableTable/LocalVariableTypeTable — the local
// variable names that make a decompiled jar read like the original source. SourceFile and LineNumberTable
// are kept on purpose: without them every player crash report says "(Unknown Source)".
tasks.named<JavaCompile>("compileJava") {
    options.debugOptions.debugLevel = "source,lines"
}

// Measurement benches are opt-in. They are not assertion tests — they print tables that no automated run
// reads — and FlowFieldPerfBench alone cost 2.5 s of the suite's 3.2 s. Gating them on a system property
// rather than on @Disabled keeps them genuinely runnable: `--tests` selects tests, it does not re-enable a
// disabled one, and the test JVM is forked so a bare -D on the Gradle command line never reaches it.
//   ./gradlew test -Plb.bench=true --tests "com.dreykaoas.lethalbreed.ai.flowfield.FlowFieldPerfBench"
val benchEnabled = providers.gradleProperty("lb.bench").orElse("false")

tasks.test {
    useJUnitPlatform()
    systemProperty("lb.bench", benchEnabled.get())
}

// ---- Kernel packaging: copy the OpenCL source to the .clx resource name expected by GpuContext ----
// The loader reads /kernels/bellman_ford.clx. We simply copy the plaintext .cl to that name (no
// transformation) and exclude the raw .cl so there is exactly one copy in the jar.
tasks.processResources {
    // Exclude the raw .cl from the output; the .clx copy is written by doLast below.
    exclude("kernels/*.cl")
    doLast {
        // Truncate each line at its first `//` — the kernel has 23 comment-bearing lines and 10 of them are
        // TRAILING comments on live code (18-22, 27, 36, 45, 61, 68), so dropping whole lines would delete
        // working kernel code. There are no block comments and no `//` inside any string literal, so
        // first-`//` truncation is safe. readText/writeText, not readBytes: line 1 holds a U+2014 em dash.
        val raw = file("src/main/resources/kernels/bellman_ford.cl").readText(Charsets.UTF_8)
        val stripped = raw.lineSequence()
                .map { line -> val i = line.indexOf("//"); if (i >= 0) line.substring(0, i) else line }
                .map { it.trimEnd() }
                .filter { it.isNotEmpty() }
                .joinToString("\n", postfix = "\n")
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        outDir.resolve("bellman_ford.clx").writeText(stripped, Charsets.UTF_8)
        logger.lifecycle("[kernel] bellman_ford.cl -> .clx, comments stripped " +
                "(${raw.toByteArray(Charsets.UTF_8).size} -> ${stripped.toByteArray(Charsets.UTF_8).size} bytes)")
    }
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
            source(devSourceSet) // dev harnesses on the dedicated-server run classpath (gradlew runServer)
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

// `gradlew build` produces exactly one artifact: build/libs/lethalbreed-<version>.jar, the player jar.
// build/devlibs holds Loom's unmapped intermediate — an implementation detail of remapJar, never shipped.
// No sources jar, no javadoc jar, no dev flavour: dev tooling lives in src/dev and runs under runClient/
// runServer only. Source is MIT in a private repo, so the jar is unobfuscated by choice.
