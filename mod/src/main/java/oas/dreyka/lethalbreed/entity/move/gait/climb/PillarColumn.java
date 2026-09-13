package oas.dreyka.lethalbreed.entity.move.gait.climb;

import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;
import oas.dreyka.lethalbreed.config.domain.engine.FlowConfig;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The bookkeeping of one ascent, read off the entity. Which XZ cell the column is locked to lives here,
 * because it is a block position; everything that is only arithmetic lives in {@link ClimbProgress}, where
 * it can be tested without a world.
 *
 * <p>Separated from {@link PillarClimb} because these are the fields a reader has to hold in their head to
 * follow the climb, and none of them touch the world: the climb decides, this remembers.
 */
final class PillarColumn {

    private final SmartZombie owner;
    private final Zombie entity;

    private final ClimbProgress progress = new ClimbProgress();

    private int colX = 0;
    private int colZ = 0;
    private int standY = 0;   // block-Y the zombie last jumped from (support is laid here)

    PillarColumn(SmartZombie owner, Zombie entity) {
        this.owner = owner;
        this.entity = entity;
    }

    boolean running() {
        return progress.running();
    }

    int age() {
        return progress.age();
    }

    BlockPos supportPos() {
        return new BlockPos(colX, standY, colZ);
    }

    /** True once the zombie has cleared the block it left, so a support can be dropped into that cell. */
    boolean clearOfLastRung(double supportHeight) {
        return entity.getY() >= standY + supportHeight;
    }

    void cancel() {
        progress.cancel();
    }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    void tickCooldown() {
        progress.tickCooldown();
    }

    /** Arm a fresh ascent, or refuse when one is running, the cooldown is up, or the zombie is airborne. */
    boolean start() {
        if (!progress.start(entity.onGround(), entity.getY(), entity.blockPosition().getY())) {
            return false;
        }
        lockColumn(); // the whole pillar rises straight up one fixed XZ cell
        owner.setState(ZombieState.BUILDING);
        return true;
    }

    /** Record the rung the zombie is about to jump from. */
    void lockColumn() {
        BlockPos here = entity.blockPosition();
        colX = here.getX();
        colZ = here.getZ();
        standY = here.getY();
    }

    /** Bail while inactive, drop out if the owner is no longer valid, otherwise age the ascent one tick. */
    boolean beginStep() {
        return progress.beginStep(owner.isValid());
    }

    /** True once the current rung has made no height gain for longer than {@code climbJumpMaxAge}
     *  activations, so the caller aborts where it would otherwise climb in place. */
    boolean stalled() {
        return progress.stalled(entity.blockPosition().getY(), CombatMoveConfig.climbJumpMaxAge);
    }

    /** Height risen since this ascent began. */
    double risen() {
        return progress.risen(entity.getY());
    }

    /** End the ascent normally (topped out, or reached height): drop the jump intent and stop. */
    void finish() {
        entity.setJumping(false);
        progress.finish();
    }

    /** Abort the ascent (height cap, stall, ceiling) and arm the give-up cooldown so the dispatcher does not
     *  immediately retry it and falls back to ordinary ground movement instead. */
    void giveUp() {
        entity.setJumping(false);
        progress.giveUp(FlowConfig.climbGiveUpCooldown);
    }
}
