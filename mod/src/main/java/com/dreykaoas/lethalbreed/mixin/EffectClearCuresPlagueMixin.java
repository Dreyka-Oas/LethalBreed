package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /effect clear} (and anything else calling {@link LivingEntity#removeAllEffects()} directly) fully
 * cures the Super Contamination plague, latent or symptomatic. Symptomatic already self-healed on the next
 * tick sweep once the skull effect went missing ({@code ContaminationTick}), but latent has no effect at all
 * to go missing — so {@code /effect clear} previously did nothing for a latent victim. This makes the cure
 * immediate and uniform across both stages instead of relying on that indirect, symptomatic-only inference.
 *
 * <p>Milk is unaffected by this: it goes through {@code ClearAllStatusEffectsConsumeEffect}, a different
 * code path, guarded separately by {@code MilkKeepsPlagueMixin}.
 */
@Mixin(LivingEntity.class)
public class EffectClearCuresPlagueMixin {

    @Inject(method = "removeAllEffects()Z", at = @At("TAIL"))
    private void lethalbreed$clearPlagueOnEffectClear(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (ContaminationManager.isContaminated(self)) {
            ContaminationManager.clearPlague(self);
        }
    }
}
