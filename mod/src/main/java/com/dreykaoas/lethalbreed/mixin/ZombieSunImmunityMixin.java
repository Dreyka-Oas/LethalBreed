package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase-gated daylight immunity for the VANILLA burn path. Vanilla {@code Zombie.aiStep()} ignites the zombie
 * only when {@link Zombie#isSunSensitive()} is true; once the horde reaches
 * {@link WorldSpawnConfig#sunImmunePhase} we force it false so the sun stops burning them (they harden as the
 * phases climb). The mod's own forced burn ({@code SmartZombie.applySunBurn}) is gated on the SAME threshold,
 * so both burn paths close together. Husk already returns false here, so it is unaffected (and stays unburnt).
 */
@Mixin(Zombie.class)
public abstract class ZombieSunImmunityMixin {

    @Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$sunImmuneAtHighPhase(CallbackInfoReturnable<Boolean> cir) {
        if (PhaseManager.current() >= WorldSpawnConfig.sunImmunePhase) {
            cir.setReturnValue(false);
        }
    }
}
