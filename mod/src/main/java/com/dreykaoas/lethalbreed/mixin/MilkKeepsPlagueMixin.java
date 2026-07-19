package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drinking milk (the vanilla "clear all effects" consume effect) must NOT cure the Super Contamination plague — it
 * is a disease, not a status effect a glass of milk can wash out. So we snapshot the skull effect before the clear
 * and re-apply it right after. The {@code /effect clear} command goes through {@code LivingEntity.removeAllEffects}
 * directly (not this consume effect), so it still removes the plague as intended.
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public class MilkKeepsPlagueMixin {

    @Inject(method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("TAIL"))
    private void lethalbreed$restorePlague(Level level, ItemStack stack, LivingEntity entity,
                                           CallbackInfoReturnable<Boolean> cir) {
        // If the drinker is still carrying the plague and was symptomatic, milk just stripped the skull effect —
        // put it straight back so the plague (and the hallucination) survives the drink.
        if (ContaminationManager.isSymptomatic(entity)
                && entity.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null) {
            int amp = Math.max(0, ContaminationManager.plagueLevel(entity) - 1);
            entity.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                    MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
        }
    }
}
