package com.dreykaoas.lethalbreed.init;


import com.dreykaoas.lethalbreed.special.runtime.gore.GorePuddles;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

import com.dreykaoas.lethalbreed.ai.flowfield.ComputeCalibration;
import com.dreykaoas.lethalbreed.ai.flowfield.gpu.GpuComputeManager;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.pack.runtime.PackSavedData;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.tick.TickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Registers server start/stop lifecycle hooks. Dev-only start hooks (headless climb / compute self-test)
 * live in the {@code dev} source set and are wired by {@code DevBootstrap}, not here.
 *
 * <p>Teardown happens at <b>two</b> points, and which one you need depends on whether your state has to
 * reach the save file:
 *
 * <ul>
 *   <li>{@code SERVER_STOPPING} (HEAD of {@code MinecraftServer.stopServer()}) — for anything that must be
 *       written back onto entities before the world is saved. {@code stopServer()} calls
 *       {@code saveAllChunks(...)} and then {@code serverLevel.close()} strictly between the two events, so
 *       this is the last moment an entity mutation still lands in NBT. The {@code NoAI} release lives here;
 *       see the comment on that handler before moving anything into or out of it.</li>
 *   <li>{@code SERVER_STOPPED} (TAIL) — for process-wide ({@code static}, JVM-lived) state that references
 *       entities or a {@code ServerLevel} and only needs dropping, not persisting. Unreleased, a stopped
 *       world stays pinned in memory until the next one loads (or forever, for a static collection).</li>
 * </ul>
 *
 * <p>When you add such state, purge it at the matching point — do not rely on the next server tick to do it,
 * and do not consolidate the two handlers into one.
 */
public final class LifecycleInit {
    private LifecycleInit() {}

    /** How many offending keys the join notice names before it stops and points at the log. A join
     *  message that fills the chat box is a message nobody reads, and past a handful of unrepairable
     *  keys the file needs opening anyway. */

    public static void register(ZombieRegistry registry, DimensionManager dimensions, TickScheduler scheduler) {
        // Warm the GPU compute backend at boot (when enabled) so its detection line — GPU name or CPU
        // fallback — is logged once at startup instead of lazily on the first flow-field solve.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (FlowConfig.useGpu) {
                GpuComputeManager.get().isAvailable();
                // Auto-calibrate the CPU↔GPU crossover on this machine when asked (one-off boot cost).
                if (FlowConfig.gpuAutoCalibrate) {
                    ComputeCalibration.calibrate();
                }
            }
            PhaseManager.get().load(server); // restore the persisted phase (survives close/reopen)
            PackSavedData.loadAll(server, dimensions); // packs keep their route and ghosts across a restart
        });

        ConfigNotice.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Before saveAllChunks: see the class javadoc for why this cannot move to STOPPED.
            PackSavedData.saveAll(server, dimensions);
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
        });
    }
}
