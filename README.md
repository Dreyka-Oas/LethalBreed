# LethalBreed

A Fabric mod that turns Minecraft's zombies into a relentless, environment-aware threat: flow-field pathfinding
that pillars, digs, bridges and swims, hunting by both sight and sound, endless phase escalation, a
contamination plague and 8 special variants, built to scale toward ~1000 active zombies. Every mechanic and
parameter is documented on the site, [lethalbreed.pages.dev](https://lethalbreed.pages.dev), in French
and English.

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

LethalBreed drives zombie AI itself, so it cannot run alongside any other mod that alters it. Known ids
are refused at startup; an unlisted one is caught from the goals attached to the first zombie it meets.
Everything that does not touch zombie AI is fine, performance mods included.

Addons implement `oas.dreyka.lethalbreed.api.LethalBreedAddon` and declare it under the
`lethalbreed:addon` entrypoint. See `LethalBreedApi` for the phase event and the AI namespace allowlist,
which is how an addon's own zombie goal avoids being read as a conflict.

A Fabric mod. The mod is in [`mod/`](mod/).

MIT, © 2026 Dreyka Oas. Play it, share it, fork it, build an addon on it and publish that addon, all
without asking. Keep the copyright and permission notice with any substantial copy of the code, which
is the whole of what MIT requires. See [LICENSE](LICENSE). A mention is welcome as a courtesy, never
as a condition.
