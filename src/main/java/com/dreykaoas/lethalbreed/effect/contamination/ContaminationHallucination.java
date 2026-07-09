package com.dreykaoas.lethalbreed.effect.contamination;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.effect.contamination.ContaminationEpisodes.EpisodeTimers;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Zombie-vision hallucination flare: a fourth episode on its own random timer, independent of the three
 * {@link ContaminationEpisodes}. Applies/removes the transient ZOMBIE_VISION effect; the victim's client reads
 * it to draw other players as zombies.
 */
public final class ContaminationHallucination {
    private ContaminationHallucination() {}

    /** Zombie-vision hallucination flare timer per victim; transient (reseeded on load, cleared on cure/death). */
    private static final java.util.Map<LivingEntity, EpisodeTimers> hallucTimers = new java.util.HashMap<>();

    /** Drive the zombie-vision hallucination flare for one victim: OFF between flares, ON (ZOMBIE_VISION applied)
     *  for a random duration, then a random gap. Duration scales up / gap scales down with intensity, like episodes. */
    public static void tickHallucination(LivingEntity e, long t, double mult) {
        EpisodeTimers st = hallucTimers.computeIfAbsent(e, k -> {
            EpisodeTimers s = new EpisodeTimers();
            s.nextStart = t + rollHallucGap(mult); // first flare after a full gap
            return s;
        });
        if (st.activeUntil > 0) {
            if (t >= st.activeUntil) {                       // flare ends
                e.removeEffect(LethalBreedEffects.ZOMBIE_VISION);
                st.activeUntil = 0;
                st.nextStart = t + rollHallucGap(mult);
            }
        } else if (t >= st.nextStart) {                      // flare starts
            e.addEffect(new MobEffectInstance(LethalBreedEffects.ZOMBIE_VISION,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            st.activeUntil = t + rollHallucDur(mult);
        }
    }

    private static long rollHallucDur(double mult) {
        return ContaminationState.rollWindow(ContaminationConfig.contamHallucDurMinSec,
                ContaminationConfig.contamHallucDurMaxSec, mult);
    }

    private static long rollHallucGap(double mult) {
        return ContaminationState.rollWindow(ContaminationConfig.contamHallucGapMinSec,
                ContaminationConfig.contamHallucGapMaxSec, 1.0 / Math.max(1.0e-3, mult));
    }

    /** Forget a victim's hallucination timer (on cure/death). Does NOT remove the effect itself — callers already
     *  strip ZOMBIE_VISION separately as part of cure/death. */
    public static void clear(LivingEntity e) {
        hallucTimers.remove(e);
    }
}
