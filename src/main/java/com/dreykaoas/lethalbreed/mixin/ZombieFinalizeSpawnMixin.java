package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.entity.ZombieVariation;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies LethalBreed's per-zombie variation (random size/strength/speed) inside
 * {@code Zombie.finalizeSpawn}, which runs BEFORE the entity is added to the world and tracked to
 * clients. Doing it here (instead of the post-sync {@code ENTITY_LOAD} event) means the client
 * receives the entity already at its final {@code SCALE}, with no visible grow/shrink animation.
 *
 * <p>Covers every fresh-spawn path that routes through {@code finalizeSpawn}: natural spawning,
 * spawners, spawn eggs and command summons. Chunk-reloaded zombies do not call finalizeSpawn — they
 * already carry the persisted permanent modifier in NBT, so they also show no resize.
 */
@Mixin(Zombie.class)
public abstract class ZombieFinalizeSpawnMixin {

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void lethalbreed$applyVariation(CallbackInfoReturnable<SpawnGroupData> cir) {
        ZombieVariation.apply((Zombie) (Object) this);
    }
}
