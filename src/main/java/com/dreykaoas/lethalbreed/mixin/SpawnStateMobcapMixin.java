package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Scales the global MONSTER mob-cap by the current phase (LethalBreed owns the hostile population). Vanilla
 * lets a category spawn while its live count is under {@code maxPerChunk * spawnableChunks / MAGIC_NUMBER};
 * we widen that ceiling by {@link WorldSpawnConfig#mobcapPerPhase}[phase] so higher phases hold more zombies
 * at once. Phase 0 → factor 0 → the cap is 0 → nothing spawns (the "classic" base).
 *
 * <p>Only MONSTER is touched; other categories keep vanilla behaviour.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateMobcapMixin {

    @Shadow
    @Final
    private int spawnableChunkCount;

    @Shadow
    @Final
    private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;

    @Inject(method = "canSpawnForCategoryGlobal", at = @At("HEAD"), cancellable = true)
    private void lethalbreed$scaleMobcap(MobCategory category, CallbackInfoReturnable<Boolean> cir) {
        if (!WorldSpawnConfig.nightSpawnEnabled || category != MobCategory.MONSTER) {
            return;
        }
        double factor = mobcapFactor();
        int base = category.getMaxInstancesPerChunk() * spawnableChunkCount / 289; // MAGIC_NUMBER = 17^2
        int scaled = (int) Math.floor(base * factor);
        cir.setReturnValue(mobCategoryCounts.getInt(category) < scaled);
    }

    private static double mobcapFactor() {
        double[] table = WorldSpawnConfig.mobcapPerPhase;
        int phase = PhaseManager.current();
        if (phase < 0) return 0.0;
        return phase < table.length ? table[phase] : (table.length > 0 ? table[table.length - 1] : 1.0);
    }
}
