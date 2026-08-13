package com.dreykaoas.lethalbreed.dev.arena;

import com.dreykaoas.lethalbreed.dev.DevFakePlayer;
import com.dreykaoas.lethalbreed.dev.DevVerdict;

import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.probe.DevProbe;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only, headless climb arena WITH A VERDICT. Builds a 12-block wall with a stationary target on top and
 * three zombies a short walk away, then measures what they actually did.
 *
 * <p><b>Why this used to prove nothing.</b> It was a one-shot {@code SERVER_STARTED} scenario with no
 * assertions at all: it logged its arena line, turned on the climb debug stream, and asked a human to read
 * the {@code [ClimbDbg]} output. Headless it produced ZERO {@code [ClimbDbg]} lines for a whole run — no
 * player was connected, so {@code FlowFieldManager.tick} nulled the field on its first line, the zombies
 * never pathed, and a rig that did nothing was byte-for-byte indistinguishable from a rig that passed. Two
 * things fix that: a {@link DevFakePlayer} (so the field exists and the zombies have prey), and per-zombie
 * telemetry latched over the window and turned into PASS/FAIL. The human stream still exists — this rig turns
 * the {@code DevProbe.CLIMB} channel on for its own run via {@code DevProbe.setTracing} — but nothing here
 * needs a human any more.
 *
 * <p><b>What the 3-block window at {@code gy+4..gy+6} is for.</b> Regression cover for the wall-scale's
 * face-end scan: a zombie scaling the centre column must climb PAST the gap (the wall resumes above it) to
 * the roof target, not hop off into it. {@code passed-the-window} is that assertion. The off-centre columns
 * stay solid, so the plain-wall path is exercised at the same time.
 *
 * <p>The arena geometry — platform, wall, window, villager, zombie spawns — is unchanged from the original
 * scenario; only the world rules (difficulty, phase, spawn/time gamerules), the synthetic player and the
 * measurement are new.
 */
public final class ClimbTest {
    private ClimbTest() {}

    private static final String SUITE = "climb";

    private static final int BUILD_TICK = 5;
    /** ~80 s: a 12-block ascent is a slow, many-activation behaviour, not a two-second one. */
    private static final int EVAL_TICK = 1605;

    private static int tick = -1;
    private static boolean done = false;

    private static int gy;
    private static int wallH;
    private static int wallX;
    private static int cz;
    private static int villagerY;
    private static Entity villager;
    private static FakePlayer player;

    private static final List<Zombie> MOB = new ArrayList<>();
    private static double[] maxY = new double[0];
    private static double[] minDist = new double[0];

    public static void onTick(MinecraftServer server) {
        // Dev-env gate: this force-loads chunks, builds a wall, spawns mobs AND flips
        // WorldSpawnConfig.forceDayTime at runtime. Far too destructive for a real world, so it runs ONLY
        // under gradle runServer even if the GUI toggle is left on.
        if (!DevTestConfig.devClimbTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel level = server.overworld();
        if (tick == BUILD_TICK) {
            build(level, server);
        } else if (tick > BUILD_TICK && tick < EVAL_TICK) {
            observe();
        } else if (tick == EVAL_TICK) {
            observe();
            evaluate(level, server);
        }
    }

    private static void build(ServerLevel level, MinecraftServer server) {
        int cx = 8;
        cz = 8;

        // peaceful is the dev server.properties default and removes every monster on the next tick; phase 0
        // makes SpawnFilter cull every hostile at ENTITY_LOAD. Neither has anything to do with climbing, and
        // either one silently empties the arena — which is exactly the failure mode this rig used to have.
        server.setDifficulty(Difficulty.HARD, true);
        PhaseManager.get().setPhase(server, 10);
        level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);

        // Force-load the arena chunks so entities tick even with no player online, and stop the zombies
        // burning (mod forces noon by default) by holding night + disabling the day-time rule.
        for (int dcx = -1; dcx <= 2; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                level.setChunkForced((cx >> 4) + dcx, (cz >> 4) + dcz, true);
            }
        }
        WorldSpawnConfig.forceDayTime = false;
        level.setDayTime(18000L);

        gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz); // floor level of the test platform
        wallH = 12; // tall wall to verify a high climb (was 4); villager sits at gy + wallH
        // Flat stone platform from x[cx-8..cx+4], z[cz-4..cz+4], clear air above — clean, slope-free geometry.
        for (int x = cx - 8; x <= cx + 4; x++) {
            for (int z = cz - 4; z <= cz + 4; z++) {
                level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int up = 0; up <= wallH + 3; up++) {
                    level.setBlock(new BlockPos(x, gy + up, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Wall near the east edge: 1 thick at x=cx+3, wallH tall, 7 wide along Z. Villager sits at gy+wallH.
        wallX = cx + 3;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dyy = 0; dyy < wallH; dyy++) {
                level.setBlock(new BlockPos(wallX, gy + dyy, cz + dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        // Carve a 3-tall WINDOW through the centre climbing column (z=cz, y +4..+6). Regression cover for the
        // wall-scale's face-end scan: a zombie scaling here must climb PAST the gap (wall resumes above) to the
        // roof target, not hop off into it. Off-centre columns stay solid for the plain-wall path.
        for (int dyy = 4; dyy <= 6; dyy++) {
            level.setBlock(new BlockPos(wallX, gy + dyy, cz), Blocks.AIR.defaultBlockState(), 3);
        }

        // Stationary, weightless, invulnerable villager target on top-centre of the wall.
        villagerY = gy + wallH;
        Entity v = EntityType.VILLAGER.spawn(level, new BlockPos(wallX, villagerY, cz), EntitySpawnReason.COMMAND);
        if (v instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (v != null) {
            v.setInvulnerable(true);
            v.setNoGravity(true);
            v.setPos(wallX + 0.5, villagerY, cz + 0.5);
        }
        villager = v;

        // The synthetic player shares the villager's perch. It is what makes the flow field exist at all, and
        // putting it AT the goal rather than off to one side means the field's goal and the zombies' prey are
        // the same point — the configuration the shipped pathing is built for, and the one a real player
        // standing on a roof actually is.
        player = DevFakePlayer.spawn(level, wallX + 0.5, villagerY, cz + 0.5);

        // Three zombies on the flat platform to the west — they should walk to the wall base then climb it.
        MOB.clear();
        for (int i = 0; i < 3; i++) {
            Entity z = EntityType.ZOMBIE.spawn(level, new BlockPos(cx - 5 + i, gy, cz), EntitySpawnReason.COMMAND);
            if (z instanceof Zombie zb) {
                zb.setPersistenceRequired();
                MOB.add(zb);
            }
        }
        maxY = new double[MOB.size()];
        minDist = new double[MOB.size()];
        for (int i = 0; i < MOB.size(); i++) {
            maxY[i] = MOB.get(i).getY();
            minDist[i] = Double.MAX_VALUE;
        }

        // Turn the CLIMB trace channel on for this run — DevTestConfig.debugClimb defaults off, and this is
        // the one rig that wants to watch [ClimbDbg] live, same as it used to set debugClimb directly.
        DevProbe.setTracing(DevProbe.CLIMB, true);
        LethalBreed.LOGGER.info(
                "[ClimbTest] flat arena: floor y={}, wall x={} ({} tall, y {}..{}), villager @({},{},{}), {} zombies "
                        + "west, fakePlayer={}. Climbing {} blocks reaches the villager. Watch [ClimbDbg].",
                gy, wallX, wallH, gy, gy + wallH - 1, wallX + 0.5, villagerY, cz + 0.5, MOB.size(),
                player != null, wallH);
    }

    private static void observe() {
        for (int i = 0; i < MOB.size(); i++) {
            Zombie z = MOB.get(i);
            if (z.isRemoved()) {
                continue;
            }
            maxY[i] = Math.max(maxY[i], z.getY());
            if (villager != null) {
                minDist[i] = Math.min(minDist[i], z.distanceTo(villager));
            }
        }
    }

    private static void evaluate(ServerLevel level, MinecraftServer server) {
        int n = MOB.size();
        int best = -1;
        for (int i = 0; i < n; i++) {
            if (best < 0 || maxY[i] > maxY[best]) {
                best = i;
            }
        }
        double topY = best < 0 ? Double.NaN : maxY[best];
        DevVerdict.check(SUITE, "reached-top", best >= 0 && topY >= villagerY - 1,
                "highest zombie reached y=" + f(topY) + " (target y=" + villagerY + ", floor y=" + gy
                        + "); per-zombie maxY=" + arr(maxY));
        // The SAME zombie must be past the deliberate gap, not parked in it.
        DevVerdict.check(SUITE, "passed-the-window", best >= 0 && topY > gy + 7,
                "highest zombie y=" + f(topY) + " vs window top gy+7=" + (gy + 7)
                        + " — it must climb past the 3-block gap at y " + (gy + 4) + ".." + (gy + 6)
                        + " rather than hop off into it");

        int stalled = 0;
        StringBuilder stallDetail = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Zombie z = MOB.get(i);
            if (z.isRemoved()) {
                continue;
            }
            double dxWall = Math.abs(z.getX() - (wallX + 0.5));
            if (dxWall <= 3.0 && maxY[i] <= gy + 1) {
                stalled++;
                stallDetail.append(" [id=").append(z.getId()).append(" dxWall=").append(f(dxWall))
                        .append(" maxY=").append(f(maxY[i])).append(']');
            }
        }
        DevVerdict.check(SUITE, "no-stall-at-base", stalled == 0,
                stalled + " zombie(s) ended within 3 blocks of the wall having never risen above gy+1="
                        + (gy + 1) + stallDetail);

        double closest = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            closest = Math.min(closest, minDist[i]);
        }
        DevVerdict.check(SUITE, "approached", closest <= 2.0,
                "closest any zombie ever got to the target: " + f(closest) + " blocks (per-zombie minimum="
                        + arr(minDist) + ")");

        DevFakePlayer.despawn(level, player);
        for (Zombie z : MOB) {
            z.remove(Entity.RemovalReason.DISCARDED);
        }
        if (villager != null) {
            villager.remove(Entity.RemovalReason.DISCARDED);
        }
        done = true;
        DevVerdict.summary(SUITE, server);
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String arr(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i] == Double.MAX_VALUE ? "n/a" : f(a[i]));
        }
        return sb.append(']').toString();
    }
}
