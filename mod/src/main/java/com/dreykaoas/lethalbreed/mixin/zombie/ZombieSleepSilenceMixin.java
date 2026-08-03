package com.dreykaoas.lethalbreed.mixin.zombie;

import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.entity.ZombieStateAttachment;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dozing zombie stays quiet. Vanilla plays the idle groan from {@code Mob.playAmbientSound()} regardless of
 * {@code setNoAi} (it lives in the tick, not the AI goals), so a sleeper would keep growling — which reads as
 * illogical. We suppress the ambient sound whenever this zombie's synced animation state is
 * {@link ZombieState#SLEEPING}: {@code playAmbientSound} only plays when {@code getAmbientSound()} is non-null,
 * so returning null silences it. Hurt/step/death sounds are untouched (a sleeper doesn't step, and taking a hit
 * wakes it anyway). Server-side only — that is where the ambient sound fires and where ZombieStateAttachment.STATE is authoritative.
 */
@Mixin(Zombie.class)
public abstract class ZombieSleepSilenceMixin {

    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$silentWhileSleeping(CallbackInfoReturnable<SoundEvent> cir) {
        Zombie self = (Zombie) (Object) this;
        if (self.getAttachedOrElse(ZombieStateAttachment.STATE, 0) == ZombieState.SLEEPING.ordinal()) {
            cir.setReturnValue(null); // dozing → no groan
        }
    }
}
