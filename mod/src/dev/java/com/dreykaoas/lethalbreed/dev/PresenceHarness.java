package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
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
public final class PresenceHarness {
    private PresenceHarness() {}

    private static final String SUITE = "presence";

    private static final int CX = 400;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z;
    private static final int Y = ArenaBuilder.VERIFY_Y;
    private static final int ZOMBIES = 5;
    /** Zombies start this far west of the player — far enough that "closed the gap" is a real measurement. */
    private static final int START_OFFSET = 14;

    private static final int BUILD_TICK = 5;
    private static final int EVAL_TICK = 600; // ~30 s: several flow-field recompute intervals plus travel time

    private static int tick = -1;
    private static FakePlayer player;
    private static final List<Zombie> ZOMBIE_PROPS = new ArrayList<>();
    private static double startDistSum = 0.0;

    // Latched observations. Instantaneous state at the eval tick is flaky (a field can be mid-recompute, a
    // target can be dropped for one activation), so record the best state ever seen instead.
    private static boolean sawPlayerPresent = false;
    private static boolean sawFlowField = false;
    private static int maxTargeting = 0;
    private static double bestClosedSum = 0.0;

    public static void onTick(MinecraftServer server) {
        // Dev-env gate: builds blocks and force-spawns mobs. Never on a shipped jar even if the toggle is on.
        if (!ProgressionConfig.devPresenceTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == BUILD_TICK) {
            build(ow, server);
        } else if (tick > BUILD_TICK && tick < EVAL_TICK) {
            observe(ow);
        } else if (tick == EVAL_TICK) {
            observe(ow);
            evaluate(ow, server);
        }
    }

    private static void build(ServerLevel ow, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true);
        // Hold night with mob spawning off: the only zombies in this arena must be the ones we placed, and a
        // roofed-but-night arena removes sun-burn as a confounder entirely.
        WorldSpawnConfig.forceDayTime = false;
        ow.setDayTime(18000L);
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server);

        ArenaBuilder.forceChunks(ow, CX, CZ);
        // Flat roofed corridor: floor at Y-1, clear air Y..Y+3, glowstone lid at Y+4.
        for (int x = CX - START_OFFSET - 4; x <= CX + 4; x++) {
            for (int dz = -4; dz <= 4; dz++) {
                ow.setBlock(new BlockPos(x, Y - 1, CZ + dz), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, CZ + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                ow.setBlock(new BlockPos(x, Y + 4, CZ + dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }

        player = DevFakePlayer.spawn(ow, CX + 0.5, Y, CZ + 0.5);

        ZOMBIE_PROPS.clear();
        startDistSum = 0.0;
        for (int i = 0; i < ZOMBIES; i++) {
            Zombie z = EntityType.ZOMBIE.spawn(ow,
                    new BlockPos(CX - START_OFFSET, Y, CZ - 2 + i), EntitySpawnReason.COMMAND);
            if (z != null) {
                z.setPersistenceRequired();
                ZOMBIE_PROPS.add(z);
                startDistSum += player == null ? 0.0 : z.distanceTo(player);
            }
        }
        LethalBreed.LOGGER.info("[Presence] arena @({}, {}, {}) built: player={} zombies={} startDistAvg={}",
                CX, Y, CZ, player != null, ZOMBIE_PROPS.size(),
                fmt(ZOMBIE_PROPS.isEmpty() ? 0.0 : startDistSum / ZOMBIE_PROPS.size()));
    }

    private static void observe(ServerLevel ow) {
        if (player != null && ow.players().contains(player)) {
            sawPlayerPresent = true;
        }
        if (GameState.DIMENSIONS.get(ow.dimension()).flowFieldManager().active() != null) {
            sawFlowField = true;
        }
        int targeting = 0;
        double closed = 0.0;
        for (Zombie z : ZOMBIE_PROPS) {
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

    private static void evaluate(ServerLevel ow, MinecraftServer server) {
        int n = ZOMBIE_PROPS.size();
        double startAvg = n == 0 ? 0.0 : startDistSum / n;
        double bestAvg = n == 0 ? 0.0 : bestClosedSum / n;

        DevVerdict.check(SUITE, "player-in-level", sawPlayerPresent,
                "level.players()=" + ow.players().size() + " containsFake=" + (player != null && ow.players().contains(player)));
        DevVerdict.check(SUITE, "flowfield-built", sawFlowField,
                sawFlowField ? "FlowFieldManager.active() became non-null"
                        : "active() stayed null for " + EVAL_TICK + " ticks — no player was targetable");
        DevVerdict.check(SUITE, "zombies-target", maxTargeting > 0,
                maxTargeting + "/" + n + " zombies held a target at peak");
        DevVerdict.check(SUITE, "zombies-approach", n > 0 && bestAvg < startAvg - 2.0,
                "avg distance " + fmt(startAvg) + " -> " + fmt(bestAvg) + " blocks over " + n + " zombies");

        DevFakePlayer.despawn(ow, player);
        ArenaBuilder.releaseChunks(ow, CX, CZ);
        DevVerdict.summary(SUITE, server);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
