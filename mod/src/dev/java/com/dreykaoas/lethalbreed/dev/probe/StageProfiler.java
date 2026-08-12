package com.dreykaoas.lethalbreed.dev.probe;

import com.dreykaoas.lethalbreed.config.domain.engine.SchedulerConfig;

/**
 * Per-stage timing for the server tick, so the cost ranking is measured instead of argued.
 *
 * <p>The perf recap only ever reported ONE number — total AI ms/tick — which says nothing about which
 * stage to optimise. This splits that total across the stages the bucket pass runs in order, which is
 * exactly what is needed before touching any of them: the audit's own performance figures were
 * analytical, never measured, and the two that were independently re-derived came back an order of
 * magnitude smaller.
 *
 * <p><b>Cost of measuring.</b> This class lives in the {@code dev} source set and is only ever reached
 * through {@code DevSink}, i.e. through the {@code DevProbe} seam — a published jar contains neither, so
 * it never pays for measurement and never logs. When a sink IS installed it is two
 * {@code System.nanoTime()} calls per stage per zombie activation; that overhead lands identically on a
 * before and an after run, so comparisons stay valid.
 */
public final class StageProfiler {

    /** Stage order matches the order {@code LodBucketPass} executes them. The {@code >} entries are
     *  sub-stages of CLASSIFY, timed inside it, so they overlap it rather than adding to the total. */
    public enum Stage {
        CLASSIFY("classify"),      // LOD reclassify — includes the target scan and its LOS raycasts
        GRID("grid"),              // spatial-hash re-bucketing
        PACK("pack"),              // pack form/join/leave decision
        SUNBURN("sunburn"),        // daylight burn check
        MOOD("mood"),              // celebrate / flee / day-sleep / shade search
        TICK("tick"),              // the throttled per-zombie AI tick itself
        FLOWSNAP("flowsnap"),      // server-thread world read that builds the flow-field snapshot
        SCAN(">scan"),             // sub-stage: the getEntitiesOfClass AABB sweep
        ORDER(">order"),           // sub-stage: candidate ordering
        LOS(">los");               // sub-stage: line-of-sight voxel raycasts

        final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    private static final Stage[] STAGES = Stage.values();

    private final long[] nanos = new long[STAGES.length];
    private final long[] calls = new long[STAGES.length];

    public StageProfiler() {
    }

    /** Whether a sample taken right now should actually be accumulated. {@link DevSink#stage} checks this
     *  before every sample and drops it when false, so the arrays below only ever hold data collected while
     *  {@code debugLogInterval} was positive — matching exactly what {@link PerfRecap#maybeLog} will drain,
     *  instead of accumulating unboundedly whenever a sink happens to be installed. */
    public static boolean enabled() {
        return SchedulerConfig.debugLogInterval > 0;
    }

    /** Indexes the accumulator arrays directly with an already-resolved stage id, so {@link DevSink#stage} —
     *  which only has the {@code int} id from the {@code DevProbe} seam — need not round-trip through
     *  {@code Stage} just to call {@link #ordinal()} straight back to the same int. Package-private: the only
     *  caller is {@code DevSink}, same package. */
    void add(int stageOrdinal, long elapsedNanos) {
        nanos[stageOrdinal] += elapsedNanos;
        calls[stageOrdinal]++;
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
