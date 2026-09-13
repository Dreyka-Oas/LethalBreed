package oas.dreyka.lethalbreed.config.bounds;

import oas.dreyka.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the world, variation, effect and spawn options.
 *
 * <p>{@code ConfigBoundsTest} fails the build if a numeric option loses its bound.
 */
public final class WorldSpawnBounds {
    private WorldSpawnBounds() {}

    public static void register(BoundsRegistrar r) {
        r.b("varScaleMin", 0.05, 10);
        r.b("varScaleMax", 0.05, 10);
        r.b("varSpeedMin", 0.05, 10);
        r.b("varSpeedMax", 0.05, 10);
        r.b("varDamageMin", 0, 100);
        r.b("varDamageMax", 0, 100);
        r.b("varLeapMin", 0, 10);
        r.b("varLeapMax", 0, 10);
        r.b("varHealthMin", 0.05, 10);   // new option (Variation)
        r.b("varHealthMax", 0.05, 10);
        r.b("randomEffectChance", 0, 1);
        r.b("randomEffectMaxAmplifier", 0, 9);
        r.b("leapEffectPerLevel", 0, 5);
        r.b("sunBurnDurationTicks", 20, 6000);   // new option (Spawn)
        r.b("sunImmunePhase", 0, 1_000_000);
        r.b("spawnMaxExtraPasses", 1, 100_000);  // safety ceiling on the per-chunk per-tick spawn loop

    }
}
