package com.dreykaoas.lethalbreed.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes {@code SpawnState}'s private cost bookkeeping so the frequency mixin can drive extra spawn passes. */
@Mixin(NaturalSpawner.SpawnState.class)
public interface SpawnStateInvoker {

    @Invoker("canSpawn")
    boolean lethalbreed$canSpawn(EntityType<?> type, BlockPos pos, ChunkAccess chunk);

    @Invoker("afterSpawn")
    void lethalbreed$afterSpawn(Mob mob, ChunkAccess chunk);

    @Invoker("canSpawnForCategoryLocal")
    boolean lethalbreed$canSpawnLocal(MobCategory category, ChunkPos pos);
}
