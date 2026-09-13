# LethalBreed

Vanilla zombies become a relentless, environment-aware threat.

The Fabric mod itself. Flow-field pathfinding, endless phase escalation, a contamination plague and 8
special variants, with an optional AMD GPU (OpenCL) compute path and a multithreaded CPU fallback. Every
mechanic, parameter and command is documented on the wiki at
[lethalbreed.pages.dev](https://lethalbreed.pages.dev), in French and English.

Runs on Fabric Loader. Pinned versions live in `gradle.properties`, not here.

## Build

Java 21 has to be on the path. The Gradle wrapper pulls everything else itself.

```bash
./gradlew build      # player jar → build/libs/
./gradlew runClient  # dev client (loads run/mods/)
./gradlew runServer  # headless dev server + test harnesses
```

© Dreyka Oas. All rights reserved. Free to play and to share unmodified. Selling it, forking it or
reusing it means asking first. See [LICENSE](../LICENSE).
