package oas.dreyka.lethalbreed.config.bounds;

import oas.dreyka.lethalbreed.config.BoundsRegistrar;

/**
 * Clamp ranges for the Bomber's blast, puddle and gore cocktail.
 *
 * <p>{@code ConfigBoundsTest} fails the build if a numeric option loses its bound.
 */
public final class BomberBounds {
    private BomberBounds() {}

    public static void register(BoundsRegistrar r) {
        // A weight outside [0,1] would invert the blend and rank the rim above the centre.
        r.b("specialBomberFuseWeight", 0, 1);
        r.b("specialBomberPuddleBaseSec", 0, 600);
        r.b("specialBomberPuddleSpanSec", 0, 600);
        r.b("specialBomberPuddleRadiusMul", 0, 4);
        r.b("specialBomberPuddlePotency", 0, 4);
        r.b("specialBomberPuddleReapplyTicks", 1, 1200);
        r.b("specialBomberDoseBaseSec", 0, 600);
        r.b("specialBomberDoseSpanSec", 0, 600);
        r.b("specialBomberLongDoseBaseSec", 0, 600);
        r.b("specialBomberLongDoseSpanSec", 0, 600);
        r.b("specialBomberBlindDoseBaseSec", 0, 600);
        r.b("specialBomberBlindDoseSpanSec", 0, 600);
        // Vanilla stops showing meaningful severities well before this; past it the tooltip is roman numerals.
        r.b("specialBomberDoseAmpCap", 0, 9);
    }
}
