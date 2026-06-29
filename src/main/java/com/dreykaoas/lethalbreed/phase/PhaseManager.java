package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Server-global difficulty phase (1..15). Auto-advances on a ~10-minute timer (with random jitter),
 * monotonic (only up), capped at the last phase. Announces each change in chat. {@link ZombieVariation}
 * reads {@link #current()} when scaling a freshly-spawned zombie.
 *
 * <p>State is PERSISTED per-world via {@link PhaseSavedData} (in {@code <world>/data}), so the phase AND the
 * elapsed time toward the next advance survive close/reopen. The timer runs off the overworld's
 * {@code getGameTime()} (the persisted world age, monotonic across reloads) — NOT the server's since-boot
 * tick count, which resets to 0 every launch.
 */
public final class PhaseManager {
    private static final PhaseManager INSTANCE = new PhaseManager();

    public static PhaseManager get() {
        return INSTANCE;
    }

    // Cached mirror of the persisted state, so the hot static read in the spawn hook needs no server lookup.
    private int phase = 1;
    private long lastAdvanceGameTime = Long.MIN_VALUE;
    private long nextIntervalTicks = -1;
    private final Random rng = new Random();

    // The world-attached store this mirror writes through (null until load()).
    private PhaseSavedData store;

    private PhaseManager() {}

    /** Current phase, readable from anywhere (e.g. the spawn hook) without plumbing a server reference. */
    public static int current() {
        return INSTANCE.phase;
    }

    /** SERVER_STARTED: bind to the overworld's persisted phase data and restore the cached mirror from it.
     *  Replaces the old "reset to phase 1 each session" — the whole point is that it no longer resets. */
    public void load(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        store = overworld.getDataStorage().computeIfAbsent(PhaseSavedData.TYPE);
        phase = store.phase;
        lastAdvanceGameTime = store.lastAdvanceGameTime;
        nextIntervalTicks = store.nextIntervalTicks;
        com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info(
                "[LethalBreed] phase loaded: {} (worldAge={}, nextIn={})",
                phase, overworld.getGameTime(), nextIntervalTicks);
        logPhases();
    }

    /** Push the cached mirror into the world store and mark it dirty so it is written on the next save. */
    private void persist() {
        if (store != null) {
            store.phase = phase;
            store.lastAdvanceGameTime = lastAdvanceGameTime;
            store.nextIntervalTicks = nextIntervalTicks;
            store.setDirty();
        }
    }

    /** Reset to phase 1 (e.g. a future /lethalphase reset). Persists, so the reset survives a reload too. */
    public void reset() {
        phase = 1;
        lastAdvanceGameTime = Long.MIN_VALUE;
        nextIntervalTicks = -1;
        persist();
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
        long now = server.overworld().getGameTime();
        if (lastAdvanceGameTime == Long.MIN_VALUE) {
            lastAdvanceGameTime = now;
            scheduleNext();
            persist();
            return;
        }
        if (now - lastAdvanceGameTime >= nextIntervalTicks) {
            phase++;
            lastAdvanceGameTime = now;
            scheduleNext();
            persist();
            com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info(
                    "[LethalBreed] phase advanced -> {} (worldAge={})", phase, now);
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
        lastAdvanceGameTime = server.overworld().getGameTime();
        scheduleNext();
        persist();
        broadcast(server);
    }

    public void broadcast(MinecraftServer server) {
        PhaseConfig.PhaseDef d = PhaseConfig.def(phase);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§c☠ §lPhase " + phase + "§r§c — §6" + d.name()), false);
    }
}
