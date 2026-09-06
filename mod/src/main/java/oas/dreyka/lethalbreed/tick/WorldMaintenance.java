package oas.dreyka.lethalbreed.tick;

import oas.dreyka.lethalbreed.config.domain.WorldSpawnConfig;

import oas.dreyka.lethalbreed.dimension.DimensionManager;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.config.domain.PackConfig;
import oas.dreyka.lethalbreed.pack.PackState;
import oas.dreyka.lethalbreed.pack.runtime.PackMaterializer;
import oas.dreyka.lethalbreed.pack.runtime.PackVirtualMove;
import oas.dreyka.lethalbreed.pack.runtime.PackMarch;
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

    /** Advance every pack in every loaded dimension: lifecycle first, then the march that plants the shared
     *  waypoint. Must run BEFORE the bucket pass, because LodManager reads that waypoint in the same activation, so
     *  planting it afterwards would leave marching members frozen for one whole cycle. */
    void tickPacks(MinecraftServer server, long gameTime) {
        forEachLoadedContext(server, (level, ctx) -> {
            ctx.packManager().tick(gameTime);
            boolean sweep = PackConfig.packVirtualEnabled
                    && gameTime % Math.max(1, PackConfig.packMaterializeInterval) == 0L;
            for (PackState pack : ctx.packManager().all()) {
                if (sweep) {
                    PackMaterializer.tick(level, pack, gameTime);
                }
                if (pack.phase == PackState.Phase.VIRTUAL) {
                    // A dematerialised pack has no members to walk it: it advances as a point instead.
                    PackVirtualMove.tick(level, ctx.packManager(), pack, gameTime);
                } else {
                    PackMarch.tick(level, ctx.packManager(), pack, gameTime);
                }
            }
        });
    }

    void refreshTargetIndex(MinecraftServer server) {
        forEachLoadedContext(server, (level, ctx) -> ctx.targetIndex().refresh());
    }

    /** Recompute each dimension's shared flow field (throttled + move-gated inside {@code tick}). */
    void recomputeFlowFields(MinecraftServer server, long tickCounter) {
        forEachLoadedContext(server, (level, ctx) -> ctx.flowFieldManager().tick(level, tickCounter));
    }

    /**
     * Apply queued world mutations under budget and expire old zombie-placed blocks.
     *
     * <p>The two calls that touch a placement's lifetime take the world age, not {@code tickCounter}. The
     * counter restarts at 0 every launch, so a block laid at counter 40000 and reloaded came back looking as
     * if it had been placed in the future and never expired. {@code getGameTime()} is persisted with the
     * world and monotonic across reloads, which is what an age has to be measured against.
     *
     * <p>The breaking and breach passes keep the counter: neither survives a restart, so the basis they
     * count from cannot be observed across one.
     */
    void drainBlockOps(MinecraftServer server, long tickCounter) {
        forEachLoadedContext(server, (level, ctx) -> {
            long worldAge = level.getGameTime();
            ctx.blockOps().drain(level, ctx.placedBlocks(), worldAge);
            ctx.breakManager().tick(level, tickCounter);
            ctx.breachCoordinator().tick(level, tickCounter);
            ctx.placedBlocks().tick(level, worldAge);
        });
    }

    /** Run {@code action} for every dimension context whose {@link ServerLevel} is currently loaded, skipping
     *  the unloaded ones. The single place the per-dimension iterate-and-null-check lives. */
    private void forEachLoadedContext(MinecraftServer server, LoadedContextAction action) {
        for (Map.Entry<ResourceKey<Level>, WorldAiContext> e : dimensions.contexts().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            action.run(level, e.getValue());
        }
    }

    @FunctionalInterface
    private interface LoadedContextAction {
        void run(ServerLevel level, WorldAiContext ctx);
    }
}
