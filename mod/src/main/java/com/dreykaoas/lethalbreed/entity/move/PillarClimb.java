package com.dreykaoas.lethalbreed.entity.move;


import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The vertical-ascent state machine: jump-and-place. When a target is perched above with no flush wall to
 * scale, the zombie builds a dirt column straight up beneath itself — a real jump cycle (velocity impulse +
 * {@code hurtMarked}), never a setPos levitation, so it stands on what it builds. Owns the whole ascent:
 * the active flag, the post-give-up cooldown, the height/stall watchdog and the column bookkeeping.
 * See the {@code entity-velocity-not-applying} skill.
 */
public final class PillarClimb {
    private final SmartZombie owner;
    private final Zombie entity;

    private boolean running = false;
    private int age = 0;
    private double startY = 0.0;
    private int topY = 0;     // highest block-Y reached this ascent (for the stall watchdog)
    private int rungAge = 0;  // activations since the last full-block height gain
    private int climbCd = 0;  // post-give-up cooldown before another ascent may start

    // Per-tick heading scratch, refilled by computeHeading() at the top of each step (few climbing zombies, so
    // reusing fields over a throwaway struct keeps the ascent allocation-free without hurting readability).
    private double dyToTarget = -1.0; // target height above the feet, or -1 when there is no target
    private double hx = 0.0;          // horizontal delta to the target (x, z) and its length
    private double hz = 0.0;
    private double h = 0.0;

    private int pillarColX = 0;
    private int pillarColZ = 0;
    private int pillarStandY = 0; // block-Y the zombie last jumped from (support is laid here)

    public PillarClimb(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
    }

    public boolean active() { return running; }

    /** Force the ascent off (used when the zombie enters water and must not climb/build). */
    public void cancel() { running = false; }

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
        if (running || climbCd > 0 || !entity.onGround()) {
            return;
        }
        running = true;
        // Reset the per-ascent watchdog bookkeeping the moment the ascent starts.
        age = 0;
        startY = entity.getY();
        topY = entity.blockPosition().getY();
        rungAge = 0;
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
        if (!beginStep()) {
            return;
        }
        computeHeading();

        if (DevTestConfig.debugClimb && (age % 3 == 1)) {
            LethalBreed.LOGGER.info("[ClimbDbg] z{} PILLAR y={} dyTgt={} horiz={} age={} risen={} ground={}",
                    entity.getId(), MoveMath.f1(entity.getY()), MoveMath.f1(dyToTarget), MoveMath.f1(h), age,
                    MoveMath.f1(risen()), entity.onGround());
        }

        // Reached the target's height → hop forward off the column toward the target and stop.
        if (!owner.hasTarget() || dyToTarget < CombatMoveConfig.pillarFinishHeight) {
            if (h > ExpertConfig.expertPillarHeadingEpsilon) {
                double fs = CombatMoveConfig.pillarFinishSpeed;
                entity.setDeltaMovement(hx / h * fs,
                        MoveMath.jumpVelocity(entity, CombatMoveConfig.pillarFinishJump), hz / h * fs);
                entity.hurtMarked = true;
            }
            finish();
            return;
        }
        boolean stalled = updateStallWatchdog();

        // A solid ceiling straight overhead blocks the rise. Instead of giving up, mine it out like a player
        // pillaring into a roof: request the block each tick (progressive break) and keep the column running so
        // the zombie resumes climbing once it's gone. Only break breakable blocks — bedrock/containers stop us.
        BlockPos ceilPos = BlockPos.containing(
                entity.getX(), entity.getY() + entity.getBbHeight() + ExpertConfig.expertPillarCeilingOffset,
                entity.getZ());
        boolean ceiling = level.getBlockState(ceilPos).blocksMotion();
        if (ceiling && MoveMath.breakableSolid(level, ceilPos)) {
            ctx.breakManager().request(ceilPos, entity);
            owner.setState(ZombieState.BREAKING);
            entity.setJumping(false);
            return; // hold position (don't jump into an unbroken ceiling) — retry next tick
        }

        // Height budget spent, an unbreakable ceiling, or the rung stalled → give up; the column stays (and is
        // auto-removed by the tracker). The zombie stands on what it built.
        if (risen() >= FlowConfig.pillarMaxHeight || ceiling || stalled) {
            giveUp();
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
            if (entity.getY() >= pillarStandY + ExpertConfig.expertPillarSupportHeight) {
                ctx.blockOps().enqueuePlace(new BlockPos(pillarColX, pillarStandY, pillarColZ));
            }
        }
    }

    /** {@code step} preamble: bail while inactive, drop out (and clear {@code running}) if the owner is
     *  no longer valid, otherwise age the ascent one tick. Returns true when {@link #step} should continue. */
    private boolean beginStep() {
        if (!running) {
            return false;
        }
        if (!owner.isValid()) {
            running = false;
            return false;
        }
        age++;
        return true;
    }

    /** Refill the heading scratch ({@link #dyToTarget}, {@link #hx}, {@link #hz}, {@link #h}) toward the current
     *  target — or a downward {@code dyToTarget} when there is none. */
    private void computeHeading() {
        dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        hx = owner.tgtX() - entity.getX();
        hz = owner.tgtZ() - entity.getZ();
        h = Math.sqrt(hx * hx + hz * hz);
    }

    /** Advance the stall watchdog from the current block-Y: a new rung resets it, otherwise it ages. Returns
     *  true once the current rung has made no height gain for longer than {@code climbJumpMaxAge} activations
     *  (support can't land / lip overhang / ceiling) — the caller then aborts so it doesn't climb in place. */
    private boolean updateStallWatchdog() {
        int curY = entity.blockPosition().getY();
        if (curY > topY) {
            topY = curY;
            rungAge = 0;
        } else {
            rungAge++;
        }
        return rungAge > CombatMoveConfig.climbJumpMaxAge;
    }

    /** Height risen since this ascent began. */
    private double risen() {
        return entity.getY() - startY;
    }

    /** End the ascent normally (topped out / reached height): drop the jump intent and clear {@code running}. */
    private void finish() {
        entity.setJumping(false);
        running = false;
    }

    /** Abort the ascent (height cap / stall / ceiling) and arm the give-up cooldown so the dispatcher does not
     *  immediately retry it and instead falls back to ordinary ground movement. */
    private void giveUp() {
        finish();
        climbCd = FlowConfig.climbGiveUpCooldown;
    }
}
