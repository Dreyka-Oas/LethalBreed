package com.dreykaoas.lethalbreed.dev.arena.shade;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.dev.DevVerdict;

import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
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
 * re-arms the search). Its per-entity {@code SHADE_SCAN} delta over 400 ticks must sit near
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
 * <p>Area B's zombie starts behind a bedrock GATE that opens only once {@code isSeekingShade()} is observed.
 * Without it the check was flaky in a way that looked like a pass: the shelter is six blocks off on an open
 * plate, so vanilla {@code RandomStrollGoal} can walk the zombie under the roof before the mood ever runs its
 * search — and once under cover it is no longer exposed, so it dozes and the seek never happens. The rig then
 * finds a zombie asleep under the shelter having exercised nothing. Two consecutive runs of identical code
 * disagreed on exactly this, one reporting FAIL with the zombie already under cover. The gate makes the walk
 * the thing being measured rather than the thing being hoped for, and it opens on the observed condition
 * rather than on a tick count, so a slow first activation delays the walk instead of invalidating it.
 *
 * <p><b>Why the two areas run in series, not together.</b> {@code SHELTER_SCAN} is one process-wide counter.
 * Area B's zombie also calls {@code findShade} (it succeeds, then stops), so building both at once would fold
 * B's scans into A's measurement and blur exactly the number the rig exists to state. Same pattern as
 * {@code ContamRig}'s start offsets: rigs that share process-global state are serialised in time.
 */
public final class ShadeHarness {
    private ShadeHarness() {}

    static final String SUITE = "shade";

    static final int Y = ArenaBuilder.VERIFY_Y;   // 101 — walkable surface of the plate
    static final int PLATE_Y = Y - 1;             // 100 — solid top of the plate

    /** Area A: open plate + an inescapable 1×1 pen, in the rain, no cover within {@code shelterSearchRadius}. */
    static final int AX = 90;
    static final int AZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460
    /** Diagnostic tick offset: the exposure preconditions are logged rather than assumed. */
    /** Area B: open plate + a roofed shelter {@link #B_SHELTER_DX} blocks east. */
    static final int BX = 150;
    static final int BZ = ArenaBuilder.VERIFY_BAND_Z + 60; // z=460

    /** Observation window for each area, in ticks. */
    static final int WINDOW = 400;
    static final int A_BUILD = 5;
    private static final int A_EVAL = A_BUILD + WINDOW;   // 405
    static final int B_BUILD = A_EVAL + 15;       // 420
    private static final int B_EVAL = B_BUILD + WINDOW;   // 820

    private static int tick = -1;
    private static boolean done = false;

    private static int savedRetry;
    private static boolean savedSunShelter;
    private static boolean savedClearWeather;


    public static void onTick(MinecraftServer server) {
        if (!DevTestConfig.devShadeTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == A_BUILD) {
            worldRules(ow, server);
            ShadeAreaA.build(ow);
        } else if (tick > A_BUILD && tick < A_EVAL) {
            ShadeAreaA.observe(ow, tick, true);
        } else if (tick == A_EVAL) {
            ShadeAreaA.observe(ow, tick, false);
            ShadeAreaA.evaluate(ow);
        } else if (tick == B_BUILD) {
            // Do NOT clear the rain area A asked for, however tempting: tried, measured, reverted. Under
            // rain the zombie does not burn (onFire=false) and still runs the shade search — the seek is
            // driven by exposure, not by damage — and it walks to the shelter. Clearing the sky makes it
            // catch fire instead: it loses ~9 HP in 120 ticks while pathing, drops its target, and ends the
            // window wandering. reaches-shelter went from passing to 1/5 on that change alone.
            ShadeAreaB.build(ow);
        } else if (tick > B_BUILD && tick < B_EVAL) {
            ShadeAreaB.observe(ow, tick);
        } else if (tick == B_EVAL) {
            ShadeAreaB.observe(ow, tick);
            ShadeAreaB.evaluate(ow);
            teardown(ow, server);
        }
    }

    private static void worldRules(ServerLevel ow, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true); // peaceful (dev default) deletes every monster
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
    static void plate(ServerLevel ow, int cx, int cz, int half) {
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


    /** Hand back everything the rig borrowed from the process, then close the suite. Lives here rather than
     *  in an area: it undoes what {@link #worldRules} did, and only the harness knows the run is over. */
    private static void teardown(ServerLevel ow, MinecraftServer server) {
        ZombieMoodConfig.shelterRetryTicks = savedRetry;
        ZombieMoodConfig.sunShelterEnabled = savedSunShelter;
        WorldSpawnConfig.clearWeather = savedClearWeather;
        ow.setWeatherParameters(6000, 0, false, false);
        done = true;
        DevVerdict.summary(SUITE, server);
    }

    /** Positions one {@code findShade} call touches at radius {@code r} — the cost the throttle bounds. */
    static long sweepSize(int r) {
        long vBand = Math.max(4, r / 2);
        return (2L * r + 1) * (2L * r + 1) * (2L * vBand + 1);
    }
}
