package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Jump-and-place vertical ascent. When a target is perched above with no flush wall to scale, the zombie
 * builds a dirt column straight up beneath itself — a real jump cycle (velocity impulse + {@code
 * hurtMarked}), never a setPos levitation, so it stands on what it builds. Owns the pillar mutable state
 * and the post-give-up climb cooldown. See the {@code entity-velocity-not-applying} skill.
 */
public final class PillarClimb extends Ascent {
    private int pillarColX = 0;
    private int pillarColZ = 0;
    private int pillarStandY = 0; // block-Y the zombie last jumped from (support is laid here)

    public PillarClimb(SmartZombie owner) {
        super(owner);
    }

    /**
     * Begin building a dirt column up toward a target perched above (wall, tower, overhang or open gap).
     * {@link #step} drives a real jump-and-place cycle each tick — like a player pillaring up — so the
     * zombie stands on what it builds and never levitates. The column is auto-removed by the tracker.
     */
    public void initiate() {
        if (running || climbCd > 0 || !entity.onGround()) {
            return;
        }
        running = true;
        beginAscent();
        // Lock the column to where we start so the whole pillar rises straight up one fixed XZ cell.
        pillarColX = entity.blockPosition().getX();
        pillarColZ = entity.blockPosition().getZ();
        pillarStandY = entity.blockPosition().getY();
        owner.setState(ZombieState.BUILDING);
    }

    /**
     * Per-tick while pillaring: a real jump-and-place cycle (no levitation). On the ground the zombie is
     * launched with a one-shot upward velocity impulse; once airborne and clear of the block it left, a dirt
     * support is dropped into that cell so it lands one block higher. Repeats until it reaches the target's
     * height, the height cap, or a ceiling.
     */
    public void step(ServerLevel level, WorldAIContext ctx) {
        if (!running) {
            return;
        }
        if (!owner.isValid()) {
            running = false;
            return;
        }
        age++;

        double dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        double hx = owner.tgtX() - entity.getX();
        double hz = owner.tgtZ() - entity.getZ();
        double h = Math.sqrt(hx * hx + hz * hz);

        if (ProgressionConfig.debugClimb && (age % 3 == 1)) {
            LethalBreed.LOGGER.info("[ClimbDbg] z{} PILLAR y={} dyTgt={} horiz={} age={} risen={} ground={}",
                    entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(dyToTarget), MoveMath.f1(h), age,
                    MoveMath.f1(risen()), entity.onGround());
        }

        // Reached the target's height → hop forward off the column toward the target and stop.
        if (!owner.hasTarget() || dyToTarget < 1.0) {
            if (h > 0.001) {
                entity.setDeltaMovement(hx / h * 0.4, MoveMath.jumpVelocity(entity, 0.42), hz / h * 0.4);
                entity.hurtMarked = true;
            }
            entity.setJumping(false);
            running = false;
            return;
        }
        boolean stalled = updateStallWatchdog();

        // Height budget spent, a solid ceiling blocks further rise, or the rung stalled → give up; the column
        // stays (and is auto-removed by the tracker). The zombie stands on what it built.
        boolean ceiling = level.getBlockState(BlockPos.containing(
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.25, entity.getZ())).blocksMotion();
        if (risen() >= FlowConfig.pillarMaxHeight || ceiling || stalled) {
            entity.setJumping(false);
            running = false;
            climbCd = FlowConfig.climbGiveUpCooldown;
            return;
        }

        // Stop navigation so a path doesn't drag the zombie off its spot.
        entity.getNavigation().stop();

        // Face the target so the zombie looks where it is climbing (not staring sideways mid-jump).
        MoveMath.faceHeading(entity, hx, hz);

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
