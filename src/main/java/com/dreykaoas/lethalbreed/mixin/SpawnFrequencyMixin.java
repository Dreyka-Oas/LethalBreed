package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Scales MONSTER spawn FREQUENCY by the phase. Vanilla runs one spawn pass per chunk per tick; we add
 * {@code ceil(frequencyPerPhase[phase]) - 1} extra passes for MONSTER (each pass still honours the mob-cap,
 * so this raises how FAST the population fills up, not the ceiling — that's the mob-cap mixin's job).
 *
 * <p>Phase 0 → factor 0 → no extra passes (and the mob-cap is 0 anyway, so nothing spawns).
 */
@Mixin(NaturalSpawner.class)
public abstract class SpawnFrequencyMixin {

    @Inject(method = "spawnForChunk", at = @At("TAIL"))
    private static void lethalbreed$extraPasses(ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnState state, List<MobCategory> categories, CallbackInfo ci) {
        if (!WorldSpawnConfig.nightSpawnEnabled || !categories.contains(MobCategory.MONSTER)) {
            return;
        }
        int extra = (int) Math.ceil(frequencyFactor()) - 1;
        SpawnStateInvoker inv = (SpawnStateInvoker) state;
        if (extra <= 0 || !inv.lethalbreed$canSpawnLocal(MobCategory.MONSTER, chunk.getPos())) {
            return;
        }
        for (int i = 0; i < extra; i++) {
            NaturalSpawner.spawnCategoryForChunk(MobCategory.MONSTER, level, chunk,
                    inv::lethalbreed$canSpawn, inv::lethalbreed$afterSpawn);
        }
    }

    private static double frequencyFactor() {
        double[] table = WorldSpawnConfig.frequencyPerPhase;
        int phase = PhaseManager.current();
        if (phase < 0) return 0.0;
        return phase < table.length ? table[phase] : (table.length > 0 ? table[table.length - 1] : 1.0);
    }
}
