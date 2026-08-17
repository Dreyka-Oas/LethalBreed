package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.harness.ChunkChurn;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;

import com.dreykaoas.lethalbreed.dev.config.ConfigOverride;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;

import java.util.UUID;

/**
 * Verifies the plague's master switch: turning {@code contaminationEnabled} off purges the in-memory tracking
 * immediately, chunk reloads while it is off do not accumulate ghost entries, and turning it back on resumes
 * the disease from its persistent attachments.
 *
 * <p><b>Why reload-accumulation is the interesting case.</b> {@code ContaminationState.TRACKED} is a
 * {@code HashSet<LivingEntity>} and {@code Entity.hashCode()} is the monotonic entity id, so a victim that
 * unloads and re-deserialises is NEVER equal to its previous incarnation. With {@code onLoad} ungated, each
 * reload below added a fresh entry to a set the (disabled) sweep could no longer clean — one dead entity pinned
 * forever per reload (audit #9). The fix gates {@code onLoad} on the same flag, so the expected count is 0.
 *
 * <p><b>The reloads are observed, not scheduled.</b> This rig used to unforce the arena, wait a fixed 20 ticks
 * and re-force, counting a reload each time. The sibling statue rig had already measured that same drop at 2,
 * ~35 and 272 ticks across runs, and once not at all inside 1200 — so on a slow run not one of those "reloads"
 * happened, the victim never left memory, and {@code no-accumulation} passed having exercised nothing. It is
 * now driven by {@link ChunkChurn}, which advances only on the witness actually leaving memory and coming back,
 * and the verdict FAILS when zero real round trips were obtained rather than passing on an empty set.
 *
 * <p><b>Never hold the entity across a reload.</b> The object is discarded and rebuilt by deserialisation, so
 * a cached reference would be a dead object whose attachments are frozen at unload time — it would report
 * whatever we last wrote and prove nothing. Every read below re-finds the victim by UUID.
 *
 * <p>Runs LAST in the plague suite (see {@link ContamRig#DISABLE_START}) because it takes the master switch
 * away from everyone else, and therefore is the one rig of the three that reports the suite summary.
 */
public final class PlagueDisableHarness extends TickPhasedHarness {

    public static final PlagueDisableHarness INSTANCE = new PlagueDisableHarness();

    private static final int CX = 90;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;

    private static final int SETUP_TICK = 5;
    private static final int DISABLE_TICK = 40;
    private static final int PURGE_CHECK_TICK = 42;
    private static final int CHURN_FIRST_TICK = 60;
    /** Round trips attempted with the plague off. Three honest ones prove per-reload accumulation as well as
     *  five guessed ones, and cost a third of the worst-case budget. */
    private static final int ROUNDS = 3;
    /** Per-half budget. Sized from the SLOWEST drop measured on this hardware (272 ticks, plus headroom for the
     *  synchronous external-disk writes that produced the 1200-tick outlier), never from the typical one. */
    private static final int HALF_BUDGET = 600;
    /** Hard deadline only. Every phase below advances on observed state, so a healthy run evaluates within a
     *  few hundred ticks; this is what stops a wedged one from hanging the suite forever. */
    private static final int EVAL_DEADLINE = 5200;

    /** Reactive progression: churn → judge accumulation and re-enable → final round trip → evaluate. */
    private enum Step { CHURNING, REENABLED, DONE }

    private UUID victimId;
    private ConfigOverride cfg;
    private Step step = Step.CHURNING;
    private ChunkChurn churn;
    private ChunkChurn finalTrip;

    private PlagueDisableHarness() {
        super("plague", true, new Stage("switch", SETUP_TICK, EVAL_DEADLINE));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devPlagueTest;
    }

    @Override
    protected int startAfterServerTick() {
        return ContamRig.DISABLE_START;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride overrides) {
        // Held so observe() can flip the master switch through the same restoring scope.
        cfg = overrides;
        cfg.set("contaminationEnabled", true);
        ContamRig.arena(ow, CX, CZ, 4, 4);
        Cow c = ContamRig.cow(ow, CX, CZ, 0.0f);
        if (c == null) {
            return;
        }
        victimId = c.getUUID();
        ContaminationManager.contaminate(c);
        DevContam.forceSymptomatic(c);
        churn = new ChunkChurn("plague-disabled reloads", CX, CZ, ROUNDS, HALF_BUDGET);
        check("tracked-on-infect", ContaminationState.TRACKED.size() == 1,
                "tracked.size()=" + ContaminationState.TRACKED.size() + " after one contaminate() — "
                        + "the baseline the purge/accumulation checks below are measured against");
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        switch (tick) {
            case DISABLE_TICK -> cfg.set("contaminationEnabled", false);
            case PURGE_CHECK_TICK -> check("purge-on-disable",
                    ContaminationState.TRACKED.isEmpty(),
                    "tracked.size()=" + ContaminationState.TRACKED.size() + " two ticks after "
                            + "contaminationEnabled=false");
            default -> churnStep(ow, tick);
        }
    }

    private void churnStep(ServerLevel ow, int tick) {
        if (tick < CHURN_FIRST_TICK || churn == null) {
            return;
        }
        if (step == Step.CHURNING) {
            churn.drive(ow, tick, victimId);
            if (churn.finished()) {
                judgeAccumulation();
                cfg.set("contaminationEnabled", true);
                // One more genuine round trip, now with the plague back on: resumption has to come off the
                // PERSISTENT attachment of a freshly deserialised victim, not off the entry we never dropped.
                finalTrip = new ChunkChurn("plague-reenabled reload", CX, CZ, 1, HALF_BUDGET);
                step = Step.REENABLED;
            }
            return;
        }
        if (step == Step.REENABLED) {
            finalTrip.drive(ow, tick, victimId);
            if (finalTrip.finished()) {
                step = Step.DONE;
            }
        }
    }

    /** Zero real round trips means the set was never given a chance to grow: that is a harness failure, not a
     *  clean bill of health, and it must read as one. */
    private void judgeAccumulation() {
        check("no-accumulation",
                ContaminationState.TRACKED.isEmpty() && churn.completed() > 0,
                "tracked.size()=" + ContaminationState.TRACKED.size() + " after " + churn.diagnosis()
                        + " with the plague disabled (pre-fix this was one entry per reload, since "
                        + "Entity.hashCode() is the entity id)");
    }

    @Override
    protected boolean readyToEvaluate(int stage, int tick) {
        return step == Step.DONE;
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        if (step != Step.DONE) {
            check("switch-completed", false, "the rig hit its " + EVAL_DEADLINE + "-tick deadline still in step "
                    + step + " — " + (churn == null ? "the arena was never built" : churn.diagnosis()));
        }
        // Re-find, never re-use: the pre-reload object is dead and its attachments are frozen at unload time.
        Entity found = victimId == null ? null : ow.getEntity(victimId);
        LivingEntity le = found instanceof LivingEntity l ? l : null;
        boolean contaminated = le != null && ContaminationManager.isContaminated(le);
        boolean inSet = le != null && ContaminationState.TRACKED.contains(le);
        boolean symptomatic = le != null && ContaminationManager.isSymptomatic(le);
        check("resumes-after-reenable", contaminated && inSet && symptomatic,
                "re-found by UUID=" + (le != null) + " contaminated=" + contaminated + " tracked.contains="
                        + inSet + " symptomatic=" + symptomatic + " (tracked.size()="
                        + ContaminationState.TRACKED.size() + ", "
                        + (finalTrip == null ? "no post-re-enable reload was attempted" : finalTrip.diagnosis())
                        + ")");

        if (le != null) {
            le.discard();
        }
        ContamRig.release(ow, CX, CZ);
    }
}
