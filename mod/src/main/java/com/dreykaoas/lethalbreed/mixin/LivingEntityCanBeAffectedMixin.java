package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Force EVERY living entity to accept the Super Contamination effect, even mobs that normally reject all
 * potion effects ({@code canBeAffected=false}, e.g. some bosses / modded immune mobs). The plague — and its
 * visible effect icon/particles — must reach any entity the plague can infect.
 */
@Mixin(LivingEntity.class)
public class LivingEntityCanBeAffectedMixin {
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$allowContamination(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean isContam = LethalBreedEffects.SUPER_CONTAMINATION != null
                && instance.getEffect().value() == LethalBreedEffects.SUPER_CONTAMINATION.value();
        // Force-accept the plague itself, AND — once a mob is infected — every other effect too, so an
        // already-contaminated (normally potion-immune) mob can still be hit with poison/slowness/etc.
        if (isContam || ContaminationManager.isContaminated(self)) {
            cir.setReturnValue(true);
        }
    }
}
