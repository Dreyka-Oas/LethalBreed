package com.dreykaoas.lethalbreed.config.bounds;

import com.dreykaoas.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the contamination options.
 *
 * <p>Split out of {@code ConfigBoundsTable}, which had grown to 305 append-only lines across nine unrelated
 * domains. Registration order does not matter — the table is a map keyed by lower-cased option name — but
 * grouping does: a bound belongs next to the options it governs, and {@code ConfigBoundsTest} fails the build
 * if any numeric option loses one.
 */
public final class ContaminationBounds {
    private ContaminationBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("contamBaseChance", 0, 1);
        r.b("contamPhaseScale", 0, 1);
        r.b("contamMaxChance", 0, 1);
        r.b("contamDamageMin", 0, 1000);
        r.b("contamDamageMax", 0, 1000);
        r.b("contamIntervalMinSec", 0, 3600);
        r.b("contamIntervalMaxSec", 0, 3600);
        r.b("contamCureCheckTicks", 1, 72_000);
        r.b("contamCureMinPct", 0, 100);
        r.b("contamCureMaxPct", 0, 100);
        r.b("contamSymptomMinDays", 0, 365);
        r.b("contamSymptomMaxDays", 0, 365);
        r.b("contamSymptomMinPct", 0, 100);
        r.b("contamSymptomMaxPct", 0, 100);
        r.b("contamLatentSlowAmount", 0, 1);
        r.b("contamLatentSlowTicks", 1, 72_000);
        r.b("contamReanimateMinHeight", 0, 16);
        r.b("contamReanimateMaxWidth", 0, 16);
        r.b("contamReanimateAspect", 0, 100);
        r.b("contamEpisodeCap", 0, 1);
        r.b("contamFoodExhaustionMult", 0, 100);
        // Levels / evolution / episodes — this whole block was added after the table and was unbounded until
        // audit #6. contamLevelStep and the jitter pair are kept deliberately tight: they multiply plague
        // damage, so an unbounded value turned a gradual DoT into a one-shot and (via a non-finite value)
        // poisoned the persistent INTENSITY attachment. The ConfigBounds non-finite guard now covers even
        // unlisted fields, but these must be listed so the magnitude itself stays sane.
        r.b("contamMaxLevel", 1, 100);
        r.b("contamEvolveMinDays", 0, 365);
        r.b("contamEvolveMaxDays", 0, 365);
        r.b("contamEvolveMinPct", 0, 100);
        r.b("contamEvolveMaxPct", 0, 100);
        r.b("contamLevelStep", 0, 10);
        r.b("contamLevelJitterMin", 0, 10);
        r.b("contamLevelJitterMax", 0, 10);
        r.b("contamSlowAmount", 0, 1);
        r.b("contamSlowDurMinSec", 0, 3600);
        r.b("contamSlowDurMaxSec", 0, 3600);
        r.b("contamSlowGapMinSec", 0, 86_400);
        r.b("contamSlowGapMaxSec", 0, 86_400);
        r.b("contamNoJumpDurMinSec", 0, 3600);
        r.b("contamNoJumpDurMaxSec", 0, 3600);
        r.b("contamNoJumpGapMinSec", 0, 86_400);
        r.b("contamNoJumpGapMaxSec", 0, 86_400);
        r.b("contamWeakAmount", 0, 1);
        r.b("contamWeakDurMinSec", 0, 3600);
        r.b("contamWeakDurMaxSec", 0, 3600);
        r.b("contamWeakGapMinSec", 0, 86_400);
        r.b("contamWeakGapMaxSec", 0, 86_400);
        r.b("contamHallucDurMinSec", 0, 3600);
        r.b("contamHallucDurMaxSec", 0, 3600);
        r.b("contamHallucGapMinSec", 0, 86_400);
        r.b("contamHallucGapMaxSec", 0, 86_400);
        r.b("contamDevTimeScale", 0, 1_000_000);

    }
}
