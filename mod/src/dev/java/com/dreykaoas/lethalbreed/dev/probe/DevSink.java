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

    private final StageProfiler profiler;
    private final PerfRecap recap;

    /** Process-global counters, indexed by DevProbe counter id. Written from the server thread and
     *  read from harnesses, so atomic rather than plain long. */
    private final AtomicLongArray counters = new AtomicLongArray(DevProbe.COUNTER_COUNT);

    /** The profiler and the recap that drains it are both built by {@code DevBootstrap} and handed in, so
     *  neither this class nor {@link PerfRecap} has to reach for the other after construction. */
    public DevSink(StageProfiler profiler, PerfRecap recap) {
        this.profiler = profiler;
        this.recap = recap;
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
