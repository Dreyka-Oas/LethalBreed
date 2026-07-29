package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Drinking milk must NOT cure the Super Contamination plague — it is a disease, not a status effect a glass
 * of milk can wash out. {@code /effect clear} must cure it. Both go through the SAME method: vanilla's
 * {@code apply} here is literally {@code return livingEntity.removeAllEffects();}.
 *
 * <p>So we redirect that one call and mark the thread while it runs. {@code EffectClearCuresPlagueMixin}
 * reads the mark and stands down; milk therefore strips only the visible skull effect, which we put straight
 * back afterwards. The mark is armed and cleared by a real {@code try}/{@code finally}, so an exception
 * cannot leave a stale flag that would make the next {@code /effect clear} silently fail to cure.
 *
 * <p>This replaces a {@code @At("TAIL")} handler that could never work: the callee's TAIL fires BEFORE the
 * caller's, so the plague attachments were already wiped by the time it tested {@code isSymptomatic()} —
 * making the whole mixin dead code and letting any player cancel the plague with a bucket of milk (audit #1).
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public class MilkKeepsPlagueMixin {

    @Redirect(
            method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
    private boolean lethalbreed$milkKeepsPlague(LivingEntity entity) {
        boolean removed;
        ClearGuard.arm();
        try {
            removed = entity.removeAllEffects();
        } finally {
            ClearGuard.disarm();
        }
        // Milk stripped the skull icon but not the disease — put the icon straight back so the plague,
        // its level and the hallucination all survive the drink.
        if (ContaminationManager.isSymptomatic(entity)
                && entity.getEffect(LethalBreedEffects.SUPER_CONTAMINATION) == null) {
            int amp = Math.max(0, ContaminationManager.plagueLevel(entity) - 1);
            entity.addEffect(new MobEffectInstance(LethalBreedEffects.SUPER_CONTAMINATION,
                    MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
        }
        return removed;
    }
}
