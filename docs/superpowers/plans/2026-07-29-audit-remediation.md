# LethalBreed — Audit Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 13 confirmed findings from `AUDIT.md` (2026-07-29) — one independently reviewable, independently revertable commit per finding.

**Architecture:** Three bands, executed in order. **Band A** (tasks 1–2) unblocks the build and removes pure waste; nothing else can be verified until task 1 lands. **Band B** (tasks 3–6) is the contamination subsystem: extract the plague's random-draw rules into a Minecraft-free class so they become genuinely unit-testable, then route every duplicated call site through it, then close the two memory leaks. **Band C** (tasks 7–12) is the behavioural and performance work, each fix local to one class.

**Tech Stack:** Fabric Loader 0.19.3, Minecraft 1.21.11, Java 21, Gradle 9.5.1 + Fabric Loom 1.17.12, SpongePowered Mixin, JUnit 5.

---

## Global Constraints

- **Java 21.** `build.gradle.kts` already declares `java { toolchain.languageVersion.set(21) }`. Never re-introduce an absolute JDK path into a versioned file.
- **Headless test source set.** `mod/src/test/` runs under plain JUnit 5 with **no Minecraft runtime**. A test may *reference* Minecraft types at compile time but must never load a class whose static initialiser touches the Fabric or Minecraft runtime. Concretely: `ContaminationState` calls `AttachmentRegistry.createPersistent(...)` in its static init, so **no unit test may reference `ContaminationState`**. `config/domain/*` has zero non-LethalBreed imports and is safe to reference.
- **Mixin `defaultRequire: 1`** (`mod/src/main/resources/lethalbreed.mixins.json`). Every injector must match at least once or the game refuses to launch — a silently-unmatched injector is impossible, which is the verification mechanism for task 8.
- **Server-thread only.** Everything in `effect/contamination/`, `block/`, `entity/`, `tick/` and `ai/flowfield/FlowFieldSnapshotBuilder` runs on the server thread. Do not add synchronisation; do not move world reads off-thread.
- **Config convention.** A new numeric config option needs three things, but only one of them is enforced:
  1. A field in `config/domain/*Config.java` — `ConfigSchema.scan()` picks it up by reflection.
  2. An entry in `config/ConfigBoundsTable.java` — **enforced**: `ConfigBoundsTest.everyScalarNumericOptionHasBounds` fails the build without it.
  3. Translation keys `lethalbreed.option.<exactFieldName>` and `lethalbreed.option.<exactFieldName>.desc` in **both** `src/main/resources/assets/lethalbreed/lang/en_us.json` and `fr_fr.json` — **not enforced by anything**. `OptionEntry.java:56` builds the key by string concatenation on the reflected field name; a missing `.desc` is skipped gracefully (`:70`), but a missing **label** renders the raw key in the config screen, truncated with no ellipsis. There is no i18n test and no build check, so this is the one that gets silently forgotten.
  
  Note that 12 of `ZombieMoodConfig`'s 28 fields — `shelterSearchRadius` and `daySleepEnabled` among them — are **already** untranslated. That is a pre-existing gap; do not fix it as a side effect of this plan, and do not use it as licence to skip translating a *new* option.
- **Behavioural non-regression gate — MANDATORY for tasks 7, 9, 10, 11.** These four touch AI freeze, block ops, the mood path and cell classification. Before **and** after each of them, run the dev harnesses headlessly and diff the verdicts:

  ```bash
  # Enable ONE arena per run — DevBootstrap warns that they build overlapping arenas.
  # Flags live in ProgressionConfig: devMechTest, devSpecialTest, devComputeTest, devClimbTest.
  cd mod && ./gradlew runServer 2>&1 | grep -E '\[(MechTest|SpecialTest|ComputeTest)\].*(PASS|FAIL)'
  ```

  Expected: **zero `FAIL`**, plus the `DONE` line for each enabled harness. `[MechTest] flee-rally` is the only automated check that a zombie hears and acts on a noise — it must stay PASS.

  **What these harnesses do NOT cover, and therefore must be checked by hand:**
  | Behaviour | Coverage |
  |---|---|
  | Breaking a block to reach a target | **none** — no rig exists |
  | Bridging / pillaring over a gap | **none** — no rig exists |
  | Climbing / jumping | scenario only (`devClimbTest`), **no verdict** — a human reads the `[ClimbDbg]` stream |
  | Hearing a noise and investigating | only the narrow flee-rally case |
  | Flow-field routing | math only, on synthetic grids — `CellClassifier` is never exercised |

  Any task in this plan that could plausibly alter one of those five must carry an explicit manual protocol in its own steps, and **must not be marked complete on a green test suite alone**. A green `./gradlew test` says nothing about whether zombies still break, bridge or climb.

- **Commit per task.** Each task ends with exactly one commit. Never batch two tasks into one commit.
- **Build command.** All commands below assume `cd mod`. After task 1, `./gradlew build` works with no property override; before task 1 it does not.

---

## Finding-number mapping

`AUDIT.md`'s synthesis table and its detail sections disagree on numbering (the detail section reuses `#5` for two different findings). **This plan uses the synthesis-table numbers.** Mapping to the detail sections:

| Plan / table # | Detail section heading | Task |
|---|---|---|
| #1 milk cures plague | `🟠 #1` | 8 |
| #2 NoAI persisted | `🟠 #2` | 7 |
| #3 PlacedBlockTracker chunk load | `🟠 #3` | 9 |
| #4 gradle.properties | `🟠 #4` | 1 |
| #5 hardcoded intensity floor | `🟠 #5` (second one, after `#10`) | 3 |
| #6 ShelterFinder no cooldown | `🟡 #5` (first one) | 10 |
| #7 flow-field snapshot on server thread | `🟡 #6` | 11 |
| #8 `SNAPSHOT` retains entities | `🟡 #7` | 5 |
| #9 contamination disabled → accumulation | `🟡 #8` | 6 |
| #10 render-target pool never released | `🟡 #9` | 12 |
| #11 `@At("TAIL")` misses latent | `🟡 #10` | 8 |
| #12 uniform draw reimplemented 5× | `🟡 #12` | 4 |
| #13 duplicated `refreshTargetIndex` | `⚪ #13` | 2 |

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRoll.java` | Every random-draw rule the plague uses, as pure functions over an injected `Random`. **Zero Minecraft imports** — this is what makes tasks 3 and 4 unit-testable. |
| `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ClearGuard.java` | A thread-local marker saying "the `removeAllEffects()` call currently on this thread's stack came from milk". Read by `EffectClearCuresPlagueMixin`, written by `MilkKeepsPlagueMixin`. |
| `mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRollTest.java` | Headless characterisation of `ContaminationRoll`. |

**Modified:** `mod/gradle.properties` · `tick/TickScheduler.java` · `tick/StageProfiler.java` · `effect/contamination/{ContaminationState,ContaminationTick,ContaminationEpisodes,ContaminationHallucination,ContaminationEvolve,ContaminationSymptoms,ContaminationLifecycle}.java` · `effect/ContaminationManager.java` · `mixin/{MilkKeepsPlagueMixin,EffectClearCuresPlagueMixin}.java` · `entity/ZombieMood.java` · `entity/mood/ShelterFinder.java` · `init/EntityEventsInit.java` · `block/PlacedBlockTracker.java` · `ai/flowfield/{FlowFieldSnapshotBuilder,CellClassifier}.java` · `client/ContaminationScreenOverlay.java` · `config/domain/{ZombieMoodConfig,FlowConfig}.java` · `config/ConfigBoundsTable.java` · language files.

**Rationale for `ContaminationRoll` as a new file rather than methods on `ContaminationState`:** `ContaminationState`'s static initialiser registers four Fabric attachment types. Loading it from a JUnit test throws. Putting the draw rules in a Minecraft-free sibling is the only way to get automated coverage on the arithmetic that findings #5 and #12 are about — and those two findings exist *precisely because* nothing tested that arithmetic.

---

## Verification honesty

Not every task can carry an automated test, and the plan says so per task rather than inventing tests that cannot run.

- **Tasks 3, 4, 10** — real JUnit tests, run headless via `./gradlew test`.
- **Tasks 1, 2** — verified by build/static inspection; the change *is* the observable.
- **Task 8** — verified by mixin application (`defaultRequire: 1` makes a mismatched injector a hard launch failure) plus a scripted in-game protocol.
- **Tasks 5, 6, 7, 9, 12** — no headless test is possible (each needs a live `ServerLevel`, `LivingEntity` or GL context). Each carries an explicit in-game protocol using the dev commands that already exist: `/lethaldev contaminate|symptoms|level|cure|timescale`, `/lethalspawn`, `/lethalphase`.
- **Task 11** — verified by measurement: the task *adds the measurement first*, records a baseline, then optimises, then re-measures. This is the only performance task with a before/after number, because it is the only one where the existing profiler can be extended to produce one.

---

# Band A — unblock and delete waste

## Task 1: Remove the absolute JDK path from `gradle.properties` (#4)

**Files:**
- Modify: `mod/gradle.properties:5-6`

**Interfaces:**
- Consumes: nothing.
- Produces: a working `./gradlew build` with no command-line override. **Every later task depends on this.**

- [ ] **Step 1: Confirm the build is broken exactly as the audit describes**

```bash
cd mod && ./gradlew help 2>&1 | tail -5
```

Expected: `Value '/opt/liberica-nik/bellsoft-liberica-vm-openjdk21-23.1.4' given for org.gradle.java.home Gradle property is invalid (Java home supplied is invalid)`.

If it instead succeeds, that directory exists on this machine — the finding still holds for every *other* machine and CI, so continue anyway.

- [ ] **Step 2: Delete the property and its now-stale comment**

In `mod/gradle.properties`, delete these two lines:

```properties
# Force build + dev runs on Liberica NIK 23 (GraalVM JIT). Adjust path if JDK moves.
org.gradle.java.home=/opt/liberica-nik/bellsoft-liberica-vm-openjdk21-23.1.4
```

And update the header comment on line 1 so it no longer promises Liberica:

```properties
# ---- Gradle / JVM ----
# JDK selection comes from the Java 21 toolchain in build.gradle.kts, NOT from a hardcoded path here.
# To pin a specific local JDK for your own machine, set org.gradle.java.home in
# ~/.gradle/gradle.properties — never in this versioned file (audit #4).
```

- [ ] **Step 3: Verify the toolchain resolves a JDK 21 on its own**

```bash
cd mod && ./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`, zero warnings, **no `-Dorg.gradle.java.home` override on the command line**.

If Gradle reports `No matching toolchains found for requested specification: {languageVersion=21}`, the machine genuinely has no JDK 21 and toolchain auto-provisioning is off — that is an environment problem, not a regression. Install a JDK 21 and re-run; do not restore the deleted line.

- [ ] **Step 4: Verify the built jar is unchanged in shape**

```bash
cd mod && unzip -l build/libs/lethalbreed-1.0.0.jar | grep -cE "com/dreykaoas/lethalbreed/dev/"
```

Expected: `0` — the `dev` source set must stay excluded from the player jar.

- [ ] **Step 5: Commit**

```bash
git add mod/gradle.properties
git commit -m "build: drop hardcoded org.gradle.java.home so the build works off the author's machine

The versioned path /opt/liberica-nik/... does not exist anywhere else, and
Gradle validates it before anything else runs — so neither the declared Java 21
toolchain nor JAVA_HOME could compensate. Every contributor and every CI run hit
'Java home supplied is invalid' with no relation to the code. Audit #4."
```

---

## Task 2: Delete the duplicated `refreshTargetIndex` call (#13)

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java:58-59`

**Interfaces:**
- Consumes: task 1's working build.
- Produces: nothing new.

- [ ] **Step 1: Confirm the duplication**

```bash
cd mod && grep -c "world.refreshTargetIndex(server);" src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java
```

Expected: `2`.

- [ ] **Step 2: Delete line 59**

`TickScheduler.onServerTick` currently reads:

```java
        world.enforceWorldRules(server);
        com.dreykaoas.lethalbreed.phase.PhaseManager.get().tick(server);
        com.dreykaoas.lethalbreed.effect.ContaminationManager.tick(server);
        world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
        world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
        world.processSound(server);
```

Remove the second `refreshTargetIndex` line so it reads:

```java
        world.enforceWorldRules(server);
        com.dreykaoas.lethalbreed.phase.PhaseManager.get().tick(server);
        com.dreykaoas.lethalbreed.effect.ContaminationManager.tick(server);
        world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
        world.processSound(server);
```

- [ ] **Step 3: Verify exactly one call remains**

```bash
cd mod && grep -c "world.refreshTargetIndex(server);" src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java
```

Expected: `1`.

- [ ] **Step 4: Build**

```bash
cd mod && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

No behavioural test is possible or warranted: `TargetIndex.refresh()` is idempotent (`reposition()` returns early when `was == now`), so the second call was pure waste with no observable output. The observable is the deleted line.

- [ ] **Step 5: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java
git commit -m "perf: stop rebuilding the target index twice per tick

Copy-paste duplicated the call, comment included, violating the invariant
TargetIndex.refresh() documents about itself ('runs once per server tick, costs
O(prey)'). refresh() is idempotent so nothing broke — the second pass was a full
sweep of every tracked prey per dimension per tick, entirely discarded. Audit #13."
```

---

# Band B — contamination: extract, unify, seal

## Task 3: Extract `ContaminationRoll` and unify the intensity floor (#5)

The plague inverts intensity to shorten the gap between flares, guarded by an anti-divide-by-zero floor. That rule is written twice with **two different floors**: `ContaminationEpisodes.rollGap` uses the configurable `ExpertConfig.expertContamIntensityFloor`, `ContaminationHallucination.rollHallucGap` uses a hardcoded `1.0e-3`. So the config option governs 3 of the 4 flare types and silently skips the fourth.

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRoll.java`
- Create: `mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRollTest.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationEpisodes.java:93-96`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationHallucination.java:47-50`

**Interfaces:**
- Consumes: `ExpertConfig.expertContamIntensityFloor` (double, `ExpertConfig.java:41`, bounded `0.000001..1000` at `ConfigBoundsTable.java:301`).
- Produces: `public static double ContaminationRoll.intensityFactor(double mult)` — the gap-shortening factor. Task 4 adds more methods to this same class.

- [ ] **Step 1: Write the failing test**

Create `mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRollTest.java`:

```java
package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of {@link ContaminationRoll} — the plague's random-draw rules.
 *
 * <p>This class exists so these rules ARE testable: they used to live on ContaminationState, whose
 * static initialiser registers Fabric attachment types and therefore cannot load under plain JUnit.
 * Findings #5 and #12 are both "the same arithmetic, written twice, drifted apart" — untested
 * arithmetic is exactly how that happens.
 */
class ContaminationRollTest {

    private final double savedFloor = ExpertConfig.expertContamIntensityFloor;

    @AfterEach
    void restoreConfig() {
        ExpertConfig.expertContamIntensityFloor = savedFloor;
    }

    @Test
    void intensityFactorInvertsTheMultiplier() {
        assertEquals(0.5, ContaminationRoll.intensityFactor(2.0), 1e-12);
        assertEquals(0.25, ContaminationRoll.intensityFactor(4.0), 1e-12);
        assertEquals(1.0, ContaminationRoll.intensityFactor(1.0), 1e-12);
    }

    @Test
    void intensityFactorHonoursTheConfiguredFloor() {
        // This is finding #5: the hallucination flare used a hardcoded 1.0e-3 while episodes used
        // this option, so tuning it moved 3 of the 4 flare types and silently skipped the fourth.
        ExpertConfig.expertContamIntensityFloor = 0.5;
        assertEquals(2.0, ContaminationRoll.intensityFactor(0.1), 1e-12);
        assertEquals(2.0, ContaminationRoll.intensityFactor(0.0), 1e-12);

        ExpertConfig.expertContamIntensityFloor = 0.01;
        assertEquals(100.0, ContaminationRoll.intensityFactor(0.001), 1e-12);
    }

    @Test
    void intensityFactorNeverDividesByZero() {
        ExpertConfig.expertContamIntensityFloor = 1.0e-6;
        assertTrue(Double.isFinite(ContaminationRoll.intensityFactor(0.0)));
        assertTrue(Double.isFinite(ContaminationRoll.intensityFactor(-5.0)));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd mod && ./gradlew test --tests '*ContaminationRollTest*'
```

Expected: compilation failure — `cannot find symbol: class ContaminationRoll`.

- [ ] **Step 3: Write the minimal implementation**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRoll.java`:

```java
package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;

/**
 * The plague's random-draw rules as pure functions. **No Minecraft imports, and none may be added** —
 * that is the whole point of this class: {@link ContaminationState} registers Fabric attachment types
 * in its static initialiser and so cannot be loaded by a headless unit test, which left every draw
 * rule the plague uses permanently untested. Findings #5 and #12 are both instances of the same
 * failure mode that produced: one rule, written in several places, quietly drifting apart.
 *
 * <p>Add a draw rule HERE and call it from the timer classes — never inline a fresh copy at a call site.
 */
public final class ContaminationRoll {
    private ContaminationRoll() {}

    /**
     * Gap-shortening factor for a flare timer: higher intensity → shorter gap, so we hand back
     * {@code 1/mult}. The divisor is floored by {@code expertContamIntensityFloor} both to avoid a
     * divide-by-zero and to cap how short an operator can drive the gaps.
     */
    public static double intensityFactor(double mult) {
        return 1.0 / Math.max(ExpertConfig.expertContamIntensityFloor, mult);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd mod && ./gradlew test --tests '*ContaminationRollTest*'
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Route both `rollGap` sites through the new helper**

In `ContaminationEpisodes.java`, replace the body of `rollGap` (lines 93-96):

```java
    /** Gap-between-flares in ticks, scaled DOWN by intensity (more frequent at higher levels). */
    private static long rollGap(Episode ep, double mult) {
        return ContaminationState.rollWindow(ep.gapMin.getAsDouble(), ep.gapMax.getAsDouble(),
                ContaminationRoll.intensityFactor(mult));
    }
```

Then delete the now-unused import at `ContaminationEpisodes.java:4`:

```java
import com.dreykaoas.lethalbreed.config.domain.ExpertConfig;
```

In `ContaminationHallucination.java`, replace the body of `rollHallucGap` (lines 47-50):

```java
    private static long rollHallucGap(double mult) {
        return ContaminationState.rollWindow(ContaminationConfig.contamHallucGapMinSec,
                ContaminationConfig.contamHallucGapMaxSec, ContaminationRoll.intensityFactor(mult));
    }
```

- [ ] **Step 6: Fix the class javadoc that promised the equivalence**

`ContaminationHallucination`'s class comment claims *"Duration scales up / gap scales down with intensity, like episodes"* — which was false until this task. It is now true, so leave the sentence but make the shared rule explicit. Replace lines 21-22:

```java
    /** Drive the zombie-vision hallucination flare for one victim: OFF between flares, ON (ZOMBIE_VISION applied)
     *  for a random duration, then a random gap. Duration scales up / gap scales down with intensity, exactly
     *  like episodes — both gaps go through {@link ContaminationRoll#intensityFactor}, so the
     *  {@code expertContamIntensityFloor} option governs all four flare types (audit #5). */
```

- [ ] **Step 7: Verify no hardcoded floor survives**

```bash
cd mod && grep -rn "1.0e-3\|1e-3\|0.001" src/main/java/com/dreykaoas/lethalbreed/effect/contamination/
```

Expected: **no match** in `ContaminationHallucination.java`. Matches elsewhere in the package are unrelated and must be left alone. (`ExpertConfig`'s own `= 1.0e-3` defaults are the option's default value, not a hardcoded floor — do not touch them.)

- [ ] **Step 8: Run the whole suite and build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 42 tests (39 existing + 3 new), 0 failures.

- [ ] **Step 9: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRoll.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRollTest.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationEpisodes.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationHallucination.java
git commit -m "fix: make expertContamIntensityFloor govern all four flare types

The gap-shortening rule was written twice with two different floors: episodes
used the configurable option, hallucination a hardcoded 1.0e-3. Tuning the option
moved 3 of 4 flare types and silently skipped the fourth, while the class javadoc
promised they behaved alike.

Extracts the rule into ContaminationRoll — a Minecraft-free class, so it is the
first plague draw rule with actual unit coverage. ContaminationState cannot be
loaded headless (Fabric attachment registration in its static init), which is why
none of this arithmetic was ever tested. Audit #5."
```

---

## Task 4: Route the five hand-rolled uniform draws through `ContaminationRoll` (#12)

`ContaminationState.rollScaled` is the canonical draw and applies two protections its five hand-written copies do not: a zero floor on `min`, and a **reorder** when `min > max`. On those five sites, inverting a min/max pair in config — with both values perfectly inside their individual bounds, since `ConfigBoundsTable` bounds each field independently and never a relation between two — produces a draw *below* the minimum, or negative. Three of the five then repeat the same `RNG.nextDouble() * 100.0 < pct` percentage roll.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRoll.java`
- Modify: `mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationRollTest.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationState.java:78-83, 126-133`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java:50-58, 104-107`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationEvolve.java:21-24`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationSymptoms.java:43-47`

**Interfaces:**
- Consumes: `ContaminationRoll.intensityFactor` from task 3.
- Produces:
  - `public static double ContaminationRoll.uniform(Random rng, double min, double max)` — clamped, reordered uniform draw in `[min, max]`.
  - `public static boolean ContaminationRoll.percent(Random rng, double minPct, double maxPct)` — draws a percentage in `[minPct, maxPct]`, then returns whether a fresh roll lands under it.

- [ ] **Step 1: Write the failing tests**

Append to `ContaminationRollTest.java` (inside the class, after the existing tests):

```java
    /** A Random that hands back a scripted sequence, so a draw's arithmetic is checked exactly. */
    private static java.util.Random scripted(double... values) {
        return new java.util.Random() {
            private int i = 0;
            @Override
            public double nextDouble() {
                return values[i++ % values.length];
            }
        };
    }

    @Test
    void uniformLerpsAcrossTheRange() {
        assertEquals(10.0, ContaminationRoll.uniform(scripted(0.0), 10.0, 20.0), 1e-12);
        assertEquals(20.0, ContaminationRoll.uniform(scripted(1.0), 10.0, 20.0), 1e-12);
        assertEquals(15.0, ContaminationRoll.uniform(scripted(0.5), 10.0, 20.0), 1e-12);
    }

    @Test
    void uniformReordersAnInvertedRange() {
        // This is finding #12: the five hand-written copies did the lerp WITHOUT this reorder, so an
        // operator who typed min=5, max=1 (both individually in bounds — ConfigBoundsTable bounds each
        // field alone, never the relation between two) got a draw BELOW the minimum.
        assertEquals(1.0, ContaminationRoll.uniform(scripted(0.0), 5.0, 1.0), 1e-12);
        assertEquals(5.0, ContaminationRoll.uniform(scripted(1.0), 5.0, 1.0), 1e-12);
        assertEquals(3.0, ContaminationRoll.uniform(scripted(0.5), 5.0, 1.0), 1e-12);
    }

    @Test
    void uniformFloorsANegativeMinimumAtZero() {
        assertEquals(0.0, ContaminationRoll.uniform(scripted(0.0), -8.0, 4.0), 1e-12);
        assertEquals(4.0, ContaminationRoll.uniform(scripted(1.0), -8.0, 4.0), 1e-12);
        // both ends negative → collapses to a constant 0, never a negative draw
        assertEquals(0.0, ContaminationRoll.uniform(scripted(0.5), -8.0, -2.0), 1e-12);
    }

    @Test
    void percentComparesASecondRollAgainstTheDrawnThreshold() {
        // first nextDouble picks the threshold in [min,max], second is the roll compared to it
        assertTrue(ContaminationRoll.percent(scripted(1.0, 0.05), 10.0, 20.0));   // threshold 20%, roll 5%
        assertFalse(ContaminationRoll.percent(scripted(0.0, 0.5), 10.0, 20.0));   // threshold 10%, roll 50%
    }

    @Test
    void percentAtZeroNeverFires() {
        assertFalse(ContaminationRoll.percent(scripted(0.5, 0.0), 0.0, 0.0));
    }
```

Add the missing import to the test's import block:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd mod && ./gradlew test --tests '*ContaminationRollTest*'
```

Expected: compilation failure — `cannot find symbol: method uniform(...)`.

- [ ] **Step 3: Add the two primitives**

Append to `ContaminationRoll.java` (inside the class), and add `import java.util.Random;` at the top:

```java
    /**
     * The one uniform draw in {@code [min, max]} behind every plague timer and every plague magnitude.
     * Clamps {@code min} to 0 and REORDERS an inverted pair before lerping — {@code ConfigBoundsTable}
     * bounds each option independently and never the relation between two, so an operator can put
     * min above max with both values perfectly in range. Without the reorder that yields a draw below
     * the minimum, or negative: a healing plague, or a cure threshold that never fires (audit #12).
     */
    public static double uniform(Random rng, double min, double max) {
        min = Math.max(0.0, min);
        max = Math.max(0.0, max);
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        return min + rng.nextDouble() * (max - min);
    }

    /**
     * Draw a percentage threshold in {@code [minPct, maxPct]}, then roll against it. True means the
     * event fires. Consumes exactly two values from {@code rng}, in that order.
     */
    public static boolean percent(Random rng, double minPct, double maxPct) {
        double pct = uniform(rng, minPct, maxPct);
        return rng.nextDouble() * 100.0 < pct;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd mod && ./gradlew test --tests '*ContaminationRollTest*'
```

Expected: PASS, 8 tests.

- [ ] **Step 5: Rewrite `rollScaled` on top of `uniform`**

In `ContaminationState.java`, replace `rollScaled` (lines 126-133) so the canonical path and the five former copies share one implementation:

```java
    /** The single [min,max]×factor uniform roll behind every plague timer: draw the range via
     *  {@link ContaminationRoll#uniform}, scale it, and convert to ticks via {@code unitTicks}
     *  (20 = per-second, 24000 = per-in-game-day) under dev compression. */
    public static long rollScaled(double min, double max, double factor, double unitTicks) {
        double v = ContaminationRoll.uniform(RNG, min, max) * factor;
        return Math.max(1L, Math.round(v * unitTicks / devTimeScale()));
    }
```

- [ ] **Step 6: Replace site 1 of 5 — the per-victim intensity jitter**

In `ContaminationState.java`, replace `recomputeIntensity` (lines 78-83):

```java
    /** Roll a fresh per-victim intensity for a level: 1 + (level-1) × step × jitter, jitter random per victim. */
    public static void recomputeIntensity(LivingEntity e, int lvl) {
        double jitter = ContaminationRoll.uniform(RNG,
                ContaminationConfig.contamLevelJitterMin, ContaminationConfig.contamLevelJitterMax);
        double mult = 1.0 + (lvl - 1) * ContaminationConfig.contamLevelStep * jitter;
        e.setAttached(INTENSITY, Math.max(1.0, mult));
    }
```

Note the `Math.max(0.0, jitter)` that used to wrap the jitter is gone: `uniform` already floors at zero, so keeping it would be a redundant second guard.

- [ ] **Step 7: Replace site 2 of 5 — the crouch cure roll**

In `ContaminationTick.java`, replace lines 49-58:

```java
            // Cure: only by staying crouched; tiny random chance per check.
            if (e.isCrouching() && t % Math.max(1, ContaminationConfig.contamCureCheckTicks) == 0
                    && ContaminationRoll.percent(ContaminationState.RNG,
                            ContaminationConfig.contamCureMinPct, ContaminationConfig.contamCureMaxPct)) {
                ContaminationLifecycle.cure(e);
                continue;
            }
```

- [ ] **Step 8: Replace site 3 of 5 — the plague damage pulse**

In `ContaminationTick.java`, replace lines 104-107 (the `float dmg = ...` statement) with:

```java
                float dmg = (float) (ContaminationRoll.uniform(ContaminationState.RNG,
                        ContaminationConfig.contamDamageMin, ContaminationConfig.contamDamageMax) * mult);
```

This is the site the audit calls out as the sharpest: an inverted `contamDamageMin`/`Max` pair previously produced a **negative** `dmg`, and `float next = e.getHealth() - dmg` then *heals* the victim — a plague that cures.

- [ ] **Step 9: Replace site 4 of 5 — the level-up roll**

In `ContaminationEvolve.java`, replace lines 20-28:

```java
        if (t >= roll) {
            if (ContaminationRoll.percent(ContaminationState.RNG,
                    ContaminationConfig.contamEvolveMinPct, ContaminationConfig.contamEvolveMaxPct)) {
                ContaminationState.setLevel(e, ContaminationState.level(e) + 1);
            }
            ContaminationState.nextEvolveRoll.put(e, t + rollEvolveIntervalTicks());
        }
```

- [ ] **Step 10: Replace site 5 of 5 — the symptom-onset roll**

In `ContaminationSymptoms.java`, replace lines 42-53:

```java
        if (t >= roll) {
            if (ContaminationRoll.percent(ContaminationState.RNG,
                    ContaminationConfig.contamSymptomMinPct, ContaminationConfig.contamSymptomMaxPct)) {
                e.setAttached(ContaminationState.SYMPTOMATIC, true);
                ContaminationState.setLevel(e, 1); // enter symptomatic at level 1 (applies icon + seeds intensity)
                ContaminationState.nextSymptomRoll.remove(e);
            } else {
                ContaminationState.nextSymptomRoll.put(e, t + rollSymptomIntervalTicks());
            }
        }
```

- [ ] **Step 11: Verify no hand-rolled draw survives in the package**

```bash
cd mod && grep -rn "RNG.nextDouble()" src/main/java/com/dreykaoas/lethalbreed/effect/contamination/
```

Expected: **zero matches.** `ContaminationRoll` draws from its injected `rng` parameter (lowercase `rng.nextDouble()`), never from the shared `RNG` static, so this grep is a clean check that no hand-rolled draw survives. Then confirm the primitives are the only remaining draw site at all:

```bash
cd mod && grep -rn "nextDouble()" src/main/java/com/dreykaoas/lethalbreed/effect/contamination/
```

Expected: exactly two matches, both in `ContaminationRoll.java` — one in `uniform`, one in `percent`.

- [ ] **Step 12: Run the whole suite and build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 47 tests, 0 failures.

- [ ] **Step 13: In-game smoke test — the plague still infects, evolves and cures**

```bash
cd mod && ./gradlew runClient
```

In-game, create a world, then:
1. `/lethaldev timescale 200` — compress plague time so days pass in seconds.
2. `/lethaldev contaminate @s` then `/lethaldev symptoms @s` — the skull icon appears.
3. Watch for ~60 s: health should tick **down** in small chips, never up. A rising health bar means the damage draw went negative — stop and re-read step 8.
4. `/lethaldev level @s 5` — the screen blur should tighten.
5. `/lethaldev cure @s` — icon, blur and damage all stop.
6. `/lethaldev timescale 1` to restore.

- [ ] **Step 14: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ \
        mod/src/test/java/com/dreykaoas/lethalbreed/effect/contamination/
git commit -m "fix: route all five hand-rolled plague draws through one guarded helper

rollScaled clamped min to zero and reordered an inverted pair; the five copies
pasted next to it did neither. ConfigBoundsTable bounds each option alone and
never the relation between two, so min>max is reachable with both values in
range — and on the damage site that yields a negative chip, i.e. a plague that
HEALS its victim. Three sites also repeated the same percentage roll verbatim.

uniform() and percent() now live in ContaminationRoll with unit coverage, and
rollScaled is rewritten on top of uniform so canonical and former-copy paths
cannot drift again. Audit #12."
```

---

## Task 5: Clear the static `SNAPSHOT` buffer before the early return (#8)

`ContaminationTick.SNAPSHOT` is a reused static scratch list. Its `clear()` sits *behind* the early-return guard, so the two ordinary paths that trip that guard — `tracked` going empty (last victim cured or dead), or the plague being switched off — leave a full batch of `LivingEntity` references in a JVM-lived static. One retained entity pins `entity.level` → `ServerLevel` → chunks → `MinecraftServer`. In single-player, returning to the menu and loading a different world keeps the previous world unreachable to the GC until the new world's first non-empty tick.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java:25-33`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationLifecycle.java:58-64`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `static void ContaminationTick.clearSnapshot()` — package-private, called by `ContaminationLifecycle.onServerStopped()`.

- [ ] **Step 1: Move the `clear()` ahead of the guard**

In `ContaminationTick.java`, replace lines 27-33:

```java
    public static void tick(MinecraftServer server) {
        // Cleared BEFORE the guard, not after: the two ordinary ways out of here — `tracked` going empty
        // (last victim cured or died) and the plague being switched off — both take the early return, and
        // a scratch buffer that only self-clears on the hot path holds its last batch forever. One retained
        // LivingEntity pins level -> ServerLevel -> chunks -> MinecraftServer (audit #8).
        SNAPSHOT.clear();
        if (!ContaminationConfig.contaminationEnabled || ContaminationState.tracked.isEmpty()) {
            return;
        }
        long t = server.getTickCount();
        SNAPSHOT.addAll(ContaminationState.tracked);
```

- [ ] **Step 2: Add the teardown hook**

Append to `ContaminationTick.java`, inside the class after `tick(...)`:

```java
    /** SERVER_STOPPED: drop the scratch buffer's references to the closing world's entities. The per-tick
     *  clear above already covers every in-session path; this covers the case where the server stops
     *  between two ticks, with a batch still resident. */
    static void clearSnapshot() {
        SNAPSHOT.clear();
    }
```

- [ ] **Step 3: Call it from the single teardown point**

In `ContaminationLifecycle.java`, replace `onServerStopped` (lines 58-64):

```java
    /** SERVER_STOPPED: drop ALL victims from every in-memory collection so a stopped world's entity graph is
     *  not pinned by these {@code static} maps into the next session. Persistent attachments are untouched.
     *
     *  <p>Every static collection in this package must be purged here. The tick sweep's scratch buffer was
     *  the one this list originally missed (audit #8) — it lives in a sibling class, so a purge written by
     *  reading only THIS file could not see it. When you add a static that holds an entity, add it here. */
    public static void onServerStopped() {
        ContaminationState.clearAllTransient();
        ContaminationEpisodes.clearAllVictims();
        ContaminationHallucination.clearAllVictims();
        ContaminationTick.clearSnapshot();
    }
```

- [ ] **Step 4: Verify the ordering statically**

```bash
cd mod && grep -n "SNAPSHOT.clear()\|contaminationEnabled" src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java | head -4
```

Expected: the `SNAPSHOT.clear()` line number is **lower** than the `contaminationEnabled` guard's line number.

- [ ] **Step 5: Build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 47 tests, 0 failures.

- [ ] **Step 6: In-game verification of the leak path**

No headless test can reach this: `tick()` requires a live `MinecraftServer`. Verify the exact reported scenario instead.

```bash
cd mod && ./gradlew runClient
```

1. Create world A. `/lethaldev contaminate @s`, then `/lethaldev symptoms @s`.
2. `/lethaldev cure @s` — `tracked` is now empty, so `tick()` takes the early return every tick from here on.
3. Return to the main menu, then create and enter world B.
4. Open the F3 screen and confirm the game is stable — the fix is a reference release, not a visible behaviour change, so the check is that nothing regressed.

Optional, if a JDK with `jcmd` is on PATH — this is the only way to actually *see* the fix:

```bash
jcmd $(jcmd -l | grep -i knot | cut -d' ' -f1) GC.class_histogram | grep -i "ServerLevel"
```

Run it at step 3 before the fix and after: the count of live `ServerLevel` instances while sitting in world B should be 1, not 2.

- [ ] **Step 7: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationLifecycle.java
git commit -m "fix: clear the tick scratch buffer before the early return, not after

SNAPSHOT.clear() sat behind the guard, so the two ordinary ways out — tracked
going empty, or the plague being switched off — both left a full batch of
LivingEntity refs in a JVM-lived static. One retained entity pins level ->
ServerLevel -> chunks -> MinecraftServer, so in single-player the previous world
stayed unreachable to the GC across the whole menu/loading window.

Also adds it to onServerStopped(), which purged seven collections but not this
one: it lives in a sibling class and was invisible to a purge written by reading
only ContaminationLifecycle. Audit #8."
```

---

## Task 6: Stop re-tracking victims while the plague is disabled (#9)

`ENTITY_LOAD` → `ContaminationLifecycle.onLoad()` re-tracks any entity whose persistent `CONTAM` attachment is positive, **without consulting `contaminationEnabled`**. Meanwhile the only runtime removal is the tick sweep, which `!contaminationEnabled` short-circuits. `Entity.hashCode()` returns `this.id` from a monotonic counter, so every chunk reload of the same victim adds a *distinct* `HashSet` entry. An admin who disables the plague on a populated world and does not restart accumulates stale entries for as long as the server runs.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationLifecycle.java:33-42`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java` (guard block)

**Interfaces:**
- Consumes: task 5's `SNAPSHOT.clear()` placement — this task edits the same guard block, so it must land after task 5.
- Produces: nothing new.

- [ ] **Step 1: Gate `onLoad` behind the enable flag**

In `ContaminationLifecycle.java`, replace `onLoad` (lines 33-42):

```java
    /** Re-track a contaminated entity after chunk reload (its attachment persists, the in-memory set doesn't).
     *  Only re-show the icon if it had already turned symptomatic.
     *
     *  <p>Gated on {@code contaminationEnabled}: the CONTAM attachment is persistent, so it outlives the
     *  option being switched off, while the tick sweep that would remove entries is itself gated on the same
     *  flag. Ungated, every chunk reload of a victim added a fresh HashSet entry — Entity.hashCode() is the
     *  monotonic entity id, so a reloaded victim is never equal to its previous incarnation (audit #9). */
    public static void onLoad(Entity e) {
        if (!ContaminationConfig.contaminationEnabled) {
            return;
        }
        if (e instanceof LivingEntity le && ContaminationState.age(le) > 0) {
            ContaminationState.tracked.add(le);
            if (ContaminationState.symptomatic(le)) {
                ContaminationSymptoms.applyIcon(le, ContaminationState.level(le) - 1);
            }
        }
    }
```

- [ ] **Step 2: Purge once on the enabled → disabled transition**

The gate above stops *new* accumulation but leaves whatever was tracked when the operator flipped the option. Add a second, independent lock: purge on the transition instead of leaving the cleanup sweep conditioned on a gameplay flag.

In `ContaminationTick.java`, replace the guard block written in task 5:

```java
    /** Was the plague enabled on the previous tick? Drives the one-shot purge below. */
    private static boolean wasEnabled = true;

    public static void tick(MinecraftServer server) {
        // Cleared BEFORE the guard, not after: the two ordinary ways out of here — `tracked` going empty
        // (last victim cured or died) and the plague being switched off — both take the early return, and
        // a scratch buffer that only self-clears on the hot path holds its last batch forever. One retained
        // LivingEntity pins level -> ServerLevel -> chunks -> MinecraftServer (audit #8).
        SNAPSHOT.clear();

        boolean enabled = ContaminationConfig.contaminationEnabled;
        if (wasEnabled && !enabled) {
            // Enabled -> disabled: purge once, here, rather than leaving the in-memory state to be cleaned
            // by a sweep that this very flag switches off. Persistent attachments are untouched, so
            // re-enabling the plague re-tracks every victim through onLoad on its next chunk load (audit #9).
            ContaminationLifecycle.onServerStopped();
        }
        wasEnabled = enabled;

        if (!enabled || ContaminationState.tracked.isEmpty()) {
            return;
        }
        long t = server.getTickCount();
        SNAPSHOT.addAll(ContaminationState.tracked);
```

- [ ] **Step 3: Reset the transition flag on server stop**

`wasEnabled` is a JVM-lived static like everything else here; a server that stops while the plague is disabled would otherwise carry `false` into the next world and skip its purge. Extend the method added in task 5:

```java
    /** SERVER_STOPPED: drop the scratch buffer's references to the closing world's entities, and re-arm the
     *  enabled/disabled transition detector so the next server starts from a known state. */
    static void clearSnapshot() {
        SNAPSHOT.clear();
        wasEnabled = true;
    }
```

- [ ] **Step 4: Build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 47 tests, 0 failures.

- [ ] **Step 5: In-game verification**

```bash
cd mod && ./gradlew runClient
```

1. Create a world. `/lethaldev contaminate @s`.
2. Spawn and infect a few mobs, or just use yourself.
3. `/lethalconfig set contaminationEnabled false` — the one-shot purge fires on the next tick.
4. Fly far enough to unload and reload the chunk you were in (or use `/lethalspawn` to force loads), several times.
5. `/lethalconfig set contaminationEnabled true` then `/lethaldev symptoms @s` — the plague must resume normally on the still-attached victim. If it does not, `onLoad` is over-gated: the victim needs a chunk reload to re-track, which is by design, so move away and back before concluding.

- [ ] **Step 6: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationLifecycle.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java
git commit -m "fix: stop accumulating stale victims while the plague is disabled

onLoad re-tracked on the persistent CONTAM attachment without consulting
contaminationEnabled, while the only runtime removal — the tick sweep — was
gated on it. Entity.hashCode() is the monotonic entity id, so every chunk reload
of the same victim inserted a DISTINCT HashSet entry that nothing would ever
remove before a restart.

Two independent locks: gate onLoad, and purge once on the enabled -> disabled
transition instead of conditioning cleanup on a gameplay flag. Audit #9."
```

---

# Band C — behaviour and performance

## Task 7: Release `NoAI` when the mod stops holding it (#2)

`ZombieMood.dozeInPlace()` calls `entity.setNoAi(true)` and records that in the instance field `noAiFrozen`. `NoAI` is persisted to NBT by vanilla (`Mob` writes it at :369-370, reads at :389); `noAiFrozen` dies with the `ZombieMood`, which `ENTITY_UNLOAD` destroys on every chunk unload. On reload the mod builds a fresh `ZombieMood` with `noAiFrozen=false`, so `clearSleepState()`'s guard — *"hand vanilla AI back exactly when WE were the ones holding it off"* — never fires for that freeze. `isEffectiveAi()` gates both `serverAiStep()` and `travel()`, so the zombie is a statue with no gravity and no navigation, kept from despawning by the mod's own `setPersistenceRequired`.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java` (add a public release method)
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java` (ENTITY_UNLOAD + ENTITY_LOAD)

**Interfaces:**
- Consumes: `SmartZombie.mood()` returns `ZombieMood` (`SmartZombie.java:41`); `SmartZombie.entity()` returns `Zombie` (`:59`); `ZombieRegistry.remove(int id)` returns the `SmartZombie` (already used at `EntityEventsInit.java:85`).
- Produces: `public void ZombieMood.releaseAiHold()` — idempotent; hands vanilla AI back if and only if this mood object is holding it.

- [ ] **Step 1: Add the public release method**

In `ZombieMood.java`, immediately after `clearSleepState()` (which ends at line 343), add:

```java
    /** Hand vanilla AI back if WE are holding it, without touching any other sleep state. Called when this
     *  mood object is about to be discarded — chunk unload or server stop — because {@code NoAI} is persisted
     *  to entity NBT by vanilla while {@code noAiFrozen} is not: a frozen zombie whose mood dies is reloaded
     *  with NoAI still true and nothing left that knows to lift it, leaving a gravity-less, navigation-less
     *  statue that setPersistenceRequired also stops from despawning (audit #2). Idempotent. */
    public void releaseAiHold() {
        if (noAiFrozen) {
            entity.setNoAi(false);
            noAiFrozen = false;
        }
    }
```

- [ ] **Step 2: Release on chunk unload**

In `EntityEventsInit.java`, inside the `ENTITY_UNLOAD` handler, extend the `sz != null` block. Replace lines 84-91:

```java
            if (entity instanceof Zombie) {
                SmartZombie sz = registry.remove(entity.getId());
                // Drop it from the spatial grid too, not just the registry. The only other grid-removal
                // path (LodBucketPass.untrack) is driven by iterating the registry, so a zombie removed
                // here was never visited again and its cell slot stayed for the rest of the session —
                // every death and every chunk unload leaked one, pinning entity -> level -> server, and
                // neighbour queries (sound, Hurleur rally, Soigneur heal) kept matching those ghosts.
                if (sz != null) {
                    // Hand vanilla AI back BEFORE the mood object goes away. NoAI is persisted to NBT,
                    // the "we froze it" flag is not — so a dozing zombie unloaded while frozen would be
                    // saved as NoAI=true with nothing left to lift it (audit #2).
                    sz.mood().releaseAiHold();
                    if (sz.pursuit().inGrid()) {
                        dimensions.get(sz.dimension()).spatialGrid().remove(sz);
                    }
                }
                VanillaTargetingGoals.drop(entity.getId()); // release any stripped-goal snapshot
            }
```

- [ ] **Step 3: Release on server stop**

`SERVER_STOPPED` calls `registry.clear()`, which drops every `SmartZombie` without touching its entity. Zombies dozing at that moment are saved frozen. In `LifecycleInit.java`, replace the `SERVER_STOPPED` body:

```java
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // Hand vanilla AI back to every zombie we are currently freezing, BEFORE dropping the registry
            // that holds the only record of which ones those are. NoAI persists to NBT; our flag does not.
            for (SmartZombie sz : registry.all()) {
                sz.mood().releaseAiHold();
            }
            registry.clear();
            dimensions.clear();
            // Release the rest of the process-wide state that references entities/levels, so the closed
            // world isn't pinned into the next session (audit #2, #20).
            scheduler.reset();
            ContaminationManager.onServerStopped();
        });
```

`ZombieRegistry.all()` already exists and returns `Collection<SmartZombie>` (`ZombieRegistry.java:35`), so no new API is needed. Add the import to `LifecycleInit.java`:

```java
import com.dreykaoas.lethalbreed.entity.SmartZombie;
```

- [ ] **Step 4: Add the defence-in-depth lift on load**

Steps 2 and 3 prevent *new* statues. Worlds already played on this build contain statues that nothing will ever lift. In `EntityEventsInit.java`, inside the `ENTITY_LOAD` handler, add immediately after `registry.add(zombie, world.dimension());`:

```java
                // Defence in depth + repair for worlds saved before audit #2 was fixed: a zombie that
                // arrives already frozen cannot have been frozen by THIS session (the mood object holding
                // that freeze is created empty, just above). Vanilla never sets NoAI on a naturally-spawned
                // zombie, so any zombie loading with it set is either one of our old statues or was placed
                // deliberately by a command/spawn egg. We lift it: a permanently inert, non-despawning
                // zombie is strictly worse than overriding a rare deliberate freeze.
                if (zombie.isNoAi()) {
                    zombie.setNoAi(false);
                }
```

> **Decision this encodes:** an operator who deliberately summoned `{NoAI:1b}` zombies as scenery will have them wake up. The audit judged permanent statues the worse failure, and a summoned-scenery zombie is far rarer than a dozing one. If the author disagrees, gate this block behind a new `ZombieMoodConfig` option defaulting to `true` — and then it needs a `ConfigBoundsTable` entry and a translation key per the Global Constraints. **Ask before adding the option; do not add it unilaterally.**

- [ ] **Step 5: Build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 47 tests, 0 failures.

- [ ] **Step 6: In-game verification — reproduce the statue, then confirm it is gone**

```bash
cd mod && ./gradlew runClient
```

1. Create a world, `/time set day`, `/lethalphase set 1` (below `sunImmunePhase`, so day-sleep is active).
2. `/lethalspawn 5` near a sheltered spot; wait for them to doze (they stop moving and the sleep pose applies).
3. Fly ~200 blocks away and back so the chunk unloads and reloads.
4. **Before the fix** the reloaded zombies hang motionless and never fall or path. **After the fix** they resume normal behaviour on reload.
5. Repeat with a server stop instead of a chunk round-trip: doze them, quit to menu, re-enter the world. Same expectation.
6. Verify the repair path: with an old save (or `/summon zombie ~ ~ ~ {NoAI:1b}`), reload the chunk — the zombie must start moving.

- [ ] **Step 7: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/init/LifecycleInit.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieRegistry.java
git commit -m "fix: hand vanilla AI back before a dozing zombie's mood is discarded

setNoAi(true) is persisted to entity NBT by vanilla; the noAiFrozen flag that
records WE were the ones holding it lives in ZombieMood, which ENTITY_UNLOAD
destroys on every chunk unload. On reload the mod builds a fresh mood with
noAiFrozen=false, so clearSleepState()'s guard never fires for that freeze —
and isEffectiveAi() gates both serverAiStep() and travel(), leaving a statue
with no gravity and no navigation that setPersistenceRequired also keeps from
despawning. A player walking away and back was enough.

Releases the hold on ENTITY_UNLOAD and SERVER_STOPPED, plus a load-time lift
that repairs statues already saved into existing worlds. Audit #2."
```

---

## Task 8: Make milk keep the plague and `/effect clear` cure it — including latent (#1, #11)

Two bugs in one pair of mixins, fixed together because either fix alone breaks the other case.

**#1** — `MilkKeepsPlagueMixin`'s javadoc claims milk *"goes through `ClearAllStatusEffectsConsumeEffect`, a different code path"*. Verified against the decompiled 1.21.11 source, that class's `apply` is literally `return livingEntity.removeAllEffects();` — the **same** path. The callee's `@At("TAIL")` therefore runs *before* the caller's, so `EffectClearCuresPlagueMixin.cure()` wipes the attachments first and `isSymptomatic()` is already false when `MilkKeepsPlagueMixin` tests it. The entire mixin is dead code, and any player cancels the plague with a bucket of milk.

**#11** — `@At("TAIL")` binds only the **last** `RETURN`. `LivingEntity.removeAllEffects()` has three: `false` (client), `false` (no effects), `true`. The handler is on `return true` only. A **latent** victim carries no plague effect at all — the infection slowdown is a transient `AttributeModifier`, not a `MobEffectInstance` — so if it holds no other effect the method returns `false` and the handler never runs. `/effect clear` does not cure latent plague, which is precisely what the mixin's own javadoc says it fixes.

**Files:**
- Create: `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ClearGuard.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/MilkKeepsPlagueMixin.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/EffectClearCuresPlagueMixin.java`

**Interfaces:**
- Consumes: `ContaminationManager.isContaminated(LivingEntity)`, `.isSymptomatic(LivingEntity)`, `.plagueLevel(LivingEntity)`, `.clearPlague(LivingEntity)` — all present at `ContaminationManager.java:76, 81, 98, 103`.
- Produces: `ClearGuard.isMilk()` / the `@Redirect` that arms it.

**Why `@Redirect` rather than the audit's first suggestion:** the audit recommended targeting `EffectCommands.clearEffects` instead. That method is `private static int clearEffects(CommandSourceStack, Collection)` and only *counts* successful `removeAllEffects()` calls — curing latent plague from there would still leave the command throwing `ERROR_CLEAR_EVERYTHING_FAILED` when the victim had no effects, since we cannot alter its counter. Redirecting the single `removeAllEffects()` call inside `ClearAllStatusEffectsConsumeEffect.apply` gives an exact, exception-safe scope with a real `try`/`finally`, needs no new mapping, and fixes both findings at once.

- [ ] **Step 1: Create the guard**

Create `mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ClearGuard.java`:

```java
package com.dreykaoas.lethalbreed.effect.contamination;

/**
 * Marks that the {@code LivingEntity.removeAllEffects()} call currently on this thread's stack came from
 * drinking milk, not from {@code /effect clear}.
 *
 * <p>Both routes are the same method — vanilla's {@code ClearAllStatusEffectsConsumeEffect.apply} is
 * literally {@code return livingEntity.removeAllEffects();}, contradicting the "different code path" the
 * mod's own javadoc claimed (audit #1). The two must behave oppositely: milk keeps the plague, the command
 * cures it. Since they are indistinguishable at the callee, the caller marks itself on the way in.
 *
 * <p>Thread-local because consume effects run on both the client and the server thread; server-only state
 * would be wrong on an integrated server where both live in one JVM. Armed and disarmed by a
 * {@code try}/{@code finally} in {@code MilkKeepsPlagueMixin}, so an exception cannot leave it set.
 */
public final class ClearGuard {
    private ClearGuard() {}

    private static final ThreadLocal<Boolean> MILK = new ThreadLocal<>();

    /** Arm the marker for the current thread. Always pair with {@link #disarm()} in a finally block. */
    public static void arm() {
        MILK.set(Boolean.TRUE);
    }

    /** Disarm and release the thread-local entry. */
    public static void disarm() {
        MILK.remove();
    }

    /** True while a milk-originated clear is in progress on this thread. */
    public static boolean isMilk() {
        return MILK.get() != null;
    }
}
```

- [ ] **Step 2: Rewrite `MilkKeepsPlagueMixin` as a redirect**

Replace the whole of `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/MilkKeepsPlagueMixin.java`:

```java
package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Drinking milk must NOT cure the Super Contamination plague — it is a disease, not a status effect a glass
 * of milk can wash out. {@code /effect clear} must cure it. Both go through the SAME method: vanilla's
 * {@code apply} here is literally {@code return livingEntity.removeAllEffects();}.
 *
 * <p>So we redirect that one call and mark the thread while it runs. {@code EffectClearCuresPlagueMixin}
 * reads the mark and stands down; milk therefore strips only the visible skull effect, which we put straight
 * back afterwards. The mark is armed and cleared by a real {@code try}/{@code finally}, so an exception
 * cannot leave a stale flag that would make the next {@code /effect clear} silently fail to cure.
 *
 * <p>This replaces a {@code @At("TAIL")} handler that could never work: the callee's TAIL fires BEFORE the
 * caller's, so the plague attachments were already wiped by the time it tested {@code isSymptomatic()} —
 * making the whole mixin dead code and letting any player cancel the plague with a bucket of milk (audit #1).
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public class MilkKeepsPlagueMixin {

    @Redirect(
            method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
    private boolean lethalbreed$milkKeepsPlague(LivingEntity entity) {
        boolean removed;
        ClearGuard.arm();
        try {
            removed = entity.removeAllEffects();
        } finally {
            ClearGuard.disarm();
        }
        // Milk stripped the skull icon but not the disease — put the icon straight back so the plague,
        // its level and the hallucination all survive the drink.
        if (ContaminationManager.isSymptomatic(entity)
                && entity.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null) {
            int amp = Math.max(0, ContaminationManager.plagueLevel(entity) - 1);
            entity.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                    MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
        }
        return removed;
    }
}
```

- [ ] **Step 3: Widen `EffectClearCuresPlagueMixin` to every return**

Replace `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/EffectClearCuresPlagueMixin.java`:

```java
package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /effect clear} fully cures the Super Contamination plague, latent or symptomatic.
 *
 * <p>{@code @At("RETURN")}, not {@code TAIL}: TAIL binds only the LAST return instruction, and
 * {@code removeAllEffects()} has three — {@code false} on the client, {@code false} when there was nothing
 * to remove, {@code true} otherwise. A LATENT victim carries no plague effect at all (the infection slowdown
 * is a transient AttributeModifier, not a MobEffectInstance), so with no other effect the method returned
 * {@code false} and the handler bound to {@code return true} never ran — meaning the command did not cure
 * latent plague, the exact case this mixin exists for (audit #11).
 *
 * <p>Milk reaches this same method and must NOT cure: {@code MilkKeepsPlagueMixin} marks the thread while
 * its own call is in flight, and we stand down for it (audit #1).
 */
@Mixin(LivingEntity.class)
public class EffectClearCuresPlagueMixin {

    @Inject(method = "removeAllEffects()Z", at = @At("RETURN"))
    private void lethalbreed$clearPlagueOnEffectClear(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // RETURN also binds the client-side early exit, which the old TAIL never reached. Plague state is
        // authoritative on the server; curing from a client-side call would desync it.
        if (self.level().isClientSide || ClearGuard.isMilk()) {
            return;
        }
        if (ContaminationManager.isContaminated(self)) {
            ContaminationManager.clearPlague(self);
        }
    }
}
```

- [ ] **Step 4: Verify the injectors match**

`lethalbreed.mixins.json` sets `"injectors": { "defaultRequire": 1 }`, so a mixin whose target no longer exists is a **hard launch failure**, not a silent no-op. That is this task's automated check.

```bash
cd mod && ./gradlew build
```

Expected: `BUILD SUCCESSFUL`. Then:

```bash
cd mod && timeout 300 ./gradlew runClient 2>&1 | grep -iE "mixin|Redirect|Critical injection" | head -30
```

Expected: no `Critical injection failure` and no `@Redirect ... could not find target`. Reaching the main menu means both injectors bound.

If the redirect fails to match, the most likely cause is the mapped descriptor of `apply`. Re-derive it from the decompiled source rather than guessing:

```bash
unzip -p mod/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/*/minecraft-merged-*-sources.jar \
  net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java
```

- [ ] **Step 5: In-game verification — the four cases**

This is the task's real test; the four cases are exactly the two findings and their two non-regressions.

```bash
cd mod && ./gradlew runClient
```

| # | Setup | Action | Expected |
|---|---|---|---|
| 1 | `/lethaldev contaminate @s`, `/lethaldev symptoms @s` | drink milk (`/give @s milk_bucket`) | skull icon **returns immediately**, screen blur persists, damage pulses continue — **finding #1 fixed** |
| 2 | `/lethaldev contaminate @s` (latent — no icon), no other effects | `/effect clear @s` | plague **cured**: a following `/lethaldev symptoms @s` must do nothing — **finding #11 fixed** |
| 3 | `/lethaldev contaminate @s`, `/lethaldev symptoms @s` | `/effect clear @s` | plague cured, icon gone, blur gone — **non-regression** |
| 4 | `/effect give @s speed 60`, then `/lethaldev contaminate @s`, `/lethaldev symptoms @s` | drink milk | speed gone, plague icon back — **non-regression: milk still clears ordinary effects** |

Then re-run case 2 immediately after case 1 in the same session. This is the specific check that the `try`/`finally` disarm works: if `ClearGuard` leaked, case 2 would silently stop curing.

- [ ] **Step 6: Fix the three places the repo contradicts itself**

The audit notes the repo states this behaviour three different ways. Now that the behaviour is settled, align the comments.

In `ContaminationTick.java`, replace the comment at lines 72-73:

```java
            // The skull icon is the symptomatic stage's only marker, so losing it means the plague is gone:
            // /effect clear wipes the attachments outright (EffectClearCuresPlagueMixin), and milk puts the
            // icon straight back (MilkKeepsPlagueMixin), so reaching here with no icon means something else
            // removed it — treat that as a cure rather than leaving a symptomatic victim with no marker.
```

In `ContaminationManager.java`, check line 22's claim that the icon is re-applied every tick — `ContaminationTick` only re-applies it when the amplifier is stale (`:81-83`). Correct the sentence to say so:

```bash
cd mod && sed -n '18,26p' src/main/java/com/dreykaoas/lethalbreed/effect/ContaminationManager.java
```

Reword whatever that comment claims so it matches `ContaminationTick.java:81-83`: the icon is re-applied only when its amplifier no longer matches the victim's level, not every tick.

- [ ] **Step 7: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ClearGuard.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/MilkKeepsPlagueMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/EffectClearCuresPlagueMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/effect/ContaminationManager.java
git commit -m "fix: milk no longer cures the plague, /effect clear now cures latent

Two mixins that cancelled each other out.

Milk and /effect clear are the SAME code path: vanilla's
ClearAllStatusEffectsConsumeEffect.apply is literally
'return livingEntity.removeAllEffects();', not the 'different code path' the
javadoc claimed. The callee's @At(TAIL) fires before the caller's, so the plague
attachments were wiped before MilkKeepsPlagueMixin tested isSymptomatic() —
dead code, and any player cancelled the plague with a bucket of milk.

@At(TAIL) also binds only the LAST return. removeAllEffects() has three, and a
latent victim carries no plague effect at all, so with no other effect it
returned false and /effect clear did nothing — the exact case that mixin exists
to fix.

Redirects the single removeAllEffects() call inside apply and marks the thread
for its duration (try/finally, so no stale flag), and widens the command handler
to @At(RETURN) with a client-side guard. Audit #1, #11."
```

---

## Task 9: Stop forcing synchronous chunk loads from `PlacedBlockTracker` (#3)

`tick()` runs once per tick per dimension and calls `level.getBlockState(p)` on every tracked position. Verified at the bytecode: `Level.getBlockState` → `getChunk(x, z, FULL, true)` with `requireChunk = true` (explicit `iconst_1`). On a `ServerLevel` cache miss that chains `addTicket(TicketType.UNKNOWN)` + `managedBlock(...)` + `join()` — a **synchronous block of the server thread**. The ceiling is `blockOpsPerTick=20` × `placedBlockLifetimeTicks=600` = 12 000 positions per dimension, and `HashMap` iteration order is uncorrelated with chunk layout, so `ServerChunkCache`'s 4-entry cache misses almost every time even when every chunk is resident.

The repo already has the right guard elsewhere: `CellClassifier.java:31` does `if (!level.isLoaded(m)) continue;`.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/block/PlacedBlockTracker.java:36-64`

**Interfaces:**
- Consumes: `CombatMoveConfig.placedBlockLifetimeTicks`.
- Produces: nothing new.

- [ ] **Step 1: Guard on chunk residency**

In `PlacedBlockTracker.java`, replace the loop body's world read (lines 44-46) so a non-resident position is skipped rather than force-loaded:

```java
        while (it.hasNext()) {
            Map.Entry<Long, State> e = it.next();
            State s = e.getValue();
            BlockPos p = BlockPos.of(e.getKey());
            // Never force a chunk load from here. Level.getBlockState() resolves through
            // getChunk(x, z, FULL, /* requireChunk */ true), which on a ServerLevel cache miss does
            // addTicket + managedBlock + join — a synchronous stall of the server thread, once per tracked
            // position per tick, with up to 12000 of them per dimension (audit #3). Same guard
            // CellClassifier:31 already uses. An unloaded placement is simply re-checked when its chunk
            // comes back; if it never does, the expiry branch below drops it on age alone.
            if (!level.isLoaded(p)) {
                // Chunk gone: decide nothing here. We can neither read the block state nor destroy it,
                // so we hold the entry and handle it when the chunk returns — even hours later.
                if (now - s.placedAt >= lifetime * ABANDON_FACTOR) {
                    it.remove(); // unloaded this long straight: give up rather than grow without bound
                }
                continue;
            }
            BlockState bs = level.getBlockState(p);
```

- [ ] **Step 2 (OPTIONAL — skip unless step 1 proves insufficient): Sweep in slices**

> **Read this before doing step 2.** Step 1 alone fixes the finding: it removes every forced chunk load, which is the synchronous stall the audit measured. Step 2 is a further optimisation that changes **when** a tracked block is visited, and therefore when it cracks and pops — it is the only part of this task that can alter observable behaviour. Bridging and pillaring have **zero automated coverage** (see the Global Constraints gate), so a regression here would be caught by nothing but a human watching dirt.
>
> **Default: skip step 2.** Do step 1, commit, measure. Only come back if the residual per-tick traversal actually shows up in `PerfRecap`. If you do proceed, the manual protocol in step 4 becomes mandatory rather than advisory, and cases 3 and 4 there are the ones that matter.

A full traversal of up to 12 000 entries per tick per dimension is the other half of the cost, and it survives step 1 because `isLoaded` is still a per-entry call. Give the tracker a rolling cursor.

Add the field next to `breakerSeq` (line 26):

```java
    private int sweepCursor = 0; // rolling start offset so each tick walks one slice, not the whole map
```

Then wrap the loop so it visits at most a slice per tick. Replace the `Iterator` setup (lines 41-42) with:

```java
        // Walk one slice per tick rather than the whole map. Cracking stages are coarse (10 steps across
        // the block's whole lifetime) and expiry is checked against absolute tick age, so a position visited
        // every Nth tick still cracks and pops on schedule — it just stops costing a full-map traversal
        // every tick. Sized so the whole map is covered at least once per crack step.
        int size = placed.size();
        int slice = Math.max(64, (size * 10) / (int) Math.max(1, lifetime) + 1);
        int visited = 0;
        int skipped = 0;
        Iterator<Map.Entry<Long, State>> it = placed.entrySet().iterator();
        while (it.hasNext() && visited < slice) {
            if (skipped < sweepCursor) {
                it.next();
                skipped++;
                continue;
            }
            visited++;
```

and close the loop by advancing the cursor after it:

```java
        }
        sweepCursor = (visited < slice) ? 0 : sweepCursor + visited;
        if (sweepCursor >= placed.size()) {
            sweepCursor = 0;
        }
```

> **Note on `HashMap` iteration and the cursor:** skipping `sweepCursor` entries by iteration order is only stable while the map is not structurally modified. `record()` inserts between ticks, so the cursor is approximate — a given position may be visited twice or skipped for one cycle. That is acceptable here (cracking stages are idempotent, expiry is absolute-tick based) but it must be stated, not discovered. If the imprecision proves visible, replace `HashMap` with `LinkedHashMap` and the cursor becomes exact.

- [ ] **Step 3: Build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 47 tests, 0 failures.

- [ ] **Step 4: In-game verification — placements still crack and pop**

The behaviour must be unchanged; only the cost changes. No headless test can reach this (it needs a `Level`).

```bash
cd mod && ./gradlew runClient
```

1. Create a superflat world, `/lethalphase set 10` so zombies bridge and pillar aggressively.
2. `/lethalspawn 20`, then stand across a gap so they bridge to you.
3. Watch a placed dirt block for `placedBlockLifetimeTicks` (600 ticks = 30 s by default): it must show progressive cracking and then pop **without dropping an item**.
4. Fly away far enough to unload the bridge, wait past the lifetime, fly back: the placements must be gone, not resurrected as permanent terrain.
5. Confirm the count drains — with `debugLogInterval > 0` in a dev environment, `PerfRecap` logs; otherwise check that no dirt lingers after twice the lifetime.

- [ ] **Step 5: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/block/PlacedBlockTracker.java
git commit -m "perf: never force a chunk load from the placed-block sweep

level.getBlockState() resolves through getChunk(x, z, FULL, requireChunk=true) —
on a ServerLevel cache miss that is addTicket + managedBlock + join, a synchronous
stall of the server thread. The sweep did it once per tracked position per tick,
up to 12000 per dimension (blockOpsPerTick 20 x lifetime 600), and HashMap order
is uncorrelated with chunk layout so the 4-entry chunk cache missed nearly every
time even with everything resident.

Adds the isLoaded() guard CellClassifier:31 already uses, and walks one slice per
tick instead of the whole map. Audit #3."
```

---

## Task 10: Give the shade search a failure cooldown (#6)

`ShelterFinder.findShade` iterates `radius=12` → 624 columns × 13 Y levels = **8 112** positions per call. Both `>= bestScore` prunes are inert until something is found, because `bestScore` starts at `Double.MAX_VALUE` — so the worst case is exactly the "no shade" case, which is also the only one that repeats. On failure the caller sets `sleepSeekingShade = false` and returns with **no memory of the failure**, so the next activation rescans in full.

The audit revised the impact down and found the one genuinely unbounded case: a zombie **in water**. `exposed` does not test water, while sun-burn is blocked by `isInWaterOrRain` — so it loops at 1 Hz forever without ever dying.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/mood/ShelterFinder.java`
- Modify: `mod/src/test/java/com/dreykaoas/lethalbreed/entity/move/` → new test at `mod/src/test/java/com/dreykaoas/lethalbreed/entity/mood/ShelterFinderTest.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java` (cooldown field + the call site at :272)

**Interfaces:**
- Consumes: `ZombieMoodConfig.shelterSearchRadius`.
- Produces: `public static double ShelterFinder.maxScore(int radius)` — the finite ceiling used to seed `bestScore`, and the only part of this task that is unit-testable.

- [ ] **Step 1: Write the failing test**

Create `mod/src/test/java/com/dreykaoas/lethalbreed/entity/mood/ShelterFinderTest.java`:

```java
package com.dreykaoas.lethalbreed.entity.mood;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless characterization of the pure part of {@link ShelterFinder}. The search itself needs a live
 * ServerLevel and cannot run here; what CAN be checked is the finite score ceiling that makes the two
 * prune branches active from the first column instead of inert until a first hit (audit #6).
 */
class ShelterFinderTest {

    @Test
    void maxScoreAdmitsTheFurthestLegalCandidate() {
        // The worst acceptable candidate sits at the corner of the horizontal square AND the edge of the
        // vertical band, so the ceiling must be at least its score or the search would prune a valid result.
        int radius = 12;
        int vBand = Math.max(4, radius / 2);
        double worstLegal = (double) radius * radius + (double) radius * radius + 3.0 * vBand * vBand;
        assertTrue(ShelterFinder.maxScore(radius) > worstLegal,
                "ceiling must strictly exceed the furthest legal candidate, else it is pruned away");
    }

    @Test
    void maxScoreIsFiniteSoPruningIsActiveImmediately() {
        assertTrue(Double.isFinite(ShelterFinder.maxScore(12)));
        assertTrue(Double.isFinite(ShelterFinder.maxScore(1)));
        assertTrue(Double.isFinite(ShelterFinder.maxScore(64)));
    }

    @Test
    void maxScoreGrowsWithRadius() {
        assertTrue(ShelterFinder.maxScore(24) > ShelterFinder.maxScore(12));
    }

    @Test
    void maxScoreHandlesADegenerateRadius() {
        assertTrue(Double.isFinite(ShelterFinder.maxScore(0)));
        assertEquals(ShelterFinder.maxScore(0), ShelterFinder.maxScore(0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd mod && ./gradlew test --tests '*ShelterFinderTest*'
```

Expected: compilation failure — `cannot find symbol: method maxScore(int)`.

- [ ] **Step 3: Add the ceiling and seed `bestScore` with it**

In `ShelterFinder.java`, add after the `V_WEIGHT` constant:

```java
    /**
     * Finite ceiling for the nearness score: one unit past the furthest candidate the search can legally
     * return. Seeding {@code bestScore} with this instead of {@code Double.MAX_VALUE} makes both prune
     * branches bite from the very first column rather than staying inert until a first hit — and the
     * no-hit case, where they stayed inert for all 8112 positions, is precisely the case that repeats
     * (audit #6).
     */
    public static double maxScore(int radius) {
        int r = Math.max(0, radius);
        int vBand = Math.max(4, r / 2);
        return 2.0 * r * r + (double) V_WEIGHT * vBand * vBand + 1.0;
    }
```

Then in `findShade`, replace line 22:

```java
        double bestScore = maxScore(radius);
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd mod && ./gradlew test --tests '*ShelterFinderTest*'
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Add the failure cooldown at the call site**

In `ZombieMood.java`, add a field next to `sleepSeekingShade` (line 51):

```java
    // Server tick before which a fresh shade search is pointless, plus where we were when it last failed.
    // findShade sweeps 8112 positions and the failing case is the one that repeats — with no memory of the
    // failure a stationary zombie rescanned the identical volume at 1 Hz forever. Normally self-limiting
    // (an exposed zombie burns and dies in ~20s), except in water: `exposed` does not test water while
    // sun-burn is blocked by isInWaterOrRain, so that zombie loops unbounded (audit #6).
    private long shadeRetryAt = Long.MIN_VALUE;
    private BlockPos shadeFailedAt = null;
```

Then replace the search call at lines 272-276:

```java
        BlockPos here = entity.blockPosition();
        // Skip the sweep while the last failure is still fresh AND we have not meaningfully moved. Moving
        // more than 4 blocks exposes genuinely new volume, so that always re-arms the search immediately.
        boolean moved = shadeFailedAt == null || shadeFailedAt.distSqr(here) > 16.0;
        if (!moved && now < shadeRetryAt) {
            return;
        }
        BlockPos shade = ShelterFinder.findShade(level, here, ZombieMoodConfig.shelterSearchRadius);
        if (shade == null) {
            sleepSeekingShade = false;
            shadeFailedAt = here;
            shadeRetryAt = now + ZombieMoodConfig.shelterRetryTicks;
            return; // no shade in range → keep roaming (can't help the burn)
        }
        shadeFailedAt = null;
        shadeRetryAt = Long.MIN_VALUE;
```

- [ ] **Step 6: Add the config option, its bound and its translations**

In `config/domain/ZombieMoodConfig.java`, next to `shelterSearchRadius`:

```java
    /** Server ticks to wait before re-running a shade search that just failed, unless the zombie moved
     *  more than 4 blocks in the meantime. 100 = 5 seconds. */
    public static int shelterRetryTicks = 100;
```

In `config/ConfigBoundsTable.java`, next to the other `shelter*` entries:

```java
        b("shelterRetryTicks", 0, 12000);
```

Now the translations. **Do not grep for `shelterSearchRadius` as a template — it has none**; 12 of `ZombieMoodConfig`'s 28 fields are untranslated already (see Global Constraints). The key format comes from `OptionEntry.java:56` and `:68`, which concatenate `"lethalbreed.option." + row.name()` where `row.name()` is the literal Java field name.

Add to `src/main/resources/assets/lethalbreed/lang/en_us.json`:

```json
  "lethalbreed.option.shelterRetryTicks": "Shade Search Retry Delay",
  "lethalbreed.option.shelterRetryTicks.desc": "Ticks before a failed shade search is retried, unless the zombie moved more than 4 blocks.",
```

And to `src/main/resources/assets/lethalbreed/lang/fr_fr.json`:

```json
  "lethalbreed.option.shelterRetryTicks": "Délai avant nouvelle recherche d'ombre",
  "lethalbreed.option.shelterRetryTicks.desc": "Ticks avant de relancer une recherche d'ombre infructueuse, sauf si le zombie s'est déplacé de plus de 4 blocs.",
```

Match the surrounding indentation and comma placement in each file — both are 409-line flat JSON objects, so a trailing comma on the last entry breaks resource loading silently at runtime rather than at build time.

> Per `ConfigCategory.java:11-49`, `shelterRetryTicks` matches no substring rule and will therefore appear on the **Misc** tab, alongside the existing `shelterSearchRadius` and `shelterSpeed`. That is consistent with its siblings, so leave it; moving all three to the Mood tab is a separate change and out of scope here.

- [ ] **Step 7: Run the whole suite and build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 51 tests, 0 failures. In particular `everyScalarNumericOptionHasBounds` must pass — if it lists `shelterRetryTicks`, step 6's bounds entry is missing or misspelled.

- [ ] **Step 8: In-game verification — the unbounded water case**

```bash
cd mod && ./gradlew runClient
```

1. Superflat world, `/time set day`, `/lethalphase set 1` (below `sunImmunePhase`, so sun-burn is on).
2. Dig a small water pool with open sky above and no shade within 12 blocks.
3. `/lethalspawn 3` into the water.
4. Before the fix these zombies rescan 8 112 positions each, every second, indefinitely. After it, each rescans once, then at most once per `shelterRetryTicks` while stationary.
5. With `debugLogInterval > 0` in a dev environment, `PerfRecap`'s `mood` stage is the one to watch — it should drop sharply for this scenario.
6. Non-regression: build a small roofed shelter 6 blocks away and confirm zombies still walk into it to doze. The cooldown must not prevent a *successful* search — moving more than 4 blocks re-arms it immediately, and success clears it outright.

- [ ] **Step 9: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/mood/ShelterFinder.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/entity/mood/ShelterFinderTest.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/domain/ZombieMoodConfig.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/ConfigBoundsTable.java \
        mod/src/main/resources/assets/lethalbreed/lang/
git commit -m "perf: remember a failed shade search instead of repeating it at 1 Hz

findShade sweeps 624 columns x 13 Y levels = 8112 positions, and both prune
branches were inert until a first hit because bestScore started at MAX_VALUE —
so the worst case was exactly the no-shade case, which is also the only one that
repeats. On failure the caller kept no memory of it and rescanned in full.

Normally self-limiting (an exposed zombie burns out in ~20s) with one exception:
a zombie in water. 'exposed' does not test water while sun-burn is blocked by
isInWaterOrRain, so that zombie looped unbounded.

Seeds bestScore with a finite ceiling so pruning bites from the first column,
and adds a retry cooldown that a >4-block move re-arms. Audit #6."
```

---

## Task 11: Measure, then cache chunks in the flow-field snapshot (#7)

The flow-field *solve* is correctly off-thread; the **snapshot** is not. `FlowFieldManager.tick:91` calls `CpuFlowField.snapshot(level, players)` *before* `computing.set(true)` and `POOL.submit`, so it runs on the server thread, classifying every grid column through `CellClassifier`'s up-to-9 `isLoaded` + `getBlockState` pairs.

The audit revised the scale down sharply: the grid tracks the players' bounding box + `flowMargin=24`, so a single player gives 49 × 49 = **2 401** cells (~1.5–2 ms per 500 ms), not the 192² ceiling. That ceiling needs players ≥144 blocks apart **on both axes**, where the cost hits ~21 ms in one tick. No throttle is active by default: `flowResampleOnMoveDist` is 0.0 and the `computing` guard never bites because the solve (8.81 ms measured) finishes well inside 500 ms.

**This is the only task with a before/after number, so it measures first.**

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/tick/StageProfiler.java` (add a stage)
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldSnapshotBuilder.java`
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/CellClassifier.java:59-60`

**Interfaces:**
- Consumes: `StageProfiler.sub(Stage, long)` — the existing static side-channel (`StageProfiler.java:46-51`).
- Produces: a new `StageProfiler.Stage.FLOWSNAP` entry appearing in the `PerfRecap` line.

- [ ] **Step 1: Add the stage so the cost is visible**

In `StageProfiler.java`, add to the `Stage` enum after `TICK`:

```java
        FLOWSNAP("flowsnap"),      // server-thread world read that builds the flow-field snapshot
```

- [ ] **Step 2: Time the snapshot**

In `FlowFieldSnapshotBuilder.snapshot`, wrap the classification loop. Replace lines 63-77:

```java
        long t0 = System.nanoTime();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < width; cx++) {
            int wx = minX + cx;
            for (int cz = 0; cz < depth; cz++) {
                int wz = minZ + cz;
                int i = cx * depth + cz;
                byte type = CellClassifier.classify(level, m, wx, wz, focusY, vtol);
                switch (type) {
                    case CellClassifier.PASSABLE -> { passable[i] = true; }
                    case CellClassifier.BREAKABLE -> { passable[i] = true; extraCost[i] = breakCost; flags[i] = FlowField.FLAG_BREAK; }
                    case CellClassifier.BUILDABLE -> { passable[i] = true; extraCost[i] = buildCost; flags[i] = FlowField.FLAG_BUILD; }
                    default -> { passable[i] = false; }
                }
            }
        }
        com.dreykaoas.lethalbreed.tick.StageProfiler.sub(
                com.dreykaoas.lethalbreed.tick.StageProfiler.Stage.FLOWSNAP, System.nanoTime() - t0);
```

- [ ] **Step 3: Record the baseline**

```bash
cd mod && ./gradlew build && ./gradlew runClient
```

In-game: set `debugLogInterval` above 0 so `PerfRecap` logs (`/lethalconfig set debugLogInterval 100`), load a world, `/lethalspawn 100`, and let it run 60 s. Record the `flowsnap` figure from the recap line.

Then record the **ceiling** case, which is the one that matters: with a second player (or a second client), separate them by ≥144 blocks on both X and Z so the grid saturates at `flowMaxGrid`. Record `flowsnap` again.

**Write both numbers into the commit message in step 7.** A performance commit without a measured before/after is exactly the analytical estimate the audit flagged as unreliable.

- [ ] **Step 4: Hoist the chunk lookup out of the inner loop**

The inner loop walks `cz`, so 16 consecutive columns share one chunk — resolving it per column repeats the work 16 times over. Change `CellClassifier.classify` to take a chunk the caller resolves once per 16-column run, and have the builder track it:

In `FlowFieldSnapshotBuilder`, replace the inner loop so the chunk is resolved on each `cz >> 4` boundary:

```java
        long t0 = System.nanoTime();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < width; cx++) {
            int wx = minX + cx;
            int lastChunkZ = Integer.MIN_VALUE;
            net.minecraft.world.level.chunk.ChunkAccess chunk = null;
            for (int cz = 0; cz < depth; cz++) {
                int wz = minZ + cz;
                int chunkZ = wz >> 4;
                if (chunkZ != lastChunkZ) {
                    // 16 consecutive columns share one chunk; resolving it per column repeated the lookup
                    // 16x over. getChunk(x, z, FULL, false) — never force a load: an absent chunk is
                    // IMPASSABLE, which is what CellClassifier's isLoaded guard already concluded.
                    chunk = level.getChunk(wx >> 4, chunkZ,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                    lastChunkZ = chunkZ;
                }
                int i = cx * depth + cz;
                byte type = (chunk == null)
                        ? CellClassifier.IMPASSABLE
                        : CellClassifier.classify(chunk, m, wx, wz, focusY, vtol);
                switch (type) {
                    case CellClassifier.PASSABLE -> { passable[i] = true; }
                    case CellClassifier.BREAKABLE -> { passable[i] = true; extraCost[i] = breakCost; flags[i] = FlowField.FLAG_BREAK; }
                    case CellClassifier.BUILDABLE -> { passable[i] = true; extraCost[i] = buildCost; flags[i] = FlowField.FLAG_BUILD; }
                    default -> { passable[i] = false; }
                }
            }
        }
        com.dreykaoas.lethalbreed.tick.StageProfiler.sub(
                com.dreykaoas.lethalbreed.tick.StageProfiler.Stage.FLOWSNAP, System.nanoTime() - t0);
```

In `CellClassifier`, change `classify` to read from the `ChunkAccess` instead of the `ServerLevel`, and drop the now-redundant `isLoaded` calls (a non-null chunk *is* the residency proof). Replace the signature and the two world-read groups:

```java
    static byte classify(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockPos.MutableBlockPos m,
                         int wx, int wz, int focusY, int vtol) {
        // Standable anywhere in the window?
        for (int y = focusY + vtol; y >= focusY - vtol; y--) {
            m.set(wx, y, wz);
            boolean feet = !chunk.getBlockState(m).blocksMotion();
            m.set(wx, y + 1, wz);
            boolean head = !chunk.getBlockState(m).blocksMotion();
            m.set(wx, y - 1, wz);
            boolean ground = chunk.getBlockState(m).blocksMotion();
            if (feet && head && ground) {
                return PASSABLE;
            }
        }
```

and likewise for the focus-plane block, replacing `level.getBlockState(...)` with `chunk.getBlockState(...)` and deleting the `if (!level.isLoaded(m)) return IMPASSABLE;` guard.

- [ ] **Step 5: Remove the two per-column allocations**

`CellClassifier:59-60` allocates two fresh `BlockPos` objects per wall column.

`MaterialRegistry.isBreakable(Level level, BlockPos pos, BlockState state)` (`MaterialRegistry.java:13`) genuinely needs a `Level` — it calls `state.getDestroySpeed(level, pos)` — so `level` must stay threaded through `classify` alongside the chunk. It does **not** retain the `BlockPos`: `getDestroySpeed` reads it and returns a float, so passing the reused `MutableBlockPos` is safe.

Keep `level` as a parameter and reuse `m`:

```java
    static byte classify(ServerLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk,
                         BlockPos.MutableBlockPos m, int wx, int wz, int focusY, int vtol) {
```

and replace the two allocations at lines 59-60 with:

```java
        if (feetSolid || headSolid) {
            // A wall. Breakable only if every solid layer is breakable. `m` is reused rather than allocating
            // two fresh BlockPos per wall column: isBreakable only reads the position (getDestroySpeed) and
            // never retains it.
            m.set(wx, focusY, wz);
            boolean feetOk = !feetSolid || MaterialRegistry.isBreakable(level, m, feetState);
            m.set(wx, focusY + 1, wz);
            boolean headOk = !headSolid || MaterialRegistry.isBreakable(level, m, headState);
            return (feetOk && headOk) ? BREAKABLE : IMPASSABLE;
        }
```

Update the call in `FlowFieldSnapshotBuilder` from step 4 to match: `CellClassifier.classify(level, chunk, m, wx, wz, focusY, vtol)`.

**Do not widen `MaterialRegistry`'s signature to take a `BlockGetter`** — that touches the break/bridge decision used across `block/` and `entity/move/`, well outside this task.

- [ ] **Step 6: Add a classification-parity self-check — this refactor has NO existing coverage**

**Do not skip this, and do not substitute the flow-field unit tests for it.** `FlowFieldChecksTest`, `FlowFieldDirectionOptimalTest` and `Neighbors8Test` all construct a `Snapshot` **directly** and feed it to `CpuFlowField.compute`. None of them ever calls `CellClassifier`. So every one of them passes unchanged even if this refactor classifies every cell in the world wrongly — and a mis-classified cell is exactly what makes a zombie stop breaking through a wall or stop bridging a gap, neither of which has any automated coverage at all.

Follow the repo's own idiom for this: `ComputeSelfTest` already proves the GPU path with `gpu-cpu-parity : PASS (all 4096 cells match)`. Do the same for classification.

Add to `ComputeSelfTest`, gated by the existing `devComputeTest` flag, a check that classifies a live region **both ways** — the old `ServerLevel`-based path and the new chunk-cached path — and compares every cell:

```java
    /** Classification parity: the chunk-cached snapshot path must produce byte-identical cell types to the
     *  direct ServerLevel path it replaced. Nothing else covers this — the flow-field unit tests all build a
     *  Snapshot by hand and never reach CellClassifier, so a classification regression (a wall that stops
     *  reading as BREAKABLE, a gap that stops reading as BUILDABLE) would silently pass the whole suite and
     *  show up only as zombies that no longer break or bridge. */
    private static void classificationParity(ServerLevel level) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int origin = 0, span = 64, focusY = level.getSeaLevel(), vtol = FlowConfig.flowVerticalTolerance;
        int mismatches = 0, checked = 0;
        for (int wx = origin; wx < origin + span; wx++) {
            for (int wz = origin; wz < origin + span; wz++) {
                var chunk = level.getChunk(wx >> 4, wz >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                byte viaChunk = CellClassifier.classify(level, chunk, m, wx, wz, focusY, vtol);
                byte viaLevel = CellClassifier.classifyViaLevel(level, m, wx, wz, focusY, vtol);
                checked++;
                if (viaChunk != viaLevel) {
                    mismatches++;
                }
            }
        }
        log("classify-parity", mismatches == 0 && checked > 0,
                "chunk-cached vs direct: " + checked + " cells checked, " + mismatches + " mismatched");
    }
```

Keep the pre-refactor implementation available as `classifyViaLevel` — copy the original body of `classify` verbatim into a package-private method of that name before rewriting `classify`. It is the reference oracle; **delete it in step 8, after parity passes**, not before.

Run it:

```bash
cd mod && ./gradlew runServer 2>&1 | grep -E '\[ComputeTest\].*(PASS|FAIL)'
```

Expected: `[ComputeTest] classify-parity : PASS (… 0 mismatched)` alongside the existing checks, and **zero FAIL** anywhere.

If parity fails, the refactor is wrong — the most likely cause is the deleted `isLoaded` guard: `chunk.getBlockState` on a `y` outside the chunk's build height returns void air rather than the `IMPASSABLE` the old guard produced. Fix that rather than relaxing the check.

- [ ] **Step 7: Confirm the suite and re-measure**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 51 tests, 0 failures. Note again that a green suite here is **necessary but not sufficient** — step 6's parity check is what actually covers this refactor.

Then run the behavioural gate from the Global Constraints (`devMechTest`, then `devSpecialTest`), expecting zero `FAIL`, and repeat step 3's two scenarios exactly to record the new `flowsnap` figures.

- [ ] **Step 8: Manual protocol — breaking and bridging**

Neither behaviour has any rig, and both are governed by what `CellClassifier` returns. This is the only check that exists for them.

```bash
cd mod && ./gradlew runClient
```

1. Superflat world, `/lethalphase set 10`, `/time set night`.
2. **Breaking:** wall yourself in behind 2 blocks of dirt, `/lethalspawn 10` outside. Zombies must break through and reach you. If they mill about without breaking, the wall is no longer classifying as BREAKABLE — revert.
3. **Bridging:** stand across a 5-block gap with no path around. Zombies must pillar/bridge across. If they stall at the edge, gaps are no longer classifying as BUILDABLE — revert.
4. **Climbing:** enable `devClimbTest`, `./gradlew runServer`, and read the `[ClimbDbg]` stream. Zombies must scale the 12-block wall and reach the villager on top **without hopping off into the 3-block window at y+4..+6** — that window is deliberate regression cover for the wall-scale face-end scan.

Only once all four pass, delete the `classifyViaLevel` oracle added in step 6 and re-run `./gradlew build`.

Expected: roughly a 16× reduction in chunk resolution, so a clear drop in both. **If `flowsnap` does not measurably improve, revert the task** — the change adds complexity and is justified only by the number.

- [ ] **Step 9: Commit, with the measured numbers in the message**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/tick/StageProfiler.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldSnapshotBuilder.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/CellClassifier.java
git commit -m "perf: resolve the flow-field snapshot's chunk once per 16 columns

The solve is correctly off-thread; the snapshot that feeds it is not. It runs on
the server thread before the submit, classifying every grid column through up to
9 getBlockState pairs. The inner loop walks cz, so 16 consecutive columns share
one chunk and the lookup was repeated 16x over.

Adds a 'flowsnap' profiler stage first, so the cost is measured rather than
argued — the audit's own analytical figures came back an order of magnitude off
when re-derived.

  measured, 1 player (49x49 grid): <BEFORE> ms -> <AFTER> ms
  measured, 2 players >=144 blocks apart (192x192): <BEFORE> ms -> <AFTER> ms

Also drops the two per-wall-column BlockPos allocations. Audit #7."
```

Replace the four `<BEFORE>`/`<AFTER>` placeholders with the figures from steps 3 and 6. **Do not commit with the placeholders in place.**

---

## Task 12: Drain and close the client render-target pool (#10)

`ContaminationScreenOverlay.RESOURCE_POOL` is a `CrossFrameResourcePool` on which **nothing** ever calls `endFrame()`, `clear()` or `close()`. Verified with `javap` against the 1.21.11 jar: `CrossFrameResourcePool.release()` is just `pool.addFirst(new ResourceEntry(...))` — it frees nothing. `endFrame()` is the only thing that decrements `framesToLive`, calls `close()` and removes the entry; there is no autonomous expiry (the `3` passed to the constructor is a frame count to retain, not a purge delay). Vanilla's `GameRenderer` calls `endFrame()` every frame, `clear()` on resize and `close()` on shutdown.

Baseline retention is ~33 MB of VRAM at 1080p (each of the 5 chains declares two non-persistent full-screen targets, colour RGBA8 + depth), held until the game exits — including after returning to the menu. Steady state does not grow per frame, but **each distinct window size** produces a different `RenderTargetDescriptor` whose old pair is never re-matched and never closed. That is the accumulation vector vanilla neutralises with `clear()` in `resize()`.

**Files:**
- Modify: `mod/src/main/java/com/dreykaoas/lethalbreed/client/ContaminationScreenOverlay.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing new.

- [ ] **Step 1: Drain the pool on every frame, including the early exits**

This is the critical detail: both early returns (`isCreative/isSpectator` at line 67, `level <= 0` at line 71) currently skip the pool entirely, so a pool holding targets from when the player *was* sick would never drain once they were cured. Restructure `render()`:

```java
    private static void render() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        try {
            // The plague icon stays in Creative/Spectator, but its symptoms (including this vision blur) are
            // suspended there — matches the server, which freezes all active effects while the player can't
            // take normal damage.
            if (p != null && (p.isCreative() || p.isSpectator())) {
                return;
            }
            int level = plagueLevel(p);
            if (level <= 0) {
                return;
            }

            // Pick the chain for this level (clamped): higher level = smaller clear centre, stronger edge blur.
            Identifier chainId = CHAIN_IDS[Math.min(level, MAX_LEVEL) - 1];
            PostChain chain = mc.getShaderManager().getPostChain(chainId, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) {
                chain.process(mc.getMainRenderTarget(), RESOURCE_POOL);
            }
        } finally {
            // In a finally, and therefore on the early-exit paths too. CrossFrameResourcePool.release() only
            // pushes an entry back onto the free list; endFrame() is the ONLY thing that decrements
            // framesToLive, closes the target and drops it, and there is no autonomous expiry. Skipping it on
            // the not-sick path is what kept ~33 MB of VRAM (1080p, colour + depth, x2) alive until the
            // process exited — including after returning to the menu (audit #10).
            RESOURCE_POOL.endFrame();
        }
    }
```

- [ ] **Step 2: Close the pool when the world goes away**

`endFrame()` drains what is *matched*; a target allocated for a previous window size is never re-acquired and so never drains. Vanilla handles that with `clear()` in `resize()`. The mod has no resize hook, so close on world unload — which also covers the return-to-menu case the audit calls out.

Add to `ContaminationScreenOverlay`:

```java
    /** Release every pooled render target outright. Called on world unload: entries allocated for a previous
     *  window size are never re-acquired by a later frame (the RenderTargetDescriptor differs), so endFrame()
     *  alone never reaches them — vanilla neutralises the same vector with clear() inside resize(). */
    private static void releasePool() {
        RESOURCE_POOL.close();
    }
```

and register it in `register()`:

```java
    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ID,
                (GuiGraphics g, net.minecraft.client.DeltaTracker tick) -> render());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> releasePool());
    }
```

Confirm that Fabric API entrypoint is on the classpath before relying on it:

```bash
cd mod && grep -rn "ClientPlayConnectionEvents\|ClientLifecycleEvents" src/main/java/ | head
```

If neither is already used in this project, verify the module is present in the `fabric_version` bundle (`fabric-lifecycle-events-v1`) — it is part of the standard Fabric API, so it should be. If `close()` turns out not to exist on `CrossFrameResourcePool` in 1.21.11, check the actual API surface before improvising:

```bash
# javap is not on PATH on this machine — call it out of the JDK directly.
~/.jdks/jdk-21.0.12+8/bin/javap \
  -classpath ~/.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar \
  com.mojang.blaze3d.resource.CrossFrameResourcePool
```

If `close()` is absent, the fallback is `RESOURCE_POOL.clear()` — the method vanilla's `GameRenderer` uses in `resize()`, which is the exact vector being closed here. Use whichever the disassembly shows; do not invent a third.

- [ ] **Step 3: Build**

```bash
cd mod && ./gradlew test build
```

Expected: `BUILD SUCCESSFUL`, 51 tests, 0 failures.

- [ ] **Step 4: In-game verification**

No headless test can reach this — it needs a GL context.

```bash
cd mod && ./gradlew runClient
```

1. Create a world, `/lethaldev contaminate @s`, `/lethaldev symptoms @s`, `/lethaldev level @s 5`.
2. Confirm the radial blur renders and tightens with level — **the fix must not break the effect**, which is the main regression risk of putting `endFrame()` in a `finally`.
3. `/lethaldev cure @s`. The blur stops.
4. **Resize the window several times** while symptomatic — this is the accumulation vector. Watch VRAM with `nvidia-smi` / `radeontop` / `intel_gpu_top`: it should plateau, not step up with each new size.
5. Return to the main menu. VRAM should drop back — that is `releasePool()` firing.
6. Re-enter a world and confirm the blur still works: a pool closed too eagerly would render nothing or crash on the next frame.

- [ ] **Step 5: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/client/ContaminationScreenOverlay.java
git commit -m "fix: drain and close the overlay's render-target pool

Nothing ever called endFrame(), clear() or close() on RESOURCE_POOL.
CrossFrameResourcePool.release() only pushes an entry back onto the free list;
endFrame() is the only thing that decrements framesToLive, closes the target and
drops it, and there is no autonomous expiry — the 3 passed to the constructor is
a retain count, not a purge delay. Vanilla's GameRenderer calls endFrame() every
frame, clear() on resize and close() on shutdown; this called none of them.

~33 MB of VRAM at 1080p held until the process exited, including after returning
to the menu, plus one orphaned pair per distinct window size that endFrame()
could never re-match.

endFrame() now runs in a finally so the not-sick early exits drain the pool too —
without that the pool never drained once the player was cured. Audit #10."
```

---

## Out of scope — deliberately not planned

`AUDIT.md`'s "Qualité de code" table lists 13 ⚪ BAS items and three cases the audit explicitly flagged as **needing a decision of intent before any refactoring**. None are in this plan.

**The three intent decisions** — these are questions for the author, not tasks:
- `Ascent` / `PillarClimb`: collapsing the single-subclass hierarchy is correct only if no second ascent strategy is planned. If one is, the fix is to correct the javadoc instead.
- `TargetSelector.isAudible` vs `SoundEventBus.tickPlayers`: three real behavioural differences (3D vs horizontal distance; `acting`/`hurt` absent on the player side). If those are deliberate — a player's server-side position being unreliable is a good reason — the correct fix is **editorial**, and merging them would be a regression.
- `sunShelterEnabled`: declared, bounded, translated, never read. The audit notes the author already documented the anomaly on the wiki, so it is listed for completeness of the dead-code axis, not as a defect to fix.

**The 13 BAS items** are all genuine but low-value, and — per `AUDIT.md`'s own "Vérification inégale" caveat — the code-quality lens was added mid-audit and was **not** put through the adversarial refutation pass that the other findings got. Only #5 and #12 were independently verified, and both are planned above (tasks 3 and 4). The rest should be re-verified before anyone spends time on them.

The audit's other open items are likewise not planned, because they are not code changes: run `osv-scanner` against `jocl:2.0.5` on a machine that has one (no vulnerability scanner is installed here, so no CVE claim was made either way), verify the production HTTP headers, and check `gradle-wrapper.jar` against the official checksums.

---

## Execution order and dependencies

```
Task 1 (build) ──> everything else
Task 2 (dedup)      independent
Task 3 (roll) ──> Task 4 (five sites)
Task 5 (snapshot) ──> Task 6 (disable purge)      [same guard block]
Task 7 (NoAI)       independent
Task 8 (mixins)     independent
Task 9 (blocks)     independent
Task 10 (shade)     independent
Task 11 (flowfield) independent
Task 12 (overlay)   independent
```

Only two ordering constraints exist beyond task 1: **3 before 4**, and **5 before 6** (both edit the same guard block in `ContaminationTick.tick`). Everything else can be reordered or dropped freely — which is the point of one commit per finding.

**Expected final state:** 51 tests, 0 failures; `./gradlew clean build` succeeds with no property override and zero warnings; the player jar still excludes the `dev` source set.
