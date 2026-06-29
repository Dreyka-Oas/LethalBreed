package com.dreykaoas.lethalbreed.entity.move;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.block.MaterialRegistry;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared leaf math/helpers for the zombie movement steps. Stateless, no Minecraft-side mutation. */
public final class MoveMath {
    private MoveMath() {
    }

    /** Quantise a signed delta to a cardinal step: +1 / -1 / 0 (dead-zone within 0.5). */
    public static int stepSign(double d) {
        return d > 0.5 ? 1 : (d < -0.5 ? -1 : 0);
    }

    /**
     * Fall the zombie would take stepping into column (x,z) from feet-level {@code y}: 0 = flat ground
     * straight ahead, 1 = a one-block step-down, etc. {@link Integer#MAX_VALUE} when no solid landing is
     * found within {@code max} below — a genuine pit / unsafe fall it should bridge or stair instead.
     * The scanned column is clear above the landing by construction, so the fall path is unobstructed.
     */
    static int fallDistanceInto(ServerLevel level, int x, int y, int z, int max) {
        for (int yy = y - 1; yy >= y - 1 - max; yy--) {
            if (level.getBlockState(new BlockPos(x, yy, z)).blocksMotion()) {
                return (y - 1) - yy;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * An upward jump impulse with the live Jump Boost effect folded in — so a zombie given the potion (or any
     * mod adding the effect) jumps higher dynamically, exactly like vanilla {@code getJumpPower()} adds
     * {@code 0.1 * (amplifier + 1)}. Never hard-codes the boost; reads the current effect each jump.
     */
    static double jumpVelocity(LivingEntity entity, double base) {
        MobEffectInstance jump = entity.getEffect(MobEffects.JUMP_BOOST);
        return jump != null ? base + 0.1 * (jump.getAmplifier() + 1) : base;
    }

    /**
     * Horizontal multiplier for the leap, folding in the custom {@link LethalBreedEffects#LEAP} effect — the
     * horizontal analogue of {@link #jumpVelocity}: each level adds {@code leapEffectPerLevel} to the reach.
     * Returns 1.0 when the zombie doesn't carry the effect. Read live each leap (dynamic, never hard-coded).
     */
    static double leapDistanceFactor(LivingEntity entity) {
        MobEffectInstance e = entity.getEffect(LethalBreedEffects.LEAP);
        return e != null ? 1.0 + WorldSpawnConfig.leapEffectPerLevel * (e.getAmplifier() + 1) : 1.0;
    }

    /**
     * How many vertical blocks the zombie must clear to walk through — its actual occupied height
     * (`getBbHeight`, which already reflects the per-zombie SCALE), rounded up. Capped by {@code
     * maxBreakHeight} so a giant doesn't bore a huge tunnel, floored at 1.
     */
    static int breakHeight(LivingEntity entity) {
        int n = (int) Math.ceil(entity.getBbHeight() - 1.0e-4);
        return Math.max(1, Math.min(n, CombatMoveConfig.maxBreakHeight));
    }

    /** True if (x,y,z) is a motion-blocking block the configured material rules allow breaking. */
    static boolean breakableSolid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.blocksMotion() && MaterialRegistry.isBreakable(level, p, s);
    }

    public static String f1(double v) {
        return String.format("%.1f", v);
    }
}
