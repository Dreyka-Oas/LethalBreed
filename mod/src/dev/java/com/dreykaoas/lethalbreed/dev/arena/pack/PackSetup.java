package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.List;

/**
 * The preconditions every pack stage needs, stated rather than inherited.
 *
 * <p>Two of these were learned the hard way and are the reason this is a named step instead of three lines
 * inside {@code build}: the world defaults hostile to this rig, and a rig that inherits its preconditions
 * measures whatever the previous run happened to leave behind.
 */
final class PackSetup {
    private PackSetup() {}

    /** Night, so no day-sleep competes with the migration. */
    private static final long NIGHT = 18000L;

    static void prepare(ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        // server.properties ships difficulty=peaceful, and peaceful deletes every monster on the tick after
        // it spawns — setPersistenceRequired does not save it. Without this line the corridor is empty a
        // dozen ticks in, and the rig reports "no pack formed" about a population that no longer exists.
        server.setDifficulty(Difficulty.HARD, true);
        // Phase 0 culls every hostile at ENTITY_LOAD, so an arena built there measures an empty corridor.
        PhaseManager.get().setPhase(server, 1);
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        cfg.set("forceDayTime", false);
        ow.setDayTime(NIGHT);
        cfg.set("packEnabled", true);
        cfg.set("packMigrationEnabled", true);
        cfg.set("packDecisionDivisor", 1);  // decide every activation: converge inside a 300-tick window
        cfg.set("packsPerTick", 8);
        cfg.set("packFormMinSize", 3);
        // The pack debug stream used to be gated per stage by a `debugPacks` config option. That option is
        // gone: the trace now runs on the DevProbe.PACKS channel, which DevBootstrap enables once for the
        // whole dev run, so there is no per-stage switch left to flip.
    }

    /**
     * Log what the stage actually got, rather than what it asked for.
     *
     * <p>"No pack formed" has two very different causes — the rule declined, or the zombies never reached
     * the rule at all — and the verdict alone cannot tell them apart. {@code registered} separates them: a
     * zombie absent from the registry is never offered to {@code PackPass}.
     */
    static void report(int stage, List<Zombie> spawned) {
        LethalBreed.LOGGER.info("[Pack] stage {} : spawned={} registered={} registrySize={} packEnabled={}"
                        + " formMin={} divisor={}",
                stage, spawned.size(), PackArena.registered(spawned), GameState.REGISTRY.size(),
                PackConfig.packEnabled, PackConfig.packFormMinSize, PackConfig.packDecisionDivisor);
    }
}
