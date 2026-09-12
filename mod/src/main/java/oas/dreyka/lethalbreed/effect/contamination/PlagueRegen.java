package oas.dreyka.lethalbreed.effect.contamination;

import oas.dreyka.lethalbreed.config.domain.ContaminationConfig;

/**
 * How hard the plague fights the body's own recovery.
 *
 * <p>The curve lives here rather than in the mixin that redirects the heal, because a mixin cannot be reached
 * from a headless test and these two numbers are the whole rule.
 */
public final class PlagueRegen {
    private PlagueRegen() {}

    /** Chance in 0..1 that a single natural heal tick is lost at {@code level}, read live so an edit applies
     *  to the players already infected rather than to the next ones only. */
    public static float skipChance(int level) {
        if (level <= 0) {
            return 0.0f;
        }
        float perLevel = (float) ContaminationConfig.contamRegenSkipPerLevel;
        float cap = (float) ContaminationConfig.contamRegenSkipMax;
        return Math.min(cap, perLevel * level);
    }
}
