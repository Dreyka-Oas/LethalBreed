package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;

import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.entity.ZombieVariation;
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
        if (!CombatMoveConfig.leapEnabled || owner.isClimbing() || !entity.onGround() || leapCd > 0) {
            return false;
        }
        double horiz = Math.sqrt(horizSq);
        double ldf = MoveMath.leapDistanceFactor(entity); // custom LEAP effect → farther reach (1.0 if absent)
        double lo = Math.min(CombatMoveConfig.leapMinRange, CombatMoveConfig.leapMaxRange);
        double hi = Math.max(CombatMoveConfig.leapMinRange, CombatMoveConfig.leapMaxRange);
        if (horiz < lo || horiz > hi * ldf
                || Math.abs(dy) >= CombatMoveConfig.leapMaxVerticalDiff
                || entity.getRandom().nextFloat() >= CombatMoveConfig.leapChance) {
            return false;
        }
        double inv = 1.0 / horiz;
        double ndx = dx * inv;
        double ndz = dz * inv;
        // Only leap if there's ground to land on — never leap into a gap / off a short bridge.
        if (!hasLanding(level, ndx, ndz)) {
            return false;
        }
        entity.setDeltaMovement(ndx * CombatMoveConfig.leapHorizontalSpeed * leapFactor * ldf,
                MoveMath.jumpVelocity(entity, CombatMoveConfig.leapUpward * leapFactor),
                ndz * CombatMoveConfig.leapHorizontalSpeed * leapFactor * ldf);
        entity.hurtMarked = true;
        leapCd = CombatMoveConfig.leapCooldownActivations;
        owner.setState(ZombieState.PURSUING_PLAYER);
        return true;
    }

    /** True if there is solid ground near where a leap would land (so we don't jump into a gap). */
    private boolean hasLanding(ServerLevel level, double ndx, double ndz) {
        int dist = 3;
        int lx = Mth.floor(entity.getX() + ndx * dist);
        int lz = Mth.floor(entity.getZ() + ndz * dist);
        int ly = Mth.floor(entity.getY());
        for (int yy = ly + 1; yy >= ly - 3; yy--) {
            if (level.getBlockState(new BlockPos(lx, yy, lz)).blocksMotion()) {
                return true;
            }
        }
        return false;
    }
}
