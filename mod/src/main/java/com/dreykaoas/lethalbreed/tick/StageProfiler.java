package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.SchedulerConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-stage timing for the server tick, so the cost ranking is measured instead of argued.
 *
 * <p>The perf recap only ever reported ONE number — total AI ms/tick — which says nothing about which
 * stage to optimise. This splits that total across the stages the bucket pass runs in order, which is
 * exactly what is needed before touching any of them: the audit's own performance figures were
 * analytical, never measured, and the two that were independently re-derived came back an order of
 * magnitude smaller.
 *
 * <p><b>Cost of measuring.</b> Enabled only in a development environment AND when
 * {@code debugLogInterval > 0}, so a published jar never pays for it and never logs. When enabled it is
 * two {@code System.nanoTime()} calls per stage per zombie activation; that overhead lands identically
 * on a before and an after run, so comparisons stay valid.
 */
public final class StageProfiler {

    /** Stage order matches the order {@code LodBucketPass} executes them. The {@code >} entries are
     *  sub-stages of CLASSIFY, timed inside it, so they overlap it rather than adding to the total. */
    public enum Stage {
        CLASSIFY("classify"),      // LOD reclassify — includes the target scan and its LOS raycasts
        GRID("grid"),              // spatial-hash re-bucketing
        SUNBURN("sunburn"),        // daylight burn check
        MOOD("mood"),              // celebrate / flee / day-sleep / shade search
        TICK("tick"),              // the throttled per-zombie AI tick itself
        SCAN(">scan"),             // sub-stage: the getEntitiesOfClass AABB sweep
        ORDER(">order"),           // sub-stage: candidate ordering
        LOS(">los");               // sub-stage: line-of-sight voxel raycasts

        final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    /** Sub-stage timing is recorded from {@code TargetSelector}, which sits outside the tick package and has
     *  no scheduler reference. One profiler exists per server, so a static side-channel is the honest
     *  trade — and the whole thing is compiled out of the hot path by {@link #enabled()} anyway. */
    private static volatile StageProfiler active;

    /** Record a sub-stage sample from outside the tick package. No-op when profiling is off. */
    public static void sub(Stage s, long elapsedNanos) {
        StageProfiler p = active;
        if (p != null) {
            p.add(s, elapsedNanos);
        }
    }

    private static final Stage[] STAGES = Stage.values();
    private static final boolean DEV = FabricLoader.getInstance().isDevelopmentEnvironment();

    private final long[] nanos = new long[STAGES.length];
    private final long[] calls = new long[STAGES.length];

    public StageProfiler() {
        active = this;
    }

    /** Whether measurement is on right now. Checked once per activation, not per stage. */
    public static boolean enabled() {
        return DEV && SchedulerConfig.debugLogInterval > 0;
    }

    public void add(Stage s, long elapsedNanos) {
        int i = s.ordinal();
        nanos[i] += elapsedNanos;
        calls[i]++;
    }

    /** Formatted breakdown over {@code ticks} server ticks, ordered most-expensive first. Empty when
     *  nothing was measured, so the recap line stays clean on an idle server. */
    public String drain(int ticks) {
        long total = 0;
        for (int i = 0; i < STAGES.length; i++) {
            if (!STAGES[i].label.startsWith(">")) {
                total += nanos[i]; // sub-stages overlap their parent — excluded from the base
            }
        }
        if (total == 0L) {
            java.util.Arrays.fill(calls, 0L);
            return "";
        }
        Integer[] order = new Integer[STAGES.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Long.compare(nanos[b], nanos[a]));

        StringBuilder sb = new StringBuilder(" | stages:");
        for (int i : order) {
            if (nanos[i] == 0L) {
                continue;
            }
            double msPerTick = nanos[i] / (double) ticks / 1_000_000.0;
            double usPerCall = calls[i] == 0 ? 0 : nanos[i] / (double) calls[i] / 1000.0;
            sb.append(String.format(" %s=%.2fms(%.0f%%,%.1fus/call)",
                    STAGES[i].label, msPerTick, 100.0 * nanos[i] / total, usPerCall));
        }
        java.util.Arrays.fill(nanos, 0L);
        java.util.Arrays.fill(calls, 0L);
        return sb.toString();
    }
}
