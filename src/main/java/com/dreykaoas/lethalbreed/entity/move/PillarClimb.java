package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Jump-and-place vertical ascent. When a target is perched above with no flush wall to scale, the zombie
 * builds a dirt column straight up beneath itself — a real jump cycle (velocity impulse + {@code
 * hurtMarked}), never a setPos levitation, so it stands on what it builds. Owns the pillar mutable state
 * and the post-give-up climb cooldown. See the {@code entity-velocity-not-applying} skill.
 */
public final class PillarClimb {
    private final SmartZombie owner;
    private final Zombie entity;

    private boolean pillaring = false;
    private int pillarAge = 0;
    private double pillarStartY = 0.0;
    private int pillarColX = 0;
    private int pillarColZ = 0;
    private int pillarStandY = 0; // block-Y the zombie last jumped from (support is laid here)
    private int pillarTopY = 0;   // highest block-Y reached this climb (for the stall watchdog)
    private int pillarRungAge = 0; // activations since the last full-block gain on the current rung
    private int climbCd = 0;

    public PillarClimb(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
    }

    public boolean active() { return pillaring; }
    public boolean onCooldown() { return climbCd > 0; }

    /** Force the ascent off (used when the zombie enters water and must not build). */
    public void cancel() { pillaring = false; }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    public void tickCooldown() {
        if (climbCd > 0) {
            climbCd--;
        }
    }

    /**
     * Begin building a dirt column up toward a target perched above (wall, tower, overhang or open gap).
     * {@link #step} drives a real jump-and-place cycle each tick — like a player pillaring up — so the
     * zombie stands on what it builds and never levitates. The column is auto-removed by the tracker.
     */
    public void initiate() {
        if (pillaring || climbCd > 0 || !entity.onGround()) {
            return;
        }
        pillaring = true;
        pillarAge = 0;
        pillarStartY = entity.getY();
        // Lock the column to where we start so the whole pillar rises straight up one fixed XZ cell.
        pillarColX = entity.blockPosition().getX();
        pillarColZ = entity.blockPosition().getZ();
        pillarStandY = entity.blockPosition().getY();
        pillarTopY = pillarStandY;
        pillarRungAge = 0;
        owner.setState(ZombieState.BUILDING);
    }

    /**
     * Per-tick while pillaring: a real jump-and-place cycle (no levitation). On the ground the zombie is
     * launched with a one-shot upward velocity impulse; once airborne and clear of the block it left, a dirt
     * support is dropped into that cell so it lands one block higher. Repeats until it reaches the target's
     * height, the height cap, or a ceiling.
     */
    public void step(ServerLevel level, WorldAIContext ctx) {
        if (!pillaring) {
            return;
        }
        if (!owner.isValid()) {
            pillaring = false;
            return;
        }
        pillarAge++;

        double dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        double hx = owner.tgtX() - entity.getX();
        double hz = owner.tgtZ() - entity.getZ();
        double h = Math.sqrt(hx * hx + hz * hz);

        if (ProgressionConfig.debugClimb && (pillarAge % 3 == 1)) {
            LethalBreed.LOGGER.info("[ClimbDbg] z{} PILLAR y={} dyTgt={} horiz={} age={} risen={} ground={}",
                    entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(dyToTarget), MoveMath.f1(h), pillarAge,
                    MoveMath.f1(entity.getY() - pillarStartY), entity.onGround());
        }

        // Reached the target's height → hop forward off the column toward the target and stop.
        if (!owner.hasTarget() || dyToTarget < 1.0) {
            if (h > 0.001) {
                entity.setDeltaMovement(hx / h * 0.4, MoveMath.jumpVelocity(entity, 0.42), hz / h * 0.4);
                entity.hurtMarked = true;
            }
            entity.setJumping(false);
            pillaring = false;
            return;
        }
        // Stall watchdog: count activations since the last full-block height gain. Reaching a new rung resets
        // it; if the current rung makes no progress within climbJumpMaxAge activations the support can't land
        // (queue full, sideways-blocked arc, ceiling) — abort so the zombie doesn't jump in place forever.
        int curY = entity.blockPosition().getY();
        if (curY > pillarTopY) {
            pillarTopY = curY;
            pillarRungAge = 0;
        } else {
            pillarRungAge++;
        }

        // Height budget spent, a solid ceiling blocks further rise, or the rung stalled → give up; the column
        // stays (and is auto-removed by the tracker). The zombie stands on what it built.
        boolean ceiling = level.getBlockState(BlockPos.containing(
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.25, entity.getZ())).blocksMotion();
        boolean stalled = pillarRungAge > CombatMoveConfig.climbJumpMaxAge;
        if (entity.getY() - pillarStartY >= FlowConfig.pillarMaxHeight || ceiling || stalled) {
            entity.setJumping(false);
            pillaring = false;
            climbCd = FlowConfig.climbGiveUpCooldown;
            return;
        }

        // Stop navigation so a path doesn't drag the zombie off its spot.
        entity.getNavigation().stop();

        // Face the target so the zombie looks where it is climbing (not staring sideways mid-jump).
        if (h > 1.0e-2) {
            float yaw = (float) (Mth.atan2(hz, hx) * (180.0 / Math.PI)) - 90.0f;
            entity.setYRot(yaw);
            entity.yBodyRot = yaw;
            entity.yHeadRot = yaw;
        }

        if (entity.onGround()) {
            // Grounded on the column: record this rung and launch a jump. A direct upward velocity impulse
            // survives into the next tick's travel() and lifts it ~1.1 blocks. Zero the horizontal component
            // so the hop is straight up onto the support block.
            pillarColX = entity.blockPosition().getX();
            pillarColZ = entity.blockPosition().getZ();
            pillarStandY = entity.blockPosition().getY();
            entity.setDeltaMovement(0.0, MoveMath.jumpVelocity(entity, FlowConfig.pillarJumpPower), 0.0);
            entity.hurtMarked = true;
        } else {
            // Airborne and clear of the block we left → drop a support into that cell so we land one higher.
            if (entity.getY() >= pillarStandY + 1.0) {
                ctx.blockOps().enqueuePlace(new BlockPos(pillarColX, pillarStandY, pillarColZ));
            }
        }
    }
}
