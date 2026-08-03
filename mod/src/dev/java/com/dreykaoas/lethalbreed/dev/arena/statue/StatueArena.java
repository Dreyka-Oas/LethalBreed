package com.dreykaoas.lethalbreed.dev.arena.statue;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.sleep.DaySleep;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The statue rig's world: the conditions it needs, where to look for the probe, and what it prints when the
 * probe refuses to doze.
 *
 * <p>Split out of {@link StatueHarness}, which held 508 lines: a two-run protocol, its schedule, its arena,
 * its lookups and its diagnostics. What is left there is the protocol; what is here is everything the protocol
 * asks the world about.
 */
final class StatueArena {
    private StatueArena() {}

    /** Roofed, lit, permanent DAY at a phase below {@code sunImmunePhase} — the exact conditions in which an
     *  idle zombie takes the shade-less {@code dozeInPlace} branch instead of a shade hunt. */
    static void worldRules(ServerLevel ow, MinecraftServer server) {
        // peaceful (the dev server.properties default) removes every monster on the next tick.
        server.setDifficulty(Difficulty.HARD, true);
        WorldSpawnConfig.forceDayTime = false;
        ow.setDayTime(1000L);                                        // morning: isBrightOutside() == true
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server); // doDaylightCycle off — day stays day
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);   // the only zombie here is our probe
        // Phase 1: hostile spawns are allowed at all (phase 0 culls EVERY monster at ENTITY_LOAD), it is below
        // sunImmunePhase (5) so the day-sleep path is the pre-immunity one, and below dayAwakePhaseStart (10)
        // so the probe cannot roll into the awake minority and simply never doze.
        PhaseManager.get().setPhase(server, 1);
    }
    static AABB arenaBox() {
        return new AABB(StatueHarness.CX - StatueHarness.HALF - 2, StatueHarness.Y - 6, StatueHarness.CZ - StatueHarness.HALF - 2, StatueHarness.CX + StatueHarness.HALF + 2, StatueHarness.Y + 12, StatueHarness.CZ + StatueHarness.HALF + 2);
    }
    static Zombie findProbe(ServerLevel ow) {
        for (Zombie z : ow.getEntitiesOfClass(Zombie.class, arenaBox())) {
            Component name = z.getCustomName();
            if (name != null && StatueHarness.PROBE_NAME.equals(name.getString())) {
                return z;
            }
        }
        return null;
    }
    /**
     * Every input {@code ZombieMood.handleDaySleep} branches on, printed as data.
     *
     * <p>When {@code dozes} fails, the verdict alone says only "it never froze" — which of the eight gates
     * refused is left to guesswork, and guessing is how the last two attempts at this rig went wrong. In
     * particular {@code isBrightOutside()} folds in the WEATHER (rain and thunder raise the ambient darkness),
     * and this rig pins the time of day but not the sky, so a stormy save file alone would keep the probe awake.
     */
    static void logDozeInputs(ServerLevel ow, Zombie probe, int sincePhase) {
        if (probe == null) {
            LethalBreed.LOGGER.info("[Statue] t+{}: no probe in the arena", sincePhase);
            return;
        }
        SmartZombie sz = GameState.REGISTRY.get(probe.getId());
        int phase = PhaseManager.current();
        LethalBreed.LOGGER.info("[Statue] t+{}: registered={} state={} noAi={} onGround={} pos={} | "
                        + "bright={} rain={} thunder={} skyDarken={} | phase={} staysAwake={} sunImmune={} "
                        + "canSeeSky={} onFire={} hurtTime={} hasTarget={}",
                sincePhase, sz != null, sz == null ? "n/a" : sz.state(), probe.isNoAi(), probe.onGround(),
                probe.blockPosition(), ow.isBrightOutside(), ow.isRaining(), ow.isThundering(),
                ow.getSkyDarken(), phase, DaySleep.staysAwake(probe, phase), !DaySleep.burnsInSun(phase),
                ow.canSeeSky(probe.blockPosition()), probe.isOnFire(), probe.hurtTime,
                sz != null && sz.hasTarget());
    }
    static Zombie probe(ServerLevel ow) {
        if (StatueHarness.probeId == null) {
            return null;
        }
        Entity e = ow.getEntity(StatueHarness.probeId);
        return e instanceof Zombie z && !z.isRemoved() ? z : null;
    }
    static boolean sleeping(Zombie z) {
        if (z == null) {
            return false;
        }
        SmartZombie sz = GameState.REGISTRY.get(z.getId());
        return sz != null && sz.mood().isSleeping();
    }
}
