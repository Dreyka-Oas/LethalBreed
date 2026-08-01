package com.dreykaoas.lethalbreed.dev.contam;

import com.dreykaoas.lethalbreed.dev.ArenaBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;

/**
 * The two pieces of scaffolding every contamination harness needs, written once: a roofed slab of arena in the
 * verification band, and an inert Cow victim.
 *
 * <p><b>Why a Cow.</b> {@code ContaminationLifecycle.contaminate} refuses outright on any {@code Zombie}, so a
 * zombie prop can never be contaminated and every plague assertion against one would pass vacuously. The
 * existing {@code MechTestArena.buildContamination} already settled on a cow for exactly this reason; this is
 * the same choice, factored out rather than copied.
 *
 * <p><b>Why {@code setNoAi} + {@code setPersistenceRequired}.</b> A wandering victim leaves the force-loaded
 * column and a despawned one vanishes mid-measurement — both look identical to a mechanic that stopped
 * working. Neither is a property of the plague, so both are removed as confounders.
 *
 * <p><b>Why the health override.</b> The damage rig deliberately runs a fast, heavy pulse (see
 * {@code PlagueDamageHarness}); a stock 10-HP cow dies within four pulses and the run measures a corpse
 * instead of a health curve. Raising MAX_HEALTH keeps the victim alive for the whole observation window
 * without ever healing it, which would destroy the monotone-decrease assertion.
 */
public final class ContamRig {
    private ContamRig() {}

    /** Arena floor Y shared with the rest of the verification band. */
    public static final int Y = ArenaBuilder.VERIFY_Y;

    // ---------------------------------------------------------------------------------------------------
    // Suite schedule. The three "plague" rigs run in ONE server lifetime and cannot overlap in TIME, only in
    // space, because two of them mutate PROCESS-GLOBAL state:
    //   * PlagueDamageHarness rewrites ContaminationConfig's damage/interval range for its whole window;
    //   * PlagueDisableHarness switches ContaminationConfig.contaminationEnabled OFF for ~250 ticks, which
    //     would freeze every other rig's plague dead and make their checks pass or fail for the wrong reason;
    //   * LeakProbeHarness calls ContaminationManager.onServerStopped(), purging everyone's tracking.
    // Serialising them here — one offset per rig, with slack between — is why each can assert on global state
    // at all. The LAST rig in this order is the one that emits DevVerdict.summary (and therefore ALL DONE).
    // ---------------------------------------------------------------------------------------------------

    /** Global server tick at which {@code PlagueDamageHarness} starts its local clock. */
    public static final int DAMAGE_START = 20;
    /** Global server tick at which {@code LeakProbeHarness} starts (after the damage rig restored config). */
    public static final int LEAK_START = 460;
    /** Global server tick at which {@code PlagueDisableHarness} starts. Runs last; owns the suite summary. */
    public static final int DISABLE_START = 620;

    /**
     * Force-load and build a roofed, lit box centred on ({@code cx}, {@code cz}): stone floor at {@code Y-1},
     * clear air {@code Y..Y+3}, glowstone lid at {@code Y+4}. Roofed so no sun-burn / sky-light interaction can
     * confound a plague measurement.
     */
    public static void arena(ServerLevel ow, int cx, int cz, int halfX, int halfZ) {
        ArenaBuilder.forceChunks(ow, cx, cz);
        for (int x = cx - halfX; x <= cx + halfX; x++) {
            for (int z = cz - halfZ; z <= cz + halfZ; z++) {
                ow.setBlock(new BlockPos(x, Y - 1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
                ow.setBlock(new BlockPos(x, Y + 4, z), net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Spawn an inert cow at ({@code x}, {@link #Y}, {@code z}). {@code maxHealth &lt;= 0} keeps the vanilla
     * value; anything larger raises MAX_HEALTH and fills the bar to match.
     *
     * <p>{@code EntityType.COW.spawn} is used rather than {@code create()+addFreshEntity} because a cow's
     * {@code finalizeSpawn} has no baby-roll hazard (unlike the zombie props in the legacy mech arena) and
     * {@code spawn} places it correctly on the floor in one call.
     */
    public static Cow cow(ServerLevel ow, int x, int z, float maxHealth) {
        Cow c = EntityType.COW.spawn(ow, new BlockPos(x, Y, z), EntitySpawnReason.COMMAND);
        if (c == null) {
            return null;
        }
        c.setNoAi(true);
        c.setPersistenceRequired();
        if (maxHealth > 0.0f) {
            AttributeInstance mh = c.getAttribute(Attributes.MAX_HEALTH);
            if (mh != null) {
                mh.setBaseValue(maxHealth);
            }
            c.setHealth(maxHealth);
        }
        return c;
    }

    /** Release the 3x3 force-load taken by {@link #arena}. */
    public static void release(ServerLevel ow, int cx, int cz) {
        ArenaBuilder.releaseChunks(ow, cx, cz);
    }

    public static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
