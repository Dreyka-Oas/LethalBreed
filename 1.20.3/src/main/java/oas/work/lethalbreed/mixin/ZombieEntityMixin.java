package oas.work.lethalbreed.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import oas.work.lethalbreed.SizeLogic;
import oas.work.lethalbreed.ZombieLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {
    @Inject(method = "isBaby", at = @At("HEAD"), cancellable = true)
    private void alwaysAdult(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "createZombieAttributes", at = @At("RETURN"), cancellable = true)
    private static void modifyAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
        cir.setReturnValue(ZombieLogic.injectAttributes(cir.getReturnValue()));
    }

    @Inject(method = "initialize", at = @At("TAIL"))
    private void onInitialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, NbtCompound nbt, CallbackInfoReturnable<EntityData> cir) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        SizeLogic.randomizeStats(zombie, true);
        oas.work.lethalbreed.EquipmentLogic.randomizeEquipment(zombie);
        zombie.setCanPickUpLoot(true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;
        oas.work.lethalbreed.MutantLogic.tickTentacles(zombie);
    }
}








