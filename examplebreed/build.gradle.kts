plugins {
    id("fabric-loom") version "1.17.12"
    java
}

version = "1.0.0"
group = "com.example"

base { archivesName.set("examplebreed") }

repositories {
    // The one line that makes LethalBreed resolvable. It stops at the local repository on purpose:
    // `gradlew publishToMavenLocal` in ../mod is what puts the artifact there.
    mavenLocal()
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.4+1.21.11")

    // What this whole folder exists to prove: one coordinate, no cloned repository, no question asked.
    modImplementation("oas.dreyka.lethalbreed:lethalbreed:1.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
