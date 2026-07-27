package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.phase.PhaseTable;

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
 * {@code ceil(PhaseTable.frequency(phase)) - 1} extra passes for MONSTER (each pass still honours the
 * mob-cap, so this raises how FAST the population fills up, not the ceiling — that's the mob-cap mixin's
 * job). No per-phase ceiling: the curve keeps climbing with the phase.
 *
 * <p>Phase 0 → factor 0 → no extra passes (and the mob-cap is 0 anyway, so nothing spawns).
 *
 * <p>The pass count IS however capped at {@link WorldSpawnConfig#spawnMaxExtraPasses}, because this is a
 * loop on the server thread whose trip count comes from an unbounded formula: without the cap a large
 * phase — forced by command, or simply reached by a very long-lived world — turns one chunk-tick into
 * minutes of {@code spawnCategoryForChunk}, and the tick never returns. The cap is deliberately far above
 * anything normal progression reaches, so it is a safety net rather than a balance change.
 */
@Mixin(NaturalSpawner.class)
public abstract class SpawnFrequencyMixin {

    @Inject(method = "spawnForChunk", at = @At("TAIL"))
    private static void lethalbreed$extraPasses(ServerLevel level, LevelChunk chunk,
            NaturalSpawner.SpawnState state, List<MobCategory> categories, CallbackInfo ci) {
        if (!WorldSpawnConfig.nightSpawnEnabled || !categories.contains(MobCategory.MONSTER)) {
            return;
        }
        int extra = Math.min((int) Math.ceil(PhaseTable.frequency(PhaseManager.current())) - 1,
                Math.max(1, WorldSpawnConfig.spawnMaxExtraPasses));
        SpawnStateInvoker inv = (SpawnStateInvoker) state;
        if (extra <= 0 || !inv.lethalbreed$canSpawnLocal(MobCategory.MONSTER, chunk.getPos())) {
            return;
        }
        for (int i = 0; i < extra; i++) {
            NaturalSpawner.spawnCategoryForChunk(MobCategory.MONSTER, level, chunk,
                    inv::lethalbreed$canSpawn, inv::lethalbreed$afterSpawn);
        }
    }
}
