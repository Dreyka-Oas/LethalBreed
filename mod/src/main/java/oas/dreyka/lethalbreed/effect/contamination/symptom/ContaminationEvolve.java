package oas.dreyka.lethalbreed.effect.contamination.symptom;

import oas.dreyka.lethalbreed.effect.contamination.ContaminationRoll;
import oas.dreyka.lethalbreed.effect.contamination.ContaminationState;
import oas.dreyka.lethalbreed.effect.contamination.PlagueDeadlines;

import oas.dreyka.lethalbreed.config.domain.ContaminationConfig;

import net.minecraft.world.entity.LivingEntity;

/** Level-up roll while symptomatic: every 1-2 in-game days a chance to climb one level toward the cap. */
public final class ContaminationEvolve {
    private ContaminationEvolve() {}

    public static void tickEvolve(LivingEntity e, long t) {
        if (ContaminationState.level(e) >= Math.max(1, ContaminationConfig.contamMaxLevel)) {
            return;
        }
        Long roll = ContaminationState.NEXT_EVOLVE_ROLL_TICK.get(e);
        if (roll == null) {
            armRoll(e, t);
            return;
        }
        if (t >= roll) {
            if (ContaminationRoll.percent(ContaminationState.RNG,
                    ContaminationConfig.contamEvolveMinPct, ContaminationConfig.contamEvolveMaxPct)) {
                ContaminationState.setLevel(e, ContaminationState.level(e) + 1);
            }
            armRoll(e, t);
        }
    }

    private static void armRoll(LivingEntity e, long t) {
        PlagueDeadlines.set(PlagueDeadlines.Deadline.EVOLVE_ROLL, e, t + rollEvolveIntervalTicks());
    }

    /** Next level-up roll delay in ticks, uniform in [minDays, maxDays] x 24000. */
    private static long rollEvolveIntervalTicks() {
        return ContaminationState.rollScaled(ContaminationConfig.contamEvolveMinDays,
                ContaminationConfig.contamEvolveMaxDays, 1.0, 24000.0);
    }
}
