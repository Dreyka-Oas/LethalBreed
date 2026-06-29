package com.dreykaoas.lethalbreed.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Super Contamination" — a HARMFUL marker effect (skull icon). It does nothing on its own; all behaviour
 * (ramping damage to death, progressive hunger drain, sneak-only cure, milk-immunity, death→zombification)
 * is driven by {@code effect.ContaminationManager}, with a persistent counter attachment as the
 * source of truth so drinking milk only hides the icon for a tick before it's re-applied.
 */
public class SuperContaminationEffect extends MobEffect {
    public SuperContaminationEffect(MobEffectCategory category, int particleColor) {
        super(category, particleColor);
    }
}
