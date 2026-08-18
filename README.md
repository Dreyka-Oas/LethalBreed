<div align="center">

# 🧟 LethalBreed

### Vanilla zombies become a relentless, environment-aware threat.

[![Explore the dossier](https://img.shields.io/badge/Explore%20the%20dossier-lethalbreed.pages.dev-8b0000?style=for-the-badge&labelColor=1a1a1a)](https://lethalbreed.pages.dev)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-5b8731?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.3-dbb69b?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21-b07219?style=for-the-badge&logo=openjdk&logoColor=white)](https://bell-sw.com/)

</div>

A Fabric mod that turns Minecraft's zombies into a **systemic, escalating threat**: flow-field pathfinding
that pillars, digs, bridges and swims, hunting by sight **and** sound, endless phase escalation, a
contamination plague and 8 special variants — built to scale toward ~1000 active zombies. Every mechanic and
parameter is documented on the site, in French and English.


```mermaid
flowchart LR
    W["Your wall, at night"]:::obstacle
    W --> P["Pillars over it"]:::act
    W --> G["Digs under it"]:::act
    W --> B["Bridges the gap"]:::act
    W --> S["Swims the moat"]:::act
    P --> Y(("You")):::target
    G --> Y
    B --> Y
    S --> Y
    Y -.->|"you break line of sight"| M["Heads for where<br/>it last heard you"]:::memory
    M --> Y
    classDef obstacle fill:#1a1a1a,stroke:#8b0000,stroke-width:2px,color:#f5f5f5
    classDef act fill:#6b1010,stroke:#c62828,stroke-width:1px,color:#f5f5f5
    classDef target fill:#8b0000,stroke:#f5f5f5,stroke-width:2px,color:#ffffff
    classDef memory fill:#2a2a2a,stroke:#dbb69b,stroke-width:1px,color:#dbb69b
```

> [!WARNING]
> LethalBreed drives zombie AI itself, so it **cannot** run alongside any other mod that alters it.
> Known ids are refused at startup; an unlisted one is caught from the goals attached to the first
> zombie it meets. Everything that does not touch zombie AI is fine, performance mods included.

The mod is in [`mod/`](mod/).

> [!IMPORTANT]
> © Dreyka Oas — all rights reserved. Free to play and to share unmodified; **not** to sell, fork or
> reuse without asking. See [LICENSE](LICENSE).
