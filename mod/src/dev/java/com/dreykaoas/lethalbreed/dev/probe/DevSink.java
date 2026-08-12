package com.dreykaoas.lethalbreed.dev.probe;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The dev half of {@link DevProbe}. Owns everything the player jar must not contain: the stage
 * accumulators and their labels, the dev counters, the formatted perf recap and the trace prefixes.
 *
 * <p>Installed once by {@code DevBootstrap#install}. A shipped jar never loads this class, so nothing
 * here needs an environment check.
 */
public final class DevSink implements DevProbe.Sink {

    private final StageProfiler profiler;
    private final PerfRecap recap;

    /** Process-global counters, indexed by DevProbe counter id. Written from the server thread and
     *  read from harnesses, so atomic rather than plain long. */
    private final AtomicLongArray counters = new AtomicLongArray(DevProbe.COUNTER_COUNT);

    /** Per-entity tallies for counters that carry an entity id (currently only SHADE_SCAN): one map per
     *  counter id, indexed exactly like {@link #counters}, rather than a single map keyed by a packed
     *  {@code counter*N + entityId} integer — entity ids are not bounded by any fixed range and packing risks
     *  a silent collision across counters. Plain {@link ConcurrentHashMap} under the server thread is fine
     *  here (dev-only, single writer thread, harnesses read between ticks); no eviction policy is needed
     *  because a dev harness run lasts minutes, not a live server's uptime. */
    private final List<ConcurrentHashMap<Integer, AtomicLong>> perEntity;

    /** The profiler and the recap that drains it are both built by {@code DevBootstrap} and handed in, so
     *  neither this class nor {@link PerfRecap} has to reach for the other after construction. */
    public DevSink(StageProfiler profiler, PerfRecap recap) {
        this.profiler = profiler;
        this.recap = recap;
        List<ConcurrentHashMap<Integer, AtomicLong>> m = new ArrayList<>(DevProbe.COUNTER_COUNT);
        for (int i = 0; i < DevProbe.COUNTER_COUNT; i++) {
            m.add(new ConcurrentHashMap<>());
        }
        this.perEntity = m;
    }

    /** The installed sink, cast back to its concrete type. Harnesses read counters through this single
     *  accessor rather than casting {@link DevProbe#sink} themselves at each call site — {@code sink} is
     *  always a {@code DevSink} in a dev environment, since {@code DevBootstrap#install} is the only place
     *  that constructs one. */
    public static DevSink get() {
        return (DevSink) DevProbe.sink;
    }

    /** Total for one counter since process start. Read by the headless harnesses. */
    public long counter(int counter) {
        return counters.get(counter);
    }

    /** Tally for one entity on one counter since process start (0 if that entity never incremented it).
     *  Only meaningful for counters incremented with a real entity id, not {@link DevProbe#GLOBAL}. */
    public long counter(int counter, int entityId) {
        AtomicLong v = perEntity.get(counter).get(entityId);
        return v == null ? 0L : v.get();
    }

    /** Reset every counter — harnesses call this between scenarios. */
    public void resetCounters() {
        for (int i = 0; i < DevProbe.COUNTER_COUNT; i++) {
            counters.set(i, 0L);
            perEntity.get(i).clear();
        }
    }

    @Override
    public void stage(int stage, long nanos) {
        // debugLogInterval gates whether a sample is worth keeping at all — see StageProfiler#enabled.
        // Without this, PerfRecap.maybeLog's own "interval <= 0" bail-out means these arrays would just
        // accumulate forever, never drained, until the interval is turned on mid-session.
        if (!StageProfiler.enabled()) {
            return;
        }
        profiler.add(stage, nanos);
    }

    @Override
    public void count(int counter, int entityId) {
        counters.incrementAndGet(counter);
        if (entityId != DevProbe.GLOBAL) {
            perEntity.get(counter).computeIfAbsent(entityId, k -> new AtomicLong()).incrementAndGet();
        }
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
