package oas.dreyka.lethalbreed.entity.move.gait;

import oas.dreyka.lethalbreed.entity.move.MoveMath;


import oas.dreyka.lethalbreed.config.domain.move.LeapConfig;

import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieState;
import oas.dreyka.lethalbreed.entity.genes.ZombieVariation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Occasional leap/pounce to catch a target at mid-range. Only fires from the ground, off cooldown, within
 * the configured range band, and only when there is real ground to land on (never into a gap). Owns its
 * own cooldown and the per-zombie leap-distance factor.
 */
public final class Leap {
    private final SmartZombie owner;
    private final Zombie entity;
    private final double leapFactor;
    private int leapCd = 0;

    public Leap(SmartZombie owner) {
        this.owner = owner;
        this.entity = owner.entity();
        this.leapFactor = ZombieVariation.leapFactor(entity);
    }

    /** Decrement the leap cooldown each activation (called from the bucketed tick). */
    public void tickCooldown() {
        if (leapCd > 0) {
            leapCd--;
        }
    }

    /**
     * Try a leap toward (dx,dz) at horizontal distance² {@code horizSq}, vertical offset {@code dy}. Returns
     * true when it leapt (the caller should let the arc carry the zombie this activation).
     */
    public boolean tryLeap(ServerLevel level, double dx, double dz, double dy, double horizSq) {
        if (!LeapConfig.leapEnabled || owner.isClimbing() || !entity.onGround() || leapCd > 0) {
            return false;
        }
        double horiz = Math.sqrt(horizSq);
        double ldf = MoveMath.leapDistanceFactor(entity); // custom LEAP effect → farther reach (1.0 if absent)
        double lo = Math.min(LeapConfig.leapMinRange, LeapConfig.leapMaxRange);
        double hi = Math.max(LeapConfig.leapMinRange, LeapConfig.leapMaxRange);
        if (horiz < lo || horiz > hi * ldf
                || Math.abs(dy) >= LeapConfig.leapMaxVerticalDiff
                || entity.getRandom().nextFloat() >= LeapConfig.leapChance) {
            return false;
        }
        double inv = 1.0 / horiz;
        double ndx = dx * inv;
        double ndz = dz * inv;
        // Only leap if there's ground to land on, never leap into a gap / off a short bridge.
        if (!hasLanding(level, ndx, ndz, ldf)) {
            return false;
        }
        entity.setDeltaMovement(ndx * LeapConfig.leapHorizontalSpeed * leapFactor * ldf,
                MoveMath.jumpVelocity(entity, LeapConfig.leapUpward * leapFactor),
                ndz * LeapConfig.leapHorizontalSpeed * leapFactor * ldf);
        entity.hurtMarked = true;
        leapCd = LeapConfig.leapCooldownActivations;
        owner.setState(ZombieState.PURSUING_PLAYER);
        return true;
    }

    /**
     * True if there is solid ground near where a leap would land (so we don't jump into a gap).
     *
     * <p>The probe follows {@code ldf}. The launch velocity below is multiplied by it (a Leaper carrying
     * LEAP flies roughly twice as far) while this scan used a flat {@code leapLandingScanDist}, so the
     * invariant "never leap into a gap" was checked at 3 blocks for a jump landing at 7 or more. Scaling the
     * probe by the same factor keeps the check aimed where the zombie will actually come down.
     */
    private boolean hasLanding(ServerLevel level, double ndx, double ndz, double ldf) {
        int dist = Mth.ceil(LeapConfig.leapLandingScanDist * Math.max(1.0, ldf));
        int lx = Mth.floor(entity.getX() + ndx * dist);
        int lz = Mth.floor(entity.getZ() + ndz * dist);
        int ly = Mth.floor(entity.getY());
        for (int yy = ly + 1; yy >= ly - LeapConfig.leapLandingScanDepth; yy--) {
            if (level.getBlockState(new BlockPos(lx, yy, lz)).blocksMotion()) {
                return true;
            }
        }
        return false;
    }
}
