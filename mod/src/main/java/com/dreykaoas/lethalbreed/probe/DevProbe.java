package com.dreykaoas.lethalbreed.probe;

import net.minecraft.server.MinecraftServer;

/**
 * The single seam between shipped code and development instrumentation.
 *
 * <p>Per-stage timings, dev counters and debug traces all measure things that happen INSIDE {@code main},
 * so they cannot simply live in the {@code dev} source set — {@code main} has to call out. This class is
 * that call-out, and it is deliberately the only one: everything it forwards to lives in {@code dev}.
 *
 * <p><b>Cost in a player jar.</b> {@link #sink} is {@code volatile}, so {@link #on()} is one volatile field
 * read plus a null check, paid at every call site, every tick — a volatile load is not constant-folded or
 * hoisted the way a stable static would be, so this is a real, ongoing (if negligible) cost, not one the JIT
 * eliminates. What DOES disappear on a player jar is everything BEHIND the gate: the
 * {@code System.nanoTime()} calls and message building at every call site sit behind {@link #on()} rather
 * than in front of it, so those never run. {@link #traceMask} works the same way for the per-channel debug
 * logs.
 *
 * <p>{@code DevBootstrap} assigns both fields via {@link #install}, which {@code LethalBreedMod} reaches
 * through its existing {@code devHook("install")} reflective call (the second of the two-phase
 * {@code devHook("registerConfig")} / {@code devHook("install")} shape), so this seam inherits that call's
 * dev-env gate for free and adds no second reflective lookup.
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
