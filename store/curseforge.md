<!-- ────────────────────────────────────────────────────────────────────────
     FORM FIELDS — not part of the description body.
     Paste this file into the project description with the editor set to
     Markdown, not WYSIWYG. HTML comments render as nothing, so this block
     is harmless if the file is pasted whole.

     Summary:
       Vanilla zombies become a systemic threat: they pillar, dig, bridge and
       swim, hunt by sight and sound, escalate every night without ceiling,
       and carry a plague milk will not cure.

     Licence      Custom — link it to the LICENSE file in the repository.
                  Free to play and to share unmodified, never to sell,
                  everything else on request. See the modpack section below:
                  it is the one people will actually ask about.
     Categories   Mobs, Adventure and RPG, Server Utility
     Links        Website  https://lethalbreed.pages.dev
                  Source   https://github.com/Dreyka-Oas/LethalBreed
     ──────────────────────────────────────────────────────────────────── -->

# LethalBreed

**Standard zombie strain, uncontained from day zero.**

You built a wall. Vanilla zombies walk into it and wait for the sun.

These ones pillar over it. Or dig under it. Or bridge the moat, or swim it. And when they lose sight of
you they do not shrug and wander off — they head for where they last heard you.

Then the sun comes up, and tomorrow night they are stronger than they were tonight. There is no cap on
that, and it never goes back down.

<!-- IMAGE — banner. A wall at night with a zombie halfway up a pillar of its own making, player on
     top. If only one image ever goes on this page, make it this one. -->

## Before you install — one hard rule

**LethalBreed cannot run alongside any mod that changes zombie AI.**

It drives the zombies itself, so a second AI engine is a head-on conflict. Known mods are declared
incompatible and the loader simply refuses to start. Unlisted ones do not get through either — the mod
inspects the goals actually attached to the first zombie it meets. Caught at startup, the game will not
launch; caught mid-game, the server stops **cleanly** and saves your world first.

Everything else is fine, performance mods very much included.

## Every night is worse than the last

A difficulty counter climbs on its own, about every half hour of world time, shared across every
dimension on the server. It never falls.

Zombies carry no swords, no armour, no gear of any kind — the escalation is raw statistics and potion
effects. Tier 0 is plain vanilla. By tier 15 they have three to four and a half times the health and
around three times the damage. Past that the curves bend toward ceilings they never quite touch, so
tier 30, tier 50 and tier 100 still feel different from one another rather than blurring together.

<!-- IMAGE — the same location on night 1 and on a late tier, side by side if you can manage it.
     Numbers on a page do not land; two screenshots do. -->

## They hunt like something that wants to reach you

- **They build.** Real climbing, block by block, standing on what they place. Up to 24 blocks high.
- **They break to fit.** A larger zombie carves a larger hole — the opening is sized from its own body.
- **They come down safely.** Walking, then a short drop, then stairs already in the terrain, then a
  shaft, and only as a last resort a staircase built out over the void.
- **They listen.** Losing line of sight buys you nothing on its own.

<!-- IMAGE — a tunnel bored through a hill straight at a base, or a bridge thrown across water.
     Caption with what you were doing when you noticed. -->

## A plague you will not notice for a week

Super Contamination has no bar and no icon. It sits latent for **five to ten in-game days** in total
silence. The only tell is a half-second stumble at the moment you catch it, and you will miss it.

Four ways to catch it, and three of them never involve being hit: a zombie's blow, a Bomber's filth,
the pool that filth leaves on the ground, or a potion brewed from rotten flesh — which means it can be
used as a weapon. Armour is irrelevant to the last three.

When it finally declares itself it runs five worsening levels, with an intensity rolled per person, so
you and the player next to you will not have the same illness. Damage pulses, hunger drains, and four
separate episodes come and go on their own timers: you slow down, you cannot jump, you hit softer, and
you start seeing other players as zombies.

**Milk will not cure it.** The vanilla remedy is deliberately neutralised against this one.

<!-- IMAGE — the contamination overlay at a high severity level, ideally with the hallucination
     effect visible (another player rendering as a zombie). -->

## Eight of them are not like the others

Up to a third of spawns carry a variant, unlocked gradually as the difficulty climbs:

| | Unlocks | What it does |
|---|---|---|
| **Sprinter** | tier 2 | Permanently faster |
| **Leaper** | tier 3 | Clears far more ground in a jump than it should |
| **Bomber** | tier 4 | Arms, swells, detonates — see below |
| **Howler** | tier 5 | Calls every idle zombie within 24 blocks onto its target |
| **Healer** | tier 6 | Heals wounded zombies around it |
| **Juggernaut** | tier 6 | Bigger, double health, permanent Resistance II |
| **Necromancer** | tier 9 | Summons more zombies |
| **Splitter** | tier 11 | Splits in two when it dies |

The **Bomber** is the one that will kill you first. It does not blow up on contact: it stops three
blocks away, freezes, and swells for anywhere between one and a half and six seconds. That is your
window to leave. The longer the fuse burned, the bigger the blast — and the ring of filth it throws
reaches half again as far as the blast does, so stepping just outside lethal range saves your health
bar and infects you anyway. Every Bomber mixes its own cocktail of afflictions, so no two hurt the same
way, and the puddle it leaves keeps working on whoever walks back into it.

<!-- IMAGE — a Bomber mid-swell at close range, abdomen visibly distended. -->

## It is built to run a real horde

The target is around a thousand active zombies at once.

One shared flow field per dimension does the pathfinding for all of them, recomputed off the main
server thread so it does not stall the tick. If your machine has an OpenCL-capable GPU it is used
automatically; if it does not, or if anything goes wrong, it falls back to a multi-core CPU solver
without saying a word. A headless dedicated server with no graphics card runs exactly the same
simulation, just spread across cores.

Nothing extra to install on the host — the GPU bindings ship inside the jar.

## Configuration

`/lethalconfig` opens a proper in-game settings screen. `/lethalphase` tells you the current tier.
Everything is editable as JSON on disk too. Config is operator-gated and applies server-wide.

Needs Fabric Loader, Fabric API and Java 21. The server does all the real work; the client half adds
the contamination overlay, the hallucinations and the settings screen.

Pairs happily with Lithium, Krypton, FerriteCore, C2ME, ScalableLux, ServerCore, VMP, Immersive
Optimization and Spark — none of them touch zombie AI.

## Modpacks — please read

**Ask me first, and the answer is usually yes.**

LethalBreed is not under an open licence. You are free to play it and free to pass the jar on
unmodified, credited and at no charge — but putting it in a published modpack, forking it, modifying it
or reusing its code all need my permission in advance.

Asking takes a minute: use the report channel linked from
**<https://lethalbreed.pages.dev>**, no account needed. Permission is given in writing and covers what
it names. What is never allowed is selling it, or putting it behind a paywall, early access, a required
donation or a link shortener.

The full terms ship inside the jar as `LICENSE`, and that text is the one that counts.

## The full dossier

Every mechanic, every value, every command, documented properly — in English and in French:

**<https://lethalbreed.pages.dev>**
