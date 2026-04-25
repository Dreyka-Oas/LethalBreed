package oas.work.lethalbreed.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MutantDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void onDeathSpawn(DamageSource damageSource, CallbackInfo ci) {
        if ((Object)this instanceof Zombie zombie) {
            oas.work.lethalbreed.MutantLogic.onDeath(zombie);
        }
    }
}







