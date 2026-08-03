package com.dreykaoas.lethalbreed.dev.arena.shade;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.harness.TickWait;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.sleep.DaySleep;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * Area B: does a shade sweep that SUCCEEDS still put the zombie under cover?
 *
 * <p>Same open plate plus a roofed shelter six blocks east, and a bedrock starting gate that opens only once
 * {@code isSeekingShade()} is observed. Without the gate this measured luck: vanilla RandomStrollGoal can walk
 * the zombie under the roof before the mood ever searches, and once under cover it dozes and never seeks — the
 * rig then finds it asleep in the right place having exercised nothing. Two consecutive runs of identical code
 * disagreed on exactly that.
 *
 * <p>Split out of ShadeHarness, which held both areas, their diagnostics and the tick dispatch in 485 lines.
 */
final class ShadeAreaB {
    private ShadeAreaB() {}

    static final int BX = 150;
    static final int BZ = ArenaBuilder.VERIFY_BAND_Z + 60;   // z=460
    private static final int B_HALF = 10;
    private static final int B_SHELTER_DX = 6;

    private static Zombie bZombie;
    private static boolean seekLatched = false;
    private static boolean bShelteredLatched = false;
    /** The starting gate: shut until the shade-seek engages, so the walk cannot be an accident. */
    private static boolean gateOpen = false;
    private static final TickWait SEEK_ENGAGED =
            new TickWait("the area-B zombie to start seeking shade", 200);

    static void build(ServerLevel ow) {
        ShadeHarness.plate(ow, BX, BZ, B_HALF);
        // A roofed 5×5 open-sided shelter: corner pillars and a lid. Open sides so reaching it is a plain walk
        // — the rig measures the SEARCH and the arrival, not the mod's ability to break into a box.
        int sx = BX + B_SHELTER_DX;
        for (int x = sx - 2; x <= sx + 2; x++) {
            for (int z = BZ - 2; z <= BZ + 2; z++) {
                ow.setBlock(new BlockPos(x, ShadeHarness.Y + 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int dy = 0; dy <= 2; dy++) {
                    ow.setBlock(new BlockPos(sx + dx, ShadeHarness.Y + dy, BZ + dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        // A 1×1 bedrock pen, open to the sky, exactly like area A's — but here it is a STARTING GATE, opened the
        // moment the shade-seek engages (see openGate). Without it this check was flaky in a way that read as a
        // pass: the shelter is 6 blocks off on an open plate, so vanilla RandomStrollGoal can walk the zombie
        // under the roof before the mood ever runs its search. Once under cover it is no longer `exposed`, it
        // dozes, and the seek never happens — the rig then sees a zombie asleep under the shelter having never
        // exercised the mechanic. Two consecutive runs of identical code disagreed on precisely this.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    ow.setBlock(new BlockPos(BX + dx, ShadeHarness.Y + dy, BZ + dz), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        bZombie = EntityType.ZOMBIE.spawn(ow, new BlockPos(BX, ShadeHarness.Y, BZ), EntitySpawnReason.COMMAND);
        if (bZombie != null) {
            bZombie.setPersistenceRequired();
            bZombie.setPos(BX + 0.5, ShadeHarness.Y, BZ + 0.5);
        }
        gateOpen = false;
        SEEK_ENGAGED.start(0);
        LethalBreed.LOGGER.info("[Shade] area B built @({}, {}, {}): roofed 5×5 shelter at x={} ({} blocks "
                + "east); zombie={}", BX, ShadeHarness.Y, BZ, sx, B_SHELTER_DX, bZombie != null);
    }

    static void observe(ServerLevel ow, int tick) {
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
        openGate(ow, sz, tick);
        if (!ow.canSeeSky(bZombie.blockPosition()) || sz.mood().isSleeping()) {
            bShelteredLatched = true;
        }
        if ((tick - ShadeHarness.B_BUILD) % 40 == 0) {
            logSeekInputs(ow, sz, tick);
        }
    }

    /**
     * Hold the zombie at the spawn until it has decided to go for the shelter, then let it go.
     *
     * <p>This is what makes {@code reaches-shelter} measure the mechanic instead of luck. The gate opens on the
     * observed condition — {@code isSeekingShade()} — never on a tick count, so a slow first activation delays
     * the walk rather than invalidating it. If the seek never engages, the gate stays shut and the rig says so
     * with the budget it spent, which is a far more useful failure than a zombie found asleep somewhere.
     */
    static void openGate(ServerLevel ow, SmartZombie sz, int tick) {
        if (gateOpen) {
            return;
        }
        int t = tick - ShadeHarness.B_BUILD;
        switch (SEEK_ENGAGED.poll(t, sz.mood().isSeekingShade())) {
            case PENDING -> { }
            case MET -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            ow.setBlock(new BlockPos(BX + dx, ShadeHarness.Y + dy, BZ + dz),
                                    Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                gateOpen = true;
                LethalBreed.LOGGER.info("[Shade] B gate opened at t+{}: the seek engaged, the walk is now the "
                        + "thing being measured.", t);
            }
            case TIMED_OUT -> {
                DevVerdict.check(ShadeHarness.SUITE, "seek-engages", false, SEEK_ENGAGED.describe()
                        + " — the zombie never asked for shade, so nothing downstream measures the search.");
                gateOpen = true; // stop re-reporting; reaches-shelter will fail on its own terms below
            }
        }
    }

    /**
     * Every gate {@code handleDaySleep} passes through before it can even ASK for shade.
     *
     * <p>Needed because the failure this rig hit is invisible in its own verdict: the zombie ended UNDER the
     * shelter with {@code bShelteredLatched} true, yet {@code seekLatched} was false — it arrived by wandering,
     * never by seeking, and a check that only looked at where it ended up would have called that a pass. The
     * first gate is {@code level.isBrightOutside()}, which folds in the WEATHER: this rig deliberately makes it
     * rain for area A, and rain plus thunder pushes the ambient darkness past the daylight threshold. So the
     * sky conditions are printed as numbers rather than assumed.
     */
    static void logSeekInputs(ServerLevel ow, SmartZombie sz, int tick) {
        BlockPos at = bZombie.blockPosition();
        int phase = PhaseManager.current();
        LethalBreed.LOGGER.info("[Shade] B t+{}: pos={} state={} | bright={} rain={} thunder={} skyDarken={} | "
                        + "canSeeSky={} burnsInSun={} onFire={} hasTarget={} seeking={} health={}",
                tick - ShadeHarness.B_BUILD, at, sz.state(), ow.isBrightOutside(), ow.isRaining(), ow.isThundering(),
                ow.getSkyDarken(), ow.canSeeSky(at), DaySleep.burnsInSun(phase), bZombie.isOnFire(),
                sz.hasTarget(), sz.mood().isSeekingShade(), bZombie.getHealth());
    }

    static void evaluate(ServerLevel ow) {
        boolean underCover = bZombie != null && !bZombie.isRemoved()
                && !ow.canSeeSky(bZombie.blockPosition());
        SmartZombie sz = bZombie == null ? null : GameState.REGISTRY.get(bZombie.getId());
        boolean asleep = sz != null && sz.mood().isSleeping();
        DevVerdict.check(ShadeHarness.SUITE, "reaches-shelter", seekLatched && (underCover || asleep || bShelteredLatched),
                "seek latched=" + seekLatched + ", under cover now=" + underCover + ", sleeping=" + asleep
                        + ", was ever sheltered=" + bShelteredLatched
                        + ", pos=" + (bZombie == null ? "n/a" : bZombie.blockPosition())
                        + " — the retry cooldown must not block a SUCCESSFUL search");

        if (bZombie != null) {
            bZombie.remove(Entity.RemovalReason.DISCARDED);
        }
        ArenaBuilder.releaseChunks(ow, BX, BZ);
    }
}
