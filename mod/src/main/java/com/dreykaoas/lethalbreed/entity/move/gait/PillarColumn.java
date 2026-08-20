package com.dreykaoas.lethalbreed.entity.move.gait;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The bookkeeping of one ascent: is it running, how high has it risen, which XZ cell is the column locked
 * to, and has the current rung stopped making progress.
 *
 * <p>Separated from {@link PillarClimb} because these are the fields a reader has to hold in their head to
 * follow the climb, and none of them touch the world: the climb decides, this remembers.
 */
final class PillarColumn {

    private final SmartZombie owner;
    private final Zombie entity;

    private boolean running = false;
    private int age = 0;
    private double startY = 0.0;
    private int topY = 0;     // highest block-Y reached this ascent (for the stall watchdog)
    private int rungAge = 0;  // activations since the last full-block height gain
    private int climbCd = 0;  // post-give-up cooldown before another ascent may start

    private int colX = 0;
    private int colZ = 0;
    private int standY = 0;   // block-Y the zombie last jumped from (support is laid here)

    PillarColumn(SmartZombie owner, Zombie entity) {
        this.owner = owner;
        this.entity = entity;
    }

    boolean running() {
        return running;
    }

    int age() {
        return age;
    }

    BlockPos supportPos() {
        return new BlockPos(colX, standY, colZ);
    }

    /** True once the zombie has cleared the block it left, so a support can be dropped into that cell. */
    boolean clearOfLastRung(double supportHeight) {
        return entity.getY() >= standY + supportHeight;
    }

    void cancel() {
        running = false;
    }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    void tickCooldown() {
        if (climbCd > 0) {
            climbCd--;
        }
    }

    /** Arm a fresh ascent, or refuse when one is running, the cooldown is up, or the zombie is airborne. */
    boolean start() {
        if (running || climbCd > 0 || !entity.onGround()) {
            return false;
        }
        running = true;
        age = 0;
        startY = entity.getY();
        topY = entity.blockPosition().getY();
        rungAge = 0;
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

    /**
     * Advance the stall watchdog from the current block-Y: a new rung resets it, otherwise it ages. True once
     * the current rung has made no height gain for longer than {@code climbJumpMaxAge} activations (support
     * cannot land, a lip overhangs, a ceiling is in the way), so the caller aborts rather than climb in place.
     */
    boolean stalled() {
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
    double risen() {
        return entity.getY() - startY;
    }

    /** End the ascent normally (topped out, or reached height): drop the jump intent and stop. */
    void finish() {
        entity.setJumping(false);
        running = false;
    }

    /** Abort the ascent (height cap, stall, ceiling) and arm the give-up cooldown so the dispatcher does not
     *  immediately retry it and falls back to ordinary ground movement instead. */
    void giveUp() {
        finish();
        climbCd = FlowConfig.climbGiveUpCooldown;
    }
}
