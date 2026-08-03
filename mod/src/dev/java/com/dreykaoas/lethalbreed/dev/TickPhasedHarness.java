package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.LethalBreed;
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
 * opened at its build tick and released once its evaluation is over — which is exactly what the hand-written
 * {@code savedX} fields failed to do whenever an exception skipped the restore.
 *
 * <p><b>Every phase runs under a supervisor.</b> The scope's lifetime spans three callbacks across hundreds
 * of ticks, so a {@code finally} around one of them is not enough: a throw from {@code build} or
 * {@code observe} would strand the overrides for the whole process AND leave the harness running against a
 * half-built arena, still printing a verdict on it. {@link #onTick} therefore catches everything, releases
 * the scope, records a {@code harness-error} FAIL so the run cannot look green, and stops.
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
    private final boolean reportsSummary;
    private final Stage[] stages;

    private int tick = -1;
    private int current = 0;
    private boolean done = false;
    private ConfigOverride overrides;

    protected TickPhasedHarness(String suite, Stage... stages) {
        this(suite, true, stages);
    }

    /**
     * @param reportsSummary false for a harness that SHARES its suite with others and is not the last to run.
     *        {@code DevVerdict.summary} emits the load-bearing {@code ALL DONE} marker, so exactly one harness
     *        per suite may call it — the three plague rigs share the "plague" suite and only the disable rig,
     *        which runs last, reports.
     */
    protected TickPhasedHarness(String suite, boolean reportsSummary, Stage... stages) {
        this.reportsSummary = reportsSummary;
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

    /**
     * Server tick before which this harness does not start counting at all. Rigs that share process-global
     * state — the three plague rigs mutate {@code contaminationEnabled} — cannot run at once and are
     * serialised in time by staggered start offsets. Default 0: start immediately.
     */
    protected int startAfterServerTick() {
        return 0;
    }

    /** Build stage {@code stage}'s arena. Put every config change on {@code cfg} — a holder field written
     *  directly will not be restored. */
    protected abstract void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg);

    /** Called on every tick strictly between this stage's build and eval, and once more at the eval tick. */
    protected abstract void observe(int stage, ServerLevel ow, int tick);

    /** Report this stage's checks via {@link #check}. Do NOT call {@link DevVerdict#summary} — the base does,
     *  once, after the last stage. */
    protected abstract void evaluate(int stage, ServerLevel ow, MinecraftServer server);

    /**
     * Whether this stage has everything it needs and should be judged NOW, before its {@code evalTick}.
     *
     * <p>Exists for rigs that advance on observed state rather than on a schedule. Such a rig has to budget for
     * the worst case it might meet — a chunk drop measured at up to 1200 ticks here — and without this hook that
     * worst case is also its BEST case: every run pays the full budget idling, even the ones that finished in
     * twenty ticks. With it, {@code evalTick} stops being the plan and becomes what it should always have been,
     * the deadline.
     *
     * <p>Default false: a fixed-schedule rig keeps its exact previous behaviour.
     */
    protected boolean readyToEvaluate(int stage, int tick) {
        return false;
    }

    /** Register this with {@code ServerTickEvents.END_SERVER_TICK}. */
    public final void onTick(MinecraftServer server) {
        // Dev-env gate: these build blocks and force-spawn mobs. Never on a shipped jar even if a flag is on.
        if (done || !enabled() || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        if (server.getTickCount() < startAfterServerTick()) {
            return;
        }
        tick++;
        // Supervisor. The ConfigOverride's lifetime spans build -> observe* -> evaluate, i.e. three callbacks
        // across hundreds of ticks, so guarding only evaluate() left two of them able to strand the scope:
        // the process kept the harness's values, and every measurement taken afterwards — by this rig or the
        // next one in the suite — was quietly made under someone else's config. Worse, the harness stayed
        // "alive": done was never set, so the following ticks kept running against a half-built arena and
        // still printed a verdict on it.
        try {
            dispatch(server);
        } catch (RuntimeException | Error e) {
            abort(server, e);
        }
    }

    private void dispatch(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        Stage s = stages[current];

        if (tick == s.buildTick()) {
            overrides = ConfigOverride.open();
            build(current, ow, server, overrides);
        } else if (tick > s.buildTick() && tick < s.evalTick()) {
            observe(current, ow, tick);
            if (readyToEvaluate(current, tick)) {
                judge(ow, server);
            }
        } else if (tick == s.evalTick()) {
            observe(current, ow, tick);
            judge(ow, server);
        }
    }

    /** Close the current stage: judge it, hand its config back, and advance (or finish the suite). */
    private void judge(ServerLevel ow, MinecraftServer server) {
        try {
            evaluate(current, ow, server);
        } finally {
            releaseScope();
            current++;
            if (current >= stages.length) {
                done = true;
                if (reportsSummary) {
                    DevVerdict.summary(suite, server);
                }
            }
        }
    }

    /**
     * A phase threw. Stop the harness, hand the config back, and RECORD A FAILURE — silence here would be
     * indistinguishable from a clean run to anything reading the log, which is the exact confusion
     * {@code ALL DONE} exists to prevent.
     */
    private void abort(MinecraftServer server, Throwable cause) {
        done = true;
        String stage = stages[Math.min(current, stages.length - 1)].name();
        releaseScope();
        LethalBreed.LOGGER.error("[LethalBreed] harness {} aborted in stage {} at tick {}",
                suite, stage, tick, cause);
        DevVerdict.check(suite, "harness-error", false,
                "stage " + stage + " threw at tick " + tick + ": " + cause);
        if (reportsSummary) {
            DevVerdict.summary(suite, server);
        }
    }

    /** Restore every option this stage overrode. A failure to restore is logged, never thrown: it must not
     *  mask the original cause when this runs from {@link #abort}. */
    private void releaseScope() {
        if (overrides == null) {
            return;
        }
        try {
            overrides.close();
        } catch (RuntimeException e) {
            LethalBreed.LOGGER.error("[LethalBreed] harness {} could not restore its config overrides",
                    suite, e);
        } finally {
            overrides = null;
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
