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
        named("server") {
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
