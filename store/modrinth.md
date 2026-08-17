<!-- ────────────────────────────────────────────────────────────────────────
     FORM FIELDS — not part of the description body.
     HTML comments render as nothing, so this block is harmless if the file
     is pasted whole. Everything below the marker is the description.

     Summary (Modrinth caps it at 256 characters):
       Vanilla zombies become a systemic threat: flow-field pathfinding that
       pillars, digs, bridges and swims, hunting by sight and sound, endless
       phase escalation, a contamination plague and 8 special variants.

     Licence      Custom — select "Custom" and point it at the LICENSE file
                  in the repository. Not an open-source licence: free to play
                  and to share unmodified, never to sell, everything else on
                  request.
     Environment  Server: required — it runs the whole simulation.
                  Client: optional — visuals and the config GUI only.
                  CONFIRM before publishing by joining a modded server with a
                  vanilla client; the split is read from the source, not tested.
     Links        Source   https://github.com/Dreyka-Oas/LethalBreed
                  Website  https://lethalbreed.pages.dev
     Tags         Adventure, Mobs, Game Mechanics, Multiplayer, Optimization
     ──────────────────────────────────────────────────────────────────── -->

# LethalBreed

**Incident report — standard zombie strain, uncontained from day zero.**

Minecraft's zombies do not learn. They walk into the wall you built and stay there until morning.

LethalBreed replaces that with a horde that pillars over the wall, digs under it, bridges the gap and
swims the moat — that hunts you by sound when it cannot see you, that carries a plague you will not
notice for a week, and that gets harder every single night with no ceiling and no way back down.

<!-- IMAGE — the shot that sells the mod: a zombie three blocks up a pillar it built itself, reaching
     the top of a player's wall. Nothing else here shows the AI doing something vanilla cannot. -->

## Read this before you install

**LethalBreed is incompatible with any mod that alters zombie AI.** It drives the specimens itself, so
a second AI engine is a direct conflict, not a rough edge.

Known mod ids are declared incompatible in the manifest and the loader refuses to start. An unlisted
mod does not slip through either: LethalBreed inspects the goals actually attached to the first zombie
it meets and detects the intrusion regardless. Caught at startup, the game will not launch. Caught
mid-game, the server shuts down **cleanly**, with the world saved.

Everything that does not touch zombie AI is fine — including the performance mods below.

## Pathfinding that treats terrain as a problem to be solved

A flow field is recomputed every 10 ticks, off the server thread, on a grid bounded to 192 blocks plus
a 24-block margin around the subjects under surveillance. One field serves every specimen in a
dimension, which is what makes a large horde affordable at all.

- **It builds.** Climbing is a real vertical impulse plus a support block placed at each landing —
  never a teleport. It stands on what it builds, up to 24 blocks of total height.
- **It breaks to fit.** Break height is derived from the specimen's true collision box, so a zombie
  enlarged by random variation carves a taller opening than a normal one.
- **It comes back down.** Walking or a safe short fall, then an existing staircase, then a safe shaft,
  and only as a last resort a staircase built over the void. It never breaks the last block over a
  deep drop.
- **It hears you.** Sight is not the only sense; a specimen holds a remembered position and works
  toward it.

<!-- IMAGE — a tunnel dug straight through a hillside toward a base, or a bridge built over water.
     Caption it with what the player was doing when it happened. -->

## Escalation with no ceiling

A global counter climbs on its own, roughly every 30 minutes of world age, and **never drops back**.
It is shared across dimensions — one progression per server, not one per world.

Each specimen draws its stats from the current tier. Zombies carry no gear at all: the escalation is
statistics and potion effects, nothing else. Tier 0 is the vanilla reference; by tier 15 health runs
×3.0–4.5 and damage ×2.5–3.2. Beyond that the curves bend onto ceilings they approach without ever
reaching, so tiers 30, 50 and 100 stay meaningfully different instead of collapsing into one another.

## A plague you will not see coming

Super Contamination has no gauge and no icon. It incubates for **5 to 10 in-game days** in complete
silence — the only clue is a brief slowdown at the exact moment of infection.

It travels by four routes, and three of them involve no blow at all: contact with a specimen, a
Bomber's filth, the lingering pool that filth leaves behind, and a potion you can brew yourself from
rotten flesh. Armour changes nothing about the last three.

Once symptomatic it runs five severity levels with a per-subject intensity, so two infected players do
not present the same picture. Damage pulses, hunger drains, and four independent episodes fire on
their own timers — slowdown, blocked jump, weakened attack, and hallucination, where other players
render as zombies.

**Milk does not cure it.** The vanilla remedy is explicitly neutralised against this plague.

## Eight specimens on the loose

Up to 35% of spawns carry a documented variant, unlocking as the tiers climb: **Sprinter**, **Leaper**,
**Bomber**, **Howler**, **Healer**, **Juggernaut**, **Necromancer**, **Splitter**.

The Bomber deserves its own warning. It does not detonate on contact — it arms within three blocks,
freezes, and swells for a fuse drawn between 1.5 and 6 seconds. That is your window. A longer fuse
means a stronger blast, and the ring of filth reaches half again as far as the blast itself: backing
out of lethal range buys you hit points, not immunity. Each Bomber draws its own cocktail of
afflictions, so no two are alike, and both the blast and the pool can infect you.

<!-- IMAGE — a Bomber mid-swell, abdomen distended, close enough to read the tell. Optional second
     shot: the lingering pool afterwards. -->

## Built for a horde, not a handful

The target is roughly a thousand active specimens.

If OpenCL is available the flow field solves on the GPU automatically; any GPU error falls back to the
CPU silently, and small fields skip the GPU anyway because the round trip would cost more than the
work. The CPU path is a parallel multi-core Bellman-Ford, not a single-threaded Dijkstra, so a
headless dedicated server with no GPU runs the same simulation across several cores.

The two solvers are designed to be numerically identical — same edge weights, same break and build
penalties, the OpenCL kernel reading the same cost array as the CPU solver. Turning the GPU on or off
should not change the paths your specimens take.

No external dependency is required host-side: JOCL ships inside the jar.

## Configuration

`/lethalconfig` opens an in-game GUI; `/lethalphase` reports the current tier. Everything is also
readable and editable as JSON on disk. Config changes are operator-gated and global to the server.

## Requirements

Fabric Loader, Fabric API and Java 21. The version of Minecraft it targets is on this page's metadata —
it is not repeated here, so it cannot go stale.

The server runs the entire simulation. The client half adds the contamination overlay, the
hallucinations, the render tells and the config screen.

Pairs well with the usual performance stack — Lithium, Krypton, FerriteCore, C2ME, ScalableLux,
ServerCore, VMP, Immersive Optimization and Spark are all suggested by the mod itself. None of them
touch zombie AI.

## Licence

Free to play, forever and unconditionally. Free to pass on unmodified, credited and at no charge.

**Not to be sold**, in any disguise. Modifying it, forking it, reusing the code or publishing it in a
modpack all need permission first — ask, and it is usually a yes. The full terms ship inside the jar
as `LICENSE`, and that text is the one that governs.

## The dossier

Every mechanic, every parameter and every command is documented in full at
**<https://lethalbreed.pages.dev>** — in French and in English.
