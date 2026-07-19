package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Per-dimension world-level upkeep that frames the per-zombie passes: enforcing world rules,
 * propagating sound, recomputing flow fields, and draining queued block mutations.
 */
final class WorldMaintenance {
    private final DimensionManager dimensions;

    WorldMaintenance(DimensionManager dimensions) {
        this.dimensions = dimensions;
    }

    /** Keep the overworld at constant daytime and clear weather (config-gated). */
    void enforceWorldRules(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        if (ow == null) {
            return;
        }
        if (WorldSpawnConfig.clearWeather && ow.isRaining()) {
            ow.setWeatherParameters(6000, 0, false, false);
        }
    }

    /** Emit player/loud sounds and distribute them to nearby zombies, per dimension. */
    void processSound(MinecraftServer server) {
        forEachLoadedContext(server, (level, ctx) -> {
            ctx.soundBus().tickPlayers(level);
            ctx.soundBus().tickEntities(level);
            ctx.soundBus().process(ctx.spatialGrid(), level.getGameTime());
        });
    }

    /** Recompute each dimension's shared flow field (throttled + move-gated inside {@code tick}). */
    void recomputeFlowFields(MinecraftServer server, long tickCounter) {
        forEachLoadedContext(server, (level, ctx) -> ctx.flowFieldManager().tick(level, tickCounter));
    }

    /** Apply queued world mutations under budget and expire old zombie-placed blocks. */
    void drainBlockOps(MinecraftServer server, long tickCounter) {
        forEachLoadedContext(server, (level, ctx) -> {
            ctx.blockOps().drain(level, ctx.placedBlocks(), tickCounter);
            ctx.breakManager().tick(level, tickCounter);
            ctx.placedBlocks().tick(level, tickCounter);
        });
    }

    /** Run {@code action} for every dimension context whose {@link ServerLevel} is currently loaded, skipping
     *  the unloaded ones. The single place the per-dimension iterate-and-null-check lives. */
    private void forEachLoadedContext(MinecraftServer server, LoadedContextAction action) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            action.run(level, e.getValue());
        }
    }

    @FunctionalInterface
    private interface LoadedContextAction {
        void run(ServerLevel level, WorldAIContext ctx);
    }
}
