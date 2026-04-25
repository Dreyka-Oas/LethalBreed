package oas.work.lethalbreed.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import oas.work.lethalbreed.SizeLogic;
import oas.work.lethalbreed.ZombieLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieEntityMixin {
    @Inject(method = "isBaby", at = @At("HEAD"), cancellable = true)
    private void alwaysAdult(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "createAttributes", at = @At("RETURN"), cancellable = true)
    private static void modifyAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.setReturnValue(ZombieLogic.injectAttributes(cir.getReturnValue()));
    }

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void onInitialize(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, SpawnGroupData entityData, CallbackInfoReturnable<SpawnGroupData> cir) {
        Zombie zombie = (Zombie) (Object) this;
        SizeLogic.randomizeStats(zombie, true);
        oas.work.lethalbreed.EquipmentLogic.randomizeEquipment(zombie);
        zombie.setCanPickUpLoot(true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        Zombie zombie = (Zombie) (Object) this;
        oas.work.lethalbreed.MutantLogic.tickTentacles(zombie);
    }
}







