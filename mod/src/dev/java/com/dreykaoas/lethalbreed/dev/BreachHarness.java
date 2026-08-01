package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The two world-mutating behaviours that had ZERO automated coverage anywhere in the repo: breaking INTO a
 * sealed structure, and BRIDGING a gap that has no way round. Both are downstream of the flow field, the
 * breach coordinator ({@code Obstacle.handleToward} → {@code BreachCoordinator.resolve}) and the
 * {@code CellClassifier} chunk-cache refactor, so a regression in any of them shows up here and nowhere else.
 *
 * <p><b>The fake player is the bait AND the flow field.</b> {@code FlowFieldManager.tick} builds its field
 * around targetable PLAYERS and nothing else — with none present it nulls the field on its first line and
 * zombies never route through a breakable/buildable cell at all. Using the synthetic player as the target
 * too (rather than parking it elsewhere and baiting with a villager) means the field's goal and the zombies'
 * target are the same point, which is the configuration the shipped code is actually built for. It also means
 * the two rigs cannot run at once — {@link DevFakePlayer} owns a single UUID slot — so they are serialised in
 * time on a global tick offset, the same way {@code ContamRig} serialises the plague rigs.
 *
 * <p><b>Why {@code requireLineOfSight} is turned off for the run.</b> The break rig's target sits inside a
 * sealed dirt box, and sight is the only live-target acquisition path ({@code TargetSelector.findNearest} is
 * vision-only by design; hearing feeds MEMORY instead). A real player in that box gives itself away by moving
 * — Fabric's {@code FakePlayer} overrides {@code tick()} to a no-op, so it emits no footsteps and could never
 * be heard. Rather than fake a noise, the rig removes the question: the zombies know where the prey is, and
 * what is measured is purely whether they get THROUGH the wall. Restored when the suite closes.
 */
public final class BreachHarness {
    private BreachHarness() {}

    private static final String SUITE = "breach";

    private static final int Y = ArenaBuilder.VERIFY_Y;   // 101 — walkable
    private static final int FLOOR_Y = Y - 1;             // 100 — solid

    // ---- Break rig -----------------------------------------------------------------------------------
    private static final int KX = 210;
    private static final int KZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    private static final int K_PLATE = 14;
    /** Half-extent of the dirt box: inner cavity is ±2, so the walls are 2 blocks thick. */
    private static final int K_OUT = 4;
    private static final int K_IN = 2;
    private static final int K_TOP = Y + 4;   // ceiling occupies Y+4 and Y+5 → also 2 thick
    private static final int ZOMBIES = 6;
    private static final int K_STANDOFF = 10; // zombies spawn here, i.e. 6 blocks clear of the wall face

    // ---- Bridge rig ----------------------------------------------------------------------------------
    private static final int GX = 270;
    private static final int GZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    /** Trench x ∈ [GX-2, GX+2] — 5 wide, cut to {@link #TRENCH_FLOOR}. */
    private static final int TRENCH_HALF = 2;
    private static final int TRENCH_FLOOR = 90;
    private static final int PLAT = 9;      // each platform is PLAT wide in x
    private static final int HALF_Z = 5;    // platforms + trench span z ∈ [GZ-5, GZ+5]

    // ---- Schedule ------------------------------------------------------------------------------------
    private static final int BREAK_BUILD = 5;
    private static final int BREAK_EVAL = 605;
    private static final int BRIDGE_BUILD = 620;
    private static final int BRIDGE_EVAL = 1220;

    private static int tick = -1;
    private static boolean done = false;
    private static boolean savedLos;

    private static FakePlayer player;
    private static final List<Zombie> MOB = new ArrayList<>();

    // Break-rig observations
    private static final Set<BlockPos> WALL = new HashSet<>();
    private static boolean breached = false;
    private static boolean anyZombieInside = false;
    private static boolean breakTargeted = false;
    private static int wallGone = 0;

    // Bridge-rig observations
    private static final Set<BlockPos> TRENCH = new HashSet<>();
    private static int bridgeBlocks = 0;
    private static int maxTracked = 0;
    private static boolean bridgeTargeted = false;

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devBreachTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == BREAK_BUILD) {
            worldRules(ow, server);
            buildBreakRig(ow);
        } else if (tick > BREAK_BUILD && tick < BREAK_EVAL) {
            observeBreak(ow);
        } else if (tick == BREAK_EVAL) {
            observeBreak(ow);
            evaluateBreak(ow);
        } else if (tick == BRIDGE_BUILD) {
            buildBridgeRig(ow);
        } else if (tick > BRIDGE_BUILD && tick < BRIDGE_EVAL) {
            observeBridge(ow);
        } else if (tick == BRIDGE_EVAL) {
            observeBridge(ow);
            evaluateBridge(ow, server);
        }
    }

    private static void worldRules(ServerLevel ow, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true); // peaceful (dev default) deletes every monster
        WorldSpawnConfig.forceDayTime = false;
        ow.setDayTime(18000L);                                       // night: no sun-burn, no day-doze
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        // Phase 10: well clear of phase 0 (which culls every hostile at ENTITY_LOAD) and a raid-grade phase,
        // which is the regime breaking/bridging exist for.
        PhaseManager.get().setPhase(server, 10);
        savedLos = TargetingConfig.requireLineOfSight;
        TargetingConfig.requireLineOfSight = false; // see the class javadoc
    }

    /** Flat plate with an unbreakable rim, so nothing wanders off the edge and falls out of the experiment. */
    private static void plate(ServerLevel ow, int cx, int cz, int halfX, int halfZ) {
        for (int x = cx - halfX; x <= cx + halfX; x++) {
            for (int z = cz - halfZ; z <= cz + halfZ; z++) {
                ow.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 8; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        rim(ow, cx, cz, halfX + 1, halfZ + 1);
    }

    /** Bedrock ring at the plate boundary. Bedrock has a negative destroy speed, so {@code MaterialRegistry}
     *  refuses it outright and {@code CellClassifier} marks the column IMPASSABLE — a hard boundary the flow
     *  field routes around instead of a wall that becomes a second, uninteresting breach. */
    private static void rim(ServerLevel ow, int cx, int cz, int halfX, int halfZ) {
        for (int x = cx - halfX; x <= cx + halfX; x++) {
            for (int z = cz - halfZ; z <= cz + halfZ; z++) {
                if (x != cx - halfX && x != cx + halfX && z != cz - halfZ && z != cz + halfZ) {
                    continue;
                }
                for (int dy = -1; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Break rig
    // ---------------------------------------------------------------------------------------------------

    private static void buildBreakRig(ServerLevel ow) {
        ArenaBuilder.forceChunks(ow, KX, KZ);
        plate(ow, KX, KZ, K_PLATE, K_PLATE);

        WALL.clear();
        for (int x = KX - K_OUT; x <= KX + K_OUT; x++) {
            for (int z = KZ - K_OUT; z <= KZ + K_OUT; z++) {
                for (int y = Y; y <= K_TOP + 1; y++) {
                    boolean inner = Math.abs(x - KX) <= K_IN && Math.abs(z - KZ) <= K_IN && y <= Y + 2;
                    BlockPos p = new BlockPos(x, y, z);
                    if (inner) {
                        ow.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    } else {
                        ow.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                        WALL.add(p);
                    }
                }
            }
        }

        player = DevFakePlayer.spawn(ow, KX + 0.5, Y, KZ + 0.5);
        spawnZombies(ow, KX - K_STANDOFF, KZ);
        LethalBreed.LOGGER.info("[Breach] break rig @({}, {}, {}): sealed dirt box, {} wall blocks, player "
                + "inside={}, {} zombies at x={}.", KX, Y, KZ, WALL.size(), player != null, MOB.size(),
                KX - K_STANDOFF);
    }

    private static void spawnZombies(ServerLevel ow, int x, int z) {
        MOB.clear();
        for (int i = 0; i < ZOMBIES; i++) {
            Zombie zb = EntityType.ZOMBIE.spawn(ow, new BlockPos(x, Y, z - ZOMBIES / 2 + i),
                    EntitySpawnReason.COMMAND);
            if (zb != null) {
                zb.setPersistenceRequired();
                MOB.add(zb);
            }
        }
    }

    private static boolean anyTargeting() {
        for (Zombie zb : MOB) {
            if (zb.isRemoved()) {
                continue;
            }
            SmartZombie sz = GameState.REGISTRY.get(zb.getId());
            if (zb.getTarget() != null || (sz != null && sz.hasTarget())) {
                return true;
            }
        }
        return false;
    }

    private static void observeBreak(ServerLevel ow) {
        if (anyTargeting()) {
            breakTargeted = true;
        }
        if ((tick & 7) == 0) { // the full wall scan is ~400 positions; every 8th tick is plenty
            int gone = 0;
            for (BlockPos p : WALL) {
                if (!ow.getBlockState(p).is(Blocks.DIRT)) {
                    gone++;
                }
            }
            wallGone = Math.max(wallGone, gone);
            if (gone > 0) {
                breached = true;
            }
        }
        for (Zombie zb : MOB) {
            if (zb.isRemoved()) {
                continue;
            }
            BlockPos b = zb.blockPosition();
            if (Math.abs(b.getX() - KX) <= K_IN && Math.abs(b.getZ() - KZ) <= K_IN
                    && b.getY() >= Y - 1 && b.getY() <= Y + 2) {
                anyZombieInside = true;
            }
        }
    }

    private static void evaluateBreak(ServerLevel ow) {
        // MANDATORY: total inaction must never read as a pass. If nothing ever held a target the zombies were
        // idle props and "the wall is intact" says nothing about breaking.
        DevVerdict.check(SUITE, "zombies-targeted", breakTargeted,
                MOB.size() + " zombies spawned; at least one held a target during the window: " + breakTargeted);
        DevVerdict.check(SUITE, "wall-broken", breached,
                wallGone + "/" + WALL.size() + " wall blocks gone; a zombie got inside the cavity: "
                        + anyZombieInside);

        DevFakePlayer.despawn(ow, player);
        player = null;
        for (Zombie zb : MOB) {
            zb.remove(Entity.RemovalReason.DISCARDED);
        }
        MOB.clear();
        ArenaBuilder.releaseChunks(ow, KX, KZ);
    }

    // ---------------------------------------------------------------------------------------------------
    // Bridge rig
    // ---------------------------------------------------------------------------------------------------

    private static void buildBridgeRig(ServerLevel ow) {
        ArenaBuilder.forceChunks(ow, GX, GZ);
        int nearMax = GX - TRENCH_HALF - 1;
        int nearMin = nearMax - PLAT + 1;
        int farMin = GX + TRENCH_HALF + 1;
        int farMax = farMin + PLAT - 1;

        // Both platforms as one flat plate, then the trench is CUT out of it — so the only difference between
        // the two sides is the hole, and there is no path around it (the bedrock rim closes both ends in z).
        for (int x = nearMin; x <= farMax; x++) {
            for (int z = GZ - HALF_Z; z <= GZ + HALF_Z; z++) {
                ow.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 8; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        TRENCH.clear();
        for (int x = GX - TRENCH_HALF; x <= GX + TRENCH_HALF; x++) {
            for (int z = GZ - HALF_Z; z <= GZ + HALF_Z; z++) {
                for (int y = TRENCH_FLOOR; y <= FLOOR_Y; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    ow.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    TRENCH.add(p);
                }
            }
        }
        rim(ow, (nearMin + farMax) / 2, GZ, (farMax - nearMin) / 2 + 1, HALF_Z + 1);

        player = DevFakePlayer.spawn(ow, farMax - 2 + 0.5, Y, GZ + 0.5);
        spawnZombies(ow, nearMin + 2, GZ);
        maxTracked = 0;
        LethalBreed.LOGGER.info("[Breach] bridge rig @({}, {}, {}): platforms x[{}..{}] and x[{}..{}], trench "
                        + "x[{}..{}] cut to y={} ({} cells); player={}, {} zombies at x={}.",
                GX, Y, GZ, nearMin, nearMax, farMin, farMax, GX - TRENCH_HALF, GX + TRENCH_HALF, TRENCH_FLOOR,
                TRENCH.size(), player != null, MOB.size(), nearMin + 2);
    }

    private static void observeBridge(ServerLevel ow) {
        if (anyTargeting()) {
            bridgeTargeted = true;
        }
        maxTracked = Math.max(maxTracked,
                GameState.DIMENSIONS.get(ow.dimension()).placedBlocks().trackedCount());
        if ((tick & 7) != 0) {
            return;
        }
        int dirt = 0;
        for (BlockPos p : TRENCH) {
            if (ow.getBlockState(p).is(Blocks.DIRT)) {
                dirt++;
            }
        }
        bridgeBlocks = Math.max(bridgeBlocks, dirt);
    }

    private static void evaluateBridge(ServerLevel ow, MinecraftServer server) {
        DevVerdict.check(SUITE, "zombies-targeted-bridge", bridgeTargeted,
                MOB.size() + " zombies spawned; at least one held a target during the window: " + bridgeTargeted);
        DevVerdict.check(SUITE, "gap-bridged", bridgeBlocks > 0,
                bridgeBlocks + " DIRT blocks appeared inside the " + TRENCH.size() + "-cell trench volume; "
                        + "placedBlocks().trackedCount() peaked at " + maxTracked);
        // The end-to-end link PlacedBlockHarness stubs out: a real zombie placement really reaching the real
        // tracker, so the dirt it lays down is on the clock rather than becoming permanent terrain.
        DevVerdict.check(SUITE, "placement-tracked", maxTracked > 0,
                "PlacedBlockTracker.trackedCount() peaked at " + maxTracked + " during the bridge window");

        DevFakePlayer.despawn(ow, player);
        player = null;
        for (Zombie zb : MOB) {
            zb.remove(Entity.RemovalReason.DISCARDED);
        }
        MOB.clear();
        ArenaBuilder.releaseChunks(ow, GX, GZ);
        TargetingConfig.requireLineOfSight = savedLos;
        done = true;
        DevVerdict.summary(SUITE, server);
    }
}
