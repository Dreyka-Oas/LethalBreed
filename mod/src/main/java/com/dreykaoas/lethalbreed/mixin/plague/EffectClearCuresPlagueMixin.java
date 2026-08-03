package com.dreykaoas.lethalbreed.mixin.plague;

import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.effect.contamination.ClearGuard;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /effect clear} fully cures the Super Contamination plague, latent or symptomatic.
 *
 * <p>{@code @At("RETURN")}, not {@code TAIL}: TAIL binds only the LAST return instruction, and
 * {@code removeAllEffects()} has three — {@code false} on the client, {@code false} when there was nothing
 * to remove, {@code true} otherwise. A LATENT victim carries no plague effect at all (the infection slowdown
 * is a transient AttributeModifier, not a MobEffectInstance), so with no other effect the method returned
 * {@code false} and the handler bound to {@code return true} never ran — meaning the command did not cure
 * latent plague, the exact case this mixin exists for (audit #11).
 *
 * <p>Milk reaches this same method and must NOT cure: {@code MilkKeepsPlagueMixin} marks the thread while
 * its own call is in flight, and we stand down for it (audit #1).
 */
@Mixin(LivingEntity.class)
public class EffectClearCuresPlagueMixin {

    @Inject(method = "removeAllEffects()Z", at = @At("RETURN"))
    private void lethalbreed$clearPlagueOnEffectClear(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // RETURN also binds the client-side early exit, which the old TAIL never reached. Plague state is
        // authoritative on the server; curing from a client-side call would desync it.
        if (self.level().isClientSide() || ClearGuard.isMilk()) {
            return;
        }
        if (ContaminationManager.isContaminated(self)) {
            ContaminationManager.clearPlague(self);
        }
    }
}
