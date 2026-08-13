# Mod Complexity Refactor & Remaining Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break up the LethalBreed mod's remaining oversized/duplicated methods (the ones two prior cleanup passes deliberately left alone) into small, well-named private helpers with zero behavior change, plus fix the last few small audit findings (a design smell in an effect registration, a duplicated pack helper, one dead lambda, one magic-number nit).

**Architecture:** Every task in this plan is a pure "extract method" refactor: cut a contiguous, self-contained block of statements out of an oversized method into a new small private method, call it from the exact same spot in the exact same order. No branch reordering, no logic changes, no new fields, no changed public signatures. This is the safest category of refactor there is, but it touches the mod's hottest per-tick game-loop code, so every task is verified two ways: the existing JUnit suite (`./gradlew test`), AND the project's own headless dedicated-server dev-test harness (`LB_DEV_TEST=<suite> ./gradlew runServer`), which boots a real server with a `DevFakePlayer` and runs a scripted scenario ending in a `[LB-Verify] SUITE ... : x/y PASS` + `[LB-Verify] ALL DONE` line in `run/server/logs/latest.log` — this is the closest thing to an in-game playtest available in this headless environment, and it is a REAL server tick loop, not a mock.

**Tech Stack:** Java 21, Fabric (Minecraft 1.21.11), JUnit 5, the project's own `src/dev` headless test harness (`DevFakePlayer`, `DevTestSelector`, `DevVerdict`, `TickPhasedHarness`).

## Global Constraints

- **Zero behavior change.** Every task is a mechanical extraction verified by identical test/harness output before and after. If an implementer finds that a "clean" extraction actually requires changing behavior (reordering, new allocation on a hot path, a changed return value), STOP and report DONE_WITH_CONCERNS rather than force it.
- **No new allocations on hot paths.** `LodBucketPass.run`, `TargetSelector.findNearest`, and `ZombieBrain.tick` run per-zombie, every server tick (or close to it). Extracted helpers must take/return primitives or existing object references only.
- **Headless dev-server verification protocol** (used instead of a manual in-game smoke check, since this environment has no graphical Minecraft client):
  1. From `mod/`, run: `LB_DEV_TEST=<suite> timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-<suite>.out 2>&1` for each suite named in the task.
  2. Wait for the process to exit (the harness self-halts via `DevTestConfig.devAutoHalt`, which is already `true` in `run/server/config/oas/lethalbreed.json` — do not change this file).
  3. Check `run/server/logs/latest.log`: `grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log` must be `1`, and `grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log` must return NOTHING (empty output = pass). If either check fails, treat it as a real regression — do not proceed to commit.
  4. Also check `ls run/server/crash-reports/ | wc -l` before and after — a new crash report file is an automatic failure regardless of the log lines above.
  5. `run/server/logs/latest.log` is overwritten by each `runServer` invocation, so run suites one at a time, checking the log after each before starting the next.
- **The dev source set is already on the classpath for `runServer`** (confirmed: `build.gradle.kts` wires `source(devSourceSet)` into the server run config) — no build changes are needed to use `LB_DEV_TEST`.
- **`FlowFieldPerfBench.java` and `RoutingQualityMeasureTest.java` are out of scope** — already handled in a prior pass.
- **`ZombieBellyModelMixin.java` (Task 2) has NO headless coverage** — it is `@Environment(EnvType.CLIENT)` rendering code; the headless server harness never loads client classes. This task is verified by `./gradlew compileJava` only, and is flagged for a manual visual check by the user later (spawn a Bombeur, watch its belly swell — the two named constants must produce the exact same visual result as the old literals, which they do by construction since the values are unchanged).
- Every deletion/extraction in this plan was verified against the current source by three independent design passes before this plan was written (Aug 2026 snapshot) — re-verify with a fresh `grep`/`Read` at the start of each task in case the tree moved.
- Do not touch `pack/runtime/PackMaterializer.java`'s or `pack/runtime/PackLifecycle.java`'s null/`isValid()` guard loops — investigated and deliberately rejected (see Task 3's notes): the four loops differ in guard condition, resolved type, and iteration-safety requirements enough that a shared helper would need a callback parameter for marginal benefit.
- Commit hygiene: stage only the files each task names. This worktree may have an untracked plan document under `docs/superpowers/` at the repo root (outside `mod/`) that must NOT be included in any commit.

---

## File Structure

**New files:**
- `mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackDestinationPick.java` — shared border-read + `PackWander.next` + field-write helper for `PackMarch` and `PackVirtualMove` (Task 3)

**Modified files:** listed per task below. No files are deleted, no public signatures change, no new fields are added anywhere.

---

### Task 1: Fix `ZOMBIE_VISION` registration (design smell, not a mechanical dedup)

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/LethalBreedEffects.java:52-54`

**Interfaces:**
- Consumes: `com.dreykaoas.lethalbreed.effect.MarkerEffect(MobEffectCategory, int)` — already exists, same package, no import needed.
- Produces: nothing other tasks depend on.

**Context:** `ZOMBIE_VISION` is currently registered with `new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B)` — the SAME class used for the actual `SUPER_CONTAMINATION` effect, whose `onEffectStarted` unconditionally calls `ContaminationManager.contaminate(entity)`. This is only harmless today because `ZOMBIE_VISION` happens to only ever be applied to entities already contaminated (`contaminate()` no-ops on an already-infected target). The correct base class already exists in this codebase: `effect/MarkerEffect.java` (a bare `MobEffect` subclass with no side effects, exact constructor match), and `effect/LeapEffect.java` proves this exact "extend `MarkerEffect`, override nothing" pattern is the established idiom here.

- [ ] **Step 1: Re-verify the current registration**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -n -B2 -A2 "ZOMBIE_VISION = " src/main/java/com/dreykaoas/lethalbreed/effect/LethalBreedEffects.java
```
Expected: shows `new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B)` on the line after `ZOMBIE_VISION = Registry.registerForHolder(...)`.

- [ ] **Step 2: Swap the effect class**

In `mod/src/main/java/com/dreykaoas/lethalbreed/effect/LethalBreedEffects.java`, change:

```java
        ZOMBIE_VISION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "zombie_vision"),
                new SuperContaminationEffect(MobEffectCategory.HARMFUL, 0x3A5F0B));
```

to:

```java
        ZOMBIE_VISION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(LethalBreed.MOD_ID, "zombie_vision"),
                new MarkerEffect(MobEffectCategory.HARMFUL, 0x3A5F0B));
```

No import needed — `MarkerEffect` is in the same package (`com.dreykaoas.lethalbreed.effect`) as this file.

- [ ] **Step 3: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Headless dev-server verification**

Run the `plague` and `mech` suites (both touch contamination/hallucination):
```bash
LB_DEV_TEST=plague timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-plague.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log   # expect: 1
grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log      # expect: no output
```
```bash
LB_DEV_TEST=mech timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-mech.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log   # expect: 1
grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log      # expect: no output
```
Also confirm no new crash report: `ls run/server/crash-reports/` before and after Step 4 should show the same file count.

- [ ] **Step 5: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/LethalBreedEffects.java
git commit -m "fix(effect): register ZOMBIE_VISION as a plain MarkerEffect, not SuperContaminationEffect"
```

---

### Task 2: Name the girth-multiplier magic numbers in `ZombieBellyModelMixin`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Context:** This is `@Environment(EnvType.CLIENT)` rendering code — the headless dev-server harness cannot exercise it (no client ever boots in `runServer`). This task is verified by compilation only; flag it to the user for a visual check later (spawn a Bombeur, confirm its belly still swells identically — the values themselves are unchanged, only given names).

- [ ] **Step 1: Replace the file's content**

Read the current file first to confirm it still matches (the "require = 0" comment line was already shortened by an earlier cleanup pass — do not revert it):

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
cat src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java
```

Then replace the whole file with:

```java
package com.dreykaoas.lethalbreed.mixin.client.model;

import com.dreykaoas.lethalbreed.client.ZombieRenderFlags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inflates the BOMBEUR's belly (the {@code body} part) as its fuse burns. The model part is shared across
 * all zombies, so the scale is rewritten every frame from the render state's charge — 0 restores the
 * normal shape. Charge 1 → ~2.3x girth on x/z, a touch on y.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractZombieModel.class)
public class ZombieBellyModelMixin {

    /** How much charge=1 inflates the belly on x/z, on top of the base 1.0 scale — see class javadoc. */
    private static final float GIRTH_XZ_SCALE = 1.3f;
    /** How much charge=1 inflates the belly on y, on top of the base 1.0 scale — see class javadoc. */
    private static final float GIRTH_Y_SCALE = 0.35f;

    // require = 0: purely presentational — see com.dreykaoas.lethalbreed.client.PresentationalMixinNotes.
    @Inject(require = 0, method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V",
            at = @At("TAIL"))
    private void lethalbreed$swellBelly(ZombieRenderState state, CallbackInfo ci) {
        float charge = ((ZombieRenderFlags) state).lethalbreed$bellyCharge();
        ModelPart body = ((HumanoidModel<?>) (Object) this).body;
        float girth = 1.0f + charge * GIRTH_XZ_SCALE;
        body.xScale = girth;
        body.zScale = girth;
        body.yScale = 1.0f + charge * GIRTH_Y_SCALE;
    }
}
```

If the "require = 0" comment line you read in the current file differs from the one shown above (e.g. it still has the old 3-line form), keep whatever the CURRENT file actually has for that one line — do not change it as part of this task, only add the two named constants and use them in place of the two literals.

- [ ] **Step 2: Compile**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java
git commit -m "refactor(mixin): name the belly girth-multiplier magic numbers"
```

Note in your report: this task has no headless test coverage (client-only rendering code) — flag it as needing a manual visual check (spawn a Bombeur, watch the belly swell as its fuse burns) before the user fully trusts it, though the risk is minimal since the numeric values themselves are unchanged.

---

### Task 3: Dedupe pack destination-picking (`PackMarch` + `PackVirtualMove`)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackDestinationPick.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackMarch.java` (the `chooseDestination` method, around lines 72-91, plus its `WorldBorder` import if it becomes unused)
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackVirtualMove.java` (the `chooseNext` method, around lines 54-67, plus its `WorldBorder` import if it becomes unused)

**Interfaces:**
- Consumes: `com.dreykaoas.lethalbreed.pack.PackWander.next(...)` and `com.dreykaoas.lethalbreed.pack.PackWander.Destination` (both already exist, unchanged), `com.dreykaoas.lethalbreed.pack.PackManager.rngFor(PackState)` (already exists, unchanged).
- Produces: `PackDestinationPick.pick(ServerLevel level, PackState pack): void` — package-private, used only by `PackMarch` and `PackVirtualMove` (both in the same package `com.dreykaoas.lethalbreed.pack.runtime`).

**Context:** `PackMarch.chooseDestination` and `PackVirtualMove.chooseNext` both read the current `WorldBorder`, call `PackWander.next(...)`, and write `destX`/`destZ`/`headingX`/`headingZ` onto the pack — an identical ~10-line block. Each caller's own dwell-time bookkeeping differs (`PackMarch` jitters and resets stall-detection fields; `PackVirtualMove` does a plain `Math.max(0, ...)`) and stays with its own caller. `PackWander` itself is deliberately kept free of any Minecraft world-object dependency (its own javadoc states this, and `PackNoPlayerAccessTest` depends on that isolation), so the new shared helper goes in `pack/runtime/` instead, next to its two callers.

- [ ] **Step 1: Re-verify the current content of both methods**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
sed -n '1,95p' src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackMarch.java
sed -n '1,70p' src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackVirtualMove.java
```

Confirm `chooseDestination` and `chooseNext` still look like the extraction below expects (border read → `PackWander.next` → field writes → dwell/stall bookkeeping). If the actual content differs meaningfully from what's described here, STOP and report DONE_WITH_CONCERNS with what you found instead.

- [ ] **Step 2: Create the shared helper**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackDestinationPick.java`:

```java
package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.pack.PackWander;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Shared plumbing between {@link PackMarch} and {@link PackVirtualMove}: both need a fresh heading/destination
 * from {@link PackWander#next} built off the current world border, and both write it onto the pack the same
 * way. Only the two callers' dwell-time bookkeeping differs, so that part stays with each of them.
 */
final class PackDestinationPick {
    private PackDestinationPick() {}

    /** Pick a new destination/heading for {@code pack} at its current position and write destX/destZ/headingX/
     *  headingZ onto it. Does not touch {@code dwellUntil} or anything else — callers own that. */
    static void pick(ServerLevel level, PackState pack) {
        WorldBorder border = level.getWorldBorder();
        double[] heading = new double[2];
        PackWander.Destination next = PackWander.next(
                (int) Math.round(pack.x), (int) Math.round(pack.z), pack.headingX, pack.headingZ,
                (int) Math.floor(border.getMinX()), (int) Math.floor(border.getMinZ()),
                (int) Math.ceil(border.getMaxX()), (int) Math.ceil(border.getMaxZ()),
                PackManager.rngFor(pack), heading);
        pack.destX = next.x();
        pack.destZ = next.z();
        pack.headingX = heading[0];
        pack.headingZ = heading[1];
    }
}
```

If the actual `PackWander.next(...)` signature or `PackWander.Destination` accessor names you find in Step 1 differ from what's shown above, use the REAL signature/accessors you find — adapt this helper to match reality, don't force a signature mismatch.

- [ ] **Step 3: Update `PackMarch.chooseDestination`**

Replace the method body with:

```java
    private static void chooseDestination(ServerLevel level, PackState pack, long gameTime) {
        PackDestinationPick.pick(level, pack);
        pack.dwellUntil = gameTime + dwell(pack);
        // A fresh destination is further away than the old one by construction, so carrying the previous
        // distance over would score the very first check as "no headway" and start the stuck counter at 1.
        pack.lastDistToDest = Double.MAX_VALUE;
        pack.lastAdvanceTick = gameTime;
    }
```

(`dwell(pack)` is an existing method in this same file — do not change it.) If the `import net.minecraft.world.level.border.WorldBorder;` at the top of `PackMarch.java` is no longer referenced anywhere else in the file after this change (check with `grep -n "WorldBorder" src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackMarch.java`), remove that import.

- [ ] **Step 4: Update `PackVirtualMove.chooseNext`**

Replace the method body with:

```java
    private static void chooseNext(ServerLevel level, PackState pack, long gameTime) {
        PackDestinationPick.pick(level, pack);
        pack.dwellUntil = gameTime + Math.max(0, PackConfig.packDwellTicks);
    }
```

Same import check as Step 3: remove `import net.minecraft.world.level.border.WorldBorder;` from `PackVirtualMove.java` if it's now unused.

- [ ] **Step 5: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, including `PackMergeRuleTest`, `PackWanderTest`, `PackAdvanceTest`, `PackNoPlayerAccessTest`, `PackTetherTest`, `PackStateTest`, `PackJoinRuleTest` unchanged.

- [ ] **Step 6: Headless dev-server verification**

```bash
LB_DEV_TEST=pack timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-pack.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log   # expect: 1
grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log      # expect: no output
```
This suite already reported `11/11 PASS` in a prior run per the codebase's own log history — confirm it still does. Check `run/server/crash-reports/` for a new file (should be none).

- [ ] **Step 7: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackDestinationPick.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackMarch.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/pack/runtime/PackVirtualMove.java
git commit -m "refactor(pack): extract shared PackDestinationPick.pick from PackMarch/PackVirtualMove"
```

---

### Task 4: Extract `EntityEventsInit`'s `ENTITY_LOAD` lambda into a named method

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `EntityEventsInit.onEntityLoad(ZombieRegistry, DimensionManager, Entity, ServerLevel): void` — private static, no other task depends on it.

**Context:** `registerTracking`'s `ServerEntityEvents.ENTITY_LOAD.register(...)` call currently passes a ~60-line inline lambda. This task moves the lambda body verbatim into a named private static method, leaving a one-line lambda that just calls it. `ENTITY_UNLOAD`'s registration (also in this method) is untouched.

- [ ] **Step 1: Re-verify the current method**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
sed -n '1,115p' src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java
```
Confirm `registerTracking` still has the `ENTITY_LOAD.register((entity, world) -> { ... })` shape with the branches described below (phase-gated cull, blocked-drowned/baby discard, contamination re-track, target indexing, zombie registration + pack rejoin). If it has drifted meaningfully, report DONE_WITH_CONCERNS with what you found.

- [ ] **Step 2: Add the import**

Add `import net.minecraft.world.entity.Entity;` to the import block if not already present (check first — the file likely already imports specific entity types like `Zombie`; `Entity` itself may or may not already be imported).

- [ ] **Step 3: Replace the `ENTITY_LOAD` registration and add the new method**

Replace the `ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { ... });` block (the whole lambda) with:

```java
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> onEntityLoad(registry, dimensions, entity, world));
```

Then add this new method immediately after `registerTracking` (i.e., directly after the closing brace of `registerTracking`, before whatever method currently follows it):

```java
    /** One ENTITY_LOAD firing: phase-gated spawn filtering, blocked-variant discards, contamination
     *  re-tracking, target indexing, zombie registration and pack re-join. Moved out of the registration
     *  lambda in {@link #registerTracking} verbatim — the reasoning behind each branch below is unchanged
     *  from before the extraction. */
    private static void onEntityLoad(ZombieRegistry registry, DimensionManager dimensions,
                                      Entity entity, ServerLevel world) {
        // Phase-gated hostile filtering. In phase 0 (classic) NOTHING hostile spawns; in phases 1..15 only
        // plain Zombie is allowed (every other hostile is culled). Applies only to freshly-added entities,
        // not chunk-reloads (isAddedToLevel true == first add). We gate on the type filter regardless.
        if (WorldSpawnConfig.nightSpawnEnabled && SpawnFilter.shouldCull(entity)) {
            entity.discard();
            return;
        }
        // Discard blocked drowned/babies BEFORE tracking, so we don't contamination-track an entity we
        // then toss this same load.
        if (WorldSpawnConfig.blockDrowned && entity.getType() == EntityType.DROWNED) {
            entity.discard();
            return;
        }
        if (WorldSpawnConfig.blockBabyZombies && entity instanceof Zombie zb && zb.isBaby()) {
            zb.discard();
            return;
        }
        ContaminationManager.onLoad(entity); // re-track contaminated
        // Index anything huntable that isn't a zombie or a player, so target acquisition never has to
        // walk the horde to discard it (see TargetIndex). Registered AFTER the discard branches above,
        // so a culled entity is never indexed in the first place.
        if (TargetIndex.indexable(entity)) {
            dimensions.get(world.dimension()).targetIndex().track((net.minecraft.world.entity.LivingEntity) entity);
        }
        // Track all zombie variants (plain Zombie, Husk, ZombieVillager, ZombifiedPiglin...).
        // Drowned + babies are handled above (discarded when blocked).
        if (entity instanceof Zombie zombie) {
            if (WorldSpawnConfig.stripZombieEquipment) {
                SpawnControl.stripEquipment(zombie);
            }
            // Vanilla despawns non-persistent MONSTER-category mobs once every player is far enough away
            // (random roll past 32 blocks, unconditional past 128) — that would silently undo the whole
            // LOD/FROZEN system (TickScheduler/SpatialGrid), which exists specifically to keep the zombie
            // population alive-but-cheap while the player is elsewhere, not to have it vanish outright.
            zombie.setPersistenceRequired();
            AiConflictDetector.scanZombie(zombie, world); // once: detect foreign zombie-AI mods
            SmartZombie sz = registry.add(zombie, world.dimension());
            // A member that went to disk carrying its pack attachment (see EntityEventsInit's
            // ENTITY_UNLOAD handler, and PackAttachment's own javadoc) re-joins here, on the way back.
            // Without this, the attachment is written but never read, so a straggler never returns to
            // its pack — the pack's `detached` count never comes down, and it can outlive every real
            // member it ever had.
            if (PackConfig.packEnabled) {
                long packId = zombie.getAttachedOrElse(PackAttachment.PACK, PackJoinRule.NO_PACK);
                if (packId != PackJoinRule.NO_PACK) {
                    dimensions.get(world.dimension()).packManager().rejoin(sz, packId);
                }
            }
            // Deliberately NO "lift NoAI on load" repair here. It was tried and reverted: ENTITY_LOAD
            // fires for freshly-added entities too, not just chunk reloads, so it cancelled a
            // setNoAi(true) applied by the caller a line before addFreshEntity — which is exactly how
            // this project's own dev harness builds its arenas (MechTestArena:64 "stay on the open
            // platform (don't wander into shade/void)"). Measured: with the lift in place the headless
            // `phasescale` case reported 0 zombies and FAILed; without it, PASS (16 tanky, hp 65.5-317.5).
            // Nothing distinguishes one of our old statues from a map-maker's deliberately frozen prop,
            // so the repair cannot be made safe. Audit #2 is prevented at the source instead: the freeze
            // is released on ENTITY_UNLOAD and on SERVER_STOPPING (before saveAllChunks), so no new
            // statue is ever written. A world already carrying one can be repaired by hand with
            //   /data merge entity @e[type=zombie,limit=1] {NoAI:0b}
        }
    }
```

**Important:** the exact statements/branches inside this method must match whatever you actually read in Step 1, verbatim — the block shown above is what a prior read of this file found; if Step 1 shows any difference (even a comment wording change from later edits), use what's actually in the file, not this text, for anything beyond the branch/statement structure itself. The mechanical rule is: whatever was inside the old lambda body goes inside this new method, unchanged, in the same order.

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Headless dev-server verification**

This lambda fires on every entity load, so any suite that spawns entities exercises it. Run these three:
```bash
LB_DEV_TEST=pack timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-pack.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
```bash
LB_DEV_TEST=special timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-special.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
```bash
LB_DEV_TEST=mech timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-mech.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
Each must show `1` for the ALL DONE count and no FAIL lines. Check `run/server/crash-reports/` for new files after each.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java
git commit -m "refactor(init): extract ENTITY_LOAD lambda into EntityEventsInit.onEntityLoad"
```

---

### Task 5: Extract `TargetSelector.findNearest` (4-arg overload)

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `TargetSelector.collectCandidates(ServerLevel, Mob, double, TargetIndex): List<LivingEntity>`, `TargetSelector.shuffleAndOrder(List<LivingEntity>, Mob): double[]`, `TargetSelector.nearestVisible(ServerLevel, Mob, List<LivingEntity>, double[], double, int, boolean): LivingEntity` — all `private static`, used only within this file.

**Context:** Only the 4-arg `findNearest(ServerLevel level, Mob self, double radius, TargetIndex index)` overload is touched — NOT the 5-arg "sticky" overload, NOT `isValid`/`isAudible`/`canSee`. This method runs per-zombie targeting, so no new allocations may be introduced by the split itself (the split below only moves existing allocations, doesn't add any).

- [ ] **Step 1: Re-verify the current method**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -n "public static LivingEntity findNearest" src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java
sed -n '80,205p' src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java
```
Confirm the 4-arg overload still does: broad-phase collection (via `TargetIndex` or a world scan fallback) → shuffle → insertion-sort by distance-band/height → LOS scan. If it has drifted meaningfully, report DONE_WITH_CONCERNS.

- [ ] **Step 2: Add the three private static helpers**

Add these three methods to the class (place them near the `findNearest` overload they serve, e.g. directly after it):

```java
    private static List<LivingEntity> collectCandidates(ServerLevel level, Mob self, double radius, TargetIndex index) {
        // Broad phase. MEASURED (StageProfiler, ~100 zombies): asking the world for every LivingEntity in an
        // 80-block box was ~50% of the whole reclassify stage, itself ~40% of the mod's tick time — because
        // it visits the entire horde only to have isValid reject Zombie on each one.
        //
        // Shrinking the box does NOT fix that, and that was tested rather than assumed: narrowing the
        // vertical extent to 24 blocks left the sweep at 23.8us/call against 22.1 without it. The cost is
        // the entities inside, not the volume. So the horde is simply never offered to the scan: prey lives
        // in the mod's own TargetIndex, and players — few, and far too important to risk a bookkeeping slip
        // hiding one — are read live from the level.
        List<LivingEntity> candidates = new ArrayList<>();
        if (index != null) {
            index.collectInto(candidates, self.getX(), self.getZ(), radius);
            for (Player p : level.players()) {
                candidates.add(p);
            }
            candidates.removeIf(e -> !isValid(self, e));
        } else {
            // No index wired (unit tests, or a call path that predates it): fall back to the world scan.
            AABB box = self.getBoundingBox().inflate(radius);
            candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> isValid(self, e));
        }
        return candidates;
    }

    private static double[] shuffleAndOrder(List<LivingEntity> candidates, Mob self) {
        int n = candidates.size();
        // Shuffle first so entities that end up EXACTLY tied (same distance band AND same height gap) resolve
        // at random — the sort below is stable, so it preserves this randomised order for equal keys.
        for (int i = n - 1; i > 0; i--) {
            int j = self.getRandom().nextInt(i + 1);
            LivingEntity tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        // Sort nearest-first, but treat distances within TIE_BAND as "equally close": among two roughly-as-close
        // candidates (e.g. one overhead, one at our level) prefer the one nearest in HEIGHT — a target at the
        // zombie's own level is reachable without a climb, so it wins over one perched above at the same range.
        // Exact ties (same band + same height) keep the random order from the shuffle above.
        //
        // Keys are computed ONCE per candidate rather than inside the comparator: a comparator that calls
        // distanceToSqr twice per comparison performs ~2*n*log(n) distance computations for an n-element list,
        // and this runs for every zombie on every bucket activation (measured at ~40% of the mod's tick time).
        final double TIE_BAND = 4.0; // 4 = (2 blocks)²: distances differing by <2 blocks count as equal
        final double selfY = self.getY();
        long[] band = new long[n];
        double[] heightGap = new double[n];
        double[] distSq = new double[n];
        for (int i = 0; i < n; i++) {
            LivingEntity e = candidates.get(i);
            double d = self.distanceToSqr(e);
            distSq[i] = d;
            band[i] = (long) (d / TIE_BAND);
            heightGap[i] = Math.abs(e.getY() - selfY);
        }
        // Insertion sort over the parallel arrays: n is small (zombies are excluded by isValid, so these are
        // just the nearby prey), it is stable — so exact ties keep the shuffle's random order, as before — and
        // unlike sorting an Integer[] index array it boxes nothing on a path that runs per zombie per activation.
        for (int i = 1; i < n; i++) {
            LivingEntity ce = candidates.get(i);
            long cb = band[i];
            double ch = heightGap[i];
            double cd = distSq[i];
            int j = i - 1;
            while (j >= 0 && (band[j] > cb || (band[j] == cb && heightGap[j] > ch))) {
                candidates.set(j + 1, candidates.get(j));
                band[j + 1] = band[j];
                heightGap[j + 1] = heightGap[j];
                distSq[j + 1] = distSq[j];
                j--;
            }
            candidates.set(j + 1, ce);
            band[j + 1] = cb;
            heightGap[j + 1] = ch;
            distSq[j + 1] = cd;
        }
        return distSq;
    }

    private static LivingEntity nearestVisible(ServerLevel level, Mob self, List<LivingEntity> candidates,
                                                double[] distSq, double radiusSq, int n, boolean prof) {
        long tLos = prof ? System.nanoTime() : 0L;
        try {
            for (int i = 0; i < n; i++) {
                // SIGHT only: within the visual detect radius AND (if required) an unobstructed line of sight.
                // Iterated in distance order, so the first visible candidate is the nearest visible one.
                if (distSq[i] <= radiusSq
                        && (!TargetingConfig.requireLineOfSight || canSee(level, self, candidates.get(i)))) {
                    return candidates.get(i); // nearest seen — done
                }
            }
            return null;
        } finally {
            if (prof) {
                StageProfiler.sub(StageProfiler.Stage.LOS, System.nanoTime() - tLos);
            }
        }
    }
```

If `StageProfiler.sub(StageProfiler.Stage, long)` or `StageProfiler.Stage.LOS`/`.SCAN`/`.ORDER` don't exist with those exact names in the current codebase (check via `grep -n "StageProfiler.Stage\." src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java` and `grep -n "enum Stage" -A5 src/main/java/com/dreykaoas/lethalbreed/tick/StageProfiler.java`), use the ACTUAL profiler API you find — do not invent stage names that don't exist. If the original method didn't sub-profile these three phases at all (only the outer `t0` timer existed), drop the `prof`/timing code from these helpers entirely and keep only the functional logic — report this deviation in your self-review.

- [ ] **Step 3: Replace the `findNearest` body**

Replace the 4-arg `findNearest` method body with:

```java
    public static LivingEntity findNearest(ServerLevel level, Mob self, double radius, TargetIndex index) {
        boolean prof = StageProfiler.enabled();
        long t0 = prof ? System.nanoTime() : 0L;
        List<LivingEntity> candidates = collectCandidates(level, self, radius, index);
        if (prof) {
            StageProfiler.sub(StageProfiler.Stage.SCAN, System.nanoTime() - t0);
        }
        int n = candidates.size();
        if (n == 0) {
            return null; // nothing in range: skip the shuffle, the sort and the radius pass entirely
        }
        double radiusSq = radius * radius;
        if (n == 1) {
            // Overwhelmingly the common case once zombies are excluded. Ordering is meaningless for one
            // element, so go straight to the visibility test.
            LivingEntity only = candidates.get(0);
            if (self.distanceToSqr(only) > radiusSq) {
                return null;
            }
            long tl = prof ? System.nanoTime() : 0L;
            boolean seen = !TargetingConfig.requireLineOfSight || canSee(level, self, only);
            if (prof) {
                StageProfiler.sub(StageProfiler.Stage.LOS, System.nanoTime() - tl);
            }
            return seen ? only : null;
        }
        long tOrder = prof ? System.nanoTime() : 0L;
        double[] distSq = shuffleAndOrder(candidates, self);
        if (prof) {
            StageProfiler.sub(StageProfiler.Stage.ORDER, System.nanoTime() - tOrder);
        }
        return nearestVisible(level, self, candidates, distSq, radiusSq, n, prof);
    }
```

Leave the method's own leading Javadoc comment ("Nearest valid living target that the zombie can SEE...") exactly where it is, unmoved — it documents the whole method and this task does not touch it (a separate, already-completed task fixed its position relative to the OTHER `findNearest` overload).

If your Step 1 re-read of the original method shows it did NOT already have an `n == 1` fast path (i.e., it went straight from `collectCandidates`-equivalent logic into shuffle+sort+scan for every candidate count including 1), do not introduce this fast path as part of this task — that would be a behavior change (skipping the shuffle for n=1 is harmless since one element can't be reordered, but the fast path ALSO skips allocating/populating the `band`/`heightGap`/`distSq` arrays, which is fine functionally but you must confirm it doesn't skip anything else, like the profiler sub-timing categories, that the original always did). If in doubt, keep the ORIGINAL method's exact control flow for the n==1 case and only extract the three helpers, calling all three unconditionally rather than adding a new fast path.

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Headless dev-server verification**

```bash
LB_DEV_TEST=presence timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-presence.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
```bash
LB_DEV_TEST=special timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-special.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
Both must show `1` ALL DONE and no FAIL lines. Check `run/server/crash-reports/` for new files.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java
git commit -m "refactor(util): extract collectCandidates/shuffleAndOrder/nearestVisible from TargetSelector.findNearest"
```

---

### Task 6: Extract `ContaminationTick.tick`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ContaminationTick.refreshEnabledState(): boolean`, `ContaminationTick.applySymptomaticEffects(LivingEntity, ServerLevel, long): void` — both `private static`, used only within this file.

**Context:** `tick(MinecraftServer)` (~113 lines) covers: enable/disable transition purge, per-victim snapshot iteration with cleanup/cure checks, and (for symptomatic victims) icon upkeep, creative/spectator pause, level-up roll, health/food pulse, episodes, and hallucination. The symptomatic-effects tail is always the last thing done for a victim in the loop, so its `continue` statements become `return` in the extracted method.

- [ ] **Step 1: Re-verify the current method**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
sed -n '1,150p' src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java
```
Confirm the method still has the shape described (enabled-transition check, snapshot loop with age/cure/latent branches, then the symptomatic tail). If it has drifted meaningfully, report DONE_WITH_CONCERNS.

- [ ] **Step 2: Add the two private static helpers**

Add these two methods to the class:

```java
    /** Reads the current enabled flag and fires the one-shot enabled→disabled purge when the plague was
     *  just switched off. */
    private static boolean refreshEnabledState() {
        boolean enabled = ContaminationConfig.contaminationEnabled;
        if (wasEnabled && !enabled) {
            // Enabled -> disabled: purge once, here, rather than leaving the in-memory state to be cleaned
            // by a sweep that this very flag switches off. Persistent attachments are untouched, so
            // re-enabling the plague re-tracks every victim through onLoad on its next chunk load (audit #9).
            ContaminationLifecycle.onServerStopped();
        }
        wasEnabled = enabled;
        return enabled;
    }

    /** Runs every active symptomatic effect for one victim this tick: icon upkeep, the creative/spectator
     *  pause, level-up roll, the health/food pulse, episodes, and hallucination. This is always the tail of
     *  the per-victim sweep, so the original loop's {@code continue} statements become {@code return} here. */
    private static void applySymptomaticEffects(LivingEntity e, ServerLevel level, long t) {
        // The skull icon is the symptomatic stage's only marker, so losing it means the plague is gone:
        // /effect clear wipes the attachments outright (EffectClearCuresPlagueMixin), and milk puts the
        // icon straight back (MilkKeepsPlagueMixin), so reaching here with no icon means something else
        // removed it — treat that as a cure rather than leaving a symptomatic victim with no marker.
        int lvl = ContaminationState.level(e);
        int wantAmp = Math.max(0, lvl - 1);
        MobEffectInstance cur = e.getEffect(LethalBreedEffects.SUPER_CONTAMINATION);
        if (cur == null) {
            ContaminationLifecycle.cure(e);
            return;
        }
        if (cur.getAmplifier() != wantAmp) {
            ContaminationSymptoms.applyIcon(e, wantAmp);
        }

        // A player who can't take normal damage (Creative / Spectator) keeps the plague — icon, level, everything
        // stays — but none of its active effects fire: no health/food pulse, no episodes, no hallucination, no
        // evolution. The moment they return to Survival/Adventure the symptoms resume from where they were.
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }

        // Level-up roll: every 1–2 in-game days a chance to climb toward maxLevel (recomputes intensity).
        ContaminationEvolve.tickEvolve(e, t);

        double mult = ContaminationState.intensity(e); // per-victim intensity for the current level

        // Slow plague pulse: every 5–10 real seconds (random per pulse) shave a small chip off BOTH health and
        // (players) food. Higher level → bigger chip (×mult). Zombies are never tracked, so it can't chip its
        // own kind. Only the final, fatal chip goes through the vanilla damage pipeline (death/reanimation).
        Long due = ContaminationState.nextPulse.get(e);
        if (due == null) {
            ContaminationState.nextPulse.put(e, t + rollIntervalTicks());
        } else if (t >= due) {
            float dmg = (float) (ContaminationRoll.uniform(ContaminationState.RNG,
                    ContaminationConfig.contamDamageMin, ContaminationConfig.contamDamageMax) * mult);
            float next = e.getHealth() - dmg;
            if (next > 0.0f) {
                e.setHealth(next);
            } else {
                e.hurtServer(level, e.damageSources().magic(), Float.MAX_VALUE);
            }
            if (e instanceof Player p) {
                // Exhaustion drains food gradually: 4.0 exhaustion = 1 food point, so this removes ~dmg food.
                p.getFoodData().addExhaustion(dmg * (float) ContaminationConfig.contamFoodExhaustionMult);
            }
            ContaminationState.nextPulse.put(e, t + rollIntervalTicks());
        }

        // Random episodic afflictions (slow / no-jump / weak-strike) — each on its own timer, scaled by mult.
        ContaminationEpisodes.tickEpisodes(e, t, mult);

        // Zombie-vision hallucination — a fourth episode on its own random timer. Applies/removes the transient
        // ZOMBIE_VISION effect; the victim's client reads it to draw other players as zombies.
        ContaminationHallucination.tickHallucination(e, t, mult);
    }
```

If any method/field name referenced above (`wasEnabled`, `rollIntervalTicks()`, `ContaminationState.nextPulse`, etc.) doesn't match what you find in Step 1, use the actual names from the file — this text describes the shape verified during planning, not a guaranteed byte-exact current state.

- [ ] **Step 3: Replace the `tick` body**

Replace the `tick(MinecraftServer)` method body with:

```java
    public static void tick(MinecraftServer server) {
        // Cleared BEFORE the guard, not after: the two ordinary ways out of here — `tracked` going empty
        // (last victim cured or died) and the plague being switched off — both take the early return, and
        // a scratch buffer that only self-clears on the hot path holds its last batch forever. One retained
        // LivingEntity pins level -> ServerLevel -> chunks -> MinecraftServer (audit #8).
        SNAPSHOT.clear();

        boolean enabled = refreshEnabledState();
        if (!enabled || ContaminationState.tracked.isEmpty()) {
            return;
        }
        long t = server.getTickCount();
        SNAPSHOT.addAll(ContaminationState.tracked);
        for (int i = 0; i < SNAPSHOT.size(); i++) {
            LivingEntity e = SNAPSHOT.get(i);
            if (e == null || e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                // Fully drop the victim from all six collections, not just `tracked` — an unloaded/dead/
                // dimension-changed entity left in the timer maps pins the whole world graph (audit #2).
                // Persistent attachments stay, so a chunk that reloads re-tracks the victim via onLoad.
                ContaminationLifecycle.forgetAllTransient(e);
                continue;
            }
            int c = ContaminationState.age(e);
            if (c <= 0) {
                ContaminationLifecycle.cure(e);
                continue;
            }

            // Cure: only by staying crouched; tiny random chance per check.
            if (e.isCrouching() && t % Math.max(1, ContaminationConfig.contamCureCheckTicks) == 0
                    && ContaminationRoll.percent(ContaminationState.RNG,
                            ContaminationConfig.contamCureMinPct, ContaminationConfig.contamCureMaxPct)) {
                ContaminationLifecycle.cure(e);
                continue;
            }

            c++;
            e.setAttached(ContaminationState.CONTAM, c);

            // Dev-only visual: since the latent stage is invisible by design, show a debug action-bar tag so a
            // developer can confirm infection state in-game. Never shown outside a dev environment.
            ContaminationSymptoms.showDevIndicator(e);

            if (!ContaminationState.symptomatic(e)) {
                ContaminationSymptoms.tickLatent(e, t);
                continue;
            }

            applySymptomaticEffects(e, level, t);
        }
    }
```

Again: if Step 1's re-read shows any different field/method names or a materially different branch, use what's actually there — the mechanical rule is "same statements, same order, just calling the two new helpers where their blocks used to be inline."

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Headless dev-server verification**

```bash
LB_DEV_TEST=plague timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-plague.out 2>&1
grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log; grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
```
Expect `1` and no FAIL lines. Check `run/server/crash-reports/` for a new file.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java
git commit -m "refactor(contamination): extract refreshEnabledState/applySymptomaticEffects from ContaminationTick.tick"
```

---

### Task 7: Extract `LodBucketPass.run`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java`

**Interfaces:**
- Consumes: the existing `mark(StageProfiler, StageProfiler.Stage, boolean, long): long` helper (already exists from a prior cleanup pass — do not redefine it).
- Produces: `LodBucketPass.hardFreezeSkip(SmartZombie, ServerLevel, double): boolean` (static), `LodBucketPass.classifyAndUpdate(SmartZombie, ServerLevel, WorldAIContext, WorldAIContext, boolean): LODLevel` (instance method — uses `this.profiler`), `LodBucketPass.divisorFor(LODLevel, int): int` (static), `LodBucketPass.tickAndCollect(SmartZombie, ServerLevel, WorldAIContext, boolean, Set<SmartZombie>, Set<SmartZombie>): void` (instance method) — all `private`, used only within this class.

**Context:** This is the mod's core per-zombie, per-tick dispatcher — the hottest path in the whole mod. `run(...)` (~123 lines) does: bucket-membership/validity checks, a frozen-reclassify stagger skip, a hard-freeze player-distance skip, classify→grid→pack→sunburn→mood, a distance-tier throttle, a per-tick AI budget, and finally the zombie's own `tick()` + climber/swimmer collection. NO new allocations may be introduced — all extracted helpers take/return primitives, enum values, or existing references only.

- [ ] **Step 1: Re-verify the current method**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
cat src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java
```
Confirm `run(...)`'s current shape still matches: bucket check → validity/untrack → frozen-reclassify stagger → hard-freeze skip → classify/grid/pack/sunburn/mood → FROZEN re-check → throttle divisor → budget check → `sz.tick(...)` + climber/swimmer collection. Also confirm the existing `mark(...)` helper's exact signature (from the prior cleanup task) so the code below calls it correctly. If the file has drifted meaningfully, report DONE_WITH_CONCERNS with what you found.

- [ ] **Step 2: Add the four extracted helpers**

Add these four methods to the class (a sensible location: after the `mark(...)` helper, before `run(...)`):

```java
    /** Player simulation-distance cutoff: if no player is within hardFreeze blocks, freeze WITHOUT the
     *  target scan classify() does, and report that the caller should skip this activation. NOTE this is
     *  deliberately PLAYER-only — a zombie hunting a non-player target (villager/animal) with no player
     *  within hardFreeze is frozen too, i.e. autonomous hunts far from any player pause until a player
     *  approaches. That tradeoff is why this defaults to 0 (off); enable it only if you accept "nobody's
     *  watching → stop simulating" semantics.
     *  A pack member is exempt: this cutoff wipes target AND memory before classify() runs, so with
     *  hardFreeze on, every migrating pack would stop dead the moment it left a player's radius —
     *  which is precisely when a migration is supposed to be happening. The cutoff still applies to
     *  every loose zombie, so its point (stop simulating what nobody watches) survives. */
    private static boolean hardFreezeSkip(SmartZombie sz, ServerLevel level, double hardFreeze) {
        if (hardFreeze > 0.0 && !sz.pursuit().pack().inPack()) {
            Player np = level.getNearestPlayer(sz.entity(), hardFreeze);
            if (np == null) {
                sz.pursuit().clearTarget();
                sz.pursuit().clearMemory();
                sz.setLod(LODLevel.FROZEN);
                return true;
            }
        }
        return false;
    }

    /** Runs the classify → grid → pack → sun-burn → mood phase for one zombie activation and returns the
     *  LOD tier after mood processing (mood can un-freeze a zombie, so the tier must be re-read afterward). */
    private LODLevel classifyAndUpdate(SmartZombie sz, ServerLevel level, WorldAIContext classifyCtx,
                                        WorldAIContext ctx, boolean prof) {
        long t = prof ? System.nanoTime() : 0L;
        // Reclassify every activation so LOD + nearest-player (used for pillaring) stay fresh for
        // ALL buckets — a global tick%interval would only ever align with bucket 0.
        LODManager.classify(sz, level, classifyCtx.targetIndex());
        t = mark(profiler, StageProfiler.Stage.CLASSIFY, prof, t);
        LODLevel lod = sz.lod();
        // Keep FROZEN zombies in the spatial grid (their tick() — which inserts them — is skipped below)
        // so neighbour queries still find them: a Hurleur rallying idle zombies, a Soigneur healing them,
        // and sound propagation all target exactly these.
        ctx.spatialGrid().update(sz, sz.entity().blockPosition().getX(), sz.entity().blockPosition().getZ());
        t = mark(profiler, StageProfiler.Stage.GRID, prof, t);
        // Pack decision runs here, BEFORE the FROZEN skip: a zombie with nothing to hunt is frozen, and
        // a frozen zombie looking for company is the nominal case for forming a pack, not an edge one.
        PackPass.decide(sz, ctx);
        t = mark(profiler, StageProfiler.Stage.PACK, prof, t);
        // Daylight burn must apply even to idle/FROZEN zombies (whose full tick() below is skipped).
        sz.applySunBurn(level);
        t = mark(profiler, StageProfiler.Stage.SUNBURN, prof, t);
        // Mood (celebrate/flee/regen) also runs before the FROZEN skip so a targetless fleeing/celebrating
        // zombie still gets processed; it can un-freeze itself (LOD→HIGH), so re-read the tier afterward.
        sz.updateMood(level, ctx);
        mark(profiler, StageProfiler.Stage.MOOD, prof, t);
        return sz.lod();
    }

    /** Distance-tier throttle divisor: distant zombies run their AI less often. Under server lag (stress=2)
     *  every tier — HIGH included — is throttled extra to shed load. */
    private static int divisorFor(LODLevel lod, int stress) {
        int divisor = switch (lod) {
            case MEDIUM -> SchedulerConfig.lodMediumTickDivisor;
            case LOW -> SchedulerConfig.lodLowTickDivisor;
            default -> 1;
        };
        return divisor * stress;
    }

    private void tickAndCollect(SmartZombie sz, ServerLevel level, WorldAIContext ctx, boolean prof,
                                 Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        long tt = prof ? System.nanoTime() : 0L;
        sz.tick(level, ctx);
        mark(profiler, StageProfiler.Stage.TICK, prof, tt);
        if (sz.isClimbing()) {
            climbers.add(sz);
        }
        if (sz.isSwimming()) {
            swimmers.add(sz);
        }
    }
```

If `this.profiler` isn't the field's actual name (check the class's field declarations), use the real field name throughout.

- [ ] **Step 3: Replace the `run` body**

Replace the loop body inside `run(...)` so that:
1. The bucket-membership check, validity/`untrack` check, and the frozen-reclassify stagger skip are UNCHANGED (left exactly as they are — do not touch them).
2. Immediately after the frozen-reclassify skip, replace the inline hard-freeze block with:
```java
            if (hardFreezeSkip(sz, level, hardFreeze)) {
                continue;
            }
```
3. Replace the inline classify/grid/pack/sunburn/mood block (everything from the `boolean prof = StageProfiler.enabled();` line through the mood update and FROZEN re-check) with:
```java
            boolean prof = StageProfiler.enabled();

            WorldAIContext classifyCtx = dimensions.get(sz.dimension());
            WorldAIContext ctx = dimensions.get(sz.dimension());
            LODLevel lod = classifyAndUpdate(sz, level, classifyCtx, ctx, prof);
            if (lod == LODLevel.FROZEN) {
                continue;
            }
```
4. Replace the inline throttle-divisor computation (`int divisor = 1; if (SchedulerConfig.throttleByLod) { divisor = switch(...); } divisor *= stress;`) with:
```java
            int divisor = SchedulerConfig.throttleByLod ? divisorFor(lod, stress) : stress;
```
5. Leave the `dueThisActivation`/budget-check/`spent++` lines UNCHANGED (do not extract them — `spent` is a loop-scoped mutable accumulator and extracting it would need a return value or boxing, which this task's ground rules forbid).
6. Replace the final `sz.tick(level, ctx); ... climbers.add/swimmers.add` block with:
```java
            tickAndCollect(sz, level, ctx, prof, climbers, swimmers);
```

The full method after these replacements should read (verify this matches what you produce):

```java
    void run(MinecraftServer server, int buckets, int currentBucket, Set<SmartZombie> climbers, Set<SmartZombie> swimmers) {
        // buckets is supplied by the scheduler (the same value it used to derive currentBucket), so membership
        // stays consistent even when autoScaleBuckets recomputes it from population each tick. Computing the
        // bucket live (id % buckets) means a count change re-spreads every zombie at once — none stranded.
        int frozenDiv = Math.max(1, SchedulerConfig.frozenReclassifyDivisor);
        double hardFreeze = SchedulerConfig.lodHardFreezeRadius;
        int budget = SchedulerConfig.aiTickBudget; // 0 = unlimited full ticks this server tick
        int spent = 0;
        // Graceful degradation: under server lag, double every LOD divisor (HIGH included) to shed AI load.
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        int stress = (SchedulerConfig.msptThrottle && mspt > SchedulerConfig.msptThrottleThreshold) ? 2 : 1;
        long round = frozenRound++;
        for (SmartZombie sz : registry.all()) {
            if (Math.floorMod(sz.id(), buckets) != currentBucket) {
                continue;
            }
            if (!sz.isValid()) {
                untrack(sz);
                continue;
            }

            ServerLevel level = server.getLevel(sz.dimension());
            if (level == null) {
                continue;
            }

            if (frozenDiv > 1 && sz.lod() == LODLevel.FROZEN
                    && Math.floorMod(sz.id() + round / buckets, frozenDiv) != 0L) {
                continue;
            }

            if (hardFreezeSkip(sz, level, hardFreeze)) {
                continue;
            }

            boolean prof = StageProfiler.enabled();

            WorldAIContext classifyCtx = dimensions.get(sz.dimension());
            WorldAIContext ctx = dimensions.get(sz.dimension());
            LODLevel lod = classifyAndUpdate(sz, level, classifyCtx, ctx, prof);
            if (lod == LODLevel.FROZEN) {
                continue;
            }
            int divisor = SchedulerConfig.throttleByLod ? divisorFor(lod, stress) : stress;
            if (!sz.dueThisActivation(divisor)) {
                continue;
            }
            if (budget > 0 && spent >= budget) {
                continue;
            }
            spent++;

            tickAndCollect(sz, level, ctx, prof, climbers, swimmers);
        }
    }
```

Preserve every comment from the original that isn't shown above but that Step 1 shows exists (e.g. the frozen-reclassify-stagger comment block) — this listing omits some of the longer inline comments for brevity in this plan document, but the actual file must keep them all, moved with their code exactly as before.

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Headless dev-server verification (broad — this method runs for every zombie every tick)**

Run all of these, checking the log after each before starting the next:
```bash
for suite in presence pack plague mech climb shade; do
  LB_DEV_TEST=$suite timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-$suite.out 2>&1
  echo "=== $suite ==="
  grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log
  grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
done
```
Every suite must show `1` for the ALL DONE count and produce no FAIL-line output. Check `run/server/crash-reports/` for any new file across the whole run.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java
git commit -m "refactor(tick): extract hardFreezeSkip/classifyAndUpdate/divisorFor/tickAndCollect from LodBucketPass.run"
```

---

### Task 8: Extract `ZombieBrain.tick`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `handleSleeping(): boolean`, `handleSheltering(ServerLevel): boolean`, `handleFleeing(ServerLevel): boolean`, `handleNoTarget(WorldAIContext, ZombiePursuit): boolean`, `handleSwimEntry(): boolean` — all `private`, instance methods, used only within this class.

**Context:** `tick(ServerLevel, WorldAIContext)` (~114 lines) is a state-machine dispatcher: special behavior tick, FROZEN check, then a sequence of early-return "mode" checks (sleeping, sheltering, fleeing, pillar-active, no-target, swim-entry), then the tightly-coupled stuck-detection → leap → breaking/dispatch tail. Only the clean early-return branches are extracted; the tail (13 shared locals across ~42 lines) is left as one block per this plan's no-force-split rule.

- [ ] **Step 1: Re-verify the current method**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
cat src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java
```
Confirm `tick(...)`'s shape still matches. If it has drifted meaningfully, report DONE_WITH_CONCERNS.

- [ ] **Step 2: Add the five extracted helpers**

Add these five methods directly after `tick(...)`, before `climbStep(...)`:

```java
    private boolean handleSleeping() {
        if (!owner.mood().isSleeping()) return false;
        pillar.cancel();
        entity.getNavigation().stop();
        owner.setState(ZombieState.SLEEPING);
        return true;
    }

    private boolean handleSheltering(ServerLevel level) {
        if (!owner.mood().isSheltering()) return false;
        pillar.cancel();
        owner.setState(ZombieState.SHELTERING);
        owner.mood().driveShelter(level);
        return true;
    }

    private boolean handleFleeing(ServerLevel level) {
        if (!owner.mood().isFleeing()) return false;
        pillar.cancel();
        owner.setState(ZombieState.FLEEING);
        owner.mood().driveFlee(level);
        return true;
    }

    private boolean handleNoTarget(WorldAIContext ctx, ZombiePursuit p) {
        if (p.hasTarget()) return false;
        owner.setState(p.hasSound() && nav.navigateToSound(ctx) ? ZombieState.PURSUING_SOUND : ZombieState.IDLE);
        return true;
    }

    private boolean handleSwimEntry() {
        if (!(CombatMoveConfig.floatInWater && entity.isInWater()
                && (!entity.onGround() || entity.isUnderWater()))) {
            return false;
        }
        pillar.cancel();
        swimming = true;
        owner.setState(ZombieState.PURSUING_PLAYER);
        return true;
    }
```

- [ ] **Step 3: Replace the `tick` body**

Replace the whole `tick(ServerLevel level, WorldAIContext ctx)` method body with:

```java
    public void tick(ServerLevel level, WorldAIContext ctx) {
        if (!owner.isValid()) return;
        ZombiePursuit p = owner.pursuit();
        int bx = entity.blockPosition().getX();
        int bz = entity.blockPosition().getZ();
        // No spatialGrid().update() here: LodBucketPass already refreshed this zombie's grid cell THIS same
        // activation (before the FROZEN/throttle skips) and it hasn't moved since — tick() is only reached from
        // that pass, so repeating the update is pure redundant work. bx/bz are kept for MoveDispatch below.
        p.tickSpecial();
        if (p.isSpecialActive()) SpecialBehavior.tick(owner, level, ctx);
        if (owner.lod() == LODLevel.FROZEN) return;
        // Daytime sleep: a dozing zombie holds still. It is normally FROZEN (so this isn't even reached); this is
        // a defensive stop in case it is momentarily active. The walk-to-shade is NOT here — that's a normal
        // memory-target pursuit (NORMAL state) so the full breaking/pillaring nav carries it to the shade.
        if (handleSleeping()) return;
        // Sun-shelter overrides even the retreat: a burning wounded zombie dashes to shade (mood already found
        // the refuge and dropped the target). Checked before flee so shade-seeking wins over the straight run.
        if (handleSheltering(level)) return;
        // Low-health retreat overrides the hunt: the mood step already dropped the target; here we just steer
        // away from the threat (vanilla nav, so climb/descend still work). No leap/dig/dispatch while fleeing.
        if (handleFleeing(level)) return;
        pillar.tickCooldown();
        if (pillar.active()) return; // mid climb; the per-tick climbStep finishes it
        if (handleNoTarget(ctx, p)) return;

        // The vanilla attack target (melee) is set authoritatively in LODManager.classify, which runs in the
        // SAME activation immediately before this tick — so no setTarget re-assert is needed here (was
        // duplicate work). We still read the pursuit target to drive movement dispatch below.
        LivingEntity te = p.targetEntity();
        // A day-sleeper calmly walking to its shade block (a memory target, so te == null) must NOT use the
        // combat approach — leaping toward shade reads as a jerky pounce. Plain navigation only; it still digs
        // if genuinely walled in (stuck-detection below), just no speculative hops.
        boolean shadeSeek = te == null && owner.mood().isSeekingShade();
        // A pack MARCHING to its rendezvous navigates and nothing else: no leap, no pillar, no breaching.
        // A migration must not tear through a base its route happens to cross — destruction is reserved for
        // an actual aggro. Vanilla pathing walks around whatever it can; when there is no way round at all
        // the pack stalls, and PackMarch gives up on that destination after packStuckActivations.
        boolean packMarch = te == null && p.pack().hasWaypoint();
        double dx = p.tgtX() - entity.getX();
        double dz = p.tgtZ() - entity.getZ();
        double dy = p.tgtY() - entity.getY();
        double horizSq = dx * dx + dz * dz;
        // Swim mode only when actually floating/submerged (off the ground, or head underwater). A shallow
        // puddle the zombie is STANDING in (on ground, head clear) must NOT lock it into swim — it still needs
        // to pillar/jump out, so we fall through to the normal dispatch below. Deep water → swimStep drives it.
        if (handleSwimEntry()) return;
        swimming = false;

        // Block ops only when STUCK (no horizontal progress) — else it walks/auto-steps normally. Computed
        // BEFORE the leap so a stuck zombie (mid-break/pillar) never leaps: a leap would move it off the block
        // it's breaking, stop renewing the break request, and let the progress lapse (never reaching 100%).
        boolean progressing = lastHorizDistSq < 0.0 || horizSq < lastHorizDistSq - CombatMoveConfig.stuckProgressEpsilon;
        stuckTicks = progressing ? 0 : stuckTicks + 1;
        lastHorizDistSq = horizSq;
        boolean stuck = stuckTicks >= CombatMoveConfig.stuckActivations;

        // Occasional leap; a successful leap carries the arc this tick. Suppressed while stuck (breaking) and
        // while calmly walking to shade for a day-doze (no pouncing at a shady spot).
        leap.tickCooldown();
        if (!stuck && !shadeSeek && !packMarch && leap.tryLeap(level, dx, dz, dy, horizSq)) {
            owner.setState(ZombieState.PURSUING_PLAYER);
            return;
        }

        if (breaking) {
            // Was breaking a block last tick — CONCENTRATE: hold position, don't re-path. Re-pathing would let
            // the flow field drag the zombie sideways around the wall, so it stops renewing the break request
            // and the progress lapses (block never reaches 100%). Just keep facing the block/target.
            entity.getNavigation().stop();
            MoveMath.faceHeading(entity, dx, dz);
        } else {
            // Aim at the BASE of an overhead target's column (our own Y) so we walk up and close the gap.
            double navY = (dy > FlowConfig.navYThreshold) ? entity.getY() : p.tgtY();
            nav.navTo(ctx, p.tgtX(), navY, p.tgtZ());
        }
        owner.setState(ZombieState.PURSUING_PLAYER);
        climbDebug.log(entity, p, horizSq, dy, stuck, stuckTicks, pillar.active());
        if (packMarch) {
            // Dispatch is the only path to block breaking, pillaring and forced descent — skipping it is
            // what makes a migration non-destructive. Clear the latch too, so a member that aggroes mid-wall
            // and then loses its target does not resume a break it is no longer entitled to.
            breaking = false;
            return;
        }
        // Pass the current breaking-latch (was I breaking last tick?) so a committed zombie stays anchored on
        // its block instead of being re-steered to another breach mid-break.
        MoveDispatch.choose(owner, level, ctx, pillar, te, dx, dz, dy, horizSq, stuck, bx, bz, breaking);
        // Latch for next tick: MoveDispatch sets BREAKING when it requested a block break this tick.
        breaking = owner.state() == ZombieState.BREAKING;
    }
```

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Headless dev-server verification (broad — this method runs for every non-frozen zombie every tick)**

```bash
for suite in presence pack plague mech climb shade special; do
  LB_DEV_TEST=$suite timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-$suite.out 2>&1
  echo "=== $suite ==="
  grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log
  grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
done
```
Every suite must show `1` for ALL DONE and no FAIL lines. Check `run/server/crash-reports/` for new files.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java
git commit -m "refactor(entity): extract 5 early-return branches from ZombieBrain.tick"
```

---

### Task 9: Extract `ZombieMood.update` and `ZombieMood.handleDaySleep`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `guardUpdate(ServerLevel): boolean`, `updateCelebrationExpiry(long, float): void`, `updateFleeHysteresis(long, float, LivingEntity): void`, `updateSunShelter(ServerLevel, float): void`, `daySleepDisabled(long): boolean`, `handleSleepingDoze(long, boolean, boolean, int): boolean`, `handleRoused(long, boolean, boolean, int): boolean`, `dozeIfNotExposed(ServerLevel, int): boolean`, `seekShade(ServerLevel, long): void` — all `private`, instance methods, used only within this class.

**Context:** Both oversized methods live in the same file, so this is one task. `update(...)` (~80 lines) drives the celebrate/flee/shelter/sleep state machine each mood activation; `handleDaySleep(...)` (~117 lines, called from within `update`) owns the day-sleep sub-state-machine specifically. Do this task AFTER Task 8 — it's independent in terms of files touched, but both are the riskiest tasks in this plan and running them sequentially (not in parallel) keeps regression attribution clean if the headless harness ever does report a FAIL.

- [ ] **Step 1: Re-verify both methods**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
cat src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java
```
Confirm `update(ServerLevel, WorldAIContext)` and `handleDaySleep(ServerLevel, long, LivingEntity)` still have the shapes described below. If they've drifted meaningfully, report DONE_WITH_CONCERNS with what you found — do not force a mismatched extraction.

- [ ] **Step 2: Add the four `update`-related helpers**

Add these four methods immediately after `update(...)`:

```java
    private boolean guardUpdate(ServerLevel level) {
        if (!entity.isAlive()) {
            return true;
        }
        if (!ZombieMoodConfig.moodEnabled) {
            // Mood disabled at runtime: don't leave a zombie frozen mid-doze — hand it back to the normal hunt.
            if (state == State.SLEEPING) {
                wake(level.getGameTime(), false);
            }
            return true;
        }
        return false;
    }

    private void updateCelebrationExpiry(long now, float frac) {
        if (state == State.CELEBRATING && now >= celebrateUntil) {
            entity.setAggressive(false);
            state = (ZombieMoodConfig.fleeEnabled && frac < ZombieMoodConfig.regainHealthFraction)
                    ? State.FLEEING : State.NORMAL;
            distressScreamed = false;
        }
    }

    private void updateFleeHysteresis(long now, float frac, LivingEntity fleeThreat) {
        if (ZombieMoodConfig.fleeEnabled) {
            if (state == State.FLEEING) {
                var outcome = FleeHysteresis.whileFleeing(entity, fleeThreat, frac, fleeTracker);
                if (!outcome.stayFleeing()) {
                    state = State.NORMAL;
                    distressScreamed = false;
                    if (outcome.enterFight()) {
                        corneredUntil = now + ZombieMoodConfig.corneredFightTicks;
                    }
                }
            } else if (state != State.CELEBRATING
                    && FleeHysteresis.shouldEnter(now, corneredUntil, frac, fleeThreat)) {
                state = State.FLEEING;
                distressScreamed = false;
                lastRegenTime = now;
                fleeTracker.reset();
            }
        } else if (state == State.FLEEING) {
            state = State.NORMAL;
            distressScreamed = false;
        }
    }

    private void updateSunShelter(ServerLevel level, float frac) {
        boolean fleeingOrSheltering = state == State.FLEEING || state == State.SHELTERING;
        if (SunShelterOverride.eligible(fleeingOrSheltering, frac)) {
            var res = SunShelterOverride.evaluate(entity, level, shelterTarget);
            shelterTarget = res.shelterTarget();
            state = res.sheltering() ? State.SHELTERING : State.FLEEING;
        } else if (state == State.SHELTERING) {
            shelterTarget = null; // no longer eligible (healed up / threat gone path handled above)
            state = State.FLEEING;
        }
    }
```

- [ ] **Step 3: Replace the `update` body**

```java
    public void update(ServerLevel level, WorldAIContext ctx) {
        if (guardUpdate(level)) {
            return;
        }
        long now = level.getGameTime();
        float max = entity.getMaxHealth();
        float frac = max <= 0.0f ? 1.0f : entity.getHealth() / max;
        LivingEntity threat = currentThreat();
        // Flee threat (only when the flee behaviour is on): a WOUNDED zombie flees the nearest nearby PLAYER too,
        // not only whatever last hit it. Sleep-disturbance below still uses the plain `threat` (a silent nearby
        // player must NOT wake a sleeper).
        LivingEntity fleeThreat = ZombieMoodConfig.fleeEnabled ? flightThreat(threat, frac) : null;

        // Celebration latch expires on its own; if still hurt AND flee is on, roll into FLEEING to keep healing.
        updateCelebrationExpiry(now, frac);

        // Flee hysteresis — ONLY when fleeEnabled. Wounded zombie retreats (+ distress rally scream in apply),
        // enters below fleeHealthFraction, leaves at regain or when cornered. Disabled → it never flees; drop any
        // lingering FLEEING straight back to the hunt (so toggling it off at runtime doesn't strand a fleer).
        updateFleeHysteresis(now, frac, fleeThreat);

        // Sun-shelter override: dashes to shade instead of a straight retreat while burning in the open.
        updateSunShelter(level, frac);

        // Daytime sleep (runs only when NOT busy fleeing/sheltering/celebrating): a targetless zombie dozes by
        // day; below the immunity phase it first shuffles to shade. Owns the SLEEPING state + wake handling.
        handleDaySleep(level, now, threat);

        // Per-state side effects (see MoodStateDispatch): drop the hunt, keep LOD alive, fire distress scream.
        // The scream measures distance from what it's fleeing (fleeThreat), so it rallies once it's opened ground.
        boolean screamed = MoodStateDispatch.apply(state, entity, level, owner, ctx, fleeThreat, distressScreamed);
        if (screamed) {
            distressScreamed = true;
            DISTRESS_COUNT.incrementAndGet();
        }

        // Self-heal while fleeing, sheltering, or celebrating and still hurt.
        boolean regenEligible = state != State.NORMAL && frac < ZombieMoodConfig.regainHealthFraction;
        lastRegenTime = MoodRegen.tick(entity, regenEligible, now, lastRegenTime);
    }
```

- [ ] **Step 4: Add the five `handleDaySleep`-related helpers**

Add these five methods immediately after `handleDaySleep(...)` (before `abandonShadeSeek(...)`):

```java
    private boolean daySleepDisabled(long now) {
        if (ZombieMoodConfig.daySleepEnabled) {
            return false;
        }
        if (state == State.SLEEPING) {
            wake(now, false);
        }
        return true;
    }

    private boolean handleSleepingDoze(long now, boolean day, boolean disturbed, int phase) {
        if (state != State.SLEEPING) {
            return false;
        }
        if (!day || disturbed || DaySleep.staysAwake(entity, phase)) {
            wake(now, false); // night fell / hit / promoted to the awake minority → back to the hunt
            return true;
        }
        if (wakeAt != Long.MIN_VALUE && now >= wakeAt) {
            wake(now, true); // reaction delay elapsed → wake and head to the noise it heard
            return true;
        }
        dozeInPlace(); // a dozing zombie is always dormant (FROZEN + NoAi-held)
        return true;
    }

    private boolean handleRoused(long now, boolean day, boolean disturbed, int phase) {
        // A HIT keeps it awake to fight back.
        if (disturbed) {
            alertUntil = now + ZombieMoodConfig.daySleepAlertTicks;
        }
        // Stay AWAKE (hunt by sight AND sound via the normal brain, never doze) when: it's night; it's roused
        // (heard a noise / been hit within daySleepAlertTicks); it's investigating a heard spot; or it's in the
        // awake minority. A merely-SEEN silent player does NOT keep it up — that's the stealth, and gating on a
        // stable alert TIMER (not a per-tick audibility test) is what stops the chase<->doze stutter.
        boolean alert = now < alertUntil;
        // A pack march has the exact signature of a noise investigation — a target point with no entity
        // behind it — so without excluding it a migrating pack would never doze again, and would burn under
        // the open sky for every phase below sunImmunePhase. Day-sleep wins over migration by design.
        boolean investigatingNoise = owner.hasTarget() && owner.targetEntity() == null && !sleepSeekingShade
                && !owner.pursuit().pack().hasWaypoint();
        if (!day || disturbed || alert || investigatingNoise || DaySleep.staysAwake(entity, phase)) {
            sleepSeekingShade = false; // busy hunting/investigating — abandon any shade-seek
            return true;
        }
        return false;
    }

    private boolean dozeIfNotExposed(ServerLevel level, int phase) {
        boolean exposed = DaySleep.burnsInSun(phase) && level.canSeeSky(entity.blockPosition());
        if (exposed) {
            return false;
        }
        // In shade, or the horde is sun-immune → doze here. Snuff any residual sun-fire from the shade-run so
        // it isn't "asleep in the shade yet still on fire".
        if (sleepSeekingShade && entity.getRemainingFireTicks() > 0
                && !level.canSeeSky(entity.blockPosition())) {
            entity.setRemainingFireTicks(0);
        }
        dozeInPlace();
        if (entity.onGround()) {
            state = State.SLEEPING; // only commit to SLEEPING once grounded+frozen; if it's still finishing a
                                    // leap/fall arc, dozeInPlace deferred — stay NORMAL and retry next tick.
        }
        return true;
    }

    private void seekShade(ServerLevel level, long now) {
        // Master toggle (ZombieMoodConfig.sunShelterEnabled): with sun-shelter off, NO shade detour exists
        // anywhere — the exposed sleeper keeps roaming in the open and simply burns. This is the second of the
        // two ShelterFinder.findShade call sites; the other is SunShelterOverride.eligible.
        if (!ZombieMoodConfig.sunShelterEnabled) {
            sleepSeekingShade = false;
            return;
        }

        // Exposed and still burning → reach shade first (TOP priority, before dozing).
        if (owner.hasTarget()) {
            // While a target is held the brain owns the walk and this method deliberately stands back — but
            // nothing used to check the walk was still going anywhere. Measured in the headless shade rig: a
            // zombie stopped ONE BLOCK short of the roof and stood there with hasTarget and isSeekingShade both
            // true from t+40 to t+320. It never arrived, so it never dozed; it never lost the memory, so it
            // never re-planned. Outside the rig's rain it burns to death beside the shade it had found.
            if (sleepSeekingShade && shadeStall.stalled(now, owner.pursuit().distanceToTargetSq())) {
                LethalBreed.LOGGER.debug("[LethalBreed] zombie {} abandoned a stalled shade-seek", entity.getId());
                abandonShadeSeek(now, entity.blockPosition());
            }
            return;
        }
        if (TargetingConfig.targetMemoryTicks <= 0) {
            return; // memory routing disabled → can't drive a shade-seek; keep roaming (it will burn)
        }
        BlockPos here = entity.blockPosition();
        // Skip the sweep while the last failure is still fresh AND we have not meaningfully moved. Moving
        // more than 4 blocks exposes genuinely new volume, so that always re-arms the search immediately.
        boolean moved = shadeFailedAt == null || shadeFailedAt.distSqr(here) > 16.0;
        if (!moved && now < shadeRetryAt) {
            return;
        }
        shadeScans++;
        BlockPos shade = ShelterFinder.findShade(level, here, ZombieMoodConfig.shelterSearchRadius);
        if (shade == null) {
            abandonShadeSeek(now, here);
            return; // no shade in range → keep roaming (can't help the burn)
        }
        shadeFailedAt = null;
        shadeRetryAt = Long.MIN_VALUE;
        shadeStall.reset(); // a fresh target gets its full patience
        // Remember the shade as a target so classify + the brain path AND break toward it, then doze on arrival.
        owner.pursuit().rememberTarget(shade.getX() + 0.5, shade.getY(), shade.getZ() + 0.5,
                now + TargetingConfig.targetMemoryTicks);
        sleepSeekingShade = true;
    }
```

- [ ] **Step 5: Replace the `handleDaySleep` body**

```java
    private void handleDaySleep(ServerLevel level, long now, LivingEntity threat) {
        if (daySleepDisabled(now)) {
            return;
        }
        // Sleep timers/NoAi are valid ONLY while actually SLEEPING. If another subsystem moved us out of it
        // (e.g. a hit forced FLEEING via the hysteresis block above, bypassing wake()), scrub the stale fields so
        // the next doze starts clean — otherwise a leftover armed wakeAt would instantly re-wake the next sleep.
        if (state != State.SLEEPING) {
            clearSleepState();
        }
        if (state != State.NORMAL && state != State.SLEEPING) {
            return; // busy fleeing / sheltering / celebrating — no dozing
        }
        boolean day = level.isBrightOutside();
        int phase = PhaseManager.current();
        // Sun-fire is deliberately NOT a disturbance: below the immunity phase an exposed zombie is ALWAYS on
        // fire, and reaching shade is exactly how it escapes that. Only a mob or NON-fire damage disturbs.
        boolean disturbed = threat != null || (entity.hurtTime > 0 && !entity.isOnFire());

        if (handleSleepingDoze(now, day, disturbed, phase)) {
            return;
        }

        // ---- state == NORMAL: hunt if roused, else shelter/sleep by day ----
        if (handleRoused(now, day, disturbed, phase)) {
            return;
        }

        // Idle daytime sleeper → SHELTER first (if it would burn under open sky), then doze.
        if (dozeIfNotExposed(level, phase)) {
            return;
        }

        seekShade(level, now);
    }
```

If Step 1's re-read shows any field/method name mismatches (`shadeStall`, `shelterTarget`, `corneredUntil`, `fleeTracker`, `wakeAt`, `sleepSeekingShade`, `shadeFailedAt`, `shadeRetryAt`, `shadeScans`, etc.), use the actual names from the file.

- [ ] **Step 6: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Headless dev-server verification (broad — mood update runs for every non-frozen zombie every activation)**

```bash
for suite in presence pack plague mech climb shade special statue clear placed breach compute; do
  LB_DEV_TEST=$suite timeout 300 ./gradlew runServer --console=plain > /tmp/lb-runserver-$suite.out 2>&1
  echo "=== $suite ==="
  grep -c '\[LB-Verify\] ALL DONE' run/server/logs/latest.log
  grep -E '\[LB-Verify\].*FAIL' run/server/logs/latest.log
done
```
This is every suite the harness has — since `ZombieMood` is exercised by essentially every zombie in every scenario, run the full set for this task. Every suite must show `1` for ALL DONE and no FAIL lines. Check `run/server/crash-reports/` for any new file across the whole run.

- [ ] **Step 8: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java
git commit -m "refactor(entity): extract update/handleDaySleep helper methods in ZombieMood"
```

---

## Self-Review Notes

- **Spec coverage:** all 6 oversized/duplicated methods named in the request (ZombieBrain.tick, ZombieMood.update+handleDaySleep, LodBucketPass.run, ContaminationTick.tick, TargetSelector.findNearest) have a task; all 4 smaller fixes (pack dedup, EntityEventsInit lambda, ZOMBIE_VISION, magic numbers) have a task. `PackMaterializer`/`PackLifecycle` guard-loop dedup is explicitly excluded with reasoning (Global Constraints) — investigated by design agent 3 and found to have 3 independent axes of difference, not a mechanical dedup candidate.
- **Placeholder scan:** every step shows exact before/after code (sourced from three independent design passes, each spot-verified against the real file at specific line numbers before this plan was written). Every task names its exact headless-suite verification command.
- **Type consistency:** helper method names/signatures introduced in each task are used consistently within that task's own steps; no task references a helper defined in a different task (each task is self-contained to one or a few files).
- Tasks 1-6 touch disjoint files and can run in any relative order. Tasks 7, 8, 9 are the highest-risk (hottest, broadest-impact code) and are ordered last, with Task 9 explicitly sequenced after Task 8 (same reasoning: keep regression attribution clean, not a file dependency).
- If ANY headless suite reports a FAIL line or a missing `ALL DONE` after a task's change, that task's implementer must treat it as a real regression, not proceed to commit, and report BLOCKED with the full suite log attached — do not guess at a fix without understanding what the suite actually checks (read the suite's harness class under `src/dev/java/.../dev/harness/` first).
