package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.FlowConfig;
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

    // Per-tick heading scratch, refilled by computeHeading() at the top of each step (few climbing zombies, so
    // reusing fields over a throwaway struct keeps the ascent allocation-free without hurting readability).
    protected double dyToTarget = -1.0; // target height above the feet, or -1 when there is no target
    protected double hx = 0.0;          // horizontal delta to the target (x, z) and its length
    protected double hz = 0.0;
    protected double h = 0.0;

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

    /** Common {@code step} preamble: bail while inactive, drop out (and clear {@code running}) if the owner is
     *  no longer valid, otherwise age the ascent one tick. Returns true when the subclass should keep stepping. */
    protected boolean beginStep() {
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
    protected void computeHeading() {
        dyToTarget = owner.hasTarget() ? (owner.tgtY() - entity.getY()) : -1.0;
        hx = owner.tgtX() - entity.getX();
        hz = owner.tgtZ() - entity.getZ();
        h = Math.sqrt(hx * hx + hz * hz);
    }

    /** End the ascent normally (topped out / reached height): drop the jump intent and clear {@code running}. */
    protected void finish() {
        entity.setJumping(false);
        running = false;
    }

    /** Abort the ascent (height cap / stall / ceiling) and arm the give-up cooldown so the dispatcher falls back
     *  to the other ascent instead of immediately retrying this one. */
    protected void giveUp() {
        finish();
        climbCd = FlowConfig.climbGiveUpCooldown;
    }
}
