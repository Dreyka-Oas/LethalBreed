package com.dreykaoas.lethalbreed.entity.move.gait;

import com.dreykaoas.lethalbreed.entity.move.MoveMath;


import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.ExpertConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.probe.DevProbe;
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

    private final PillarColumn column;

    private double dyToTarget = -1.0; // target height above the feet, or -1 when there is no target
    private double hx = 0.0;          // horizontal delta to the target (x, z) and its length
    private double hz = 0.0;
    private double h = 0.0;

    public PillarClimb(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
        this.column = new PillarColumn(owner, this.entity);
    }

    public boolean active() { return column.running(); }

    /** Force the ascent off (used when the zombie enters water and must not climb/build). */
    public void cancel() { column.cancel(); }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    public void tickCooldown() {
        column.tickCooldown();
    }

    /**
     * Begin building a dirt column up toward a target perched above (wall, tower, overhang or open gap).
     * {@link #step} drives a real jump-and-place cycle each tick — like a player pillaring up — so the
     * zombie stands on what it builds and never levitates. The column is auto-removed by the tracker.
     */
    public void initiate() {
        column.start();
    }

    /**
     * Per-tick while pillaring: a real jump-and-place cycle (no levitation). On the ground the zombie is
     * launched with a one-shot upward velocity impulse; once airborne and clear of the block it left, a dirt
     * support is dropped into that cell so it lands one block higher. Repeats until it reaches the target's
     * height, the height cap, or a ceiling.
     */
    public void step(ServerLevel level, WorldAiContext ctx) {
        if (!column.beginStep()) {
            return;
        }
        computeHeading();

        if (DevProbe.tracing(DevProbe.CLIMB) && (column.age() % 3 == 1)) {
            DevProbe.sink.trace(DevProbe.CLIMB, "z" + entity.getId()
                    + " PILLAR y=" + MoveMath.f1(entity.getY())
                    + " dyTgt=" + MoveMath.f1(dyToTarget)
                    + " horiz=" + MoveMath.f1(h)
                    + " age=" + column.age()
                    + " risen=" + MoveMath.f1(column.risen())
                    + " ground=" + entity.onGround());
        }

        // Reached the target's height → hop forward off the column toward the target and stop.
        if (!owner.hasTarget() || dyToTarget < CombatMoveConfig.pillarFinishHeight) {
            if (h > ExpertConfig.expertPillarHeadingEpsilon) {
                double fs = CombatMoveConfig.pillarFinishSpeed;
                entity.setDeltaMovement(hx / h * fs,
                        MoveMath.jumpVelocity(entity, CombatMoveConfig.pillarFinishJump), hz / h * fs);
                entity.hurtMarked = true;
            }
            column.finish();
            return;
        }
        boolean stalled = column.stalled();

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
        if (column.risen() >= FlowConfig.pillarMaxHeight || ceiling || stalled) {
            column.giveUp();
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
            column.lockColumn();
            entity.setDeltaMovement(0.0, MoveMath.jumpVelocity(entity, FlowConfig.pillarJumpPower), 0.0);
            entity.hurtMarked = true;
        } else {
            // Airborne and clear of the block we left → drop a support into that cell so we land one higher.
            if (column.clearOfLastRung(ExpertConfig.expertPillarSupportHeight)) {
                ctx.blockOps().enqueuePlace(column.supportPos());
            }
        }
    }

    /** Refill the heading scratch ({@link #dyToTarget}, {@link #hx}, {@link #hz}, {@link #h}) toward the current
     *  target — or a downward {@code dyToTarget} when there is none. */
    private void computeHeading() {
        dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        hx = owner.tgtX() - entity.getX();
        hz = owner.tgtZ() - entity.getZ();
        h = Math.sqrt(hx * hx + hz * hz);
    }
}
