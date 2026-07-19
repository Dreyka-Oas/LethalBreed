<div align="center">

# 🧟 LethalBreed

### Vanilla zombies become a relentless, environment-aware threat.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-5b8731?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3-dbb69b?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-b07219?style=for-the-badge&logo=openjdk&logoColor=white)](https://bell-sw.com/)

**📖 Documentation & wiki → [lethalbreed.pages.dev](https://lethalbreed.pages.dev)** · FR / EN
**💻 Source → [github.com/Dreyka-Oas/LethalBreed](https://github.com/Dreyka-Oas/LethalBreed)**

</div>

A Fabric mod (Minecraft 1.21.11, Java 21) that makes zombies actually hunt: flow-field pathfinding that
pillars up, descends, breaks and bridges, swims and dives, and tracks by sight **and** sound — with endless
phase escalation, a contamination plague, and 8 special variants. Scales toward ~1000 zombies, with an
optional AMD GPU (OpenCL) compute path and a multithreaded CPU fallback.

> **Every mechanic, parameter, and command is documented on the site → [lethalbreed.pages.dev](https://lethalbreed.pages.dev)**

## Build

```bash
./gradlew build      # obfuscated player jar → build/libs/
./gradlew runClient  # dev client (loads run/mods/)
./gradlew runServer  # headless dev server + test harnesses
```

## License

Private licence — © Dreyka Oas. All rights reserved.
