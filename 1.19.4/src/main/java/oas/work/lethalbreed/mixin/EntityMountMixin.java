package oas.work.lethalbreed.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMountMixin {
    @Inject(method = "startRiding", at = @At("HEAD"), cancellable = true)
    private void preventZombieRiding(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Zombie) {
            cir.setReturnValue(false);
        }
    }
}







