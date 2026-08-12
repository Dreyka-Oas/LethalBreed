package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Server-global difficulty phase (0, unbounded above). Auto-advances on a ~10-minute timer (with random
 * jitter), monotonic (only up) unless {@link ProgressionConfig#phaseMaxEnabled} pins/loops it. Announces
 * each change in chat, colored by {@link ProgressionConfig#phaseColorThresholds}. {@link ZombieVariation}
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
    private int phase = 0;
    private long lastAdvanceGameTime = Long.MIN_VALUE;
    private long nextIntervalTicks = -1;
    private final Random rng = new Random();

    // The world-attached store this mirror writes through (null until load()).
    private PhaseSavedData store;

    private PhaseManager() {}

    /**
     * Hard ceiling on the phase, whatever its provenance. Matches the {@code phaseMax} config bound
     * ({@code ConfigBoundsTable}: 1..1_000_000) so the two can't drift apart.
     *
     * <p>The phase feeds {@link PhaseTable#frequency} which drives a per-chunk, per-tick spawn loop
     * ({@code SpawnFrequencyMixin}) on the server thread — an unbounded phase is an unbounded loop.
     * The ceiling is enforced at EVERY write ({@link #clampPhase}), not just in the command, because
     * {@link #load} restores straight from the save file: a world already holding an absurd phase must
     * come back sane rather than replay the freeze on every boot.
     */
    public static final int MAX_PHASE = 1_000_000;

    /** The one place a phase value becomes authoritative. Every assignment to {@link #phase} goes through it. */
    private static int clampPhase(int p) {
        return Math.max(0, Math.min(p, MAX_PHASE));
    }

    /** Current phase, readable from anywhere (e.g. the spawn hook) without plumbing a server reference. */
    public static int current() {
        return INSTANCE.phase;
    }

    /** SERVER_STARTED: bind to the overworld's persisted phase data and restore the cached mirror from it.
     *  Replaces the old "reset to phase 1 each session" — the whole point is that it no longer resets. */
    public void load(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        store = overworld.getDataStorage().computeIfAbsent(PhaseSavedData.TYPE);
        // Clamp on restore, not just on write: a save produced before the ceiling existed (or hand-edited)
        // would otherwise reinstate an unbounded phase at every boot, which is the persistent half of the
        // /lethalphase denial of service.
        phase = clampPhase(store.phase);
        lastAdvanceGameTime = store.lastAdvanceGameTime;
        nextIntervalTicks = store.nextIntervalTicks;
        if (store.phase != phase) {
            // Write the repaired value straight back, so the save stops carrying the bad phase even if the
            // session ends before the next advance.
            com.dreykaoas.lethalbreed.LethalBreed.LOGGER.warn(
                    "[LethalBreed] persisted phase {} is out of range — clamped to {}", store.phase, phase);
            persist();
        }
        com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info(
                "[LethalBreed] phase loaded: {} (worldAge={}, nextIn={})",
                phase, overworld.getGameTime(), nextIntervalTicks);
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

    /** Cyclic color palette for the phase broadcast/command text — only the tier THRESHOLDS
     *  ({@link ProgressionConfig#phaseColorThresholds}) are configurable, this list is fixed. */
    private static final ChatFormatting[] COLOR_PALETTE = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GOLD,
            ChatFormatting.RED, ChatFormatting.DARK_RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
    };

    /** Color for a given phase, per the configured tier thresholds (largest threshold <= phase wins). */
    public static ChatFormatting colorFor(int phase) {
        double[] thresholds = ProgressionConfig.phaseColorThresholds;
        int tier = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (phase >= thresholds[i]) {
                tier = i;
            }
        }
        return COLOR_PALETTE[tier % COLOR_PALETTE.length];
    }

    /** SERVER THREAD (END_SERVER_TICK): advance the phase when its (jittered) interval has elapsed. */
    public void tick(MinecraftServer server) {
        if (!ProgressionConfig.phaseSystemEnabled) {
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
            phase = clampPhase(applyCeiling(phase + 1));
            lastAdvanceGameTime = now;
            scheduleNext();
            persist();
            com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info(
                    "[LethalBreed] phase advanced -> {} (worldAge={})", phase, now);
            broadcast(server);
        }
    }

    /** Applies the optional {@code phaseMax}/{@code phaseLoopEnabled} ceiling to a freshly-advanced phase.
     *  Pure function (no server/instance state) so it's directly unit-testable. */
    static int applyCeiling(int advancedPhase) {
        if (ProgressionConfig.phaseMaxEnabled && advancedPhase >= ProgressionConfig.phaseMax) {
            return ProgressionConfig.phaseLoopEnabled ? 1 : ProgressionConfig.phaseMax;
        }
        return advancedPhase;
    }

    private void scheduleNext() {
        int jitter = Math.max(0, ProgressionConfig.phaseJitterTicks);
        int j = jitter > 0 ? rng.nextInt(2 * jitter + 1) - jitter : 0;
        nextIntervalTicks = Math.max(1, ProgressionConfig.phaseIntervalTicks + j);
    }

    /** Force a phase (e.g. /lethalphase) and announce it. Manual override ignores the configurable
     *  {@code phaseMax} — an admin can deliberately force any phase past the auto-advance ceiling — but
     *  NOT the hard {@link #MAX_PHASE} ceiling, which exists to keep the spawn loop finite. */
    public void setPhase(MinecraftServer server, int p) {
        phase = clampPhase(p);
        lastAdvanceGameTime = server.overworld().getGameTime();
        scheduleNext();
        persist();
        broadcast(server);
    }

    public void broadcast(MinecraftServer server) {
        ChatFormatting color = colorFor(phase);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("☠ Phase " + phase).withStyle(color, ChatFormatting.BOLD), false);
    }
}
