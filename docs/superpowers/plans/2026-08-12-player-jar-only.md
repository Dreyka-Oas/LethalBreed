# Player-Jar-Only Build — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `./gradlew build` emits exactly one artifact — the player jar — containing zero developer tooling, zero dev-config surface and zero developer-readable payload, while every dev harness and dev command keeps working unchanged under `runClient` / `runServer`.

**Architecture:** `src/dev` is already excluded from `remapJar` by construction, so it is the destination, not the enemy. Dev code that `src/main` never calls is **moved** into `src/dev`. Dev instrumentation that `src/main` *does* call from hot paths cannot move, so `src/main` keeps **one** seam class — `DevProbe`, a static holder whose `sink` is `null` in the player jar — and `DevBootstrap` assigns the real implementation at runtime through the reflective hook that already exists (`LethalBreedMod#installDevHooks`). Dev config options move to a dev-side holder registered into the schema at runtime, which makes the "Dev / Debug" GUI tab vanish from the player jar with no client-side change.

**Tech Stack:** Java 21, Gradle 9.5.1 (Kotlin DSL), fabric-loom 1.17.12, Fabric Loader 0.19.3, Fabric API 0.141.4+1.21.11, Minecraft 1.21.11, JUnit Jupiter 5.10.2, JOCL 2.0.5 (JiJ).

## Global Constraints

- Module root for every relative path in this plan is `mod/`. All `./gradlew` commands run from `mod/`.
- Java 21 toolchain; `options.release = 21`. Do not change either.
- Never touch `src/main/resources/lethalbreed.mixins.json`. It is `"required": true` with `"injectors": { "defaultRequire": 1 }` — a mixin class that goes missing is a hard crash at load, and a mixin can never live in `src/dev`.
- Never move or narrow `ConfigBoundsTable`. `ConfigBoundsTest` reaches its package-private `get` only by sharing package `com.dreykaoas.lethalbreed.config`.
- `src/main/resources/assets/lethalbreed/shaders/post/radial_blur.fsh` is a **player-facing asset** driving the contamination screen effect. Do not move it, do not strip it.
- `AiConflictDetector`, `ComputeCalibration`, `LethalBreedClientConfig.sodiumPresent`, `ContaminationConfig.contamDevTimeScale`, `PackManager.all()`, `Snapshot.walk()`, `ContaminationManager.clearPlague`/`plagueLevel` are **player behaviour**. They look like dev code and are not. Do not remove them.
- `ContaminationSymptoms.tickLatent`, `applyLatentSlow`, `removeLatentSlow`, `applyIcon` are player plague behaviour. Only `DEV` and `showDevIndicator` leave.
- Commit after every task. Never squash two tasks into one commit.
- Every `git mv` in this plan is a real `git mv`, not delete+create, so history follows the file.

---

## File Structure

**New files (2):**

| Path | Responsibility |
|---|---|
| `src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java` | The one seam. Stage/counter/trace ids, the `Sink` interface, the `sink` field, the two JIT-foldable gates. Nothing else. |
| `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/DevSink.java` | The dev-side implementation: stage accumulators, the `Stage` labels, the formatted drain, the counter maps, the trace logging, the perf recap. |

**Moved files (9):** `StageProfiler`, `PerfRecap`, `ClimbDebug`, `DevTestConfig`, `LethalSpecialCommand`, `LethalPhaseCommand`, `FlowFieldChecks`, `ConfigOverride`, `InstalledMods` — all `src/main` → `src/dev`.

**Deleted files (2):** `PresentationalMixinNotes`, `BoundsSplitNote` — javadoc anchors with zero references.

**Modified in `src/main` (24):** `build.gradle.kts`, `TickScheduler`, `LodBucketPass`, `TargetSelector`, `FlowFieldSnapshotBuilder`, `ZombieBrain`, `PillarClimb`, `PackPass`, `ContaminationSymptoms`, `ContaminationTick`, `ContaminationState`, `ContaminationLifecycle`, `ContaminationManager`, `ShelterFinder`, `ZombieMood`, `PhaseManager`, `ConfigSchema`, `ConfigAccess`, `ConfigBounds`/`ConfigBoundsTable`, `ConfigCategory`, `SchedulerConfig`, `PackConfig`, `CommandInit`, `LethalConfigCommand`, plus both lang files.

---

### Task 1: One jar out of `build/libs`

Removes the sources jar and the developer-jar flavour. Reversible; nothing in `src/` changes.

**Files:**
- Modify: `build.gradle.kts:1`, `build.gradle.kts:60-65`, `build.gradle.kts:91-106`
- Delete: `scripts/build-dev.bat`
- Modify: `scripts/build-player.bat:21-22`
- Modify: `README.md:26` (module README)

**Interfaces:**
- Consumes: nothing.
- Produces: a build whose only `build/libs` output is `lethalbreed-1.0.0.jar`. Task 2 configures the same file further.

- [ ] **Step 1: Record the baseline**

```bash
cd mod
./gradlew clean build
ls -la build/libs build/devlibs
unzip -l build/libs/lethalbreed-1.0.0.jar > /tmp/lb-before.txt
wc -l /tmp/lb-before.txt
```

Expected: `build/libs` holds **two** jars — `lethalbreed-1.0.0.jar` and `lethalbreed-1.0.0-sources.jar`. Keep `/tmp/lb-before.txt`; every later task diffs against it.

- [ ] **Step 2: Delete `withSourcesJar()`**

`build.gradle.kts:60-65` currently reads:

```kotlin
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}
```

Delete line 64 only:

```kotlin
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

Do **not** replace it with `tasks.named("remapSourcesJar") { enabled = false }` — leaving `withSourcesJar()` in place also keeps the `sourcesElements` publication variant alive. With the call gone, Loom disables `RemapSourcesJarTask` itself.

- [ ] **Step 3: Delete the developer-jar flavour**

Delete `build.gradle.kts:91-106` entirely — the comment block and both task registrations:

```kotlin
// ---- Two build flavours ----
// The default `remapJar` (→ `build`) packages ONLY the main source set: the shipped PLAYER jar, with zero
// dev/test code. The `remapDevJar` task below packages main + the dev source set (harnesses + /lethaldev &
// /lethalspawn commands): the DEVELOPER jar. Use build-player.bat / build-dev.bat to produce each.
val devJar = tasks.register<Jar>("devJar") {
    archiveClassifier.set("dev-unmapped")
    from(sourceSets.main.get().output)
    from(devSourceSet.output)
}

val remapDevJar = tasks.register<RemapJarTask>("remapDevJar") {
    dependsOn(devJar)
    inputFile.set(devJar.flatMap { it.archiveFile })
    archiveClassifier.set("dev")
    addNestedDependencies.set(true)
}
```

Then delete `build.gradle.kts:1`, now unused:

```kotlin
import net.fabricmc.loom.task.RemapJarTask
```

- [ ] **Step 4: Delete `scripts/build-dev.bat` and de-filter `scripts/build-player.bat`**

```bash
git rm mod/scripts/build-dev.bat
```

`scripts/build-player.bat:21-22` currently reads:

```bat
echo [build-player] Done. Player jar(s) in build\libs\ (exclude the *-dev.jar / *-sources.jar):
dir /b build\libs\*.jar 2>nul | findstr /v /i "dev sources"
```

Replace with:

```bat
echo [build-player] Done. Player jar in build\libs\:
dir /b build\libs\*.jar 2>nul
```

The filter must go in this same commit: it was hiding the sources jar, and `findstr` matches the substring anywhere, so any future artifact with "dev" in its name would vanish silently from the listing.

- [ ] **Step 5: Rewrite the comments this invalidated**

`build.gradle.kts:183-186` currently ends with:

```kotlin
// The shipped player jar is Loom's plain remapJar (no obfuscation): the source is MIT and lives in a
// private repo, so there is nothing to hide. `gradlew build` / build-player.bat produce it directly.
```

Replace with:

```kotlin
// `gradlew build` produces exactly one artifact: build/libs/lethalbreed-<version>.jar, the player jar.
// build/devlibs holds Loom's unmapped intermediate — an implementation detail of remapJar, never shipped.
// No sources jar, no javadoc jar, no dev flavour: dev tooling lives in src/dev and runs under runClient/
// runServer only. Source is MIT in a private repo, so the jar is unobfuscated by choice.
```

In `README.md`, line 26 reads `./gradlew build   # player jar -> build/libs/` — already true after this task; leave it.

- [ ] **Step 6: Verify exactly one jar**

```bash
cd mod
./gradlew clean build
ls -1 build/libs/
./gradlew tasks --all | grep -ci devjar
```

Expected: `ls` prints exactly one line, `lethalbreed-1.0.0.jar`. The grep prints `0`.

- [ ] **Step 7: Verify the jar contents did not change**

```bash
cd mod
unzip -l build/libs/lethalbreed-1.0.0.jar > /tmp/lb-t1.txt
diff /tmp/lb-before.txt /tmp/lb-t1.txt
```

Expected: no output. This task removes a *sibling* artifact; the player jar itself is byte-identical in structure.

- [ ] **Step 8: Commit**

```bash
git add mod/build.gradle.kts mod/scripts/build-player.bat
git rm --cached mod/scripts/build-dev.bat 2>/dev/null; true
git commit -m "build: emit only the player jar (drop sources jar and dev flavour)"
```

---

### Task 2: Strip debug attributes and kernel comments

**Files:**
- Modify: `build.gradle.kts:67-70` (JavaCompile block), `build.gradle.kts:79-89` (processResources)

**Interfaces:**
- Consumes: Task 1's single-jar build.
- Produces: `compileJava` emitting `-g:source,lines`; `kernels/bellman_ford.clx` stripped of comments. No Java API surface.

- [ ] **Step 1: Measure the current debug payload**

```bash
cd mod
grep -ral LocalVariableTable --include="*.class" build/classes/java/main | wc -l
grep -ral LineNumberTable   --include="*.class" build/classes/java/main | wc -l
```

Expected: `233` and `233`. These are the before values.

- [ ] **Step 2: Scope `-g:source,lines` to `compileJava`**

`build.gradle.kts:67-70` currently reads:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
```

Append a `compileJava`-only block immediately after it:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Only the MAIN source set ships, so only it is stripped: dev and test keep full -g for IDE debugging.
// Gradle's default is `debug = true` with a null debugLevel, which hands javac a bare `-g`
// (= source,lines,vars). Dropping `vars` removes LocalVariableTable/LocalVariableTypeTable — the local
// variable names that make a decompiled jar read like the original source. SourceFile and LineNumberTable
// are kept on purpose: without them every player crash report says "(Unknown Source)".
tasks.named<JavaCompile>("compileJava") {
    options.debugOptions.debugLevel = "source,lines"
}
```

- [ ] **Step 3: Verify the attributes are gone**

```bash
cd mod
./gradlew clean compileJava
grep -ral LocalVariableTable --include="*.class" build/classes/java/main | wc -l
grep -ral LineNumberTable   --include="*.class" build/classes/java/main | wc -l
```

Expected: `0` and `233`. If `LineNumberTable` also reports 0, `debugLevel` was set to `none` — fix it before continuing.

- [ ] **Step 4: Strip comments from the packaged kernel**

`build.gradle.kts:79-89` currently reads:

```kotlin
tasks.processResources {
    // Exclude the raw .cl from the output; the .clx copy is written by doLast below.
    exclude("kernels/*.cl")
    doLast {
        val src = file("src/main/resources/kernels/bellman_ford.cl").readBytes()
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        outDir.resolve("bellman_ford.clx").writeBytes(src)
        logger.lifecycle("[kernel] copied bellman_ford.cl -> .clx (${src.size} bytes)")
    }
}
```

Replace the `doLast` body:

```kotlin
tasks.processResources {
    // Exclude the raw .cl from the output; the .clx copy is written by doLast below.
    exclude("kernels/*.cl")
    doLast {
        // Truncate each line at its first `//` — the kernel has 23 comment-bearing lines and 10 of them are
        // TRAILING comments on live code (18-22, 27, 36, 45, 61, 68), so dropping whole lines would delete
        // working kernel code. There are no block comments and no `//` inside any string literal, so
        // first-`//` truncation is safe. readText/writeText, not readBytes: line 1 holds a U+2014 em dash.
        val raw = file("src/main/resources/kernels/bellman_ford.cl").readText(Charsets.UTF_8)
        val stripped = raw.lineSequence()
                .map { line -> val i = line.indexOf("//"); if (i >= 0) line.substring(0, i) else line }
                .map { it.trimEnd() }
                .filter { it.isNotEmpty() }
                .joinToString("\n", postfix = "\n")
        val outDir = destinationDir.resolve("kernels")
        outDir.mkdirs()
        outDir.resolve("bellman_ford.clx").writeText(stripped, Charsets.UTF_8)
        logger.lifecycle("[kernel] bellman_ford.cl -> .clx, comments stripped " +
                "(${raw.toByteArray(Charsets.UTF_8).size} -> ${stripped.toByteArray(Charsets.UTF_8).size} bytes)")
    }
}
```

- [ ] **Step 5: Verify the kernel is stripped and still compiles on the GPU**

```bash
cd mod
./gradlew clean build
unzip -p build/libs/lethalbreed-1.0.0.jar kernels/bellman_ford.clx | grep -c '//'
unzip -p build/libs/lethalbreed-1.0.0.jar kernels/bellman_ford.clx | grep -c 'IMPASSABLE'
```

Expected: `0` comment markers, and at least `1` hit for `IMPASSABLE` (the `#define` on source line 15 must survive).

**Then run the game — this is mandatory, not optional.** A broken kernel is invisible in play because `GpuFlowField` silently falls back to the CPU solver:

```bash
cd mod
./gradlew runServer
```

Expected: the boot log shows the GPU compute path initialising without an OpenCL build error. If you see a fallback-to-CPU message, the strip broke the kernel — revert step 4 and investigate before proceeding.

- [ ] **Step 6: Commit**

```bash
git add mod/build.gradle.kts
git commit -m "build: strip local-variable debug tables and kernel comments from the shipped jar"
```

---

### Task 3: Introduce the `DevProbe` seam

The seam is pure logic with no Minecraft types except one method parameter, so it is unit-testable. Write the test first.

**Files:**
- Create: `src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java`
- Test: `src/test/java/com/dreykaoas/lethalbreed/probe/DevProbeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces, relied on by Tasks 4, 5, 6 and by `src/dev`:
  - `public static boolean DevProbe.on()`
  - `public static boolean DevProbe.tracing(int channel)`
  - `public static DevProbe.Sink DevProbe.sink` (mutable static, `null` by default)
  - `public static int DevProbe.traceMask` (mutable static, `0` by default)
  - `public static void DevProbe.install(Sink sink, int traceMask)`
  - `public interface DevProbe.Sink` with `void stage(int stage, long nanos)`, `void count(int counter, int entityId)`, `void trace(int channel, String message)`, `void tickEnd(net.minecraft.server.MinecraftServer server, long tickCounter, long elapsedNanos)`
  - Stage ids `CLASSIFY=0, GRID=1, PACK=2, SUNBURN=3, MOOD=4, TICK=5, FLOWSNAP=6, SCAN=7, ORDER=8, LOS=9`, `STAGE_COUNT=10`
  - Counter ids `INFECT=0, DEATH=1, SHELTER_SCAN=2, DISTRESS=3, SHADE_SCAN=4`, `COUNTER_COUNT=5`
  - Trace channels `CLIMB=0, PACKS=1, CONTAM=2`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/dreykaoas/lethalbreed/probe/DevProbeTest.java`:

```java
package com.dreykaoas.lethalbreed.probe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam's whole contract is "costs nothing and does nothing unless a dev sink was installed".
 * These tests pin that: an uninstalled probe is off, every gate is false, and no id collides.
 */
class DevProbeTest {

    /** Records what it was told, so a test can assert the seam forwards faithfully. */
    private static final class Recorder implements DevProbe.Sink {
        final List<String> events = new ArrayList<>();

        @Override public void stage(int stage, long nanos) { events.add("stage:" + stage + ":" + nanos); }
        @Override public void count(int counter, int entityId) { events.add("count:" + counter + ":" + entityId); }
        @Override public void trace(int channel, String message) { events.add("trace:" + channel + ":" + message); }
        @Override public void tickEnd(net.minecraft.server.MinecraftServer server, long tick, long nanos) {
            events.add("tick:" + tick + ":" + nanos);
        }
    }

    @AfterEach
    void resetSeam() {
        DevProbe.uninstall();
    }

    @Test
    void aFreshProbeIsOffAndHasNoSink() {
        assertNull(DevProbe.sink, "a player jar must never have a sink");
        assertFalse(DevProbe.on(), "on() must be false with no sink");
    }

    @Test
    void everyTraceChannelIsOffByDefault() {
        assertEquals(0, DevProbe.traceMask);
        assertFalse(DevProbe.tracing(DevProbe.CLIMB));
        assertFalse(DevProbe.tracing(DevProbe.PACKS));
        assertFalse(DevProbe.tracing(DevProbe.CONTAM));
    }

    @Test
    void installTurnsTheProbeOnAndEnablesOnlyTheRequestedChannels() {
        Recorder r = new Recorder();
        DevProbe.install(r, 1 << DevProbe.PACKS);

        assertTrue(DevProbe.on());
        assertFalse(DevProbe.tracing(DevProbe.CLIMB), "CLIMB was not requested");
        assertTrue(DevProbe.tracing(DevProbe.PACKS));
        assertFalse(DevProbe.tracing(DevProbe.CONTAM), "CONTAM was not requested");
    }

    @Test
    void uninstallRestoresThePlayerJarState() {
        DevProbe.install(new Recorder(), 0b111);
        DevProbe.uninstall();

        assertNull(DevProbe.sink);
        assertFalse(DevProbe.on());
        assertEquals(0, DevProbe.traceMask);
    }

    @Test
    void theSinkReceivesExactlyWhatTheCallSitePassed() {
        Recorder r = new Recorder();
        DevProbe.install(r, 0b111);

        DevProbe.sink.stage(DevProbe.FLOWSNAP, 4200L);
        DevProbe.sink.count(DevProbe.SHADE_SCAN, 77);
        DevProbe.sink.trace(DevProbe.CLIMB, "PILLAR");

        assertEquals(List.of("stage:6:4200", "count:4:77", "trace:0:PILLAR"), r.events);
    }

    @Test
    void stageIdsAreDenseAndUniqueAcrossTheDeclaredCount() {
        int[] ids = { DevProbe.CLASSIFY, DevProbe.GRID, DevProbe.PACK, DevProbe.SUNBURN, DevProbe.MOOD,
                      DevProbe.TICK, DevProbe.FLOWSNAP, DevProbe.SCAN, DevProbe.ORDER, DevProbe.LOS };
        assertEquals(DevProbe.STAGE_COUNT, ids.length);
        boolean[] seen = new boolean[DevProbe.STAGE_COUNT];
        for (int id : ids) {
            assertFalse(seen[id], "duplicate stage id " + id);
            seen[id] = true;
        }
    }

    @Test
    void counterIdsAreDenseAndUniqueAcrossTheDeclaredCount() {
        int[] ids = { DevProbe.INFECT, DevProbe.DEATH, DevProbe.SHELTER_SCAN,
                      DevProbe.DISTRESS, DevProbe.SHADE_SCAN };
        assertEquals(DevProbe.COUNTER_COUNT, ids.length);
        boolean[] seen = new boolean[DevProbe.COUNTER_COUNT];
        for (int id : ids) {
            assertFalse(seen[id], "duplicate counter id " + id);
            seen[id] = true;
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
cd mod
./gradlew test --tests 'com.dreykaoas.lethalbreed.probe.DevProbeTest'
```

Expected: FAIL — compilation error, `package com.dreykaoas.lethalbreed.probe does not exist`.

- [ ] **Step 3: Write `DevProbe`**

Create `src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java`:

```java
package com.dreykaoas.lethalbreed.probe;

import net.minecraft.server.MinecraftServer;

/**
 * The single seam between shipped code and development instrumentation.
 *
 * <p>Per-stage timings, dev counters and debug traces all measure things that happen INSIDE {@code main},
 * so they cannot simply live in the {@code dev} source set — {@code main} has to call out. This class is
 * that call-out, and it is deliberately the only one: everything it forwards to lives in {@code dev}.
 *
 * <p><b>Cost in a player jar.</b> {@link #sink} is never assigned outside a development environment, so
 * {@link #on()} is a static load plus a null check that HotSpot folds away once profiling shows the field
 * is always null — and, crucially, the {@code System.nanoTime()} calls and message building at every call
 * site sit BEHIND that gate rather than in front of it. {@link #traceMask} works the same way for the
 * per-channel debug logs.
 *
 * <p>{@code DevBootstrap} assigns both fields via {@link #install} inside its existing reflective entry
 * point ({@code LethalBreedMod#installDevHooks}), so this seam inherits that method's dev-env gate for
 * free and adds no second reflective lookup.
 *
 * <p>Ids are plain {@code int} constants rather than an enum on purpose: an enum would ship its constants,
 * its {@code values()} array and its labels. The labels live in {@code dev}.
 */
public final class DevProbe {
    private DevProbe() {}

    // Stage ids. Order matches the order LodBucketPass executes them; SCAN/ORDER/LOS are sub-stages of
    // CLASSIFY and overlap it rather than adding to the total.
    public static final int CLASSIFY = 0;
    public static final int GRID = 1;
    public static final int PACK = 2;
    public static final int SUNBURN = 3;
    public static final int MOOD = 4;
    public static final int TICK = 5;
    public static final int FLOWSNAP = 6;
    public static final int SCAN = 7;
    public static final int ORDER = 8;
    public static final int LOS = 9;
    public static final int STAGE_COUNT = 10;

    // Counter ids. Pass the entity id for per-entity counters, or -1 for process-global ones.
    public static final int INFECT = 0;
    public static final int DEATH = 1;
    public static final int SHELTER_SCAN = 2;
    public static final int DISTRESS = 3;
    public static final int SHADE_SCAN = 4;
    public static final int COUNTER_COUNT = 5;

    // Trace channels, gated individually by traceMask so a call site pays nothing to build its message.
    public static final int CLIMB = 0;
    public static final int PACKS = 1;
    public static final int CONTAM = 2;

    /** Global counters use this instead of an entity id. */
    public static final int GLOBAL = -1;

    /** What {@code dev} implements. Never referenced by {@code main} except through {@link #sink}. */
    public interface Sink {
        /** One stage sample. {@code stage} is one of the stage ids above. */
        void stage(int stage, long nanos);

        /** One counter increment. {@code entityId} is {@link #GLOBAL} for process-wide counters. */
        void count(int counter, int entityId);

        /** One debug line on {@code channel}. Only called when {@link #tracing(int)} is true. */
        void trace(int channel, String message);

        /** End of a server tick: total elapsed nanos for the mod's whole tick. */
        void tickEnd(MinecraftServer server, long tickCounter, long elapsedNanos);
    }

    /** {@code null} on a player jar. Assigned once, at mod init, by {@code DevBootstrap}. */
    public static volatile Sink sink;

    /** Bitmask of enabled trace channels; {@code 0} on a player jar. */
    public static volatile int traceMask;

    /** Install the dev implementation. Called only from {@code DevBootstrap#install}. */
    public static void install(Sink newSink, int newTraceMask) {
        sink = newSink;
        traceMask = newTraceMask;
    }

    /** Restore the player-jar state. Exists for tests; nothing in production calls it. */
    public static void uninstall() {
        sink = null;
        traceMask = 0;
    }

    /** Whether any instrumentation is listening. Guards every timing and counting call site. */
    public static boolean on() {
        return sink != null;
    }

    /** Whether {@code channel} is being traced. Guards message construction, not just the call. */
    public static boolean tracing(int channel) {
        return (traceMask & (1 << channel)) != 0;
    }
}
```

- [ ] **Step 4: Run the test to green**

```bash
cd mod
./gradlew test --tests 'com.dreykaoas.lethalbreed.probe.DevProbeTest'
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/probe/DevProbeTest.java
git commit -m "probe: add the DevProbe seam between main and the dev source set"
```

---

### Task 4: Route the profilers through the seam and move them to `src/dev`

This is the largest task and it must land in one commit: `StageProfiler` and `PerfRecap` leaving `src/main` breaks four callers simultaneously, so the code does not compile in between.

**Files:**
- Move: `src/main/java/com/dreykaoas/lethalbreed/tick/StageProfiler.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/StageProfiler.java`
- Move: `src/main/java/com/dreykaoas/lethalbreed/tick/PerfRecap.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/PerfRecap.java`
- Create: `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/DevSink.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java:22-40`, `:42-73`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java:22-31`, `:37-46`, `:71-98`, `:111-122`, `:166-168`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java:5`, `:85-117`, `:198-216`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldSnapshotBuilder.java:63`, `:92-93`
- Modify: `src/dev/java/com/dreykaoas/lethalbreed/dev/DevBootstrap.java:44`

**Interfaces:**
- Consumes: every symbol listed under Task 3's *Produces*.
- Produces: `DevSink implements DevProbe.Sink`, constructed by `DevBootstrap`. No `src/main` API.

- [ ] **Step 1: Move the two classes into `src/dev` and repackage them**

```bash
cd mod
mkdir -p src/dev/java/com/dreykaoas/lethalbreed/dev/probe
git mv src/main/java/com/dreykaoas/lethalbreed/tick/StageProfiler.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/probe/StageProfiler.java
git mv src/main/java/com/dreykaoas/lethalbreed/tick/PerfRecap.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/probe/PerfRecap.java
```

In both moved files change line 1 from `package com.dreykaoas.lethalbreed.tick;` to:

```java
package com.dreykaoas.lethalbreed.dev.probe;
```

In `StageProfiler.java`, delete the `DEV` field and rewrite `enabled()`. It currently reads:

```java
    private static final Stage[] STAGES = Stage.values();
    private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();
```

becomes:

```java
    private static final Stage[] STAGES = Stage.values();
```

and:

```java
    /** Whether measurement is on right now. Checked once per activation, not per stage. */
    public static boolean enabled() {
        return DEV && SchedulerConfig.debugLogInterval > 0;
    }
```

becomes (the class now lives in `dev`, so the environment is implied; the interval comes from the dev-side holder added in Task 6 — until then, read it from `SchedulerConfig`):

```java
    /** Whether measurement is on right now. Checked once per activation, not per stage. */
    public static boolean enabled() {
        return SchedulerConfig.debugLogInterval > 0;
    }
```

Delete the now-unused `import net.fabricmc.loader.api.FabricLoader;` (line 4).

Also delete the static side-channel — `DevSink` owns the instance now. Remove:

```java
    private static volatile StageProfiler active;

    /** Record a sub-stage sample from outside the tick package. No-op when profiling is off. */
    public static void sub(Stage s, long elapsedNanos) {
        StageProfiler p = active;
        if (p != null) {
            p.add(s, elapsedNanos);
        }
    }
```

and the constructor body's `active = this;`, leaving:

```java
    public StageProfiler() {
    }
```

- [ ] **Step 2: Write `DevSink`**

Create `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/DevSink.java`:

```java
package com.dreykaoas.lethalbreed.dev.probe;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The dev half of {@link DevProbe}. Owns everything the player jar must not contain: the stage
 * accumulators and their labels, the dev counters, the formatted perf recap and the trace prefixes.
 *
 * <p>Installed once by {@code DevBootstrap#install}. A shipped jar never loads this class, so nothing
 * here needs an environment check.
 */
public final class DevSink implements DevProbe.Sink {

    private final StageProfiler profiler = new StageProfiler();
    private final PerfRecap recap;

    /** Process-global counters, indexed by DevProbe counter id. Written from the server thread and
     *  read from harnesses, so atomic rather than plain long. */
    private final AtomicLongArray counters = new AtomicLongArray(DevProbe.COUNTER_COUNT);

    public DevSink(PerfRecap recap) {
        this.recap = recap;
    }

    /** The profiler the recap drains. Handed to PerfRecap at construction. */
    public StageProfiler profiler() {
        return profiler;
    }

    /** Total for one counter since process start. Read by the headless harnesses. */
    public long counter(int counter) {
        return counters.get(counter);
    }

    /** Reset every counter — harnesses call this between scenarios. */
    public void resetCounters() {
        for (int i = 0; i < DevProbe.COUNTER_COUNT; i++) {
            counters.set(i, 0L);
        }
    }

    @Override
    public void stage(int stage, long nanos) {
        profiler.add(StageProfiler.Stage.values()[stage], nanos);
    }

    @Override
    public void count(int counter, int entityId) {
        counters.incrementAndGet(counter);
    }

    @Override
    public void trace(int channel, String message) {
        LethalBreed.LOGGER.info("{} {}", prefix(channel), message);
    }

    @Override
    public void tickEnd(MinecraftServer server, long tickCounter, long elapsedNanos) {
        recap.accumulate(elapsedNanos);
        recap.maybeLog(server, tickCounter);
    }

    private static String prefix(int channel) {
        return switch (channel) {
            case DevProbe.CLIMB -> "[ClimbDbg]";
            case DevProbe.PACKS -> "[PackDbg]";
            case DevProbe.CONTAM -> "[ContamDbg]";
            default -> "[Dbg]";
        };
    }
}
```

- [ ] **Step 3: Gate the two per-tick `nanoTime()` calls in `TickScheduler`**

`TickScheduler.java:22-40` currently reads:

```java
public final class TickScheduler {
    private final ZombieRegistry registry;
    private final LodBucketPass bucketPass;
    private final EveryTickPass everyTickPass;
    private final WorldMaintenance world;
    private final PerfRecap perfRecap;
    private final StageProfiler profiler = new StageProfiler();

    private long tickCounter = 0L;
    private final Set<SmartZombie> climbers = new HashSet<>(); // zombies mid jump-pillar, ticked every tick
    private final Set<SmartZombie> swimmers = new HashSet<>(); // zombies in water, ticked every tick (rise/dive)

    public TickScheduler(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.bucketPass = new LodBucketPass(registry, dimensions, profiler);
        this.everyTickPass = new EveryTickPass(dimensions);
        this.world = new WorldMaintenance(dimensions);
        this.perfRecap = new PerfRecap(registry, dimensions, profiler);
    }
```

becomes:

```java
public final class TickScheduler {
    private final ZombieRegistry registry;
    private final LodBucketPass bucketPass;
    private final EveryTickPass everyTickPass;
    private final WorldMaintenance world;

    private long tickCounter = 0L;
    private final Set<SmartZombie> climbers = new HashSet<>(); // zombies mid jump-pillar, ticked every tick
    private final Set<SmartZombie> swimmers = new HashSet<>(); // zombies in water, ticked every tick (rise/dive)

    public TickScheduler(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.bucketPass = new LodBucketPass(registry, dimensions);
        this.everyTickPass = new EveryTickPass(dimensions);
        this.world = new WorldMaintenance(dimensions);
    }
```

Delete the now-unused imports of `PerfRecap` and `StageProfiler` if the file has them, and add:

```java
import com.dreykaoas.lethalbreed.probe.DevProbe;
```

`TickScheduler.java:42-73` — change line 43 and lines 70-71 only. Before:

```java
    public void onServerTick(MinecraftServer server) {
        long t0 = System.nanoTime();
```

After:

```java
    public void onServerTick(MinecraftServer server) {
        boolean probing = DevProbe.on();
        long t0 = probing ? System.nanoTime() : 0L;
```

Before:

```java
        world.drainBlockOps(server, tickCounter);
        perfRecap.accumulate(System.nanoTime() - t0);
        perfRecap.maybeLog(server, tickCounter);
        tickCounter++;
```

After:

```java
        world.drainBlockOps(server, tickCounter);
        if (probing) {
            DevProbe.sink.tickEnd(server, tickCounter, System.nanoTime() - t0);
        }
        tickCounter++;
```

- [ ] **Step 4: Drop the profiler plumbing from `LodBucketPass`**

`LodBucketPass.java:22-31` — delete the field and the constructor parameter:

```java
final class LodBucketPass {
    private final ZombieRegistry registry;
    private final DimensionManager dimensions;

    LodBucketPass(ZombieRegistry registry, DimensionManager dimensions) {
        this.registry = registry;
        this.dimensions = dimensions;
    }
```

`LodBucketPass.java:37-46` — the helper loses its profiler parameter and its enum type:

```java
    /** Record one profiling checkpoint and return the new "last timestamp", or {@code t} unchanged when
     *  profiling is off. No allocation — safe to call every activation of this hot per-zombie loop. */
    private static long mark(int stage, boolean prof, long t) {
        if (!prof) {
            return t;
        }
        long n = System.nanoTime();
        DevProbe.sink.stage(stage, n - t);
        return n;
    }
```

`LodBucketPass.java:79, 85, 89, 92, 96` — rewrite the five call sites, keeping line 96 a bare statement (its return value is discarded today and javac warns otherwise):

```java
        t = mark(DevProbe.CLASSIFY, prof, t);
```
```java
        t = mark(DevProbe.GRID, prof, t);
```
```java
        t = mark(DevProbe.PACK, prof, t);
```
```java
        t = mark(DevProbe.SUNBURN, prof, t);
```
```java
        mark(DevProbe.MOOD, prof, t);
```

`LodBucketPass.java:115`:

```java
        mark(DevProbe.TICK, prof, tt);
```

`LodBucketPass.java:166-168` — hoist the gate out of the per-zombie loop while you are here. It currently sits inside the `for (SmartZombie sz : registry.all())` body at line 168; move it above line 136:

```java
        long round = frozenRound++;
        // Per-stage timing: one static read per TICK when disabled (hoisted out of the loop — it used to be
        // re-evaluated per zombie). A shipped jar has no sink, so this folds to a constant false.
        boolean prof = DevProbe.on();
        for (SmartZombie sz : registry.all()) {
```

and delete the old lines 166-168:

```java
            // Per-stage timing: one branch per activation when disabled (a dev-only static), so a shipped
            // jar pays nothing. See StageProfiler.
            boolean prof = StageProfiler.enabled();
```

Add `import com.dreykaoas.lethalbreed.probe.DevProbe;` and delete any `StageProfiler` import.

- [ ] **Step 5: Rewrite the four `TargetSelector` blocks**

`TargetSelector.java:5` — replace:

```java
import com.dreykaoas.lethalbreed.tick.StageProfiler;
```

with:

```java
import com.dreykaoas.lethalbreed.probe.DevProbe;
```

This also removes the only reason the `util` package depended on `tick`.

`TargetSelector.java:86-116` — three guarded blocks, mechanical substitution:

```java
        boolean prof = DevProbe.on();
        long t0 = prof ? System.nanoTime() : 0L;
        List<LivingEntity> candidates = collectCandidates(level, self, radius, index);
        if (prof) {
            DevProbe.sink.stage(DevProbe.SCAN, System.nanoTime() - t0);
        }
```
```java
            long tl = prof ? System.nanoTime() : 0L;
            boolean seen = !TargetingConfig.requireLineOfSight || canSee(level, self, only);
            if (prof) {
                DevProbe.sink.stage(DevProbe.LOS, System.nanoTime() - tl);
            }
            return seen ? only : null;
```
```java
        long tOrder = prof ? System.nanoTime() : 0L;
        double[] distSq = shuffleAndOrder(candidates, self);
        if (prof) {
            DevProbe.sink.stage(DevProbe.ORDER, System.nanoTime() - tOrder);
        }
```

`TargetSelector.java:212-214` — inside the existing `finally`:

```java
        } finally {
            if (prof) {
                DevProbe.sink.stage(DevProbe.LOS, System.nanoTime() - tLos);
            }
        }
```

Keep the `boolean prof` parameter on `nearestVisible` and the `try/finally`: the timer must still be recorded on the early return at line 207.

- [ ] **Step 6: Fix the unguarded FLOWSNAP path — this is a real bug, not a cleanup**

`FlowFieldSnapshotBuilder.java:63` is unconditional today, and `:92-93` calls `StageProfiler.sub` whose `active` static is never null, so a player's server pays two `nanoTime()` calls per flow-field snapshot for nanos that are never drained.

Line 63, before:

```java
        long t0 = System.nanoTime();
```

after:

```java
        boolean prof = DevProbe.on();
        long t0 = prof ? System.nanoTime() : 0L;
```

Lines 92-93, before:

```java
        StageProfiler.sub(StageProfiler.Stage.FLOWSNAP, System.nanoTime() - t0);
```

after:

```java
        if (prof) {
            DevProbe.sink.stage(DevProbe.FLOWSNAP, System.nanoTime() - t0);
        }
```

Swap the `StageProfiler` import for `import com.dreykaoas.lethalbreed.probe.DevProbe;`.

- [ ] **Step 7: Install the sink from `DevBootstrap`**

`DevBootstrap.install()` begins at line 44 with `DevTestSelector.apply();`. Insert the seam installation as the **first** statement, before it:

```java
    public static void install() {
        // FIRST of all: hand main its instrumentation. Everything below (and every harness) reads through
        // the sink, so it must exist before any dev flag is applied or any tick listener is registered.
        PerfRecap recap = new PerfRecap(GameState.REGISTRY, GameState.DIMENSIONS, null);
        DevSink devSink = new DevSink(recap);
        recap.bindProfiler(devSink.profiler());
        DevProbe.install(devSink, (1 << DevProbe.CLIMB) | (1 << DevProbe.PACKS) | (1 << DevProbe.CONTAM));

        // FIRST, before anything reads a dev flag: let LB_DEV_TEST / -Dlethalbreed.devTest force exactly one
        // suite on and every other one off. Every gate below (and every SERVER_STARTED listener registered
        // here) therefore sees the selected configuration, not whatever the config file was left holding.
        DevTestSelector.apply();
```

`PerfRecap` currently takes the profiler in its constructor; add a setter so the two can be wired after construction. In `PerfRecap.java`, change the profiler field from `final` and add:

```java
    /** Late-bound because DevSink owns the profiler and is itself constructed with this recap. */
    public void bindProfiler(StageProfiler profiler) {
        this.profiler = profiler;
    }
```

Add the imports `com.dreykaoas.lethalbreed.probe.DevProbe`, `com.dreykaoas.lethalbreed.dev.probe.DevSink`, `com.dreykaoas.lethalbreed.dev.probe.PerfRecap` and `com.dreykaoas.lethalbreed.GameState` to `DevBootstrap`.

Also extend the class javadoc at `DevBootstrap.java:25-35`, which currently says the dev set holds "headless test harnesses + the `/lethalspawn` load-test command" — it now also owns all instrumentation.

- [ ] **Step 8: Compile and run the full suite**

```bash
cd mod
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. If `LodBucketPass.java:96` warns about an unused assignment, it was written as `t = mark(...)` — it must be a bare statement.

- [ ] **Step 9: Verify the profiler left the jar and dev still works**

```bash
cd mod
unzip -l build/libs/lethalbreed-1.0.0.jar | grep -Ec 'StageProfiler|PerfRecap'
unzip -p build/libs/lethalbreed-1.0.0.jar com/dreykaoas/lethalbreed/tick/TickScheduler.class | strings | grep -c 'PERF'
```

Expected: `0` and `0`.

```bash
cd mod
./gradlew runServer
```

Expected: the server boots. Set `debugLogInterval` to `100` in `run/server/config/oas/lethalbreed.json`, restart, and confirm a `[LethalBreed][PERF] … | stages: …` line appears every 5 seconds — that proves the seam is wired end to end.

- [ ] **Step 10: Commit**

```bash
git add -A mod/src mod/build.gradle.kts
git commit -m "probe: move StageProfiler and PerfRecap to src/dev behind the DevProbe seam"
```

---

### Task 5: Route the dev counters through the seam

**Files:**
- Modify: `src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationState.java` (delete `INFECT_COUNT`, `DEATH_COUNT`)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationLifecycle.java` (both increment sites)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/effect/ContaminationManager.java` (delete the re-exports, `forceSymptomatic`, `forceLevel`)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/mood/sleep/ShelterFinder.java` (delete `SCAN_COUNT`)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/ZombieMood.java` (delete `DISTRESS_COUNT`, the `shadeScans` field and its accessor)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/phase/PhaseManager.java` (delete `logPhases`, `LOG_PREVIEW_PHASES`)
- Modify: the `src/dev` harnesses that read these — `MechPhaseArena`, `ShadeAreaA`

**Interfaces:**
- Consumes: `DevProbe.on()`, `DevProbe.sink.count(int, int)`, `DevProbe.GLOBAL`, and counter ids `INFECT`, `DEATH`, `SHELTER_SCAN`, `DISTRESS`, `SHADE_SCAN`.
- Produces: `DevSink.counter(int)` and `DevSink.resetCounters()` — already written in Task 4 — as the harnesses' new read path.

- [ ] **Step 1: Replace each increment with a gated seam call**

The pattern is identical at all five sites. In `ContaminationLifecycle`, the `INFECT_COUNT` increment becomes:

```java
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.INFECT, DevProbe.GLOBAL);
        }
```

and the `DEATH_COUNT` increment becomes:

```java
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.DEATH, DevProbe.GLOBAL);
        }
```

In `ShelterFinder`, the `SCAN_COUNT` increment — today an unconditional atomic CAS on **every** `findShade` call — becomes:

```java
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.SHELTER_SCAN, DevProbe.GLOBAL);
        }
```

In `ZombieMood`, the `DISTRESS_COUNT` increment (line 201):

```java
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.DISTRESS, DevProbe.GLOBAL);
        }
```

and the per-zombie `shadeScans` increment (line 369) — this one carries the entity id, which is why the seam takes one:

```java
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.SHADE_SCAN, entity().getId());
        }
```

Add `import com.dreykaoas.lethalbreed.probe.DevProbe;` to each of the four files.

- [ ] **Step 2: Delete the fields, the accessor and the re-exports**

Delete, with their javadoc: `ContaminationState.INFECT_COUNT`, `ContaminationState.DEATH_COUNT`, the matching re-export fields in `ContaminationManager` (and the class-javadoc paragraph that documents them), `ShelterFinder.SCAN_COUNT`, `ZombieMood.DISTRESS_COUNT`, the `ZombieMood.shadeScans` field (line 69) **and** its accessor (lines 346-348).

Delete `ContaminationManager.forceSymptomatic` and `ContaminationManager.forceLevel` together with the `ContaminationLifecycle` methods they delegate to — zero callers in `src/main`.

Delete `PhaseManager.logPhases()` and the `LOG_PREVIEW_PHASES` constant. Keep everything else in `PhaseManager`, including `setPhase` (Task 8 moves its only shipped caller to `src/dev`, which is what keeps it public).

- [ ] **Step 3: Teach the harnesses to read from `DevSink`**

`shadeScans` needs a per-entity total, and `DevSink.counters` is global. Extend `DevSink` with a per-entity map for `SHADE_SCAN`:

```java
    /** Per-entity tallies for counters that carry an entity id. Dev-only, so a plain map under the
     *  server thread is fine; harnesses read it between ticks. */
    private final java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.atomic.AtomicLong>
            perEntity = new java.util.concurrent.ConcurrentHashMap<>();

    /** Tally for one entity on one counter. */
    public long counter(int counter, int entityId) {
        return perEntity.getOrDefault(key(counter, entityId), ZERO).get();
    }

    private static final java.util.concurrent.atomic.AtomicLong ZERO =
            new java.util.concurrent.atomic.AtomicLong(0L);

    private static Integer key(int counter, int entityId) {
        return counter * 1_000_000 + entityId;
    }
```

and make `count` route on the id:

```java
    @Override
    public void count(int counter, int entityId) {
        counters.incrementAndGet(counter);
        if (entityId != DevProbe.GLOBAL) {
            perEntity.computeIfAbsent(key(counter, entityId),
                    k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        }
    }
```

Then update the two readers. `MechPhaseArena` reads `ContaminationManager.INFECT_COUNT` / `DEATH_COUNT`; both become `sink.counter(DevProbe.INFECT)` / `sink.counter(DevProbe.DEATH)` where `sink` is `(DevSink) DevProbe.sink`. `ShadeAreaA` reads `mood.shadeScans()`; it becomes `sink.counter(DevProbe.SHADE_SCAN, zombie.entity().getId())`.

- [ ] **Step 4: Build and run the tests**

```bash
cd mod
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. If a harness still references a deleted field, the compiler names it — fix that harness, do not restore the field.

- [ ] **Step 5: Verify the strings left the jar**

```bash
cd mod
unzip -p build/libs/lethalbreed-1.0.0.jar 'com/dreykaoas/lethalbreed/phase/PhaseManager.class' | strings | grep -c 'phase preview'
unzip -l build/libs/lethalbreed-1.0.0.jar | grep -c 'ContaminationManager'
```

Expected: `0` for the preview table; `1` for `ContaminationManager` (it stays — `clearPlague`/`plagueLevel` have four live mixin callers).

- [ ] **Step 6: Run one harness end to end**

```bash
cd mod
LB_DEV_TEST=shade ./gradlew runServer
```

Expected: the shade harness still prints its `[LB-Verify]` PASS/FAIL verdict — proof the counters survived the move.

- [ ] **Step 7: Commit**

```bash
git add -A mod/src
git commit -m "probe: route dev counters through DevProbe and drop their fields from main"
```

---

### Task 6: Move the debug traces and `ClimbDebug`

**Files:**
- Move: `src/main/java/com/dreykaoas/lethalbreed/entity/move/dispatch/ClimbDebug.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/probe/ClimbTrace.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java:8`, `:32`, `:130`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/entity/move/PillarClimb.java:97-101`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/tick/PackPass.java:64-70`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/effect/contamination/symptom/ContaminationSymptoms.java` (delete `DEV` and `showDevIndicator`)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java:82-84`

**Interfaces:**
- Consumes: `DevProbe.tracing(int)`, `DevProbe.sink.trace(int, String)`, channels `CLIMB`, `PACKS`, `CONTAM`.
- Produces: nothing new.

- [ ] **Step 1: Delete the per-zombie `ClimbDebug` instance**

`ZombieBrain.java:32` allocates **one `ClimbDebug` object per `ZombieBrain`**, i.e. one per `SmartZombie`, purely to carry a counter. Delete the field (line 32) and the import (line 8) outright.

Line 130 currently reads:

```java
        climbDebug.log(entity, p, horizSq, dy, stuck, stuckTicks, pillar.active());
```

Replace it with the gated trace. Every value is already in scope: `entity` is the field at line 24, `p` the local `ZombiePursuit` at line 56, `horizSq` the local at line 95, `dy` the local at line 94, `stuck` the local at line 108, `stuckTicks` the field at line 31.

```java
        if (DevProbe.tracing(DevProbe.CLIMB)) {
            DevProbe.sink.trace(DevProbe.CLIMB, "z" + entity.getId()
                    + " pursue tgt=(" + p.tgtX() + ", " + p.tgtY() + ", " + p.tgtZ() + ")"
                    + " horizSq=" + horizSq + " dy=" + dy
                    + " stuck=" + stuck + "/" + stuckTicks
                    + " pillar=" + pillar.active());
        }
```

The message is built **inside** the guard, which is the whole point: `tracing()` is constant-false in a player jar, so none of this concatenation exists at runtime. `ClimbDebug`'s internal counter throttle moves to the dev side — `DevSink.trace` can rate-limit if the stream is too noisy.

Add `import com.dreykaoas.lethalbreed.probe.DevProbe;`.

- [ ] **Step 2: Gate the `PillarClimb` block**

`PillarClimb.java:97-101` currently reads:

```java
        if (DevTestConfig.debugClimb && (age % 3 == 1)) {
            LethalBreed.LOGGER.info("[ClimbDbg] z{} PILLAR y={} dyTgt={} horiz={} age={} risen={} ground={}",
                    entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(dyToTarget), MoveMath.f1(h), age,
                    MoveMath.f1(risen()), entity.onGround());
        }
```

Replace with:

```java
        if (DevProbe.tracing(DevProbe.CLIMB) && (age % 3 == 1)) {
            DevProbe.sink.trace(DevProbe.CLIMB, "z" + entity.getId()
                    + " PILLAR y=" + MoveMath.f1(entity.getY())
                    + " dyTgt=" + MoveMath.f1(dyToTarget)
                    + " horiz=" + MoveMath.f1(h)
                    + " age=" + age
                    + " risen=" + MoveMath.f1(risen())
                    + " ground=" + entity.onGround());
        }
```

Keep the `age % 3 == 1` throttle: it is free in a player jar (short-circuited by the false gate before it) and it keeps the dev stream readable. `dyToTarget`, `h`, `age` and `risen()` are all private state of `PillarClimb`, which is why the trace has to be built here rather than in a dev-side class.

Then delete two imports that this block was the sole user of: `PillarClimb.java:7` (`DevTestConfig`) and `PillarClimb.java:9` (`LethalBreed`). Verify with `grep -n 'DevTestConfig\|LethalBreed\.' src/main/java/com/dreykaoas/lethalbreed/entity/move/PillarClimb.java` before deleting either. Add `import com.dreykaoas.lethalbreed.probe.DevProbe;`.

This block runs on the `EveryTickPass` path (`EveryTickPass.java:28`), so it is genuinely per-tick for every climbing zombie, unthrottled by LOD — the most expensive of the three traces.

- [ ] **Step 3: Gate the `PackPass` block**

`PackPass.java:64-70` currently reads:

```java
        if (PackConfig.debugPacks) {
            // The three numbers that separate the ways "no pack formed" can happen: the rule was never
            // offered a neighbour (n), it was offered some and declined (kind=NONE), or it acted. Without
            // them the verdict says only that nothing happened, which is the least useful thing to know.
            LethalBreed.LOGGER.info("[PackDbg] id={} at ({}, {}) n={} pack={} -> {}",
                    sz.id(), Math.round(sz.x()), Math.round(sz.z()), n, tether.packId(), d.kind());
        }
```

Replace with:

```java
        if (DevProbe.tracing(DevProbe.PACKS)) {
            // The three numbers that separate the ways "no pack formed" can happen: the rule was never
            // offered a neighbour (n), it was offered some and declined (kind=NONE), or it acted. Without
            // them the verdict says only that nothing happened, which is the least useful thing to know.
            DevProbe.sink.trace(DevProbe.PACKS, "id=" + sz.id()
                    + " at (" + Math.round(sz.x()) + ", " + Math.round(sz.z()) + ")"
                    + " n=" + n + " pack=" + tether.packId()
                    + " -> " + d.kind());
        }
```

Delete `PackPass.java:3` (`import com.dreykaoas.lethalbreed.LethalBreed;`) — line 68 was its only use. **Keep** the `PackConfig` import at line 4: lines 39, 44, 93 and 94 still need it. Add `import com.dreykaoas.lethalbreed.probe.DevProbe;`.

Task 7 deletes the `PackConfig.debugPacks` field itself; this step removes its last reader.

- [ ] **Step 4: Delete the contamination dev indicator**

In `ContaminationSymptoms`, delete the `DEV` field (line 29) and the whole `showDevIndicator` method (lines 86-101), along with the `FabricLoader` import if nothing else in the file uses it. **Do not touch** `tickLatent`, `applyLatentSlow`, `removeLatentSlow` or `applyIcon` — those are player plague behaviour.

In `ContaminationTick.java:82-84`, delete the call and its guard.

- [ ] **Step 5: Move `ClimbDebug` to `src/dev` if it still has value**

```bash
cd mod
git mv src/main/java/com/dreykaoas/lethalbreed/entity/move/dispatch/ClimbDebug.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/probe/ClimbTrace.java
```

Change its package to `com.dreykaoas.lethalbreed.dev.probe` and rename the type to `ClimbTrace`. If, after the seam rewrite, nothing in `src/dev` uses it, delete the file instead — do not keep a class with no callers.

- [ ] **Step 6: Build and verify the strings are gone**

```bash
cd mod
./gradlew clean build
unzip -p build/libs/lethalbreed-1.0.0.jar 'com/dreykaoas/lethalbreed/entity/move/ZombieBrain.class' | strings | grep -c 'ClimbDbg'
unzip -p build/libs/lethalbreed-1.0.0.jar 'com/dreykaoas/lethalbreed/tick/PackPass.class' | strings | grep -c 'PackDbg'
unzip -l build/libs/lethalbreed-1.0.0.jar | grep -c 'ClimbDebug'
```

Expected: `0`, `0`, `0`.

```bash
cd mod
unzip -p build/libs/lethalbreed-1.0.0.jar 'com/dreykaoas/lethalbreed/effect/contamination/symptom/ContaminationSymptoms.class' | strings | grep -c 'INFECT'
```

Expected: `0` — the `[INFECTÉ ✦ symptômes]` / `[INFECTÉ latent]` literals are gone.

- [ ] **Step 7: Commit**

```bash
git add -A mod/src
git commit -m "probe: move climb/pack/contamination debug traces behind DevProbe channels"
```

---

### Task 7: Make the config schema registrable and move the dev options out

The riskiest task. Four structures in `src/main` are computed once, at class-init, from an immutable array; all four must become late-registrable before `DevTestConfig` can leave. Write the tests first.

**Files:**
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigSchema.java:38-79`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/ConfigAccess.java:26-42`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/ConfigBoundsTable.java` and `ConfigBounds.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/schema/ConfigCategory.java:13-20`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/domain/engine/SchedulerConfig.java:72-73` (delete `debugLogInterval`), `config/bounds/engine/PerfBounds.java:32` (delete its bound)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/domain/PackConfig.java:97-98` (delete `debugPacks`)
- Modify: `src/main/java/com/dreykaoas/lethalbreed/config/bounds/ProgressionBounds.java:78` (delete the `devSpawnRadius` bound)
- Move: `config/domain/engine/DevTestConfig.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/config/DevTestConfig.java`
- Create: `src/dev/java/com/dreykaoas/lethalbreed/dev/config/DevBounds.java`
- Modify: `src/dev/java/com/dreykaoas/lethalbreed/dev/DevBootstrap.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/LethalBreedMod.java:32-37` (hoist the dev hook above config load)
- Modify: `src/test/resources/config-option-order.txt` (delete lines 25, 207-221, and the `debugPacks` line)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public static void ConfigSchema.registerHolder(Class<?> holder)`
  - `public static List<Field> ConfigSchema.all()` — unchanged signature, now lazily recomputed
  - `static void ConfigAccess.captureDefaultsFor(Class<?> holder)`
  - `public static void ConfigBounds.registerGroup(java.util.function.Consumer<BoundsRegistrar> group)`

- [ ] **Step 1: Write the failing test for late registration**

Create `src/test/java/com/dreykaoas/lethalbreed/config/schema/ConfigSchemaRegisterTest.java`:

```java
package com.dreykaoas.lethalbreed.config.schema;

import com.dreykaoas.lethalbreed.config.ConfigAccess;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dev-side holder is registered at runtime, after class-init. Two things must survive that:
 * the option must appear in all(), and reset() must not blow up on a field whose default was
 * captured late — that path does Field.set(null, DEFAULTS.get(name)) and a missing entry is a
 * null unboxed into a primitive, i.e. IllegalArgumentException, which nothing catches.
 */
class ConfigSchemaRegisterTest {

    /** Stand-in for a dev holder — deliberately not a real one, so the test owns its lifecycle. */
    public static final class LateHolder {
        private LateHolder() {}
        public static boolean lateFlag = false;
        public static int lateCount = 7;
    }

    @Test
    void aLateRegisteredHolderAppearsInAll() {
        int before = ConfigSchema.all().size();
        ConfigSchema.registerHolder(LateHolder.class);
        List<Field> after = ConfigSchema.all();

        assertEquals(before + 2, after.size());
        assertTrue(after.stream().anyMatch(f -> f.getName().equals("lateFlag")));
        assertTrue(after.stream().anyMatch(f -> f.getName().equals("lateCount")));
    }

    @Test
    void resetDoesNotThrowOnALateRegisteredField() {
        ConfigSchema.registerHolder(LateHolder.class);
        LateHolder.lateCount = 999;

        assertDoesNotThrow(ConfigAccess::resetAllInMemory);
        assertEquals(7, LateHolder.lateCount, "the late-captured default must be restored");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
cd mod
./gradlew test --tests 'com.dreykaoas.lethalbreed.config.schema.ConfigSchemaRegisterTest'
```

Expected: FAIL — `cannot find symbol: method registerHolder(Class<?>)`.

- [ ] **Step 3: Make `ConfigSchema` registrable**

`ConfigSchema.java:38-61` currently declares `private static final Class<?>[] HOLDERS = { ... }` and `private static final List<Field> ALL = Collections.unmodifiableList(scan());`. Replace with a mutable list and an invalidatable cache, keeping every holder in its current order:

```java
    private static final List<Class<?>> HOLDERS = new ArrayList<>(List.of(
            /* the existing holder classes, in their existing order, DevTestConfig removed */));

    /** Recomputed on demand so a dev holder registered after class-init is picked up. Volatile because
     *  registration happens on the mod-init thread and reads happen on the server thread. */
    private static volatile List<Field> all;

    /**
     * Add a config holder after class-init. Called only by {@code DevBootstrap} in a development
     * environment — the shipped jar never reaches it, which is exactly why the dev options and their
     * "Dev / Debug" GUI tab do not exist in a player's game.
     */
    public static void registerHolder(Class<?> holder) {
        HOLDERS.add(holder);
        all = null;
        ConfigAccess.captureDefaultsFor(holder);
    }

    public static List<Field> all() {
        List<Field> a = all;
        if (a == null) {
            a = Collections.unmodifiableList(scan());
            all = a;
        }
        return a;
    }
```

`scan()` changes only its loop header, from an array to the list:

```java
        for (Class<?> holder : HOLDERS) {
```

- [ ] **Step 4: Add late default capture to `ConfigAccess`**

`DEFAULTS` is already a plain `LinkedHashMap`; only its reference is `final`, so mutation is legal. Add:

```java
    /**
     * Capture defaults for a holder registered after class-init. Without this, {@code reset()} does
     * {@code Field.set(null, DEFAULTS.get(name))} with a null value on a primitive field and throws
     * IllegalArgumentException — which nothing here catches.
     */
    static void captureDefaultsFor(Class<?> holder) {
        for (Field f : holder.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || Modifier.isFinal(m)) {
                continue;
            }
            try {
                DEFAULTS.put(f.getName(), ConfigType.copyIfArray(f.get(null)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot capture default for " + f.getName(), e);
            }
        }
    }
```

- [ ] **Step 5: Open a runtime door into `ConfigBoundsTable`**

`ConfigBoundsTable` is a package-private `final class` whose `b()` is `private static`, and its `static {}` block hard-calls nine `XxxBounds.register(ConfigBoundsTable::b)`. Add a package-private bridge in `ConfigBoundsTable`:

```java
    /** Register one more bounds group after class-init. Used by the dev source set only. */
    static void registerGroup(java.util.function.Consumer<BoundsRegistrar> group) {
        group.accept(ConfigBoundsTable::b);
    }
```

and re-expose it from the already-public `ConfigBounds` in the same package, since `DevBootstrap` lives in `com.dreykaoas.lethalbreed.dev`:

```java
    /** Register a bounds group defined outside this package (the dev source set). */
    public static void registerGroup(java.util.function.Consumer<BoundsRegistrar> group) {
        ConfigBoundsTable.registerGroup(group);
    }
```

`BoundsRegistrar` is already `public @FunctionalInterface void b(String, double, double)` — the registration API itself needs no change.

- [ ] **Step 6: Run the registration tests to green**

```bash
cd mod
./gradlew test --tests 'com.dreykaoas.lethalbreed.config.schema.ConfigSchemaRegisterTest'
```

Expected: PASS, 2 tests.

- [ ] **Step 7: Move `DevTestConfig` and its bounds into `src/dev`**

```bash
cd mod
mkdir -p src/dev/java/com/dreykaoas/lethalbreed/dev/config
git mv src/main/java/com/dreykaoas/lethalbreed/config/domain/engine/DevTestConfig.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/config/DevTestConfig.java
```

Change its package to `com.dreykaoas.lethalbreed.dev.config`. Delete its `debugClimb` field — Task 6 replaced its two readers with `DevProbe.tracing(DevProbe.CLIMB)`; the dev side now decides the channel mask instead. Rewrite the class javadoc, whose lines 10-13 promise holder adjacency in `ConfigSchema.HOLDERS` that no longer holds.

Create `src/dev/java/com/dreykaoas/lethalbreed/dev/config/DevBounds.java`:

```java
package com.dreykaoas.lethalbreed.dev.config;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Bounds for the dev-only options. Registered at runtime by {@code DevBootstrap}, so
 * {@code ConfigBoundsTable}'s static block never lists it and a shipped jar has no trace of it.
 */
public final class DevBounds {
    private DevBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("devSpawnRadius", 1, 256);
        r.b("debugLogInterval", 0, 1_000_000);
    }
}
```

Those two lines are the verbatim ranges moved from `ProgressionBounds.java:78` (`r.b("devSpawnRadius", 1, 256);` — the last entry in that method, followed by a stray blank line 79; cut both) and `PerfBounds.java:32` (`r.b("debugLogInterval", 0, 1_000_000);`). Also fix `ProgressionBounds`'s class javadoc at line 6, which reads "Clamp ranges for the phase, special-variant and dev options" — the dev half is leaving.

`debugLogInterval` moves to the dev holder alongside `devSpawnRadius`: add it as a field to `DevTestConfig` in `src/dev`, since `StageProfiler.enabled()` (also now dev-side) is its only reader.

Delete `SchedulerConfig.debugLogInterval` (lines 72-73) and `PackConfig.debugPacks` (lines 97-98).

- [ ] **Step 8: Register the dev holder before config load**

`installDevHooks()` is the **last** statement of `onInitialize()` (line 37), but `BootstrapInit.run()` at line 32 is where `ConfigIo.load()` runs. Registering after that means the developer's own dev options are parsed as unknown, warned about, and stripped on the next save. Hoist the hook:

```java
    @Override
    public void onInitialize() {
        // Dev hooks FIRST: DevBootstrap registers the dev config holder, and BootstrapInit.run() below
        // loads the JSON — registering after the load would see the dev options as unknown keys and drop them.
        installDevHooks();
        BootstrapInit.run();
        EntityEventsInit.register(REGISTRY, DIMENSIONS);
        TickInit.register(SCHEDULER);
        CommandInit.register();
        LifecycleInit.register(REGISTRY, DIMENSIONS, SCHEDULER);
    }
```

In `DevBootstrap.install()`, register before `DevTestSelector.apply()` — which reads dev fields and would otherwise run against an unregistered holder:

```java
        ConfigSchema.registerHolder(DevTestConfig.class);
        ConfigBounds.registerGroup(DevBounds::register);
```

- [ ] **Step 9: Simplify `ConfigCategory` and regenerate the golden file**

`ConfigCategory.java:13-20` routes any option whose name starts with `dev` or `debug` into the "Dev" category. With every such option gone from `src/main`, that rule now only matters for dev runs — keep it (the late-registered holder still needs it) but delete the note at line 20 if it references holders that moved.

Regenerate `src/test/resources/config-option-order.txt` by deleting the lines for every removed option: line 25 (`debugLogInterval`), lines 207-221 (the 15 `DevTestConfig` fields), and the `debugPacks` line. Its header says "Regenerate ONLY when an option is deliberately added or removed" — this is exactly that case.

- [ ] **Step 10: Run the config suite**

```bash
cd mod
./gradlew test --tests 'com.dreykaoas.lethalbreed.config.*'
```

Expected: PASS. `ConfigSchemaOrderTest`, `ConfigBoundsTest`, `ConfigAccessResetTest` and `ConfigLoaderTest` all touch this. `ConfigBoundsTest.everyScalarNumericOptionHasBounds` fails if you deleted a numeric option's field but left its bound, or vice versa — field and bound always move together.

- [ ] **Step 11: Verify the Dev tab is gone from the player jar and present in dev**

```bash
cd mod
./gradlew clean build
unzip -l build/libs/lethalbreed-1.0.0.jar | grep -c DevTestConfig
```

Expected: `0`.

```bash
cd mod
./gradlew runClient
```

Expected: `/lethalconfig` opens the GUI and the sidebar **still shows** a "Dev / Debug" tab, because `DevBootstrap` registered the holder. If it is missing, registration ran after the first config read — check the hoist in step 8.

- [ ] **Step 12: Commit**

```bash
git add -A mod/src
git commit -m "config: make the schema late-registrable and move dev options into src/dev"
```

---

### Task 8: Move the dev commands and trim `/lethalconfig`

**Files:**
- Move: `src/main/java/com/dreykaoas/lethalbreed/command/LethalSpecialCommand.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/command/LethalSpecialCommand.java`
- Move: `src/main/java/com/dreykaoas/lethalbreed/command/LethalPhaseCommand.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/command/LethalPhaseCommand.java`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/init/CommandInit.java:4-5`, `:9-11`, `:18-19`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/command/LethalConfigCommand.java:53-74`, and delete `get`, `set`, `reset`, `resetAll`, `unknown`, `FIELD_SUGGEST`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/command/CommandFeedback.java` (delete the orphaned 3-arg overload)
- Modify: `src/dev/java/com/dreykaoas/lethalbreed/dev/DevBootstrap.java` (register the two moved commands)

**Interfaces:**
- Consumes: `PhaseManager.setPhase` (still public — after this task its only caller is `src/dev`), `SpecialRoller.assign`.
- Produces: nothing new.

- [ ] **Step 1: Move the two commands**

```bash
cd mod
git mv src/main/java/com/dreykaoas/lethalbreed/command/LethalSpecialCommand.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/command/LethalSpecialCommand.java
git mv src/main/java/com/dreykaoas/lethalbreed/command/LethalPhaseCommand.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/command/LethalPhaseCommand.java
```

Change both packages to `com.dreykaoas.lethalbreed.dev.command`.

**They will not compile yet.** Both call `CommandFeedback`, which is package-private (`final class CommandFeedback`, all methods package-private static) — leaving the `command` package breaks them. The repo's own dev-side convention is a local helper, not a shared one: `LethalDevCommand.java:154-157` has a private `reply(...)` with a `"[LethalDev] "` prefix, and `LethalSpawnCommand.java:70` calls `src.sendSuccess` directly. Follow it — give each moved command a private `reply(...)` matching `LethalDevCommand`'s shape. Do **not** make `CommandFeedback` public; that would ship a widened API to satisfy dev code.

- [ ] **Step 2: Strip the two commands out of `CommandInit`**

Delete exactly four lines — 4, 5, 18 and 19 — and rewrite the javadoc, which currently promises three user-facing commands:

```java
package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.command.LethalConfigCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Registers the mod's single user-facing command, {@code /lethalconfig}. Every other command
 * ({@code /lethaldev}, {@code /lethalspawn}, {@code /lethalphase}, {@code /lethalspecial}) is a
 * developer tool: it lives in the {@code dev} source set and is registered by {@code DevBootstrap},
 * so a player jar contains none of them.
 */
public final class CommandInit {
    private CommandInit() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LethalConfigCommand.register(dispatcher);
        });
    }
}
```

- [ ] **Step 3: Register them from `DevBootstrap`**

Reuse the idiom already there for `/lethaldev` and `/lethalspawn` around lines 100-120 — the same `CommandRegistrationCallback.EVENT.register` lambda, adding:

```java
            LethalPhaseCommand.register(dispatcher);
            LethalSpecialCommand.register(dispatcher);
```

Update the `DevBootstrap` javadoc at lines 26-27 and the comment at line 107; both enumerate the dev command set, which now has four entries.

- [ ] **Step 4: Trim the `/lethalconfig` tree**

`LethalConfigCommand.register` currently registers seven nodes (lines 53-74). Replace the whole method with:

```java
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lethalconfig")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(LethalConfigCommand::openMenu)
                .then(Commands.literal("verify").executes(LethalConfigCommand::verify)));
    }
```

Watch the parentheses: the `verify` line gains one closing paren plus the `;` that lines 73-74 used to carry.

Then delete the now-unreferenced `get`, `set`, `reset`, `resetAll`, `unknown` and `FIELD_SUGGEST`.

**Keep `list(...)` as a private method.** `openMenu` falls back to it unconditionally for a console sender:

```java
    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return list(ctx); // console: fall back to the text dump
        }
```

Deleting the method breaks the dedicated-server console path and fails to compile. Only the `/lethalconfig list` literal disappears. Keep `gpuInfo()` too — `openMenu` calls it at line 82.

Rewrite the class javadoc at lines 22-39, whose bullets at 27-33 list all seven subcommands.

- [ ] **Step 5: Delete the orphaned `CommandFeedback` overload**

After the cut, the 3-arg `success(CommandSourceStack, String, boolean)` at `CommandFeedback.java:17` has no caller left in `src/main` — its three users were `LethalSpecialCommand:83`, `LethalPhaseCommand:35` (both moved) and `LethalConfigCommand:168` (inside the deleted `get`). Delete it. The surviving callers at lines 104, 122, 127 and 133 all use the 4-arg `success` or `failure`.

- [ ] **Step 6: Verify no dangling references to the removed subcommands**

```bash
cd mod
grep -rn 'lethalconfig list\|lethalconfig set\|lethalconfig reset\|lethalconfig get' src/ || echo CLEAN
grep -rn '/lethalphase\|/lethalspecial' src/main/ || echo CLEAN
```

Expected: `CLEAN` for the second grep. The first may still hit `ConfigAccess.java:93` and `ConfigAccessResetTest.java:19`, which mention `/lethalconfig resetall` in prose — update those comments. `LifecycleInit.java:77` names `/lethalconfig verify` and stays true, since `verify` is kept.

- [ ] **Step 7: Build, test, and check the jar**

```bash
cd mod
./gradlew clean build
unzip -l build/libs/lethalbreed-1.0.0.jar | grep 'lethalbreed/command/'
```

Expected: exactly two entries — `LethalConfigCommand.class` and `CommandFeedback.class`.

- [ ] **Step 8: Verify in game**

```bash
cd mod
./gradlew runServer
```

In the server console, confirm `/lethalconfig` prints the option dump (the console fallback), `/lethalconfig verify` works, and `/lethalphase` and `/lethalspecial` are **still available** — they moved to dev, they did not disappear.

- [ ] **Step 9: Commit**

```bash
git add -A mod/src
git commit -m "command: move /lethalphase and /lethalspecial to src/dev, trim /lethalconfig to GUI + verify"
```

---

### Task 9: Delete the accidental API surface

Nine symbols with zero `src/main` callers. Two files move to `src/dev`, two are deleted outright, the rest are member deletions or visibility narrowing.

**Files:**
- Move: `ai/flowfield/FlowFieldChecks.java` → `src/dev`, **only if** `Neighbors8` allows it (see step 1)
- Move: `config/ConfigOverride.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/config/ConfigOverride.java`
- Move: `util/InstalledMods.java` → `src/dev/java/com/dreykaoas/lethalbreed/dev/InstalledMods.java`
- Delete: `client/PresentationalMixinNotes.java`, `config/bounds/BoundsSplitNote.java`
- Modify: `config/schema/ConfigType.java:4`, `config/bounds/engine/FlowBounds.java:4`, `config/bounds/engine/PerfBounds.java:4` (dead imports)
- Modify: `init/BootstrapInit.java:33` (the `InstalledMods.detect()` call), `ai/flowfield/Neighbors8.java:6` (dead `{@link}`)
- Modify: `ai/flowfield/Snapshot.java:47`, `:51`; `pack/PackManager.java:54`; `pack/PackTether.java:65`; `ai/flowfield/CellClassifier.java:38-42`; `client/LethalBreedClientConfig.java` (`irisPresent`)
- Modify: `src/test/java/com/dreykaoas/lethalbreed/pack/PackTetherTest.java:77`

**Interfaces:**
- Consumes: `ConfigSchema.registerHolder` from Task 7 is unrelated here; `ConfigOverride.set` still uses `ConfigSchema.find(name)`, which is unchanged.
- Produces: nothing.

- [ ] **Step 1: Resolve the `FlowFieldChecks` / `Neighbors8` blocker first**

`FlowFieldChecks` reads `Neighbors8.DX/DZ/isDiagonal/cornerBlocked`, and `Neighbors8` is a package-private `final class`. Check it:

```bash
cd mod
sed -n '1,20p' src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/Neighbors8.java
grep -rn 'FlowFieldChecks' src/
```

If `Neighbors8` is package-private, `FlowFieldChecks` **cannot** move to `src/dev` — it would stop compiling. Two truthful options: delete `FlowFieldChecks` and `FlowFieldChecksTest` together (its only callers are the dev harnesses and that test), or widen `Neighbors8` to public (which ships a wider API to serve dev code — the wrong trade). **Delete.** Also remove the dead `{@link FlowFieldChecks}` at `Neighbors8.java:6`.

- [ ] **Step 2: Move `ConfigOverride` and `InstalledMods`**

```bash
cd mod
git mv src/main/java/com/dreykaoas/lethalbreed/config/ConfigOverride.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/config/ConfigOverride.java
git mv src/main/java/com/dreykaoas/lethalbreed/util/InstalledMods.java \
       src/dev/java/com/dreykaoas/lethalbreed/dev/InstalledMods.java
```

Repackage both. `ConfigOverride` is used by the dev harnesses (`PackSetup.java:46` calls `cfg.set("debugPacks", ...)`) — with `debugPacks` deleted in Task 7, that call now throws `IllegalArgumentException("no such config option: debugPacks")`. Fix `PackSetup` to set the trace mask through `DevProbe` instead:

```java
        DevProbe.traceMask = stage == 0
                ? DevProbe.traceMask | (1 << DevProbe.PACKS)
                : DevProbe.traceMask & ~(1 << DevProbe.PACKS);
```

Delete the dead import at `ConfigType.java:4`.

`InstalledMods` has **zero readers** for all nine of its booleans — its only observable effect is two log lines. Delete its call at `BootstrapInit.java:33` and let the class live in `src/dev` only if a harness wants it; otherwise delete it too.

- [ ] **Step 3: Delete the two javadoc-anchor classes**

```bash
cd mod
git rm src/main/java/com/dreykaoas/lethalbreed/client/PresentationalMixinNotes.java
git rm src/main/java/com/dreykaoas/lethalbreed/config/bounds/BoundsSplitNote.java
```

Delete the two now-dead imports at `FlowBounds.java:4` and `PerfBounds.java:4`.

- [ ] **Step 4: Delete the dead members and narrow `CellClassifier.classify`**

Delete `Snapshot.focusY()` (line 47), `Snapshot.flags()` (line 51), `PackManager.packCount()` (line 54), `PackTether.clearWaypoint()` (line 65) together with its assertion at `PackTetherTest.java:77`, and `LethalBreedClientConfig.irisPresent` with its three sites (36, 55, 81).

**Do not touch** `Snapshot.walk()` (called by `GpuFlowFieldSolver.java:40`), `PackManager.all()` (called by `PackSavedData.java:122` and `WorldMaintenance.java:58`) or `LethalBreedClientConfig.sodiumPresent` (read at line 49 to widen the cull distance). Their comments claim dev-only use; their call sites say otherwise.

Narrow `CellClassifier.classify` from `public` to package-private — it is public only "so the dev-source-set ComputeSelfTest can sweep a real world". If `ComputeSelfTest` breaks, that is the point: the sweep needs a dev-side entry, not a widened shipped API. Delete `ComputeSelfTest`'s call or give it another route.

- [ ] **Step 5: Build after every removal**

```bash
cd mod
./gradlew clean build
```

Narrowing visibility can break a caller you did not predict — build after each individual change in this task, not once at the end.

- [ ] **Step 6: Verify the classes left the jar**

```bash
cd mod
unzip -l build/libs/lethalbreed-1.0.0.jar | grep -Ec 'FlowFieldChecks|ConfigOverride|BoundsSplitNote|PresentationalMixinNotes|InstalledMods'
unzip -p build/libs/lethalbreed-1.0.0.jar 'com/dreykaoas/lethalbreed/init/BootstrapInit.class' | strings | grep -c 'perf mods'
```

Expected: `0` and `0`.

- [ ] **Step 7: Commit**

```bash
git add -A mod/src
git commit -m "cleanup: remove the accidental API surface from the shipped jar"
```

---

### Task 10: Purge the dev lang keys and fix the missing ones

35 keys per file, identical key sets on identical line numbers in `en_us.json` and `fr_fr.json`. Two keys that *look* dev are not, and one real player option has been shipping with no label at all.

**Files:**
- Modify: `src/main/resources/assets/lethalbreed/lang/en_us.json`
- Modify: `src/main/resources/assets/lethalbreed/lang/fr_fr.json`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/client/screen/OptionEntry.java:72`, `:125`
- Modify: `src/main/java/com/dreykaoas/lethalbreed/client/screen/CategoryList.java:40`

**Interfaces:**
- Consumes: Task 7's removal of the dev options from `ConfigSchema`.
- Produces: nothing.

- [ ] **Step 1: Delete the 35 dev keys from both files**

Both files are line-for-line parallel, so the same line numbers apply to each. Delete:

- line **31** — `"lethalbreed.category.Dev"`
- lines **56-59** — `debugLogInterval`, `devClimbTest`, `debugClimb`, `devSpawnRadius` labels
- lines **161-171** — `devSpecialTest`, `devMechTest`, `devComputeTest`, `devPlagueTest`, `devStatueTest`, `devClearTest`, `devPlacedTest`, `devShadeTest`, `devBreachTest`, `devPresenceTest`, `devAutoHalt` labels
- lines **250-253** — the four tooltips matching 56-59
- lines **318-328** — the eleven tooltips matching 161-171
- lines **490-493** — `debugPacks`, `debugPacks.desc`, `devPackTest`, `devPackTest.desc`

**Do not delete lines 76 and 270.** They are `lethalbreed.option.gpuDeviceIndex` and its `.desc` — a player-facing GPU option that only matches a `dev` grep because of the substring in "**Dev**iceIndex". Removing them strips a real option's label from the shipped GUI.

Line 493 is the last entry in the object; after deleting 490-493, line 489 must lose its trailing comma.

- [ ] **Step 2: Validate both files still parse**

```bash
cd mod
python3 -c "import json;[json.load(open(f'src/main/resources/assets/lethalbreed/lang/{f}.json')) for f in ('en_us','fr_fr')];print('OK')"
python3 -c "
import json
a=json.load(open('src/main/resources/assets/lethalbreed/lang/en_us.json'))
b=json.load(open('src/main/resources/assets/lethalbreed/lang/fr_fr.json'))
print(len(a), len(b), 'same keys' if a.keys()==b.keys() else 'KEY MISMATCH')
print('gpuDeviceIndex kept:', 'lethalbreed.option.gpuDeviceIndex' in a)
"
```

Expected: `OK`, then `450 450 same keys` and `gpuDeviceIndex kept: True`.

- [ ] **Step 3: Add the two missing keys for `contamDevTimeScale`**

This is a real player option — `ContaminationState.java:120` reads it to divide the plague timers — and it has **never** had a lang key in either file, so it renders as a raw key in the Contamination tab today. Its name starts with "contam", so `ConfigCategory`'s `startsWith("dev")` rule never caught it. Add to `en_us.json`:

```json
  "lethalbreed.option.contamDevTimeScale": "Plague Time Scale",
  "lethalbreed.option.contamDevTimeScale.desc": "Divides every plague timer. 1.0 = normal; lower values make the contamination progress faster.",
```

and to `fr_fr.json`:

```json
  "lethalbreed.option.contamDevTimeScale": "Échelle de temps de la peste",
  "lethalbreed.option.contamDevTimeScale.desc": "Divise tous les timers de la peste. 1.0 = normal ; une valeur plus basse accélère la progression de la contamination.",
```

Insert each pair in the same position its neighbours occupy in the other file, so the two stay line-for-line parallel.

- [ ] **Step 4: Make a missing key degrade gracefully**

Dev options are registered at runtime but their labels no longer ship, so a dev run would render `lethalbreed.option.devSpecialTest` as the row label. `OptionEntry.drawLabel` (line 72) and `CategoryList.CatEntry` (line 40) have no fallback today — `drawDesc` and `maybeTooltip` already guard with `if (s.equals(key) || s.isEmpty()) return;`, the label paths do not.

`OptionEntry.java:72`, before:

```java
        String label = Component.translatable("lethalbreed.option." + row.name()).getString();
```

after:

```java
        // Fall back to the raw field name: dev-registered options have no shipped translation, and a
        // future option that forgets one degrades to something readable instead of the full key.
        String label = Component.translatableWithFallback(
                "lethalbreed.option." + row.name(), row.name()).getString();
```

Apply the same change at `OptionEntry.java:125`, and at `CategoryList.java:40`:

```java
        this.button = Button.builder(
                Component.translatableWithFallback("lethalbreed.category." + cat, cat),
                b -> onSelect.accept(cat))
```

- [ ] **Step 5: Verify the jar and both GUIs**

```bash
cd mod
./gradlew clean build
unzip -p build/libs/lethalbreed-1.0.0.jar assets/lethalbreed/lang/en_us.json | grep -Eic 'dev only|harness|lethalspawn|"lethalbreed.category.Dev"'
unzip -p build/libs/lethalbreed-1.0.0.jar assets/lethalbreed/lang/en_us.json | grep -c gpuDeviceIndex
```

Expected: `0` and `2`.

```bash
cd mod
./gradlew runClient
```

Expected: `/lethalconfig` opens the GUI; the "Dev / Debug" tab is present (dev run) with rows labelled by their field names; the Contamination tab now shows "Plague Time Scale" instead of a raw key.

- [ ] **Step 6: Commit**

```bash
git add -A mod/src
git commit -m "lang: drop the 35 dev keys, add the missing contamDevTimeScale labels, fall back to field names"
```

---

### Task 11: Final verification and documentation

**Files:**
- Modify: `build.gradle.kts:15-18`, `:26-34` (comments describing the dev source set)
- Modify: `README.md` (module) — the build section

**Interfaces:**
- Consumes: every prior task.
- Produces: nothing.

- [ ] **Step 1: Full clean build and test**

```bash
cd mod
./gradlew clean build test
ls -1 build/libs/
```

Expected: BUILD SUCCESSFUL, and `build/libs` holds exactly one file.

- [ ] **Step 2: Prove the jar has no dev surface**

```bash
cd mod
J=build/libs/lethalbreed-1.0.0.jar
echo "dev classes:      $(unzip -l $J | grep -c 'lethalbreed/dev/')          (expect 0)"
echo "probe seam:       $(unzip -l $J | grep -c 'lethalbreed/probe/DevProbe') (expect 1)"
echo "JiJ jocl:         $(unzip -l $J | grep -c 'META-INF/jars/jocl-2.0.5.jar') (expect 1)"
echo "dev lang:         $(unzip -p $J assets/lethalbreed/lang/en_us.json | grep -ic 'dev only') (expect 0)"
echo "kernel comments:  $(unzip -p $J kernels/bellman_ford.clx | grep -c '//') (expect 0)"
echo "local var tables: $(grep -ral LocalVariableTable --include='*.class' build/classes/java/main | wc -l) (expect 0)"
echo "line tables:      $(grep -ral LineNumberTable --include='*.class' build/classes/java/main | wc -l) (expect 233)"
unzip -p $J META-INF/MANIFEST.MF | grep -c 'Fabric-Mapping-Namespace'
```

The last line must print `1` — `Fabric-Mapping-Namespace` and `Fabric-Jar-Type` are load-bearing and must survive.

- [ ] **Step 3: Diff against the baseline**

```bash
cd mod
unzip -l build/libs/lethalbreed-1.0.0.jar > /tmp/lb-after.txt
diff /tmp/lb-before.txt /tmp/lb-after.txt
```

Expected: only removals — the classes and members this plan moved out. Any *addition* other than `probe/DevProbe.class` is a mistake; investigate before shipping.

- [ ] **Step 4: Prove the dev workflow is intact**

```bash
cd mod
./gradlew runServer
```

Expected: the server boots, `/lethaldev`, `/lethalspawn`, `/lethalphase`, `/lethalspecial` all resolve, and the "Dev / Debug" config tab exists. Then run one harness:

```bash
cd mod
LB_DEV_TEST=pack ./gradlew runServer
```

Expected: the pack harness prints its `[LB-Verify]` verdict.

- [ ] **Step 5: Rewrite the build comments that describe the old contract**

`build.gradle.kts:15-18` and `:26-34` describe the dev source set as holding "headless test harnesses + the `/lethalspawn` load-test command". It now also holds all instrumentation, the dev config holder and four commands. Rewrite both blocks to say what is true, and note the one seam class in `main` and why it exists.

- [ ] **Step 6: Commit**

```bash
git add mod/build.gradle.kts mod/README.md
git commit -m "docs: describe the player-jar-only build contract"
```

---

## Self-Review

**Spec coverage.** Goal 1 (one jar) — Task 1. Goal 2 (no dev flavour) — Task 1, with the dev tooling preserved in `src/dev` rather than deleted, per the author's constraint. Goal 3 (no developer payload) — Tasks 2, 9, 10 for the sources jar, debug tables, kernel comments, API surface and lang prose. Goal 4 (no dev detection or dev commands) — Tasks 4, 5, 6, 7, 8 remove all four `isDevelopmentEnvironment()` sites (`LethalBreedMod`'s survives, and must: it is the seam's gate), the profilers, the counters, the traces, the dev config and the dev commands.

**Known gaps, stated rather than hidden.** The `META-INF/maven/org.jocl/jocl/pom.xml` inside the JiJ jar and the ~11 `Fabric-*` manifest attributes are not addressed — both were judged not worth their risk in the audit. The five JOCL native libraries stay: four of five are dead weight for any given player, and there is no way to split them without breaking the "a dedicated server needs no extra dependency" promise.

**Type consistency.** `DevProbe.Sink` is spelled identically in Tasks 3–6. `DevProbe.on()` gates timing and counting; `DevProbe.tracing(int)` gates message construction — the two are never interchanged. `DevSink.counter(int)` (global) and `DevSink.counter(int, int)` (per entity) are distinct overloads, introduced in Tasks 4 and 5 respectively. `ConfigSchema.registerHolder(Class<?>)`, `ConfigAccess.captureDefaultsFor(Class<?>)` and `ConfigBounds.registerGroup(Consumer<BoundsRegistrar>)` are declared in Task 7 and consumed only there and in `DevBootstrap`.

**Ordering hazards to respect.** Task 7 step 8 hoists `installDevHooks()` above `BootstrapInit.run()`; doing Task 7 before Task 4 would install the config holder before the sink exists. Keep the task order. Within Task 4, steps 1–7 must land in one commit — the tree does not compile between them.
