package com.dreykaoas.lethalbreed.entity.mood;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Pure helpers for the daytime-sleep behaviour: whether a zombie still burns in the sun at the current phase,
 * and whether THIS zombie is one of the growing minority that stays awake by day at high phases.
 *
 * <p>The awake fraction is a function of the PHASE, not of the individual — {@code awake(phase) =
 * clamp((phase - dayAwakePhaseStart + 1) * dayAwakePhaseSlope, 0, 1)}. Each zombie carries a stable
 * per-entity roll in {@code [0,1)}; it stays awake when its roll falls under the fraction. Because the
 * threshold only rises with the phase, the awake set only ever grows — a zombie awake at phase 15 is still
 * awake at phase 20 (its roll never changes).
 */
public final class DaySleep {
    private DaySleep() {}

    /** True while daylight still burns zombies (below the immunity phase) — they must reach shade to sleep. */
    public static boolean burnsInSun(int phase) {
        return phase < WorldSpawnConfig.sunImmunePhase;
    }

    /** Fraction [0,1] of the horde that stays awake during the day at this phase. 0 below the start phase. */
    public static double awakeFraction(int phase) {
        if (phase < ZombieMoodConfig.dayAwakePhaseStart) {
            return 0.0;
        }
        double f = (phase - ZombieMoodConfig.dayAwakePhaseStart + 1) * ZombieMoodConfig.dayAwakePhaseSlope;
        return f < 0.0 ? 0.0 : (f > 1.0 ? 1.0 : f);
    }

    /** True when THIS zombie belongs to the awake minority (so it does NOT sleep during the day). */
    public static boolean staysAwake(Zombie entity, int phase) {
        double frac = awakeFraction(phase);
        return frac > 0.0 && roll01(entity) < frac;
    }

    /** Stable per-zombie value in [0,1) from its UUID — same every tick, survives world reload, well spread.
     *  Computed in double: the max masked hash (2^31-1) over 2^31 stays strictly below 1.0, so at a 100% awake
     *  fraction every zombie qualifies (a float divide would round the top hash up to exactly 1.0 and miss it). */
    private static double roll01(Zombie entity) {
        int h = entity.getUUID().hashCode() & 0x7fffffff;
        return h / 2147483648.0; // 2^31 — result in [0, 0.99999...]
    }
}
