package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.dev.contam.ContamRig;
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
 * <p><b>Why reload-accumulation is the interesting case.</b> {@code ContaminationState.tracked} is a
 * {@code HashSet<LivingEntity>} and {@code Entity.hashCode()} is the monotonic entity id, so a victim that
 * unloads and re-deserialises is NEVER equal to its previous incarnation. With {@code onLoad} ungated, each of
 * the five reload cycles below added a fresh entry to a set the (disabled) sweep could no longer clean —
 * five distinct dead entities pinned forever, one per reload (audit #9). The fix gates {@code onLoad} on the
 * same flag, so the expected count here is 0, not 5.
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
    private static final int CYCLE_FIRST_TICK = 60;
    private static final int CYCLE_PERIOD = 40;
    private static final int CYCLES = 5;
    /** Half a period after each unforce: long enough for the chunk to actually drop before we pull it back. */
    private static final int CYCLE_RELOAD_OFFSET = 20;
    private static final int NO_ACCUM_TICK = 280;
    private static final int REENABLE_TICK = 290;
    private static final int FINAL_RELOAD_TICK = 310;
    private static final int EVAL_TICK = 360;

    private UUID victimId;
    private int reloadsSeen;
    private ConfigOverride cfg;

    private PlagueDisableHarness() {
        super("plague", true, new Stage("switch", SETUP_TICK, EVAL_TICK));
    }

    @Override
    protected boolean enabled() {
        return ProgressionConfig.devPlagueTest;
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
        ContaminationManager.forceSymptomatic(c);
        check("tracked-on-infect", ContaminationState.tracked.size() == 1,
                "tracked.size()=" + ContaminationState.tracked.size() + " after one contaminate() — "
                        + "the baseline the purge/accumulation checks below are measured against");
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        switch (tick) {
            case DISABLE_TICK -> cfg.set("contaminationEnabled", false);
            case PURGE_CHECK_TICK -> check("purge-on-disable",
                    ContaminationState.tracked.isEmpty(),
                    "tracked.size()=" + ContaminationState.tracked.size() + " two ticks after "
                            + "contaminationEnabled=false");
            case NO_ACCUM_TICK -> check("no-accumulation",
                    ContaminationState.tracked.isEmpty(),
                    "tracked.size()=" + ContaminationState.tracked.size() + " after " + reloadsSeen
                            + " unforce/re-force cycles with the plague disabled (pre-fix this was one entry "
                            + "per reload, since Entity.hashCode() is the entity id)");
            case REENABLE_TICK -> {
                cfg.set("contaminationEnabled", true);
                ArenaBuilder.releaseChunks(ow, CX, CZ);
            }
            case FINAL_RELOAD_TICK -> ArenaBuilder.forceChunks(ow, CX, CZ);
            default -> cycle(ow, tick);
        }
    }

    /** Five unforce → (20 ticks) → re-force cycles, so the victim is genuinely re-deserialised each time. */
    private void cycle(ServerLevel ow, int tick) {
        int since = tick - CYCLE_FIRST_TICK;
        if (since < 0 || since >= CYCLES * CYCLE_PERIOD) {
            return;
        }
        int phase = since % CYCLE_PERIOD;
        if (phase == 0) {
            ArenaBuilder.releaseChunks(ow, CX, CZ);
        } else if (phase == CYCLE_RELOAD_OFFSET) {
            ArenaBuilder.forceChunks(ow, CX, CZ);
            reloadsSeen++;
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        // Re-find, never re-use: the pre-reload object is dead and its attachments are frozen at unload time.
        Entity found = victimId == null ? null : ow.getEntity(victimId);
        LivingEntity le = found instanceof LivingEntity l ? l : null;
        boolean contaminated = le != null && ContaminationManager.isContaminated(le);
        boolean inSet = le != null && ContaminationState.tracked.contains(le);
        boolean symptomatic = le != null && ContaminationManager.isSymptomatic(le);
        check("resumes-after-reenable", contaminated && inSet && symptomatic,
                "re-found by UUID=" + (le != null) + " contaminated=" + contaminated + " tracked.contains="
                        + inSet + " symptomatic=" + symptomatic + " (tracked.size()="
                        + ContaminationState.tracked.size() + ")");

        if (le != null) {
            le.discard();
        }
        ContamRig.release(ow, CX, CZ);
    }
}
