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
        if (WorldSpawnConfig.forceDayTime) {
            // Hold only the time-of-day, preserving the day counter (don't reset to day 0 each tick).
            long target = Math.floorMod(WorldSpawnConfig.forcedDayTime, 24000L);
            long current = ow.getDayTime();
            long timeOfDay = Math.floorMod(current, 24000L);
            if (timeOfDay != target) {
                ow.setDayTime(current - timeOfDay + target);
            }
        }
        if (WorldSpawnConfig.clearWeather && ow.isRaining()) {
            ow.setWeatherParameters(6000, 0, false, false);
        }
    }

    /** Recompute each active dimension's flow field once per tick (throttled inside the manager). */
    void recomputeFlowFields(MinecraftServer server, long tickCounter) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level != null) {
                e.getValue().flowFieldManager().tick(level, tickCounter);
            }
        }
    }

    /** Emit player/loud sounds and distribute them to nearby zombies, per dimension. */
    void processSound(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            WorldAIContext ctx = e.getValue();
            ctx.soundBus().tickPlayers(level);
            ctx.soundBus().process(ctx.spatialGrid(), level.getGameTime());
        }
    }

    /** Apply queued world mutations under budget and expire old zombie-placed blocks. */
    void drainBlockOps(MinecraftServer server, long tickCounter) {
        for (Map.Entry<ResourceKey<Level>, WorldAIContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            WorldAIContext ctx = e.getValue();
            ctx.blockOps().drain(level, ctx.placedBlocks(), tickCounter);
            ctx.breakManager().tick(level, tickCounter);
            ctx.placedBlocks().tick(level, tickCounter);
        }
    }
}
