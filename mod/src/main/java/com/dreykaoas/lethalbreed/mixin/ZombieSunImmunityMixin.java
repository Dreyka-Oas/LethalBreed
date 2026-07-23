package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase-gated daylight immunity for the VANILLA burn path.
 *
 * <p><b>1.21.11 refactor:</b> the undead daylight-burn moved OUT of {@code Zombie} into the base
 * {@code Mob} class. {@code Mob.aiStep()} now checks the {@code minecraft:burn_in_daylight} entity-type
 * tag and calls the private {@code Mob.burnUndead()}, which (via {@code isSunBurnTick()}) sets the mob
 * alight with {@code igniteForSeconds(8)}. {@code Zombie.isSunSensitive()} still exists but is
 * VESTIGIAL — nothing calls it — so the previous "inject into isSunSensitive" approach was a silent
 * no-op and zombies burned at EVERY phase regardless of {@link WorldSpawnConfig#sunImmunePhase}.
 *
 * <p>We instead cancel {@code burnUndead()} at HEAD for zombies once the horde reaches
 * {@link WorldSpawnConfig#sunImmunePhase} (they harden as the phases climb). The {@code instanceof Zombie}
 * guard keeps other daylight-burning mobs (skeletons, …) on vanilla behaviour. Cancelling here also skips
 * the helmet-damage side effect — an immune zombie shouldn't burn at all. The mod's forced burn
 * ({@code SmartZombie.applySunBurn}) is gated on the SAME threshold, so both burn paths close together.
 * Below the threshold nothing is cancelled, so vanilla still burns base zombies and the forced path still
 * burns Husk.
 */
@Mixin(Mob.class)
public abstract class ZombieSunImmunityMixin {

    @Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$sunImmuneAtHighPhase(CallbackInfo ci) {
        if ((Object) this instanceof Zombie && PhaseManager.current() >= WorldSpawnConfig.sunImmunePhase) {
            ci.cancel();
        }
    }
}
