package oas.dreyka.lethalbreed.init;


import oas.dreyka.lethalbreed.special.runtime.gore.GorePuddles;
import oas.dreyka.lethalbreed.config.domain.engine.FlowConfig;

import oas.dreyka.lethalbreed.ai.flowfield.GpuFlowField;
import oas.dreyka.lethalbreed.ai.flowfield.Snapshot;
import oas.dreyka.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import oas.dreyka.lethalbreed.dimension.DimensionManager;
import oas.dreyka.lethalbreed.effect.ContaminationManager;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.ZombieRegistry;
import oas.dreyka.lethalbreed.entity.spawn.SpawnFilter;
import oas.dreyka.lethalbreed.block.PlacedBlockSavedData;
import oas.dreyka.lethalbreed.util.target.VanillaTargetingGoals;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import oas.dreyka.lethalbreed.pack.runtime.PackSavedData;
import oas.dreyka.lethalbreed.phase.PhaseManager;
import oas.dreyka.lethalbreed.tick.TickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Registers server start/stop lifecycle hooks. Dev-only start hooks (headless climb / compute self-test)
 * live in the {@code dev} source set and are wired by {@code DevBootstrap}, not here.
 *
 * <p>Teardown happens at <b>two</b> points, and which one you need depends on whether your state has to
 * reach the save file:
 *
 * <ul>
 *   <li>{@code SERVER_STOPPING} (HEAD of {@code MinecraftServer.stopServer()}): for anything that must be
 *       written back onto entities before the world is saved. {@code stopServer()} calls
 *       {@code saveAllChunks(...)} and then {@code serverLevel.close()} strictly between the two events, so
 *       this is the last moment an entity mutation still lands in NBT. The {@code NoAI} release lives here;
 *       see the comment on that handler before moving anything into or out of it.</li>
 *   <li>{@code SERVER_STOPPED} (TAIL): for process-wide ({@code static}, JVM-lived) state that references
 *       entities or a {@code ServerLevel} and only needs dropping, not persisting. Unreleased, a stopped
 *       world stays pinned in memory until the next one loads (or forever, for a static collection).</li>
 * </ul>
 *
 * <p>When you add such state, purge it at the matching point. Do not rely on the next server tick to do it,
 * and do not consolidate the two handlers into one.
 */
public final class LifecycleInit {
    private LifecycleInit() {}

    /** How many offending keys the join notice names before it stops and points at the log. A join
     *  message that fills the chat box is a message nobody reads, and past a handful of unrepairable
     *  keys the file needs opening anyway. */

    public static void register(ZombieRegistry registry, DimensionManager dimensions, TickScheduler scheduler) {
        // Warm the GPU compute backend at boot (when enabled) so its detection line (GPU name or CPU
        // fallback) is logged once at startup instead of lazily on the first flow-field solve.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (FlowConfig.useGpu) {
                GpuComputeManager.get().isAvailable();
            }
            // One throwaway solve on the backend that will serve the game, so the first real chase does not
            // pay for the kernel's first-call buffer allocation (or the CPU pool's first spin-up).
            GpuFlowField.compute(Snapshot.openSquare(64));
            PhaseManager.get().load(server); // restore the persisted phase (survives close/reopen)
            PackSavedData.loadAll(server, dimensions); // packs keep their route and ghosts across a restart
            // Placed dirt keeps the world age it was laid at, so it resumes crumbling instead of
            // being forgotten and left standing forever.
            restorePlacedBlocks(server, dimensions);
        });

        ConfigNotice.register();

        // Every world save, autosave included, and not only the shutdown one below: SERVER_STOPPING is never
        // reached by a crash or a power cut, so a world that had been autosaving for hours still came back
        // with no pack and no tracked dirt at all. Fires at the head of saveAllChunks, before the data
        // storage is flushed, which is what makes the write land in the same save.
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            PackSavedData.saveAll(server, dimensions);
            storePlacedBlocks(server, dimensions);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Before saveAllChunks: see the class javadoc for why this cannot move to STOPPED.
            PackSavedData.saveAll(server, dimensions);
            storePlacedBlocks(server, dimensions);
            // Hand vanilla AI back to every zombie we are currently freezing, BEFORE saveAllChunks flushes
            // NoAI to NBT. NoAI persists to NBT; our flag does not.
            for (SmartZombie sz : registry.all()) {
                sz.mood().releaseAiHold();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            registry.clear();
            dimensions.clear();
            // Release the rest of the process-wide state that references entities/levels, so the closed
            // world isn't pinned into the next session (audit #2, #20).
            scheduler.reset();
            ContaminationManager.onServerStopped();
            GorePuddles.onServerStopped();
            SpawnFilter.onServerStopped();
            VanillaTargetingGoals.clearAll();
        });
    }

    /** Hand every dimension's saved dirt back to its tracker, so a reopened world resumes the countdown
     *  instead of leaving the blocks standing untracked, and therefore standing forever. */
    private static void restorePlacedBlocks(MinecraftServer server, DimensionManager dimensions) {
        for (ServerLevel level : server.getAllLevels()) {
            dimensions.get(level.dimension()).placedBlocks()
                    .restore(level.getDataStorage().computeIfAbsent(PlacedBlockSavedData.TYPE).placements());
        }
    }

    /** Write every dimension's tracked dirt back, on STOPPING for the same reason as the packs. */
    private static void storePlacedBlocks(MinecraftServer server, DimensionManager dimensions) {
        for (ServerLevel level : server.getAllLevels()) {
            level.getDataStorage().computeIfAbsent(PlacedBlockSavedData.TYPE)
                    .store(dimensions.get(level.dimension()).placedBlocks().snapshot());
        }
    }
}
