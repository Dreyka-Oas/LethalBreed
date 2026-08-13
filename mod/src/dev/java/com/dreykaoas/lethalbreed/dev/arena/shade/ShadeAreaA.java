package com.dreykaoas.lethalbreed.dev.arena.shade;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ZombieMoodConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.probe.DevSink;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * Area A: does the shade sweep back off when it keeps finding nothing?
 *
 * <p>An open stone plate with no cover inside {@code shelterSearchRadius}, and a 1x1 bedrock pen the victim
 * cannot leave, in the rain — the one state where an exposed zombie neither finds shade nor burns to death, so
 * the loop the cooldown bounds runs forever. The number asserted on is the PROBE'S OWN sweep count, read
 * per-entity off {@link DevSink}, because the process-wide {@code SHELTER_SCAN} counter is global: measured
 * 243 global sweeps with 421 foreign zombies resident and 66 with 90, while the penned probe made four.
 *
 * <p>Split out of ShadeHarness, which held both areas, their diagnostics and the tick dispatch in 485 lines.
 */
final class ShadeAreaA {
    private ShadeAreaA() {}

    static final int AX = 90;
    static final int AZ = ArenaBuilder.VERIFY_BAND_Z + 60;   // z=460
    private static final int A_HALF = 15;                    // 30x30 plate

    private static Zombie aZombie;
    private static long scanBase;
    /** Highest count of zombies OTHER than the probe seen during the window. Non-zero voids the measurement. */
    private static int foreignZombies;
    private static long lastTracedScans = -1;
    /** Latched: the victim was, at least once, genuinely exposed AND rain-protected — the precondition
     *  without which a low scan count means nothing at all. */
    private static boolean aExposedAndWet = false;

    static void build(ServerLevel ow) {
        ShadeHarness.plate(ow, AX, AZ, A_HALF);
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
                    ow.setBlock(new BlockPos(AX + dx, ShadeHarness.Y + dy, AZ + dz), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        aZombie = ArenaBuilder.spawnZombie(ow, new BlockPos(AX, ShadeHarness.Y, AZ));
        if (aZombie != null) {
            aZombie.setPersistenceRequired();
            aZombie.setPos(AX + 0.5, ShadeHarness.Y, AZ + 0.5);
        }
        foreignZombies = clearForeignZombies(ow);
        scanBase = DevSink.get().counter(DevProbe.SHELTER_SCAN);   // kept only to show the global/per-entity gap in the log
        LethalBreed.LOGGER.info("[Shade] area A built @({}, {}, {}): open {}×{} plate + 1×1 pen, raining={}, "
                        + "no cover within shelterSearchRadius={}; zombie={} scanBase={}",
                AX, ShadeHarness.Y, AZ, A_HALF * 2, A_HALF * 2, ow.isRaining(),
                ZombieMoodConfig.shelterSearchRadius, aZombie != null, scanBase);
    }

    /** Latch (and, once, log) the two preconditions the throttle measurement rests on. */
    static void observe(ServerLevel ow, int tick, boolean logIt) {
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
                    DevSink.get().counter(DevProbe.SHELTER_SCAN) - scanBase);
        }
        traceScans(at, tick);
        foreignZombies = Math.max(foreignZombies, countForeignZombies(ow));
    }

    /**
     * Remove every zombie in the world except the probe, and report how many there were.
     *
     * <p>The process-wide {@code SHELTER_SCAN} counter is ONE counter. This rig already serialises its own two
     * areas for that reason, but it billed the probe for the whole world: the dev arenas share a persistent
     * save and every rig marks its zombies {@code setPersistenceRequired}, so leftovers from earlier runs load
     * with the arena chunks and — being exposed, by day, with no cover — each start sweeping too.
     *
     * <p>That is what made this check meaningless rather than merely noisy. Three runs of identical code
     * measured 2, 23 and 188 sweeps against a budget of 6, and the trace shows why: between ticks 6 and 8 the
     * global counter jumped by 42, then 38, then 33, while the probe sat in a 1×1 pen. The number was never
     * about the throttle; it was about how many strangers happened to be resident.
     *
     * <p>Safe because this world is a scratch arena: the dev harnesses build and demolish it every run, and
     * {@code SPAWN_MOBS} is off for the duration, so nothing repopulates behind us.
     */
    static int clearForeignZombies(ServerLevel ow) {
        int removed = 0;
        for (Entity e : ow.getAllEntities()) {
            if (e instanceof Zombie z && z != aZombie) {
                z.discard();
                removed++;
            }
        }
        if (removed > 0) {
            LethalBreed.LOGGER.info("[Shade] removed {} leftover zombie(s) so SHELTER_SCAN measures the probe "
                    + "alone.", removed);
        }
        return 0; // the purge succeeded; any NEW arrival is what the window must catch
    }

    /** Zombies other than the probe, sampled during the window: a newcomer voids the measurement. */
    static int countForeignZombies(ServerLevel ow) {
        int n = 0;
        for (Entity e : ow.getAllEntities()) {
            if (e instanceof Zombie z && z != aZombie) {
                n++;
            }
        }
        return n;
    }

    /**
     * Print WHICH shade target each sweep produced, and how far apart the sweeps are.
     *
     * <p>{@code scan-throttled} only reports a total, and a total cannot separate the two ways the throttle can
     * fail. It arms on a search that FINDS NOTHING; a search that finds a target the zombie then never reaches
     * clears the cooldown instead of arming it, and re-runs the full 8112-position sweep on every activation
     * once the memory expires. Those look identical in the count and need opposite fixes, so the target — in
     * particular whether its ShadeHarness.Y is BELOW the plate, i.e. unreachable cover under our own floor — is the
     * discriminating evidence.
     */
    static void traceScans(BlockPos at, int tick) {
        long scans = DevSink.get().counter(DevProbe.SHELTER_SCAN) - scanBase;
        if (scans == lastTracedScans) {
            return;
        }
        lastTracedScans = scans;
        SmartZombie sz = GameState.REGISTRY.get(aZombie.getId());
        LethalBreed.LOGGER.info("[Shade] sweep #{} at t={} pos={} | seeking={} hasTarget={} target=({},{},{})",
                scans, tick, at,
                sz != null && sz.mood().isSeekingShade(), sz != null && sz.hasTarget(),
                sz == null ? "n/a" : String.format("%.1f", sz.tgtX()),
                sz == null ? "n/a" : String.format("%.1f", sz.tgtY()),
                sz == null ? "n/a" : String.format("%.1f", sz.tgtZ()));
    }

    static void evaluate(ServerLevel ow) {
        // THE PROBE'S OWN sweeps, read per-entity off DevSink. The process-wide SHELTER_SCAN counter is
        // global and this world is a populated city: measured 243 global sweeps with 421 foreign zombies
        // resident, and 66 with 90, while the probe sat in a 1x1 pen the whole time. The global number was
        // never about the throttle.
        SmartZombie sz = aZombie == null ? null : GameState.REGISTRY.get(aZombie.getId());
        long delta = sz == null ? -1 : DevSink.get().counter(DevProbe.SHADE_SCAN, aZombie.getId());
        long global = DevSink.get().counter(DevProbe.SHELTER_SCAN) - scanBase;
        long budget = ShadeHarness.WINDOW / Math.max(1, ZombieMoodConfig.shelterRetryTicks) + 2;
        DevVerdict.check(ShadeHarness.SUITE, "scan-throttled", sz != null && delta <= budget,
                "the probe ran findShade " + delta + " times in " + ShadeHarness.WINDOW + " ticks (budget " + budget
                        + " = window/" + ZombieMoodConfig.shelterRetryTicks + "+2). The process-wide counter "
                        + "moved " + global + " over the same window, with " + foreignZombies + " other "
                        + "zombie(s) resident — which is why the per-entity count is the one asserted on. "
                        + "Each call sweeps " + ShadeHarness.sweepSize(ZombieMoodConfig.shelterSearchRadius) + " positions.");

        boolean alive = aZombie != null && aZombie.isAlive() && !aZombie.isRemoved();
        boolean wetNow = alive && aZombie.isInWaterOrRain();
        boolean skyNow = alive && ow.canSeeSky(aZombie.blockPosition());
        DevVerdict.check(ShadeHarness.SUITE, "wet-case-alive", alive && wetNow && skyNow && aExposedAndWet,
                "victim alive=" + alive + " canSeeSky=" + skyNow + " inWaterOrRain=" + wetNow
                        + " (both held during the window: " + aExposedAndWet + ")"
                        + " health=" + (alive ? aZombie.getHealth() : -1.0f)
                        + " pos=" + (alive ? aZombie.blockPosition().toString() : "n/a")
                        + " — exposed enough to hunt for shade, protected enough never to burn down; a "
                        + "burned-to-death victim would pass scan-throttled for the wrong reason");

        if (aZombie != null) {
            aZombie.remove(Entity.RemovalReason.DISCARDED); // stop it feeding SHELTER_SCAN during area B
        }
        ArenaBuilder.releaseChunks(ow, AX, AZ);
    }
}
