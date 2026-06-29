package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Random;

/**
 * Server-global difficulty phase (1..15). Auto-advances on a ~10-minute timer (with random jitter),
 * monotonic (only up), capped at the last phase. Announces each change in chat. {@link ZombieVariation}
 * reads {@link #current()} when scaling a freshly-spawned zombie. In-memory only (resets on server start).
 */
public final class PhaseManager {
    private static final PhaseManager INSTANCE = new PhaseManager();

    public static PhaseManager get() {
        return INSTANCE;
    }

    private int phase = 1;
    private long lastAdvanceTick = Long.MIN_VALUE;
    private long nextIntervalTicks = -1;
    private final Random rng = new Random();

    private PhaseManager() {}

    /** Current phase, readable from anywhere (e.g. the spawn hook) without plumbing a server reference. */
    public static int current() {
        return INSTANCE.phase;
    }

    public void reset() {
        phase = 1;
        lastAdvanceTick = Long.MIN_VALUE;
        nextIntervalTicks = -1;
        logPhases();
    }

    /** Dump the full phase list to the server console so it's visible at a glance. */
    public void logPhases() {
        com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info("[LethalBreed] Phases ({}):", PhaseConfig.count());
        for (int i = 1; i <= PhaseConfig.count(); i++) {
            com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info("  Phase {} — {}", i, PhaseConfig.def(i).name());
        }
    }

    /** SERVER THREAD (END_SERVER_TICK): advance the phase when its (jittered) interval has elapsed. */
    public void tick(MinecraftServer server) {
        if (!ProgressionConfig.phaseSystemEnabled || phase >= PhaseConfig.count()) {
            return;
        }
        long now = server.getTickCount();
        if (lastAdvanceTick == Long.MIN_VALUE) {
            lastAdvanceTick = now;
            scheduleNext();
            return;
        }
        if (now - lastAdvanceTick >= nextIntervalTicks) {
            phase++;
            lastAdvanceTick = now;
            scheduleNext();
            broadcast(server);
        }
    }

    private void scheduleNext() {
        int jitter = Math.max(0, ProgressionConfig.phaseJitterTicks);
        int j = jitter > 0 ? rng.nextInt(2 * jitter + 1) - jitter : 0;
        nextIntervalTicks = Math.max(1, ProgressionConfig.phaseIntervalTicks + j);
    }

    /** Force a phase (e.g. /lethalphase) and announce it. */
    public void setPhase(MinecraftServer server, int p) {
        phase = Math.max(1, Math.min(PhaseConfig.count(), p));
        lastAdvanceTick = server.getTickCount();
        scheduleNext();
        broadcast(server);
    }

    public void broadcast(MinecraftServer server) {
        PhaseConfig.PhaseDef d = PhaseConfig.def(phase);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§c☠ §lPhase " + phase + "§r§c — §6" + d.name()), false);
    }
}
