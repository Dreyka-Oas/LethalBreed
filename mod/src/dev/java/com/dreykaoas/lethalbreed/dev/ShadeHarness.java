package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.ShelterFinder;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

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

/**
 * Audit finding #6: {@code ShelterFinder.findShade} sweeps {@code (2r+1)² × (2·max(4,r/2)+1)} positions —
 * 8125 at the shipped radius 12 — and the case that FAILS is exactly the case that repeats. A zombie with no
 * shade in range normally solves itself (it is on fire and dead in ~20 s), except when something blocks the
 * burn: {@code exposed} tests only {@code canSeeSky}, while {@code applySunBurn} returns early on
 * {@code isInWaterOrRain}. A zombie in that gap is alive, stationary, exposed and shade-less forever,
 * rescanning the identical volume at 1 Hz. The fix is {@code shelterRetryTicks} plus a "have I moved" test;
 * this rig measures whether it holds.
 *
 * <p><b>Area A — the throttle.</b> An open stone plate with no cover inside {@code shelterSearchRadius}, and a
 * 1×1 bedrock pen the zombie cannot leave (so it can never "move more than 4 blocks", which legitimately
 * re-arms the search). Its {@code ShelterFinder.SCAN_COUNT} delta over 400 ticks must sit near
 * {@code window / shelterRetryTicks}, not near one scan per activation. The measured number is logged so the
 * before/after lives in the log rather than in an argument.
 *
 * <p><b>The no-burn state is produced by RAIN, not by standing in water — and that is a finding.</b> The
 * audit describes the unbounded case as "a burning zombie in water". Built that way, this rig measured ZERO
 * scans: a zombie whose feet are in water is not {@code exposed} at all, because {@code canSeeSky} is false
 * for a position inside a water block (water is motion-blocking and attenuates sky light), so
 * {@code handleDaySleep} takes the "already in shade → doze" branch and never searches. The reachable form of
 * the bug is the other half of the same {@code isInWaterOrRain} guard: RAIN. A zombie standing in the open in
 * the rain has {@code canSeeSky == true} (so it is exposed and hunts for shade) and
 * {@code isInWaterOrRain == true} (so {@code applySunBurn} returns early and it never burns down) — alive,
 * stationary, exposed and shade-less indefinitely, which is exactly the loop the retry cooldown bounds.
 *
 * <p><b>Why {@code wet-case-alive} is not ceremony.</b> A zombie that burned to death stops scanning too, and
 * would pass the throttle check for entirely the wrong reason. The rig therefore also asserts the victim is
 * alive and still in the no-burn state — i.e. still in the unbounded-loop situation the finding is about.
 *
 * <p><b>Area B — the non-regression.</b> Same open plate plus a roofed shelter 6 blocks away. The cooldown
 * must throttle a FAILING search only; a search that succeeds must still put the zombie under cover on the
 * first attempt.
 *
 * <p><b>Why the two areas run in series, not together.</b> {@code SCAN_COUNT} is one process-wide counter.
 * Area B's zombie also calls {@code findShade} (it succeeds, then stops), so building both at once would fold
 * B's scans into A's measurement and blur exactly the number the rig exists to state. Same pattern as
 * {@code ContamRig}'s start offsets: rigs that share process-global state are serialised in time.
 */
public final class ShadeHarness {
    private ShadeHarness() {}

    private static final String SUITE = "shade";

    private static final int Y = ArenaBuilder.VERIFY_Y;   // 101 — walkable surface of the plate
    private static final int PLATE_Y = Y - 1;             // 100 — solid top of the plate

    /** Area A: open plate + an inescapable 1×1 pen, in the rain, no cover within {@code shelterSearchRadius}. */
    private static final int AX = 90;
    private static final int AZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    private static final int A_HALF = 15;                          // 30×30 plate
    /** Diagnostic tick offset: the exposure preconditions are logged rather than assumed. */
    private static final int A_PROBE = 60;
    /** Area B: open plate + a roofed shelter {@link #B_SHELTER_DX} blocks east. */
    private static final int BX = 150;
    private static final int BZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    private static final int B_HALF = 10;
    private static final int B_SHELTER_DX = 6;

    /** Observation window for each area, in ticks. */
    private static final int WINDOW = 400;
    private static final int A_BUILD = 5;
    private static final int A_EVAL = A_BUILD + WINDOW;   // 405
    private static final int B_BUILD = A_EVAL + 15;       // 420
    private static final int B_EVAL = B_BUILD + WINDOW;   // 820

    private static int tick = -1;
    private static boolean done = false;

    private static int savedRetry;
    private static boolean savedSunShelter;
    private static boolean savedClearWeather;

    private static Zombie aZombie;
    private static Zombie bZombie;
    private static long scanBase;
    /** Latched over the window: the victim was, at least once, genuinely exposed AND rain-protected — the
     *  precondition without which a low scan count means nothing at all. */
    private static boolean aExposedAndWet = false;
    private static boolean seekLatched = false;
    private static boolean bShelteredLatched = false;

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devShadeTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == A_BUILD) {
            worldRules(ow, server);
            buildAreaA(ow);
        } else if (tick > A_BUILD && tick < A_EVAL) {
            observeAreaA(ow, tick == A_BUILD + A_PROBE);
        } else if (tick == A_EVAL) {
            observeAreaA(ow, false);
            evaluateAreaA(ow);
        } else if (tick == B_BUILD) {
            buildAreaB(ow);
        } else if (tick > B_BUILD && tick < B_EVAL) {
            observeAreaB(ow);
        } else if (tick == B_EVAL) {
            observeAreaB(ow);
            evaluateAreaB(ow, server);
        }
    }

    private static void worldRules(ServerLevel ow, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true); // peaceful (dev default) deletes every monster
        WorldSpawnConfig.forceDayTime = false;
        ow.setDayTime(1000L);                                        // day: the sun-shelter path is live
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server); // and stays day for the whole run
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        // Phase 1: hostiles may exist at all (phase 0 culls them at ENTITY_LOAD), below sunImmunePhase (5) so
        // daylight still burns and the shade hunt is live, below dayAwakePhaseStart (10) so the victim cannot
        // roll into the awake minority and skip the whole behaviour.
        PhaseManager.get().setPhase(server, 1);
        // Rain is the whole mechanism of area A (see the class javadoc), so the mod's own weather enforcement
        // — which resets rain to clear every tick — has to stand down for the run.
        savedClearWeather = WorldSpawnConfig.clearWeather;
        WorldSpawnConfig.clearWeather = false;
        ow.setWeatherParameters(0, 24000, true, false);
        savedRetry = ZombieMoodConfig.shelterRetryTicks;
        savedSunShelter = ZombieMoodConfig.sunShelterEnabled;
        ZombieMoodConfig.shelterRetryTicks = 100;
        // sunShelterEnabled gates BOTH findShade call sites; with it off there is no shade behaviour at all and
        // every check below would pass or fail for a reason that has nothing to do with the throttle.
        ZombieMoodConfig.sunShelterEnabled = true;
    }

    /** Flat solid plate: top at {@link #PLATE_Y}, clear air above. */
    private static void plate(ServerLevel ow, int cx, int cz, int half) {
        ArenaBuilder.forceChunks(ow, cx, cz);
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                ow.setBlock(new BlockPos(x, PLATE_Y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 6; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildAreaA(ServerLevel ow) {
        plate(ow, AX, AZ, A_HALF);
        // A 1×1 BEDROCK pen, open to the sky. The victim must stay put: shadeRetryAt is deliberately bypassed
        // when the zombie has moved more than 4 blocks (new volume really is worth a new sweep), so a zombie
        // free to wander a 30×30 plate would re-arm the search legitimately and the measured count would say
        // nothing about the cooldown. Bedrock because MaterialRegistry refuses a negative destroy speed
        // outright — the pen can never become the experiment.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    ow.setBlock(new BlockPos(AX + dx, Y + dy, AZ + dz), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        aZombie = EntityType.ZOMBIE.spawn(ow, new BlockPos(AX, Y, AZ), EntitySpawnReason.COMMAND);
        if (aZombie != null) {
            aZombie.setPersistenceRequired();
            aZombie.setPos(AX + 0.5, Y, AZ + 0.5);
        }
        scanBase = ShelterFinder.SCAN_COUNT.get();
        LethalBreed.LOGGER.info("[Shade] area A built @({}, {}, {}): open {}×{} plate + 1×1 pen, raining={}, "
                        + "no cover within shelterSearchRadius={}; zombie={} scanBase={}",
                AX, Y, AZ, A_HALF * 2, A_HALF * 2, ow.isRaining(),
                ZombieMoodConfig.shelterSearchRadius, aZombie != null, scanBase);
    }

    /** Latch (and, once, log) the two preconditions the throttle measurement rests on. */
    private static void observeAreaA(ServerLevel ow, boolean logIt) {
        if (aZombie == null || aZombie.isRemoved()) {
            return;
        }
        BlockPos at = aZombie.blockPosition();
        boolean sky = ow.canSeeSky(at);
        boolean wet = aZombie.isInWaterOrRain();
        if (sky && wet) {
            aExposedAndWet = true;
        }
        if (logIt) {
            LethalBreed.LOGGER.info("[Shade] area A probe: pos={} canSeeSky={} inWaterOrRain={} onFire={} "
                            + "health={} raining={} scans={}",
                    at, sky, wet, aZombie.isOnFire(), aZombie.getHealth(), ow.isRaining(),
                    ShelterFinder.SCAN_COUNT.get() - scanBase);
        }
    }

    private static void evaluateAreaA(ServerLevel ow) {
        long delta = ShelterFinder.SCAN_COUNT.get() - scanBase;
        long budget = WINDOW / Math.max(1, ZombieMoodConfig.shelterRetryTicks) + 2;
        DevVerdict.check(SUITE, "scan-throttled", delta <= budget,
                "findShade ran " + delta + " times in " + WINDOW + " ticks (budget " + budget + " = window/"
                        + ZombieMoodConfig.shelterRetryTicks + "+2). Each call sweeps "
                        + sweepSize(ZombieMoodConfig.shelterSearchRadius) + " positions.");

        boolean alive = aZombie != null && aZombie.isAlive() && !aZombie.isRemoved();
        boolean wetNow = alive && aZombie.isInWaterOrRain();
        boolean skyNow = alive && ow.canSeeSky(aZombie.blockPosition());
        DevVerdict.check(SUITE, "wet-case-alive", alive && wetNow && skyNow && aExposedAndWet,
                "victim alive=" + alive + " canSeeSky=" + skyNow + " inWaterOrRain=" + wetNow
                        + " (both held during the window: " + aExposedAndWet + ")"
                        + " health=" + (alive ? aZombie.getHealth() : -1.0f)
                        + " pos=" + (alive ? aZombie.blockPosition().toString() : "n/a")
                        + " — exposed enough to hunt for shade, protected enough never to burn down; a "
                        + "burned-to-death victim would pass scan-throttled for the wrong reason");

        if (aZombie != null) {
            aZombie.remove(Entity.RemovalReason.DISCARDED); // stop it feeding SCAN_COUNT during area B
        }
        ArenaBuilder.releaseChunks(ow, AX, AZ);
    }

    private static void buildAreaB(ServerLevel ow) {
        plate(ow, BX, BZ, B_HALF);
        // A roofed 5×5 open-sided shelter: corner pillars and a lid. Open sides so reaching it is a plain walk
        // — the rig measures the SEARCH and the arrival, not the mod's ability to break into a box.
        int sx = BX + B_SHELTER_DX;
        for (int x = sx - 2; x <= sx + 2; x++) {
            for (int z = BZ - 2; z <= BZ + 2; z++) {
                ow.setBlock(new BlockPos(x, Y + 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int dy = 0; dy <= 2; dy++) {
                    ow.setBlock(new BlockPos(sx + dx, Y + dy, BZ + dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        bZombie = EntityType.ZOMBIE.spawn(ow, new BlockPos(BX, Y, BZ), EntitySpawnReason.COMMAND);
        if (bZombie != null) {
            bZombie.setPersistenceRequired();
        }
        LethalBreed.LOGGER.info("[Shade] area B built @({}, {}, {}): roofed 5×5 shelter at x={} ({} blocks "
                + "east); zombie={}", BX, Y, BZ, sx, B_SHELTER_DX, bZombie != null);
    }

    private static void observeAreaB(ServerLevel ow) {
        if (bZombie == null || bZombie.isRemoved()) {
            return;
        }
        SmartZombie sz = GameState.REGISTRY.get(bZombie.getId());
        if (sz == null) {
            return;
        }
        if (sz.mood().isSeekingShade()) {
            seekLatched = true;
        }
        if (!ow.canSeeSky(bZombie.blockPosition()) || sz.mood().isSleeping()) {
            bShelteredLatched = true;
        }
    }

    private static void evaluateAreaB(ServerLevel ow, MinecraftServer server) {
        boolean underCover = bZombie != null && !bZombie.isRemoved()
                && !ow.canSeeSky(bZombie.blockPosition());
        SmartZombie sz = bZombie == null ? null : GameState.REGISTRY.get(bZombie.getId());
        boolean asleep = sz != null && sz.mood().isSleeping();
        DevVerdict.check(SUITE, "reaches-shelter", seekLatched && (underCover || asleep || bShelteredLatched),
                "seek latched=" + seekLatched + ", under cover now=" + underCover + ", sleeping=" + asleep
                        + ", was ever sheltered=" + bShelteredLatched
                        + ", pos=" + (bZombie == null ? "n/a" : bZombie.blockPosition())
                        + " — the retry cooldown must not block a SUCCESSFUL search");

        if (bZombie != null) {
            bZombie.remove(Entity.RemovalReason.DISCARDED);
        }
        ArenaBuilder.releaseChunks(ow, BX, BZ);
        ZombieMoodConfig.shelterRetryTicks = savedRetry;
        ZombieMoodConfig.sunShelterEnabled = savedSunShelter;
        WorldSpawnConfig.clearWeather = savedClearWeather;
        ow.setWeatherParameters(6000, 0, false, false);
        done = true;
        DevVerdict.summary(SUITE, server);
    }

    /** Positions one {@code findShade} call touches at radius {@code r} — the cost the throttle bounds. */
    private static long sweepSize(int r) {
        long vBand = Math.max(4, r / 2);
        return (2L * r + 1) * (2L * r + 1) * (2L * vBand + 1);
    }
}
