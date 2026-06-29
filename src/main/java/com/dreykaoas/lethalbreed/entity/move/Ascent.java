package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Shared bookkeeping for the two vertical-ascent state machines — {@link PillarClimb} (jump-and-place) and
 * {@link WallClimb} (velocity wall-scale). Both share the active flag, the post-give-up cooldown and the
 * height/stall watchdog; only the motion itself differs, which each subclass owns in its own {@code step}.
 */
abstract class Ascent {
    protected final SmartZombie owner;
    protected final Zombie entity;

    protected boolean running = false;
    protected int age = 0;
    protected double startY = 0.0;
    protected int topY = 0;     // highest block-Y reached this ascent (for the stall watchdog)
    protected int rungAge = 0;  // activations since the last full-block height gain
    protected int climbCd = 0;  // post-give-up cooldown before another ascent may start

    protected Ascent(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
    }

    public boolean active() { return running; }
    public boolean onCooldown() { return climbCd > 0; }

    /** Force the ascent off (used when the zombie enters water and must not climb/build). */
    public void cancel() { running = false; }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    public void tickCooldown() {
        if (climbCd > 0) {
            climbCd--;
        }
    }

    /** Reset the per-ascent watchdog bookkeeping; call the moment an ascent starts. */
    protected void beginAscent() {
        age = 0;
        startY = entity.getY();
        topY = entity.blockPosition().getY();
        rungAge = 0;
    }

    /** Advance the stall watchdog from the current block-Y: a new rung resets it, otherwise it ages. Returns
     *  true once the current rung has made no height gain for longer than {@code climbJumpMaxAge} activations
     *  (support can't land / lip overhang / ceiling) — the caller then aborts so it doesn't climb in place. */
    protected boolean updateStallWatchdog() {
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
    protected double risen() {
        return entity.getY() - startY;
    }
}
