package com.dreykaoas.lethalbreed.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Base for LethalBreed's "marker" mob effects: a {@link MobEffect} that carries no attribute modifier and does
 * nothing on its own — the real behaviour lives elsewhere (a manager or a per-tick reader), and all the effect
 * itself contributes is the swirling particles a visible {@code MobEffectInstance} renders. Subclasses exist
 * only to give each marker a distinct registered identity (and, where needed, an {@code onEffectStarted} hook).
 */
public class MarkerEffect extends MobEffect {
    public MarkerEffect(MobEffectCategory category, int particleColor) {
        super(category, particleColor);
    }
}
