package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.io.ConfigIo;
import com.dreykaoas.lethalbreed.config.io.ConfigStructure;

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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

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
    private static final int JOIN_NOTICE_LINES = 6;

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

        // Tell an operator, once on join, that the config file has a structural problem. The startup
        // WARN covers dedicated-server admins who read logs; this covers everyone else, because a
        // solo player never opens latest.log and would otherwise just watch their hand-edited line
        // stop working with no explanation anywhere they look.
        //
        // Only for drift the loader could NOT repair — clean() ignores renamed typos, misplaced
        // options and stale category names, all of which the load-then-write cycle corrects by itself.
        // A message about a file that is already fixed is noise, and noise is what makes an operator
        // stop reading these.
        //
        // It names every offending key rather than counting them. This used to be a count plus
        // "/lethalconfig verify", which made the reader run a command to be told the one thing the
        // message was for; that subcommand is gone. What survives here is rare by construction (a
        // typo too ambiguous to correct, or an option written twice), so the line budget below is a
        // guard against a pathological file, not an expected path.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ConfigStructure.Report report = ConfigIo.lastReport();
            if (report == null || report.clean()) {
                return;
            }
            // Same gate the SetConfig packet and /lethalconfig use — only the people who can act on it.
            if (!handler.getPlayer().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                return;
            }
            ServerPlayer op = handler.getPlayer();
            op.sendSystemMessage(Component.literal(
                            "[LethalBreed] " + report.problemCount()
                                    + " problème(s) dans la structure de config/oas/lethalbreed.json :")
                    .withStyle(ChatFormatting.GOLD));

            int shown = 0;
            for (ConfigStructure.Unknown u : report.unknown()) {
                if (shown == JOIN_NOTICE_LINES) {
                    break;
                }
                shown++;
                op.sendSystemMessage(Component.literal(u.suggestion() != null
                                ? "  '" + u.name() + "' — vouliez-vous '" + u.suggestion()
                                        + "' ? Trop ambigu pour être corrigé tout seul."
                                : "  '" + u.name() + "' ne correspond à aucune option.")
                        .withStyle(ChatFormatting.RED));
            }
            for (String d : report.duplicated()) {
                if (shown == JOIN_NOTICE_LINES) {
                    break;
                }
                shown++;
                op.sendSystemMessage(Component.literal(
                                "  '" + d + "' est écrit dans deux catégories — une seule copie est lue.")
                        .withStyle(ChatFormatting.RED));
            }
            if (report.problemCount() > shown) {
                op.sendSystemMessage(Component.literal(
                                "  … et " + (report.problemCount() - shown) + " autre(s) — voir latest.log")
                        .withStyle(ChatFormatting.GRAY));
            }
        });

        // NoAI-release MUST happen here, on STOPPING, not on STOPPED: Fabric fires SERVER_STOPPING at HEAD of
        // MinecraftServer.stopServer() and SERVER_STOPPED at TAIL, but stopServer() calls saveAllChunks(...)
        // (flushing every loaded zombie's NoAI to disk) and then serverLevel.close() BEFORE it returns — i.e.
        // strictly between STOPPING and STOPPED. By the time STOPPED fires, the save already happened and the
        // level is closed, so releasing the hold there is a no-op that can't reach the NBT that was just
        // written. Do NOT "tidy" this back into the SERVER_STOPPED handler below — that silently reintroduces
        // the frozen-statue bug this exists to fix (audit #2).
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
            com.dreykaoas.lethalbreed.special.runtime.GorePuddles.onServerStopped();
        });
    }
}
