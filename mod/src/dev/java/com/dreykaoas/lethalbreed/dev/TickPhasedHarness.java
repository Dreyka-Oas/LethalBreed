package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.ConfigOverride;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The shape every headless arena harness had copy-pasted: a server-tick counter, a build tick, an observation
 * window, an evaluation tick, config saved on the way in and restored on the way out, and exactly one
 * {@link DevVerdict#summary} at the end.
 *
 * <p><b>Stages.</b> A harness that measures two arenas in series — they share process-global counters, so they
 * cannot run at once — declares two {@link Stage}s with disjoint tick windows. The summary fires once, after
 * the LAST stage evaluates, matching the previous hand-rolled behaviour where the last rig owned the summary.
 * Ticks that fall between one stage's evaluation and the next stage's build are idle by construction.
 *
 * <p><b>Config restore is no longer per-harness bookkeeping.</b> Each stage gets a {@link ConfigOverride}
 * opened at its build tick and closed in a {@code finally} after its evaluation. A harness that throws
 * mid-run therefore no longer leaves the process holding its test values — which is exactly what the
 * hand-written {@code savedX} fields did whenever an exception skipped the restore.
 *
 * <p><b>Idempotent once done.</b> The old harnesses were a mix: some carried a {@code done} flag, some did not
 * and simply relied on their tick counter never coming back round. The base makes it uniform — after the last
 * stage reports, {@link #onTick} returns immediately forever.
 */
public abstract class TickPhasedHarness {

    /** One arena: built at {@code buildTick}, observed until {@code evalTick}, judged at {@code evalTick}. */
    public record Stage(String name, int buildTick, int evalTick) {
        public Stage {
            if (buildTick >= evalTick) {
                throw new IllegalArgumentException(
                        "stage " + name + ": buildTick " + buildTick + " must precede evalTick " + evalTick);
            }
        }
    }

    private final String suite;
    private final Stage[] stages;

    private int tick = -1;
    private int current = 0;
    private boolean done = false;
    private ConfigOverride overrides;

    protected TickPhasedHarness(String suite, Stage... stages) {
        if (stages.length == 0) {
            throw new IllegalArgumentException("harness " + suite + " declares no stage");
        }
        for (int i = 1; i < stages.length; i++) {
            if (stages[i].buildTick() <= stages[i - 1].evalTick()) {
                throw new IllegalArgumentException("harness " + suite + ": stage " + stages[i].name()
                        + " builds at " + stages[i].buildTick() + ", before stage " + stages[i - 1].name()
                        + " evaluates at " + stages[i - 1].evalTick() + " — stages must not overlap");
            }
        }
        this.suite = suite;
        this.stages = stages.clone();
    }

    /** The harness's own {@code devXxxTest} flag. Read every tick, so toggling it off mid-run stops it. */
    protected abstract boolean enabled();

    /** Build stage {@code stage}'s arena. Put every config change on {@code cfg} — a holder field written
     *  directly will not be restored. */
    protected abstract void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg);

    /** Called on every tick strictly between this stage's build and eval, and once more at the eval tick. */
    protected abstract void observe(int stage, ServerLevel ow, int tick);

    /** Report this stage's checks via {@link #check}. Do NOT call {@link DevVerdict#summary} — the base does,
     *  once, after the last stage. */
    protected abstract void evaluate(int stage, ServerLevel ow, MinecraftServer server);

    /** Register this with {@code ServerTickEvents.END_SERVER_TICK}. */
    public final void onTick(MinecraftServer server) {
        // Dev-env gate: these build blocks and force-spawn mobs. Never on a shipped jar even if a flag is on.
        if (done || !enabled() || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        Stage s = stages[current];

        if (tick == s.buildTick()) {
            overrides = ConfigOverride.open();
            build(current, ow, server, overrides);
        } else if (tick > s.buildTick() && tick < s.evalTick()) {
            observe(current, ow, tick);
        } else if (tick == s.evalTick()) {
            observe(current, ow, tick);
            try {
                evaluate(current, ow, server);
            } finally {
                if (overrides != null) {
                    overrides.close();
                    overrides = null;
                }
                current++;
                if (current >= stages.length) {
                    done = true;
                    DevVerdict.summary(suite, server);
                }
            }
        }
    }

    /** Record one check under this harness's suite. */
    protected final void check(String name, boolean pass, String detail) {
        DevVerdict.check(suite, name, pass, detail);
    }

    protected final String suite() {
        return suite;
    }
}
