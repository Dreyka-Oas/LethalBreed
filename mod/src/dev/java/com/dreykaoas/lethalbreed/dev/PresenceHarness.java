package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.DevTestConfig;
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
    private double bestClosedSum = 0.0;

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
        double closed = 0.0;
        for (Zombie z : zombies) {
            if (z.isRemoved()) {
                continue;
            }
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (z.getTarget() != null || (sz != null && sz.hasTarget())) {
                targeting++;
            }
            if (player != null) {
                closed += z.distanceTo(player);
            }
        }
        maxTargeting = Math.max(maxTargeting, targeting);
        if (bestClosedSum == 0.0 || closed < bestClosedSum) {
            bestClosedSum = closed;
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        int n = zombies.size();
        double startAvg = n == 0 ? 0.0 : startDistSum / n;
        double bestAvg = n == 0 ? 0.0 : bestClosedSum / n;

        check("player-in-level", sawPlayerPresent,
                "level.players()=" + ow.players().size() + " containsFake=" + (player != null && ow.players().contains(player)));
        check("flowfield-built", sawFlowField,
                sawFlowField ? "FlowFieldManager.active() became non-null"
                        : "active() stayed null for " + EVAL_TICK + " ticks — no player was targetable");
        check("zombies-target", maxTargeting > 0,
                maxTargeting + "/" + n + " zombies held a target at peak");
        check("zombies-approach", n > 0 && bestAvg < startAvg - 2.0,
                "avg distance " + DevVerdict.fmt(startAvg) + " -> " + DevVerdict.fmt(bestAvg) + " blocks over " + n + " zombies");

        DevFakePlayer.despawn(ow, player);
        ArenaBuilder.releaseChunks(ow, CX, CZ);
    }
}
