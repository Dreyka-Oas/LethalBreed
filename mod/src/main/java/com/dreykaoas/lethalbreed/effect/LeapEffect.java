package com.dreykaoas.lethalbreed.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * A custom, zombie-only "Leap" {@link MarkerEffect}. The behaviour lives in {@code
 * SmartZombie.leapDistanceFactor}, which reads this effect's amplifier to extend the zombie's horizontal leap
 * reach (the horizontal analogue of how the vanilla Jump Boost is folded into the vertical impulse). All it
 * contributes by itself is the swirling particles a visible {@code MobEffectInstance} renders — "pas dans le
 * jeu, juste des particules".
 */
public class LeapEffect extends MarkerEffect {
    public LeapEffect(MobEffectCategory category, int particleColor) {
        super(category, particleColor);
    }
}
