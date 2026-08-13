# Mod & Web Dead-Code / Duplication Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate every verified dead-code, duplicate-code, and near-duplicate ("code qui se ressemble") finding from the 2026-08-10 read-only code audit of `mod/` and `web/`, without changing observable behavior.

**Architecture:** Each task is a small, isolated extract-and-delegate refactor: pull the duplicated logic into one new (or existing) helper, then make every former duplicate call that helper and delete its own copy. No task changes gameplay numbers, packet formats, save data, or rendered output — this is pure DRY/dead-code cleanup, verified by "before/after identical behavior".

**Tech Stack:** Java 21, Fabric (Minecraft 1.21.11), JUnit 5 (`org.junit.jupiter:junit-jupiter:5.10.2`) for the pure-logic test source set under `mod/src/test/java`; static HTML/CSS/JS for `web/`.

## Global Constraints

- Two separate git repositories: `mod/` changes belong to the `LethalBreed` repo (root `/run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/`); `web/` changes belong to the separate `LethalBreed-web` repo (root `/run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/web/`). Commit each repo's changes separately.
- **Scope boundary (deliberate):** this plan covers only the audit's *Dead code*, *Duplicates*, and *Similar patterns* categories — the categories the user actually asked to fix ("code mort", "code dupliqué", "code qui se ressemble"). The audit's separate *Complexity* findings (e.g. `LodBucketPass.run()` at 145 lines, `ContaminationTick.tick()` at ~110 lines, `ConfigLoader.load()`, `TargetSelector`, `EntityEventsInit.registerTracking()`, `PhaseManager`) are **out of scope**: splitting them is a behavior-preserving-in-theory but much higher-risk restructuring of hot, untested, Minecraft-coupled game loops, and deserves its own plan with in-game manual test protocols, not a mechanical dedup pass.
- **Testing convention already established by this repo** (see `mod/build.gradle.kts` lines ~26-30): only *pure-logic* classes (no Minecraft types) are reachable from the JUnit test source set. Classes that touch `Zombie`, `Mob`, `Mixin`, `ServerLevel`, etc. have **no** unit tests anywhere in this codebase — that is the project's existing, intentional boundary, not a gap this plan should try to close. Tasks touching pure-logic classes (spatial cell math, config primitive dispatch) get real JUnit tests written test-first. Tasks touching Minecraft-coupled classes (entities, mixins, commands, screens, blocks) are verified by `./gradlew compileJava`, `./gradlew test` (full regression run of the existing pure-logic suite), and a concrete manual in-game smoke check described in the task.
- Every deletion in this plan was verified with a repo-wide `grep` immediately before writing this plan (August 2026 snapshot) — re-run the same `grep` at the start of each task in case the tree has moved since.
- Do not rename any config JSON field, packet type, translation key, or public Javadoc `@link`/`@see` target outside of what a task explicitly describes.
- Findings investigated and **deliberately excluded** from this plan (do not "fix" these — re-introducing them was considered and rejected during planning):
  - `command/LethalPhaseCommand.java`, `LethalSpecialCommand.java`, `LethalConfigCommand.java` — the shared `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` line is a single Brigadier builder call repeated 3×. Extracting it would add a layer of indirection over a self-explanatory one-liner for no real DRY win.
  - `net/LethalConfigPayloads.java` — the `OpenConfig`/`SetConfig` record `Type`/`StreamCodec`/`type()` shape is the idiomatic Fabric networking boilerplate, not an accidental duplication; fighting it would fight the framework's type-safety design.
  - Merging `ZombieBellyModelMixin` + `ZombieSleepArmsMixin` into one mixin — they share an injection point but are independent concerns (belly swell vs. sleep pose); merging them would recreate the multi-responsibility-method smell this same audit flags elsewhere (see Complexity, out of scope above).
  - `effect/LethalBreedEffects.java` `ZOMBIE_VISION` reusing `SuperContaminationEffect` — a real design smell, but fixing it means introducing a new `MobEffect` subclass and touches an effect that may be active on live entities across a save; that is a behavior/compat change, not a mechanical dedup, so it is left as a documented note for a future targeted fix.
  - `mixin/plague/PlagueBlocksRegenMixin.java` hardcoded `SKIP_PER_LEVEL`/`SKIP_MAX` — making these config-driven means adding new config fields + bounds + docs, which is a feature addition, not a cleanup.
  - `mixin/spawn/SpawnFrequencyMixin.java` / `SpawnStateMobcapMixin.java` guard duplication — 2 lines, and unifying it risks behavior drift in a spawn-gating hot path for no measurable benefit.
  - `web/assets/js/chrome/lb-prefs.js:24` (`window.LB.basePath`) — confirmed zero in-repo callers, but its own comment documents it as the read side of an **external** deployment/sync script contract (`data-base-path`); removing it could break a pipeline outside this repo. Left untouched.
  - `config/ConfigBounds.java` clamp dispatch — on closer inspection (not just the audit's file list) this dispatches on the **boxed runtime value's type** (`instanceof Integer/Long/Float/Double`, 4 cases, no boolean/list branch) rather than the **declared field type** (6 cases) that `ConfigType`, `ConfigSchema`, and `ConfigWriter` dispatch on. It is not the same duplication the audit grouped it with, so it is excluded from Task 8's consolidation.

---

## File Structure

**Mod (new files):**
- `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/CellMath.java` — shared flat-XZ cell-key packing + radius-to-cell-index math (Task 5)
- `mod/src/test/java/com/dreykaoas/lethalbreed/spatial/CellMathTest.java` — its test
- `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/PresentationalMixinNotes.java` — shared Javadoc anchor for the `require = 0` rationale (Task 6)
- `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ArmPose.java` — shared arm-pose-setting helper (Task 7)
- `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitive.java` — the 6-primitive-kind enum (Task 8)
- `mod/src/test/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitiveTest.java` — its test
- `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/BoundsSplitNote.java` — shared Javadoc anchor for the "split out of ConfigBoundsTable" rationale (Task 9)

**Mod (modified files):** listed per-task below.

**Web (modified files):** listed in Task 13.

---

### Task 1: Remove unenforced client-render config fields + stale Javadoc (dead code)

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/client/LethalBreedClientConfig.java:28-40`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/tick/WorldMaintenance.java:50-51`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: nothing other tasks depend on.

**Context:** `LethalBreedClientConfig` currently has 5 fields (`maxRenderedZombies`, `reduceFarDetail`, `farDetailDistance`, `instancedRendering`, `billboardFarZombies`) that are serialized to/from `config/lethalbreed-client.json` but never read anywhere in the mod (confirmed by `grep -rn` across the whole `mod/src` tree — only their own declarations and one comment mention them). They are documented in their own Javadoc as "Phase 7, NOT enforced yet" reserved knobs. Since they do nothing today, remove them; they can be re-added, with their eventual real behavior, whenever Phase 7 rendering work actually lands (YAGNI — don't ship inert config).

`WorldMaintenance.java:50-51` has an orphaned Javadoc comment ("Re-bucket moved prey and drop dead prey, once per dimension per tick...") sitting directly above `tickPacks()`, a method about pack lifecycle/march — not prey re-bucketing. It is a leftover from a deleted/renamed method. Delete the stray comment; the correct Javadoc for `tickPacks()` already exists immediately below it.

- [ ] **Step 1: Confirm no other references exist (safety re-check)**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -rn "maxRenderedZombies\|reduceFarDetail\|farDetailDistance\|instancedRendering\|billboardFarZombies" src/
```
Expected: only hits inside `client/LethalBreedClientConfig.java` itself (the 5 field declarations plus the one comment at line ~93 that mentions `maxRenderedZombies` by name).

- [ ] **Step 2: Remove the 5 unenforced fields**

In `mod/src/main/java/com/dreykaoas/lethalbreed/client/LethalBreedClientConfig.java`, delete lines 28-40 (the `maxRenderedZombies`, `reduceFarDetail`, `farDetailDistance`, `instancedRendering`, `billboardFarZombies` field declarations and their Javadoc), so the class reads:

```java
    /** Skip rendering plain zombies farther than {@link #zombieRenderDistance} blocks. */
    public boolean cullDistantZombies = true;
    /** Distance (blocks) beyond which zombies are culled from rendering. */
    public double zombieRenderDistance = 96.0;

    /**
     * When Sodium is installed, defer to its frustum/chunk culling and keep the mod's render
     * tweaks conservative to avoid double work or conflicts.
     */
    public boolean adaptToSodium = true;
```

- [ ] **Step 3: Remove the now-stale comment that name-drops `maxRenderedZombies`**

Search the same file for the comment near line 93 (`// Deliberately does NOT log maxRenderedZombies: ...`) and delete that comment line — it referenced a field that no longer exists.

- [ ] **Step 4: Remove the orphaned Javadoc in `WorldMaintenance.java`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/tick/WorldMaintenance.java`, delete this stray block (the one starting `/** Re-bucket moved prey...`) that currently sits directly above the real Javadoc for `tickPacks()`:

```java
    /** Re-bucket moved prey and drop dead prey, once per dimension per tick. Costs O(prey), never
     *  O(zombies) — that asymmetry is the entire reason the index exists. */
```

Leave the following block (the real `tickPacks()` Javadoc, starting `/** Advance every pack in every loaded dimension: ...`) untouched.

- [ ] **Step 5: Compile and run the existing test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, no new failures. (No test exercised the removed fields — Minecraft-coupled config classes have no unit tests in this project, see Global Constraints.)

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/client/LethalBreedClientConfig.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/tick/WorldMaintenance.java
git commit -m "chore(client-config): drop 5 unenforced Phase-7 render knobs, fix orphaned javadoc"
```

---

### Task 2: Remove dead mixin accessor OR its unused half

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/MobGoalsAccessor.java`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Context:** The original audit agent claimed `lethalbreed$targetSelector()` (line 19) is dead. **This is a false positive** — a repo-wide grep during planning found it IS used, twice, in `util/VanillaTargetingGoals.java:32` and `:47`. Do not delete it. This task is a no-op verification step, kept in the plan so the false-positive is documented and nobody re-flags it later.

- [ ] **Step 1: Re-verify the accessor is used (documents the false positive)**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -rn 'lethalbreed\$targetSelector' src/
```
Expected output includes `src/main/java/com/dreykaoas/lethalbreed/util/VanillaTargetingGoals.java:32` and `:47` in addition to the declaration in `MobGoalsAccessor.java:19`. **Do not delete anything in this file.** No commit for this task — it is a verification-only step.

---

### Task 3: Dedupe hunt-dropping logic — `ZombieMood.suppressHunt()` → `MoodStateDispatch.dropHunt()`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/mood/MoodStateDispatch.java:56-61`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java:368,380-385`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `MoodStateDispatch.dropHunt(Zombie, SmartZombie)` becomes `public static` (was `private static`) — no other task calls it, but note this if you touch `MoodStateDispatch` later.

**Context:** Both methods are byte-identical:

```java
entity.setTarget(null);
owner.pursuit().clearTarget();
owner.pursuit().clearMemory();
owner.pursuit().clearSound();
```

`ZombieMood` (package `com.dreykaoas.lethalbreed.entity`) already imports `MoodStateDispatch` and its nested `State` enum, so it can call a public static method on it directly.

- [ ] **Step 1: Make `dropHunt` public in `MoodStateDispatch`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/entity/mood/MoodStateDispatch.java`, change:

```java
    /** Drop the hunt: no melee target, no stale memory/sound pursuit. */
    private static void dropHunt(Zombie entity, SmartZombie owner) {
```

to:

```java
    /** Drop the hunt: no melee target, no stale memory/sound pursuit. Also called directly by
     *  {@link com.dreykaoas.lethalbreed.entity.ZombieMood#sleep} when entering the SLEEPING state, which is
     *  not one of {@link MoodStateDispatch}'s own dispatched states. */
    public static void dropHunt(Zombie entity, SmartZombie owner) {
```

- [ ] **Step 2: Replace `ZombieMood.suppressHunt()`'s body with a delegated call, then delete it**

In `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java`, change the call site at line 368:

```java
        suppressHunt();
```

to:

```java
        MoodStateDispatch.dropHunt(entity, owner);
```

Then delete the now-unused private method (lines 380-385):

```java
    private void suppressHunt() {
        entity.setTarget(null);
        owner.pursuit().clearTarget();
        owner.pursuit().clearMemory();
        owner.pursuit().clearSound();
    }

```

- [ ] **Step 3: Compile and run the existing test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual in-game smoke check**

Launch the dev client (`./gradlew runClient`), spawn a zombie via `/lethalspecial` or let one occur naturally, wait for daytime so it enters the SLEEPING state (arms lower, stops hunting, ambient groan stops) — confirm it still stops targeting/pathing exactly as before. This is the one call site that used to go through `suppressHunt()`.

- [ ] **Step 5: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/mood/MoodStateDispatch.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java
git commit -m "refactor(mood): dedupe ZombieMood.suppressHunt into MoodStateDispatch.dropHunt"
```

---

### Task 4: Dedupe gear-stripping — `ZombieVariation.stripGear()` → `SpawnControl.stripEquipment()`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieVariation.java:15,20,45-75`

**Interfaces:**
- Consumes: `com.dreykaoas.lethalbreed.entity.spawn.SpawnControl.stripEquipment(Mob mob)` (already `public static`, unchanged).
- Produces: nothing.

**Context:** `ZombieVariation.stripGear(Zombie)` and `SpawnControl.stripEquipment(Mob)` (in `entity/spawn/SpawnControl.java`) both hold an identical 6-element `EquipmentSlot[]` array and an identical loop body (`setItemSlot(slot, ItemStack.EMPTY)` + `setDropChance(slot, 0)`, then `setCanPickUpLoot(false)`). `Zombie extends Mob`, so `SpawnControl.stripEquipment` accepts a `Zombie` directly — no signature change needed anywhere.

- [ ] **Step 1: Replace the call site**

In `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieVariation.java`, change:

```java
        stripGear(z); // zombies never carry weapons/tools/armor (also clears vanilla natural gear + pickups)
```

to:

```java
        SpawnControl.stripEquipment(z); // zombies never carry weapons/tools/armor (also clears vanilla natural gear + pickups)
```

- [ ] **Step 2: Delete the duplicated field + method**

Delete this block (currently lines 62-75):

```java
    /** The six wearable/held slots a zombie could otherwise show gear in. */
    private static final EquipmentSlot[] GEAR_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Remove every held/worn item so a zombie never appears with a weapon, tool or armor (covers vanilla
     *  natural spawn gear too), and stop it from picking gear up off the ground. */
    private static void stripGear(Zombie z) {
        for (EquipmentSlot slot : GEAR_SLOTS) {
            z.setItemSlot(slot, ItemStack.EMPTY);
            z.setDropChance(slot, 0.0f);
        }
        z.setCanPickUpLoot(false);
    }
```

- [ ] **Step 3: Add the import, remove the now-unused ones**

Add, next to the other `entity.spawn`-adjacent imports:
```java
import com.dreykaoas.lethalbreed.entity.spawn.SpawnControl;
```

Remove these two imports (both are now unused — `EquipmentSlot` and `ItemStack` were only used inside the deleted block; confirm with the grep in Step 4 before removing):
```java
import net.minecraft.world.entity.EquipmentSlot;
```
```java
import net.minecraft.world.item.ItemStack;
```

- [ ] **Step 4: Verify no other use of the removed imports remains in this file**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -n "EquipmentSlot\|ItemStack" src/main/java/com/dreykaoas/lethalbreed/entity/ZombieVariation.java
```
Expected: no output (both imports fully removed, no remaining references).

- [ ] **Step 5: Compile and run the existing test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual in-game smoke check**

Launch the dev client, spawn several natural or `/lethalspecial` zombies, confirm none carry visible weapons/armor and none show a pickup animation for dropped items — same as before this change.

- [ ] **Step 7: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieVariation.java
git commit -m "refactor(spawn): dedupe ZombieVariation gear-strip into SpawnControl.stripEquipment"
```

---

### Task 5: Extract shared spatial cell-key math (`SpatialGrid` + `TargetIndex`)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/CellMath.java`
- Create: `mod/src/test/java/com/dreykaoas/lethalbreed/spatial/CellMathTest.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/SpatialGrid.java:41-45,90-93`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/TargetIndex.java:59-65`

**Interfaces:**
- Consumes: nothing.
- Produces: `CellMath.packKey(int cx, int cz): long`, `CellMath.floorCell(double coord, int cellSize): int` — pure static functions, no other task depends on them.

**Context:** Both classes independently define an identical `packKey(int cx, int cz)` (packs a cell coordinate pair into one `long`) and an identical 2-line "floor a world coordinate down to a cell index" expression, repeated 4× per class for `minCx`/`maxCx`/`minCz`/`maxCz`. Both classes are pure logic (no Minecraft types in the math itself, only in what they store), so this is a fully unit-testable extraction — write the test first.

- [ ] **Step 1: Write the failing test**

Create `mod/src/test/java/com/dreykaoas/lethalbreed/spatial/CellMathTest.java`:

```java
package com.dreykaoas.lethalbreed.spatial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CellMathTest {

    @Test
    void packKeyIsDistinctPerCell() {
        assertNotEquals(CellMath.packKey(1, 2), CellMath.packKey(2, 1));
        assertEquals(CellMath.packKey(5, -3), CellMath.packKey(5, -3));
    }

    @Test
    void packKeyMatchesTheOriginalFormula() {
        // Pinned to the exact bit-packing both SpatialGrid and TargetIndex relied on before this
        // extraction, so an accidental formula change here is caught immediately.
        assertEquals((((long) 7) << 32) ^ (-4 & 0xffffffffL), CellMath.packKey(7, -4));
    }

    @Test
    void floorCellRoundsTowardNegativeInfinity() {
        // Math.floorDiv semantics: -1 / 16 floors to -1, not 0 (unlike a plain integer division).
        assertEquals(-1, CellMath.floorCell(-1.0, 16));
        assertEquals(-1, CellMath.floorCell(-16.0, 16));
        assertEquals(-2, CellMath.floorCell(-16.5, 16));
        assertEquals(0, CellMath.floorCell(0.0, 16));
        assertEquals(0, CellMath.floorCell(15.9, 16));
        assertEquals(1, CellMath.floorCell(16.0, 16));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails on compilation (class does not exist yet)**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew test --tests "com.dreykaoas.lethalbreed.spatial.CellMathTest"`
Expected: FAILS to compile — `cannot find symbol: class CellMath`.

- [ ] **Step 3: Create `CellMath`**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/CellMath.java`:

```java
package com.dreykaoas.lethalbreed.spatial;

/**
 * Shared flat-XZ spatial-grid math: cell-key packing and world-coordinate-to-cell-index conversion. Used
 * by both {@link SpatialGrid} (the zombie index) and {@link TargetIndex} (the prey index) so the two
 * independently-tuned indices hash cells identically, even though they use different cell sizes.
 */
public final class CellMath {
    private CellMath() {}

    /** Pack a cell coordinate pair into one long key. The single source of truth for cell hashing. */
    public static long packKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Floor a world coordinate down to its cell index for the given cell size. */
    public static int floorCell(double coord, int cellSize) {
        return Math.floorDiv((int) Math.floor(coord), cellSize);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew test --tests "com.dreykaoas.lethalbreed.spatial.CellMathTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Delegate `SpatialGrid` to `CellMath`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/SpatialGrid.java`, replace the private method:

```java
    private static long packKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }
```

with:

```java
    private static long packKey(int cx, int cz) {
        return CellMath.packKey(cx, cz);
    }
```

(Kept as a thin private wrapper rather than replacing every call site's name, since `SpatialGrid` calls `packKey(...)` in several places — this way only the one-method body changes.)

Then in `queryRadiusInto`, replace:

```java
        int minCx = Math.floorDiv((int) Math.floor(x - radius), cell);
        int maxCx = Math.floorDiv((int) Math.floor(x + radius), cell);
        int minCz = Math.floorDiv((int) Math.floor(z - radius), cell);
        int maxCz = Math.floorDiv((int) Math.floor(z + radius), cell);
```

with:

```java
        int minCx = CellMath.floorCell(x - radius, cell);
        int maxCx = CellMath.floorCell(x + radius, cell);
        int minCz = CellMath.floorCell(z - radius, cell);
        int maxCz = CellMath.floorCell(z + radius, cell);
```

- [ ] **Step 6: Delegate `TargetIndex` to `CellMath`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/spatial/TargetIndex.java`, replace:

```java
    private static long packKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }
```

with:

```java
    private static long packKey(int cx, int cz) {
        return CellMath.packKey(cx, cz);
    }
```

And in `collectInto`, replace:

```java
        int minCx = Math.floorDiv((int) Math.floor(x - radius), CELL);
        int maxCx = Math.floorDiv((int) Math.floor(x + radius), CELL);
        int minCz = Math.floorDiv((int) Math.floor(z - radius), CELL);
        int maxCz = Math.floorDiv((int) Math.floor(z + radius), CELL);
```

with:

```java
        int minCx = CellMath.floorCell(x - radius, CELL);
        int maxCx = CellMath.floorCell(x + radius, CELL);
        int minCz = CellMath.floorCell(z - radius, CELL);
        int maxCz = CellMath.floorCell(z + radius, CELL);
```

(`SpatialGrid` and `TargetIndex` are both already in package `com.dreykaoas.lethalbreed.spatial`, same as `CellMath` — no new imports needed.)

- [ ] **Step 7: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, all tests pass including the new `CellMathTest`.

- [ ] **Step 8: Manual in-game smoke check**

Launch the dev client, spawn a pack of zombies near a player and near villagers/animals, confirm targeting/aggro range and sound-bus radius queries still behave identically (zombies still notice and path to prey/players at the same distances as before).

- [ ] **Step 9: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/spatial/CellMath.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/spatial/CellMathTest.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/spatial/SpatialGrid.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/spatial/TargetIndex.java
git commit -m "refactor(spatial): extract shared cell-key math into CellMath"
```

---

### Task 6: Consolidate the "require = 0" presentational-mixin rationale (8 files)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/PresentationalMixinNotes.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/EndermanParticleMixin.java:25-27`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/GuiContaminationHudMixin.java:29-31`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/PlayerModelZombieArmsMixin.java:28-30`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java:24-26`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieSleepArmsMixin.java:25-28`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/AvatarRendererHallucinationMixin.java:28-30`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/EntityRendererMixin.java:24-26`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRendererMixin.java:27-29`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing other tasks depend on. `PresentationalMixinNotes` must be `public` (not package-private) because `mixin.client.model` and `mixin.client.render` are different packages from `mixin.client`.

**Context:** All 8 files carry the same 3-line comment (word-for-word in 6 of them, lightly paraphrased in the other 2) directly above a `require = 0` mixin annotation, explaining why that specific mixin is allowed to fail its injection silently. Comments cannot be "called" like code, so the fix is a documented anchor class the 8 short comments point back to instead of repeating themselves.

- [ ] **Step 1: Create the anchor class**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/PresentationalMixinNotes.java`:

```java
package com.dreykaoas.lethalbreed.mixin.client;

/**
 * Shared rationale for every purely-presentational client mixin that sets {@code require = 0} on its
 * injection: {@code lethalbreed.mixins.json} sets {@code defaultRequire = 1}, which turns any failed
 * injection into a hard crash at load — correct for gameplay mixins, wrong for a cosmetic one. A HUD or
 * render mod that redirects or injects into the same target should cost the player a visual effect, not
 * the whole game.
 *
 * <p>Not instantiated. Exists only as a single place for the 8 client-side cosmetic mixins to point their
 * {@code require = 0} comment at, instead of repeating this paragraph in each of them.
 */
public final class PresentationalMixinNotes {
    private PresentationalMixinNotes() {}
}
```

- [ ] **Step 2: Shrink the 8 duplicated comments to a one-line pointer**

In each of the 8 files below, find the 3-line (or similarly-worded) comment immediately above the `@Redirect(require = 0, ...)` or `@Inject(require = 0, ...)` annotation, and replace it with a single line:

```java
    // require = 0: purely presentational — see PresentationalMixinNotes.
```

Files and their exact comment block to replace:

- `mixin/client/EndermanParticleMixin.java` (currently lines 25-27):
  ```java
    // require = 0: purely presentational. lethalbreed.mixins.json sets defaultRequire=1, which turns any
    // failed injection into a hard crash at load — correct for gameplay mixins, wrong here. A HUD or
    // render mod that redirects the same call should cost the player a visual effect, not the game.
  ```
- `mixin/client/GuiContaminationHudMixin.java` (currently lines 29-31): identical text to the block above.
- `mixin/client/model/PlayerModelZombieArmsMixin.java` (currently lines 28-30): identical text.
- `mixin/client/model/ZombieBellyModelMixin.java` (currently lines 24-26): identical text.
- `mixin/client/render/AvatarRendererHallucinationMixin.java` (currently lines 28-30): identical text.
- `mixin/client/render/EntityRendererMixin.java` (currently lines 24-26): identical text.
- `mixin/client/render/LivingEntityRendererMixin.java` (currently lines 27-29): identical text.
- `mixin/client/model/ZombieSleepArmsMixin.java` (currently lines 25-28), slightly different wording:
  ```java
    // require = 0: purely presentational, same reasoning as ZombieBellyModelMixin on this very method.
    // lethalbreed.mixins.json sets defaultRequire=1, which turns any failed injection into a hard crash at
    // load — correct for gameplay mixins, wrong here. A HUD or render mod that redirects the same call
    // should cost the player a sleep pose, not the game.
  ```

Replace each of the 8 blocks above with the same one-liner:
```java
    // require = 0: purely presentational — see PresentationalMixinNotes.
```

- [ ] **Step 3: Add the import to the 4 files outside `mixin.client`**

`EndermanParticleMixin.java` and `GuiContaminationHudMixin.java` are already in package `com.dreykaoas.lethalbreed.mixin.client` — no import needed.

The other 6 files are in `mixin.client.model` or `mixin.client.render`; add this import to each of them (next to their existing imports):
```java
import com.dreykaoas.lethalbreed.mixin.client.PresentationalMixinNotes;
```

Files needing the import: `PlayerModelZombieArmsMixin.java`, `ZombieBellyModelMixin.java`, `ZombieSleepArmsMixin.java`, `AvatarRendererHallucinationMixin.java`, `EntityRendererMixin.java`, `LivingEntityRendererMixin.java`.

(The import is only referenced in a comment, so most IDEs/compilers will not complain about it being "unused" since Java does not detect comment references — however, to keep it meaningful and avoid a real unused-import warning, reference the class once in the Javadoc instead: use `{@link PresentationalMixinNotes}` in the one-line comment area's preceding Javadoc if the file has one, OR skip the import and instead spell the fully-qualified name in the comment text: `// require = 0: purely presentational — see com.dreykaoas.lethalbreed.mixin.client.PresentationalMixinNotes.` Use the fully-qualified form in the comment and do **not** add an unused import, since a plain `//` comment cannot "consume" an import and an unused import would fail this project's own lint expectations.)

- [ ] **Step 4: Corrected Step 2 comment text (use fully-qualified reference, no import)**

Re-apply Step 2 using this exact one-liner instead (supersedes the short form above), in all 8 files:
```java
    // require = 0: purely presentational — see PresentationalMixinNotes in this package's parent (mixin.client).
```

- [ ] **Step 5: Compile**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (mixin annotations are unaffected by comment changes; this only verifies no stray syntax error was introduced while editing 8 files).

- [ ] **Step 6: Manual in-game smoke check**

Launch the dev client. Confirm all 8 cosmetic effects still work: infected enderman particles turn black, HUD hearts/food tint green while contaminated, a hallucinating player sees zombie-skinned players with straight zombie arms, the Bombeur's belly still swells, a sleeping zombie's arms still lower, distant zombies are still culled per the client config. None of this should have changed — only comments moved.

- [ ] **Step 7: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/PresentationalMixinNotes.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/EndermanParticleMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/GuiContaminationHudMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/PlayerModelZombieArmsMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieSleepArmsMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/AvatarRendererHallucinationMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/EntityRendererMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRendererMixin.java
git commit -m "docs(mixin): dedupe the 8 copy-pasted require=0 rationale comments into one anchor class"
```

---

### Task 7: Extract shared arm-pose helper (`PlayerModelZombieArmsMixin` + `ZombieSleepArmsMixin`)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ArmPose.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/PlayerModelZombieArmsMixin.java:36-42`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieSleepArmsMixin.java:35-41`

**Interfaces:**
- Consumes: nothing (Task 6 already touched these two files' comments; do this task after Task 6 to avoid overlapping edits to the same files, or carefully re-apply against the current file content if done out of order).
- Produces: `ArmPose.set(HumanoidModel<?> model, float x, float y, float z): void` — no other task depends on it.

**Context:** Both mixins set 6 fields (`rightArm`/`leftArm` × `xRot`/`yRot`/`zRot`) one line at a time. `PlayerModelZombieArmsMixin` sets an asymmetric pose (`zRot` differs per arm: `0.05f` vs `-0.05f`), so the helper must take arm-specific rotation, not assume symmetry.

- [ ] **Step 1: Create `ArmPose`**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ArmPose.java`:

```java
package com.dreykaoas.lethalbreed.mixin.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Sets one arm's rotation on a shared {@link HumanoidModel}. Shared by the client-only cosmetic pose
 *  mixins ({@code PlayerModelZombieArmsMixin}, {@code ZombieSleepArmsMixin}) that each force both arms
 *  into a fixed pose at the tail of {@code setupAnim}. */
final class ArmPose {
    private ArmPose() {}

    static void set(ModelPart arm, float xRot, float yRot, float zRot) {
        arm.xRot = xRot;
        arm.yRot = yRot;
        arm.zRot = zRot;
    }
}
```

- [ ] **Step 2: Use it in `PlayerModelZombieArmsMixin`**

Replace:

```java
        // Classic zombie stance: both arms straight out (~90° forward), no swing, slight inward tilt.
        this.rightArm.xRot = -1.5f;
        this.leftArm.xRot = -1.5f;
        this.rightArm.yRot = 0.0f;
        this.leftArm.yRot = 0.0f;
        this.rightArm.zRot = 0.05f;
        this.leftArm.zRot = -0.05f;
```

with:

```java
        // Classic zombie stance: both arms straight out (~90° forward), no swing, slight inward tilt.
        ArmPose.set(this.rightArm, -1.5f, 0.0f, 0.05f);
        ArmPose.set(this.leftArm, -1.5f, 0.0f, -0.05f);
```

- [ ] **Step 3: Use it in `ZombieSleepArmsMixin`**

Replace:

```java
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        model.rightArm.xRot = 0.0f;
        model.leftArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;
```

with:

```java
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        ArmPose.set(model.rightArm, 0.0f, 0.0f, 0.0f);
        ArmPose.set(model.leftArm, 0.0f, 0.0f, 0.0f);
```

- [ ] **Step 4: Compile**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (`ArmPose` is package-private in `mixin.client.model`, same package as both mixins, so no import is needed in either.)

- [ ] **Step 5: Manual in-game smoke check**

Launch the dev client. Trigger the plague hallucination (get infected, wait for the hallucination episode) and confirm other players still show the straight zombie-arm pose with the same slight inward tilt. Separately, watch a zombie fall asleep during the day and confirm its arms still drop fully to its sides, identical to before.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ArmPose.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/PlayerModelZombieArmsMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieSleepArmsMixin.java
git commit -m "refactor(mixin): extract shared ArmPose.set helper for the two arm-pose mixins"
```

---

### Task 8: Consolidate config-primitive type dispatch (`ConfigType`, `ConfigSchema`, `ConfigWriter`)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitive.java`
- Create: `mod/src/test/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitiveTest.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigType.java:15-21`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigSchema.java:89-93`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/io/ConfigWriter.java:44-71`

**Interfaces:**
- Consumes: nothing.
- Produces: `ConfigPrimitive.of(Class<?> t): ConfigPrimitive` (nullable — returns `null` for an unrecognized type) and `ConfigPrimitive.label(): String`. No other task in this plan depends on these, but note the API shape if extending config code later.

**Context:** `ConfigType.kind(Field)`, `ConfigSchema.isSupported(Class<?>)`, and `ConfigWriter.save(Path)`'s per-field branch all independently re-derive "which of the 6 primitive kinds (`boolean`/`int`/`long`/`double`/`float`/`double[]`) is this". `ConfigBounds.clamp` was investigated too but dispatches on the boxed **runtime value's** type, not the declared field type, and only has 4 branches (no boolean/list) — it is a structurally different dispatch and is excluded (see Global Constraints).

`ConfigType.kind(Field)`'s existing fallback behavior for any type not in its explicit checks is to return `"float"` (there is no final `else` — the last `if` is a fallthrough `return "float";`). `ConfigPrimitive.of` must preserve this exact fallback via `null` + a `"float"` default at the call site, not by making `of()` itself resolve unknown types to `FLOAT`, so that `ConfigSchema.isSupported` — which must return `false` for a genuinely unsupported type — is not broken by the same lookup.

- [ ] **Step 1: Write the failing test**

Create `mod/src/test/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitiveTest.java`:

```java
package com.dreykaoas.lethalbreed.config.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigPrimitiveTest {

    @Test
    void matchesEachOfTheSixPrimitiveKinds() {
        assertEquals(ConfigPrimitive.BOOL, ConfigPrimitive.of(boolean.class));
        assertEquals(ConfigPrimitive.INT, ConfigPrimitive.of(int.class));
        assertEquals(ConfigPrimitive.LONG, ConfigPrimitive.of(long.class));
        assertEquals(ConfigPrimitive.DOUBLE, ConfigPrimitive.of(double.class));
        assertEquals(ConfigPrimitive.FLOAT, ConfigPrimitive.of(float.class));
        assertEquals(ConfigPrimitive.LIST, ConfigPrimitive.of(double[].class));
    }

    @Test
    void returnsNullForAnUnsupportedType() {
        assertNull(ConfigPrimitive.of(String.class));
        assertNull(ConfigPrimitive.of(Object.class));
    }

    @Test
    void labelsMatchTheHistoricalStrings() {
        assertEquals("bool", ConfigPrimitive.BOOL.label());
        assertEquals("int", ConfigPrimitive.INT.label());
        assertEquals("long", ConfigPrimitive.LONG.label());
        assertEquals("double", ConfigPrimitive.DOUBLE.label());
        assertEquals("float", ConfigPrimitive.FLOAT.label());
        assertEquals("list", ConfigPrimitive.LIST.label());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew test --tests "com.dreykaoas.lethalbreed.config.schema.ConfigPrimitiveTest"`
Expected: FAILS to compile — `cannot find symbol: class ConfigPrimitive`.

- [ ] **Step 3: Create `ConfigPrimitive`**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitive.java`:

```java
package com.dreykaoas.lethalbreed.config.schema;

/**
 * The 6 primitive kinds a config field can be. Single source of truth for "what kind of field is this",
 * used by {@link ConfigType#kind}, {@link ConfigSchema#isSupported}, and {@code ConfigWriter}'s per-field
 * JSON write dispatch — three places that used to independently re-derive this same 6-way type check.
 */
public enum ConfigPrimitive {
    BOOL("bool"), INT("int"), LONG("long"), DOUBLE("double"), FLOAT("float"), LIST("list");

    private final String label;

    ConfigPrimitive(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Matches a field's declared {@link Class}. Returns {@code null} for anything else — callers decide
     *  their own fallback (historically {@link ConfigType#kind} falls back to {@code "float"}'s label,
     *  while {@link ConfigSchema#isSupported} must treat {@code null} as "not supported"). */
    public static ConfigPrimitive of(Class<?> t) {
        if (t == boolean.class) return BOOL;
        if (t == int.class) return INT;
        if (t == long.class) return LONG;
        if (t == double.class) return DOUBLE;
        if (t == float.class) return FLOAT;
        if (t == double[].class) return LIST;
        return null;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew test --tests "com.dreykaoas.lethalbreed.config.schema.ConfigPrimitiveTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Delegate `ConfigType.kind`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigType.java`, replace:

```java
    public static String kind(Field f) {
        Class<?> t = f.getType();
        if (t == boolean.class) return "bool";
        if (t == int.class) return "int";
        if (t == long.class) return "long";
        if (t == double.class) return "double";
        if (t == double[].class) return "list";
        return "float";
    }
```

with:

```java
    public static String kind(Field f) {
        ConfigPrimitive p = ConfigPrimitive.of(f.getType());
        return p != null ? p.label() : "float";
    }
```

This preserves the exact historical behavior: every one of the 6 explicit types resolves to its own label, and anything else (there is none today — `ConfigFields.all()` only ever returns supported fields — but the fallback is kept byte-for-byte) resolves to `"float"`.

- [ ] **Step 6: Delegate `ConfigSchema.isSupported`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigSchema.java`, replace:

```java
    public static boolean isSupported(Class<?> t) {
        return t == boolean.class || t == int.class || t == long.class
                || t == double.class || t == float.class || t == double[].class;
    }
```

with:

```java
    public static boolean isSupported(Class<?> t) {
        return ConfigPrimitive.of(t) != null;
    }
```

(Both classes are in package `com.dreykaoas.lethalbreed.config.schema`, same as `ConfigPrimitive` — no new import needed in either file.)

- [ ] **Step 7: Delegate `ConfigWriter.save`'s per-field dispatch**

In `mod/src/main/java/com/dreykaoas/lethalbreed/config/io/ConfigWriter.java`, replace:

```java
            Class<?> t = f.getType();
            try {
                if (t == boolean.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getBoolean(null)));
                } else if (t == int.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getInt(null)));
                } else if (t == long.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getLong(null)));
                } else if (t == double.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getDouble(null)));
                } else if (t == float.class) {
                    json.add(f.getName(), new JsonPrimitive(f.getFloat(null)));
                } else if (t == double[].class) {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    for (double v : (double[]) f.get(null)) {
                        arr.add(v);
                    }
                    json.add(f.getName(), arr);
                }
            } catch (IllegalAccessException e) {
```

with:

```java
            ConfigPrimitive primitive = ConfigPrimitive.of(f.getType());
            try {
                switch (primitive) {
                    case BOOL -> json.add(f.getName(), new JsonPrimitive(f.getBoolean(null)));
                    case INT -> json.add(f.getName(), new JsonPrimitive(f.getInt(null)));
                    case LONG -> json.add(f.getName(), new JsonPrimitive(f.getLong(null)));
                    case DOUBLE -> json.add(f.getName(), new JsonPrimitive(f.getDouble(null)));
                    case FLOAT -> json.add(f.getName(), new JsonPrimitive(f.getFloat(null)));
                    case LIST -> {
                        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                        for (double v : (double[]) f.get(null)) {
                            arr.add(v);
                        }
                        json.add(f.getName(), arr);
                    }
                    case null -> { /* unsupported field type: ConfigFields.all() never yields one. */ }
                }
            } catch (IllegalAccessException e) {
```

Add the import (same package group as the existing `com.dreykaoas.lethalbreed.config.schema.*` imports at the top of the file):
```java
import com.dreykaoas.lethalbreed.config.schema.ConfigPrimitive;
```

- [ ] **Step 8: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL. In particular, `ConfigTypeTest`, `ConfigSchemaOrderTest`, and any other existing config test must still pass unchanged — this task must not alter `ConfigType.kind`'s or `ConfigSchema.isSupported`'s observable behavior for any real field type.

- [ ] **Step 9: Manual in-game smoke check**

Launch the dev client, run `/lethalconfig list` (prints every option + value) and `/lethalconfig verify` (structural health check), then change one option of each kind (a boolean, an int, a double list like `varScaleMin,varScaleMax`) via `/lethalconfig set`, restart the dev server, and confirm `config/oas/lethalbreed.json` round-trips every changed value correctly (the file write path is exactly what `ConfigWriter.save` was changed in this task).

- [ ] **Step 10: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitive.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/config/schema/ConfigPrimitiveTest.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigType.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigSchema.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/io/ConfigWriter.java
git commit -m "refactor(config): consolidate the 3 duplicated primitive-type dispatch chains into ConfigPrimitive"
```

---

### Task 9: Consolidate the "split out of ConfigBoundsTable" rationale (7 files)

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/BoundsSplitNote.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/CombatMoveBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ContaminationBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/engine/FlowBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/engine/PerfBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ProgressionBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/TargetingBounds.java:5-11`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/WorldSpawnBounds.java:5-11`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Context:** All 7 files share this identical second Javadoc paragraph, differing only in their one-line `<p>`-free summary above it:

```java
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
```

Two of the 7 (`FlowBounds`, `PerfBounds`) live in the subpackage `config.bounds.engine`, so the anchor class must be `public` and imported from there.

- [ ] **Step 1: Create the anchor class**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/BoundsSplitNote.java`:

```java
package com.dreykaoas.lethalbreed.config.bounds;

/**
 * Shared rationale for every {@code *Bounds} class in this package and {@code config.bounds.engine}: each
 * was split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine
 * unrelated domains. Registration order does not matter — the table is a map keyed by lower-cased option
 * name — but grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest}
 * fails the build if any numeric option loses one.
 *
 * <p>Not instantiated. Exists only as a single place for each {@code *Bounds} class's Javadoc to point at.
 */
public final class BoundsSplitNote {
    private BoundsSplitNote() {}
}
```

- [ ] **Step 2: Shrink each of the 7 files' Javadoc**

In each file, replace the class Javadoc block. Example for `CombatMoveBounds.java` — replace:

```java
/**
 * Clamp ranges for the leap, water, climb and block-breaking options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
 */
```

with:

```java
/**
 * Clamp ranges for the leap, water, climb and block-breaking options.
 *
 * <p>See {@link BoundsSplitNote} for why this is its own class instead of one shared table.
 */
```

Apply the same pattern (keep each file's own first-line summary, replace the `<p>Split out of...` paragraph with `<p>See {@link BoundsSplitNote} for why this is its own class instead of one shared table.`) to all 7 files:
- `CombatMoveBounds.java` — summary: "Clamp ranges for the leap, water, climb and block-breaking options."
- `ContaminationBounds.java` — summary: "Clamp ranges for the contamination options."
- `config/bounds/engine/FlowBounds.java` — summary: "Clamp ranges for the flow-field and pathing options (Compute / Pathing / Climb)."
- `config/bounds/engine/PerfBounds.java` — summary: "Clamp ranges for the scheduler, LOD and expert options — the whole Perf domain."
- `ProgressionBounds.java` — summary: "Clamp ranges for the phase, special-variant and dev options."
- `TargetingBounds.java` — summary: "Clamp ranges for the targeting and sound options (Targeting / Sound)."
- `WorldSpawnBounds.java` — summary: "Clamp ranges for the world, variation, effect and spawn options."

- [ ] **Step 3: Add the import to the 2 files in the `engine` subpackage**

In `config/bounds/engine/FlowBounds.java` and `config/bounds/engine/PerfBounds.java`, add:
```java
import com.dreykaoas.lethalbreed.config.bounds.BoundsSplitNote;
```
next to their existing `import com.dreykaoas.lethalbreed.config.BoundsRegistrar;` line. (The other 5 files are already in package `com.dreykaoas.lethalbreed.config.bounds`, same as `BoundsSplitNote` — no import needed there.)

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL, `ConfigBoundsTest` still passes unchanged (this task only touches Javadoc, no `register(...)` bound-registration logic).

- [ ] **Step 5: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/BoundsSplitNote.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/CombatMoveBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ContaminationBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/engine/FlowBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/engine/PerfBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ProgressionBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/TargetingBounds.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/WorldSpawnBounds.java
git commit -m "docs(config-bounds): dedupe the 7 copy-pasted 'split out of ConfigBoundsTable' paragraphs"
```

---

### Task 10: Extract a profiling-checkpoint helper in `LodBucketPass`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java:94-170`

**Interfaces:**
- Consumes: `com.dreykaoas.lethalbreed.tick.StageProfiler` and its nested `Stage` enum (already imported in this file).
- Produces: a private static helper local to this file; no other task depends on it.

**Context:** This is a per-zombie, per-tick hot path. The pattern `if (prof) { long n = System.nanoTime(); profiler.add(StageProfiler.Stage.X, n - t); t = n; }` repeats 5 times identically (only the `Stage` constant differs), plus a 6th slightly different final checkpoint. Extract a tiny static helper that takes and returns a primitive `long` — no allocation, no boxing, trivially inlinable by the JIT — so the hot path's performance characteristics are unchanged.

- [ ] **Step 1: Add the helper method**

In `mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java`, add this private static method near the top of the class (after field declarations, before the method that uses it):

```java
    /** Record one profiling checkpoint and return the new "last timestamp", or {@code t} unchanged when
     *  profiling is off. No allocation — safe to call every activation of this hot per-zombie loop. */
    private static long mark(StageProfiler profiler, StageProfiler.Stage stage, boolean prof, long t) {
        if (!prof) {
            return t;
        }
        long n = System.nanoTime();
        profiler.add(stage, n - t);
        return n;
    }
```

- [ ] **Step 2: Replace the 5 identical checkpoints**

Replace each of these 5 blocks:

```java
            if (prof) {
                long n = System.nanoTime();
                profiler.add(StageProfiler.Stage.CLASSIFY, n - t);
                t = n;
            }
```
```java
            if (prof) {
                long n = System.nanoTime();
                profiler.add(StageProfiler.Stage.GRID, n - t);
                t = n;
            }
```
```java
            if (prof) {
                long n = System.nanoTime();
                profiler.add(StageProfiler.Stage.PACK, n - t);
                t = n;
            }
```
```java
            if (prof) {
                long n = System.nanoTime();
                profiler.add(StageProfiler.Stage.SUNBURN, n - t);
                t = n;
            }
```
```java
            if (prof) {
                long n = System.nanoTime();
                profiler.add(StageProfiler.Stage.MOOD, n - t);
                t = n;
            }
```

each with its own one-liner (keeping them in the same relative order/positions in the method):

```java
            t = mark(profiler, StageProfiler.Stage.CLASSIFY, prof, t);
```
```java
            t = mark(profiler, StageProfiler.Stage.GRID, prof, t);
```
```java
            t = mark(profiler, StageProfiler.Stage.PACK, prof, t);
```
```java
            t = mark(profiler, StageProfiler.Stage.SUNBURN, prof, t);
```
```java
            t = mark(profiler, StageProfiler.Stage.MOOD, prof, t);
```

- [ ] **Step 3: Replace the final TICK checkpoint**

Replace:

```java
            long tt = prof ? System.nanoTime() : 0L;
            sz.tick(level, ctx);
            if (prof) {
                profiler.add(StageProfiler.Stage.TICK, System.nanoTime() - tt);
            }
```

with:

```java
            long tt = prof ? System.nanoTime() : 0L;
            sz.tick(level, ctx);
            mark(profiler, StageProfiler.Stage.TICK, prof, tt);
```

(The return value is discarded here, matching the original — nothing reads a "checkpoint after TICK" timestamp afterward.)

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual in-game smoke check with profiling on**

Launch the dev server, enable the stage profiler (per its own dev command/flag — check `StageProfiler.enabled()`'s call sites for how it's toggled, e.g. a `/lethaldev` subcommand or a dev-config flag), spawn a large horde, and confirm the profiler's per-stage breakdown (CLASSIFY/GRID/PACK/SUNBURN/MOOD/TICK) still reports plausible non-zero timings for each stage — same shape as before this change, just produced through the new helper.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java
git commit -m "refactor(tick): extract a mark() helper for LodBucketPass's repeated profiling checkpoints"
```

---

### Task 11: Hoist duplicated `children()`/`narratables()` into `OptionEntry`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/OptionEntry.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/BoolOptionEntry.java:1-9,40-48`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/NumOptionEntry.java:1-13,72-80`

**Interfaces:**
- Consumes: nothing.
- Produces: `OptionEntry` gains a new `protected abstract AbstractWidget control()` method — both subclasses must implement it. No other task touches these files.

**Context:** `BoolOptionEntry` and `NumOptionEntry` both implement `children()` and `narratables()` identically as `List.of(<their control widget>, reset)`, only the control field differs (`toggle` vs `edit`). `net.minecraft.client.gui.components.Button` and `net.minecraft.client.gui.components.EditBox` both extend `AbstractWidget`, which itself implements both `GuiEventListener` and `NarratableEntry` — so a single `AbstractWidget control()` accessor is enough to hoist both methods into the base class.

- [ ] **Step 1: Add the abstract accessor and hoisted methods to `OptionEntry`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/OptionEntry.java`, add this import:
```java
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
```
(next to the existing `net.minecraft.client.gui.components.AbstractWidget` and `Button` imports), and add these members to the class (e.g. right after the `doReset()` abstract method):

```java
    /** The one value-editing widget this row owns (a toggle button, an edit box, ...). Used to build the
     *  shared focus/narration lists below — the reset button is common to every row, only this differs. */
    protected abstract AbstractWidget control();

    @Override
    public java.util.List<? extends GuiEventListener> children() {
        return List.of(control(), reset);
    }

    @Override
    public java.util.List<? extends NarratableEntry> narratables() {
        return List.of(control(), reset);
    }
```

(`List` and `ArrayList` are already imported as `java.util.List`/`java.util.ArrayList` in this file — reuse the existing `List` import rather than the `java.util.List` fully-qualified form used above if the file already has `import java.util.List;`; check the existing import block and use the short form `List<? extends GuiEventListener>` / `List<? extends NarratableEntry>` to match the file's style.)

- [ ] **Step 2: Remove the overrides from `BoolOptionEntry`, add `control()`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/BoolOptionEntry.java`, delete:

```java
    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(toggle, reset);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(toggle, reset);
    }
```

and add in their place:

```java
    @Override
    protected net.minecraft.client.gui.components.AbstractWidget control() {
        return toggle;
    }
```

Remove the now-unused imports `net.minecraft.client.gui.components.events.GuiEventListener`, `net.minecraft.client.gui.narration.NarratableEntry`, and `java.util.List` if nothing else in the file uses them (check first — `List` may still be needed if any other method in the file returns a `List`; if not used elsewhere, remove it).

- [ ] **Step 3: Remove the overrides from `NumOptionEntry`, add `control()`**

In `mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/NumOptionEntry.java`, delete:

```java
    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(edit, reset);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(edit, reset);
    }
```

and add in their place:

```java
    @Override
    protected net.minecraft.client.gui.components.AbstractWidget control() {
        return edit;
    }
```

Remove the now-unused imports the same way as Step 2, if applicable (`EditBox` extends `AbstractWidget`, so `edit`'s field type import stays; only the `GuiEventListener`/`NarratableEntry`/possibly `List` imports become removable).

- [ ] **Step 4: Verify no leftover unused imports**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -n "^import" src/main/java/com/dreykaoas/lethalbreed/client/screen/BoolOptionEntry.java
grep -n "^import" src/main/java/com/dreykaoas/lethalbreed/client/screen/NumOptionEntry.java
```
Manually confirm every listed import is still referenced somewhere else in each file.

- [ ] **Step 5: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual in-game smoke check**

Launch the dev client, open the `/lethalconfig` in-game GUI menu, Tab through a boolean row and a numeric row, confirm keyboard focus still moves to the value control then the reset button (not skipping either), and confirm narration (if screen-reader narration is enabled in Minecraft's accessibility settings) still announces both widgets per row.

- [ ] **Step 7: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/OptionEntry.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/BoolOptionEntry.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/client/screen/NumOptionEntry.java
git commit -m "refactor(screen): hoist duplicated children()/narratables() into OptionEntry.control()"
```

---

### Task 12: Remove redundant one-line delegation `CrackingBlock.stage()`

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/block/CrackingBlock.java:16-19`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/block/BreakManager.java:99`

**Interfaces:**
- Consumes: `com.dreykaoas.lethalbreed.block.PlacedBlockPolicy.stage(double): int` (already `public static`, unchanged, already unit-tested by `PlacedBlockPolicyTest`).
- Produces: nothing.

**Context:** `CrackingBlock.stage(double)` is a pure one-line pass-through to `PlacedBlockPolicy.stage(double)`. Its only caller anywhere in the mod is `BreakManager.java:99`. Both classes are in package `com.dreykaoas.lethalbreed.block`, so `BreakManager` can call `PlacedBlockPolicy.stage(...)` directly with no new import.

- [ ] **Step 1: Confirm the only caller (safety re-check)**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
grep -rn "CrackingBlock\.stage(" src/
```
Expected: exactly one hit, `src/main/java/com/dreykaoas/lethalbreed/block/BreakManager.java:99`.

- [ ] **Step 2: Update the call site**

In `mod/src/main/java/com/dreykaoas/lethalbreed/block/BreakManager.java`, replace:

```java
            s.showStage(level, pos, CrackingBlock.stage(s.progress * 10f));
```

with:

```java
            s.showStage(level, pos, PlacedBlockPolicy.stage(s.progress * 10f));
```

- [ ] **Step 3: Delete the pass-through method**

In `mod/src/main/java/com/dreykaoas/lethalbreed/block/CrackingBlock.java`, delete:

```java
    /** Clamp an already-scaled tenths value (progress×10 / age×10÷lifetime) to a 0..9 crack stage.
     *  The arithmetic lives in {@link PlacedBlockPolicy} so it is unit-testable without Minecraft. */
    static int stage(double tenths) {
        return PlacedBlockPolicy.stage(tenths);
    }

```

- [ ] **Step 4: Compile and run the full test suite**

Run: `cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod && ./gradlew compileJava test`
Expected: BUILD SUCCESSFUL. `PlacedBlockPolicyTest` (already exists, unmodified) continues to cover the `stage(double)` arithmetic itself.

- [ ] **Step 5: Manual in-game smoke check**

Launch the dev client, have a zombie progressively break a block (per `CombatMoveConfig`'s block-breaking settings), confirm the crumbling-overlay stage still ramps 0→9 visually exactly as before.

- [ ] **Step 6: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed
git add mod/src/main/java/com/dreykaoas/lethalbreed/block/CrackingBlock.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/block/BreakManager.java
git commit -m "refactor(block): remove CrackingBlock.stage's redundant one-line delegation"
```

---

### Task 13: Remove dead CSS and dedupe the sidebar `aria-current` rule (web)

**Files:**
- Modify: `web/assets/css/components/wiki/callouts.css:27-40`
- Modify: `web/assets/css/base.css:127-133`
- Modify: `web/assets/css/components/chrome/buttons.css:26-27`
- Modify: `web/assets/css/components/wiki/param-table.css:72-79` (exact end line depends on the current selector block length — verify with Step 1 before deleting)
- Modify: `web/assets/css/components/wiki/wiki-prose.css:46-48`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Context (all 5 confirmed dead / duplicate via repo-wide grep during planning, zero HTML/JS references found for any of them):**
- `.badge` / `.badge--accent` (`callouts.css`) — never referenced.
- `.visually-hidden` (`base.css`) — never referenced.
- `.btn--primary` + its `:hover` (`buttons.css`) — never referenced (only `.btn`, `.btn--ghost`, `.btn[aria-disabled]` are used).
- `.param-group-label td` (`param-table.css`) — never referenced.
- `.wiki-sidebar a[aria-current="page"] { color: var(--ink); font-weight: 600; }` is defined identically in **both** `wiki-layout.css:40-42` and `wiki-prose.css:46-48`; both files are `@import`-ed into every page via `components.css`, so one copy is pure redundancy. Keep the one in `wiki-layout.css` (it sits with the rest of the sidebar layout rules) and delete the one in `wiki-prose.css`.

There is no CSS/JS test harness in this project (`tools/check-parity.mjs` only checks FR/EN HTML text-structure parity, not styling) — verify this task visually.

- [ ] **Step 1: Re-confirm all 5 are unreferenced (safety re-check)**

Run:
```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/web
grep -rn "badge" --include="*.html" --include="*.js" .
grep -rn "visually-hidden" --include="*.html" --include="*.js" .
grep -rn "btn--primary" --include="*.html" --include="*.js" .
grep -rn "param-group-label" --include="*.html" --include="*.js" .
```
Expected: no output for any of the 4 commands.

- [ ] **Step 2: Remove `.badge`/`.badge--accent`**

In `web/assets/css/components/wiki/callouts.css`, delete this block (currently lines 27-40):

```css
.badge {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2em 0.6em;
  border-radius: var(--radius-sm);
  background: var(--fill-primary);
  color: var(--on-fill);
}
/* --fill-accent (bio-rouge) reste sombre dans les deux thèmes — contrairement
   à --fill-primary, il ne s'inverse pas ; un texte clair fixe est donc
   correct ici sans dépendre de --on-fill. */
.badge--accent { background: var(--fill-accent); color: oklch(0.96 0.015 30); }
```

- [ ] **Step 3: Remove `.visually-hidden`**

In `web/assets/css/base.css`, delete this block (currently lines 127-133):

```css
.visually-hidden {
  position: absolute;
  width: 1px; height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
```

- [ ] **Step 4: Remove `.btn--primary` and its `:hover`**

In `web/assets/css/components/chrome/buttons.css`, delete these 2 lines (currently 26-27):

```css
.btn--primary { background: var(--fill-primary); color: var(--on-fill); }
.btn--primary:hover { background: color-mix(in oklch, var(--fill-primary) 85%, white 8%); box-shadow: var(--glow-amber); }
```

- [ ] **Step 5: Remove `.param-group-label td`**

Open `web/assets/css/components/wiki/param-table.css` and locate the `.param-group-label td { ... }` rule (starting around line 72). Read the full block first to find its exact closing brace before deleting, since the visible excerpt was truncated:

```bash
sed -n '72,90p' web/assets/css/components/wiki/param-table.css
```

Delete the entire `.param-group-label td { ... }` rule (from its opening `.param-group-label td {` line through its matching closing `}`), leaving the surrounding `@keyframes param-row-in` and the `.param-table.in-view tbody tr:nth-child(...)` rules untouched.

- [ ] **Step 6: Dedupe the sidebar `aria-current` rule**

In `web/assets/css/components/wiki/wiki-prose.css`, delete this block (currently lines 46-48):

```css
.wiki-sidebar a[aria-current="page"] {
  color: var(--ink);
  font-weight: 600;
}
```

Leave the identical rule in `web/assets/css/components/wiki/wiki-layout.css` (lines 40-43) untouched — that is the single remaining copy.

- [ ] **Step 7: Visual regression check**

Open (or serve locally, e.g. `python3 -m http.server` from `web/`) `index.html` and one `wiki/*.html` page in a browser. Confirm:
- Callout boxes still render correctly (badges were never visibly used, so nothing should visually change).
- Any element previously visually hidden via `.visually-hidden` — there are none per Step 1's grep — is not applicable.
- All CTA buttons (`.btn`) still render with their existing look (only `.btn--ghost` and the base `.btn` styles were ever applied in markup).
- Every `wiki/*.html` page's sidebar still bolds and highlights the current page's link exactly as before (this is the rule kept in `wiki-layout.css`).

- [ ] **Step 8: Commit**

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/web
git add assets/css/components/wiki/callouts.css \
        assets/css/base.css \
        assets/css/components/chrome/buttons.css \
        assets/css/components/wiki/param-table.css \
        assets/css/components/wiki/wiki-prose.css
git commit -m "chore(css): remove 5 unreferenced rules and dedupe the sidebar aria-current rule"
```

---

## Self-Review Notes

- **Spec coverage:** every finding from the 2026-08-10 audit's *Dead code*, *Duplicates*, and *Similar patterns* sections is either covered by a task above, corrected as a false positive (Task 2), or explicitly excluded with a stated reason (Global Constraints). *Complexity* findings are out of scope by design (see Global Constraints) — not silently dropped.
- **Placeholder scan:** every step shows the exact before/after code, exact file paths and line numbers as of the August 2026 snapshot, and exact commands. No "TODO"/"handle appropriately"/"similar to Task N" placeholders.
- **Type consistency:** `CellMath.floorCell(double, int)`, `ConfigPrimitive.of(Class<?>)` returning nullable `ConfigPrimitive`, `ArmPose.set(ModelPart, float, float, float)`, and `OptionEntry.control(): AbstractWidget` are each defined once and used with matching signatures everywhere they are called across their task's steps.
- Tasks 1-12 (mod) and Task 13 (web) are independent of each other except: Task 6 and Task 7 both touch `PlayerModelZombieArmsMixin.java` and `ZombieSleepArmsMixin.java` — do Task 6 before Task 7 (as ordered above) to avoid a merge conflict on the same lines within one session.
