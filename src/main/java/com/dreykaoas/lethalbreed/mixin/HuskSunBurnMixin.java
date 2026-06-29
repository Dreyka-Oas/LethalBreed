package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import net.minecraft.world.entity.monster.zombie.Husk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make ALL zombie types burn in daylight (config-gated, default ON). Base {@code Zombie} is already
 * sun-sensitive; the only LethalBreed-relevant variant that opts out is {@code Husk}, which overrides
 * {@code isSunSensitive()} to {@code false}. Force it back to {@code true} so husks (Momifié) burn like
 * the rest. Vanilla still respects the usual escapes — a Fire Resistance effect (our random buff), a
 * helmet, water, or shade — so a Fire-Res zombie survives the sun by design.
 *
 * <p>Drowned is discarded by the mod and ZombifiedPiglin doesn't override the method (inherits {@code true}),
 * so this single Husk target covers every zombie that spawns.
 */
@Mixin(Husk.class)
public abstract class HuskSunBurnMixin {

    @Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$forceSunBurn(CallbackInfoReturnable<Boolean> cir) {
        if (WorldSpawnConfig.forceAllZombiesSunBurn) {
            cir.setReturnValue(true);
        }
    }
}
