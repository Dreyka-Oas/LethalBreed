package com.dreykaoas.lethalbreed.dev.arena;

import com.dreykaoas.lethalbreed.dev.DevFakePlayer;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The foundation self-test that every other arena harness silently depends on: proves a synthetic player is
 * really present on a headless dedicated server, that the flow field is therefore built, and that zombies
 * consequently acquire it and close on it.
 *
 * <p>This is not ceremony. Before {@link DevFakePlayer} existed, a headless {@code runServer} had zero
 * players, {@code FlowFieldManager.tick} nulled the field on its first line, and the arena harnesses ran
 * their full tick budget producing no pathing telemetry at all — a rig that does nothing looks exactly like
 * a rig that passes. Any future harness that reports PASS while THIS suite would fail is reporting noise, so
 * keeping the proof runnable (rather than deleting it once it went green once) is the point.
 *
 * <p>Sited in the verification band ({@link ArenaBuilder#VERIFY_BAND_Z}) at {@link ArenaBuilder#VERIFY_Y},
 * disjoint from the legacy z≈0 arenas. Gated by {@code devPresenceTest} + a development environment.
 */
public final class PresenceHarness extends TickPhasedHarness {

    public static final PresenceHarness INSTANCE = new PresenceHarness();

    private static final int CX = 400;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;
    private static final int Y = ArenaBuilder.VERIFY_Y;
    private static final int ZOMBIES = 5;
    /** Zombies start this far west of the player — far enough that "closed the gap" is a real measurement. */
    private static final int START_OFFSET = 14;

    private static final int BUILD_TICK = 5;
    private static final int EVAL_TICK = 600; // ~30 s: several flow-field recompute intervals plus travel time

    private FakePlayer player;
    private final List<Zombie> zombies = new ArrayList<>();
    private double startDistSum = 0.0;

    // Latched observations. Instantaneous state at the eval tick is flaky (a field can be mid-recompute, a
    // target can be dropped for one activation), so record the best state ever seen instead.
    private boolean sawPlayerPresent = false;
    private boolean sawFlowField = false;
    private int maxTargeting = 0;
    // MAX_VALUE, not 0.0: with 0.0 as "unset" a sample where every zombie had been removed summed to 0.0,
    // beat the sentinel, and made zombies-approach report PASS at "0.00 blocks" — a dead arena scoring
    // better than a working one. Only a sample in which EVERY spawned zombie is still alive may win, so the
    // distance is always over the same population it started with.
    private double bestClosedSum = Double.MAX_VALUE;
    private int liveAtBest = 0;

    private PresenceHarness() {
        super("presence", new Stage("corridor", BUILD_TICK, EVAL_TICK));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devPresenceTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        server.setDifficulty(Difficulty.HARD, true);
        // Hold night with mob spawning off: the only zombies in this arena must be the ones we placed, and a
        // roofed-but-night arena removes sun-burn as a confounder entirely.
        cfg.set("forceDayTime", false);
        ow.setDayTime(18000L);
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        // Phase 1, stated rather than inherited. In phase 0 — which is what a FRESH world starts at —
        // SpawnFilter discards every hostile at ENTITY_LOAD, so all five zombies below vanish the tick they
        // are placed and the rig measures an empty corridor. This suite passed for months only because the
        // shared dev world happened to carry a higher phase in its save data; run against a new world it
        // reported "0/5 zombies held a target" with zombies=0 in the perf line. A rig must build its own
        // preconditions, exactly as StatueHarness and the mechanics arenas already do.
        PhaseManager.get().setPhase(server, 1);

        ArenaBuilder.roofedCorridor(ow, CX, CZ, Y, 4, START_OFFSET + 4, 4);

        player = DevFakePlayer.spawn(ow, CX + 0.5, Y, CZ + 0.5);

        zombies.clear();
        startDistSum = 0.0;
        for (int i = 0; i < ZOMBIES; i++) {
            Zombie z = EntityType.ZOMBIE.spawn(ow,
                    new BlockPos(CX - START_OFFSET, Y, CZ - 2 + i), EntitySpawnReason.COMMAND);
            if (z != null) {
                z.setPersistenceRequired();
                zombies.add(z);
                startDistSum += player == null ? 0.0 : z.distanceTo(player);
            }
        }
        LethalBreed.LOGGER.info("[Presence] arena @({}, {}, {}) built: player={} zombies={} startDistAvg={}",
                CX, Y, CZ, player != null, zombies.size(),
                DevVerdict.fmt(zombies.isEmpty() ? 0.0 : startDistSum / zombies.size()));
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        if (player != null && ow.players().contains(player)) {
            sawPlayerPresent = true;
        }
        if (GameState.DIMENSIONS.get(ow.dimension()).flowFieldManager().active() != null) {
            sawFlowField = true;
        }
        int targeting = 0;
        int live = 0;
        double closed = 0.0;
        for (Zombie z : zombies) {
            if (z.isRemoved()) {
                continue;
            }
            live++;
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (z.getTarget() != null || (sz != null && sz.hasTarget())) {
                targeting++;
            }
            if (player != null) {
                closed += z.distanceTo(player);
            }
        }
        maxTargeting = Math.max(maxTargeting, targeting);
        if (live == zombies.size() && closed < bestClosedSum) {
            bestClosedSum = closed;
            liveAtBest = live;
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        int n = zombies.size();
        boolean measured = liveAtBest == n && n > 0;
        double startAvg = n == 0 ? 0.0 : startDistSum / n;
        double bestAvg = measured ? bestClosedSum / n : Double.NaN;

        check("player-in-level", sawPlayerPresent,
                "level.players()=" + ow.players().size() + " containsFake=" + (player != null && ow.players().contains(player)));
        check("flowfield-built", sawFlowField,
                sawFlowField ? "FlowFieldManager.active() became non-null"
                        : "active() stayed null for " + EVAL_TICK + " ticks — no player was targetable");
        check("zombies-target", maxTargeting > 0,
                maxTargeting + "/" + n + " zombies held a target at peak");
        check("zombies-approach", measured && bestAvg < startAvg - 2.0,
                measured
                        ? "avg distance " + DevVerdict.fmt(startAvg) + " -> " + DevVerdict.fmt(bestAvg)
                                + " blocks over all " + n + " zombies"
                        : "never sampled a tick with all " + n + " zombies alive — no distance to report");

        DevFakePlayer.despawn(ow, player);
        ArenaBuilder.releaseChunks(ow, CX, CZ);
    }
}
