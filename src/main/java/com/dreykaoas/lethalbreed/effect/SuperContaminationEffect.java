package com.dreykaoas.lethalbreed.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Super Contamination" — a HARMFUL marker effect (skull icon). It does nothing on its own; all behaviour
 * (ramping damage to death, progressive hunger drain, sneak-only cure, milk-immunity) is driven by
 * {@code effect.ContaminationManager}, with a persistent counter attachment as the source of truth so
 * drinking milk only hides the icon for a tick before it's re-applied.
 */
public class SuperContaminationEffect extends MarkerEffect {
    public SuperContaminationEffect(MobEffectCategory category, int particleColor) {
        super(category, particleColor);
    }

    /** When the effect lands (e.g. from a brewed/splash potion), kick off the real contamination on the server. */
    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            ContaminationManager.contaminate(entity);
        }
    }
}
