package com.dreykaoas.lethalbreed.dev.arena.statue;

import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;

import com.dreykaoas.lethalbreed.dev.DevBootstrap;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.harness.TickWait;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.sleep.DaySleep;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * Audit finding #2, end to end: a day-dozing zombie is held motionless with {@code setNoAi(true)}, and
 * {@code NoAI} is persisted to entity NBT while the "we are the ones holding it" flag is NOT. So every path
 * that discards the {@link com.dreykaoas.lethalbreed.entity.ZombieMood} object — chunk unload, server stop —
 * must release the hold first, or the zombie comes back from disk as a gravity-less, navigation-less statue
 * that {@code setPersistenceRequired} also stops from ever despawning.
 *
 * <p><b>Two runs, one suite, self-detecting.</b> The server-restart half of the contract cannot be observed
 * inside one server lifetime — the whole question is what a SAVE FILE holds. So this harness looks for its own
 * probe zombie (custom name {@code LB_STATUE_PROBE}) in the arena at start-up and picks its phase from that:
 *
 * <ul>
 *   <li><b>PHASE A</b> (no probe — first run): build a ROOFED arena so the zombie is not sun-exposed and takes
 *       the {@code dozeInPlace} branch directly, spawn the probe, prove it freezes, unload/reload its chunk to
 *       prove the {@code ENTITY_UNLOAD} release, drive it back into a doze and then
 *       {@code server.halt(false)} <em>while it is still frozen</em> — precisely the {@code SERVER_STOPPING}
 *       scenario the fix targets.</li>
 *   <li><b>PHASE B</b> (probe present — second run): the probe just came off disk. If {@code SERVER_STOPPING}
 *       released the hold before {@code saveAllChunks}, it loads with AI intact; if not, it is a statue.</li>
 * </ul>
 *
 * Driver: run {@code LB_DEV_TEST=statue ./gradlew runServer} TWICE against the same persistent
 * {@code run/server/world}. Phase B despawns the probe, so a third run starts over at phase A.
 *
 * <p><b>What is deliberately NOT tested.</b> An earlier protocol asked for a load-time repair of pre-existing
 * statues. It was implemented and then reverted (commit {@code 0a9e4d8}): {@code ENTITY_LOAD} fires for
 * freshly-added entities too, so the repair cancelled {@code MechTestArena}'s deliberate {@code setNoAi(true)}
 * and nothing distinguishes one of our old statues from a map-maker's deliberately frozen prop. There is no
 * such behaviour in the mod, so there is no check for it here.
 *
 * <p><b>Why the reload half re-finds the zombie by UUID.</b> A chunk reload deserialises a NEW entity object;
 * the old Java reference points at a removed husk whose {@code isNoAi()} is whatever it was before the unload.
 * Asserting on the stale reference would pass no matter what the save file said.
 *
 * <p><b>Why the reload verdict is taken from {@code ENTITY_LOAD} and not from a tick.</b> This was got wrong
 * twice before it was got right, and both times the harness reported a FAIL that the mod did not deserve. The
 * state being asserted on — "what came off disk" — survives for a handful of ticks at most: the probe reloads,
 * the mood step runs on its very next activation, the day-doze conditions still hold (the arena is roofed and
 * the time of day is pinned), and it freezes itself again. Any check on a later tick therefore reads
 * {@code NoAI == true} whether the release worked or not, and cannot tell "never lifted" from "lifted, then
 * legitimately dozed again". Fabric's {@code ENTITY_LOAD} fires during deserialisation, before any mod tick
 * touches the entity, so the value it sees IS the NBT value. The rig latches it there and asserts on it.
 */
public final class StatueHarness {
    private StatueHarness() {}

    static final String SUITE = "statue";
    static final String PROBE_NAME = "LB_STATUE_PROBE";

    static final int CX = 150;
    static final int CZ = ArenaBuilder.VERIFY_BAND_Z + 40; // z=440
    static final int Y = ArenaBuilder.VERIFY_Y;            // 101
    static final int HALF = 6;

    // ---- Schedule (server ticks since the first END_SERVER_TICK) -------------------------------------
    /** Force-load + world rules. The probe scan cannot run here: setChunkForced only queues a ticket. */
    private static final int FORCE_TICK = 1;
    /** Budget for the arena to become resident before the rig gives up LOUDLY. */
    private static final int RESIDENT_TIMEOUT = 600;
    /**
     * Extra ticks granted after the blocks are resident, for the entity storage to finish deserialising.
     *
     * <p>This is the one deliberate fixed wait in the file, and it is only reached AFTER the real condition
     * ({@code isLoaded}) has been met. Entity sections load separately from the block chunk, so residency alone
     * does not prove the probe would have been seen. It is also belt-and-braces rather than the primary signal:
     * {@code ENTITY_LOAD} fires during deserialisation and is checked every tick of this window, so a probe
     * that arrives at all is caught the tick it arrives — this bound only decides how long we insist there is
     * none.
     */
    private static final int ENTITY_SETTLE = 60;
    /** Phase A: the probe must be frozen within this many ticks of the phase starting (~13 s). */
    private static final int DOZE_WINDOW = 300;
    private static final int UNFORCE_DELAY = 10;
    /** Ticks a reactive chunk wait may take before the rig FAILs rather than asserting on a stale object. */
    private static final int WAIT_TIMEOUT = 900;
    /** Ticks of dozing opportunity granted after the reload before {@code re-freezes-after-reload} is read. */
    private static final int REDOZE_WINDOW = 120;

    /** Every wait here is reactive — see the comment at the WAIT_UNLOAD branch for why a schedule cannot work. */
    private enum Step { WAIT_RESIDENT, SETTLE, RUNNING, WAIT_UNLOAD, WAIT_RELOAD, REDOZE }

    private static int tick = -1;
    private static boolean phaseB = false;
    private static boolean done = false;

    private static Step step = Step.WAIT_RESIDENT;
    private static final TickWait RESIDENT =
            new TickWait("the arena chunks to become resident after the force-load", RESIDENT_TIMEOUT);
    private static final TickWait UNLOADED =
            new TickWait("the probe's chunk to leave memory after the unforce", WAIT_TIMEOUT);
    private static final TickWait RELOADED =
            new TickWait("the probe to be deserialised again after the re-force", WAIT_TIMEOUT);
    /** Tick at which phase A started, so the doze window is measured from the phase and not from boot. */
    private static int phaseStart = 0;
    private static int settleUntil = 0;
    private static int redozeUntil = 0;
    /** UUID of the probe THIS run spawned, so a probe arriving off disk afterwards is recognisable as one we
     *  wrongly concluded did not exist. */
    private static UUID spawnedId;

    static UUID probeId;
    private static boolean dozed = false;      // latched: was it ever (SLEEPING && NoAI) before DOZE_TICK
    private static boolean redozed = false;    // latched: did it freeze again after the chunk round-trip
    /** {@code isNoAi()} of the freshly deserialised entity, sampled on the FIRST tick it exists again. */
    private static boolean noAiAtFirstSight = true;
    /** Latched over the settle window: the reloaded probe was seen un-frozen at least once. */
    private static boolean liftedAfterReload = false;
    /** Harness tick of the most recent {@code ENTITY_LOAD} for the probe, and its {@code NoAI} at that
     *  instant — i.e. straight out of NBT, before any tick of ours can have re-applied a freeze. */
    private static int lastLoadTick = -1;
    private static boolean lastLoadNoAi = false;
    /** Harness tick at which the arena chunks were re-forced, so a load AFTER it is the reload we asked for. */
    private static int reforcedAt = Integer.MAX_VALUE;

    /**
     * {@code ServerEntityEvents.ENTITY_LOAD}, wired by {@code DevBootstrap}. Reads the probe's {@code NoAI}
     * at the only moment it is still the value the save file held.
     *
     * <p>The probe's own spawn in phase A cannot pollute this: {@code setCustomName} runs AFTER
     * {@code EntityType.spawn}, so the entity carries no name yet when this fires for it.
     */
    public static void onEntityLoad(net.minecraft.world.entity.Entity entity, ServerLevel level) {
        if (!DevTestConfig.devStatueTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        if (!(entity instanceof Zombie z)) {
            return;
        }
        Component name = z.getCustomName();
        if (name == null || !PROBE_NAME.equals(name.getString())) {
            return;
        }
        lastLoadTick = tick;
        lastLoadNoAi = z.isNoAi();
        // A probe off disk that arrives AFTER we already concluded there was none means the detection window
        // closed too early: phase A has just built a second arena on top of the save file the whole two-run
        // protocol exists to read. Silently double-probing would still print ALL DONE, so say it out loud.
        if (spawnedId != null && !z.getUUID().equals(spawnedId)) {
            DevVerdict.check(SUITE, "phase-detection", false,
                    "a stored probe (" + z.getUUID() + ") loaded at t=" + tick + ", after this run had already "
                            + "concluded there was none and spawned its own (" + spawnedId + "). The residency "
                            + "wait plus " + ENTITY_SETTLE + " settle ticks was not enough — phase B was "
                            + "skipped and the save file was never read back.");
        }
        probeId = z.getUUID();
        LethalBreed.LOGGER.info("[Statue] ENTITY_LOAD probe {} at t={} with noAi={} (straight from NBT).",
                probeId, tick, lastLoadNoAi);
    }

    public static void onTick(MinecraftServer server) {
        if (!DevTestConfig.devStatueTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == FORCE_TICK) {
            StatueArena.worldRules(ow, server);
            ArenaBuilder.forceChunks(ow, CX, CZ);
            RESIDENT.start(tick);
            return;
        }
        if (tick <= FORCE_TICK) {
            return;
        }
        if (step == Step.WAIT_RESIDENT || step == Step.SETTLE) {
            awaitDetection(ow, server);
            return;
        }
        if (phaseB) {
            return; // phase B finished inside detect()
        }
        phaseATick(ow, server);
    }

    /**
     * Decide which phase this run is, once the arena can actually answer the question.
     *
     * <p>The old rule was "detect at tick 30, by then the chunks are resident". That is a guess, and the file's
     * own {@code WAIT_UNLOAD} comment rejects exactly this kind of guess for the same operation — the sibling
     * rig measured chunk timings at 2, 35 and 272 ticks across runs. Concluding "no probe" while the arena is
     * still loading makes phase A build a second arena over the save file that the two-run protocol exists to
     * read, and the run still prints ALL DONE. So: wait for residency, then keep watching for the probe through
     * a bounded settle window, and FAIL loudly if the arena never loads at all.
     */
    private static void awaitDetection(ServerLevel ow, MinecraftServer server) {
        if (step == Step.WAIT_RESIDENT) {
            switch (RESIDENT.poll(tick, ow.isLoaded(new BlockPos(CX, Y, CZ)))) {
                case PENDING -> { return; }
                case TIMED_OUT -> {
                    DevVerdict.check(SUITE, "phase-detection", false, RESIDENT.describe()
                            + " — the arena never loaded, so 'no probe' would have meant nothing");
                    finish(ow, null, server);
                    return;
                }
                case MET -> {
                    LethalBreed.LOGGER.info("[Statue] arena resident after {} ticks; watching {} more for a "
                            + "stored probe.", RESIDENT.elapsed(), ENTITY_SETTLE);
                    step = Step.SETTLE;
                    settleUntil = tick + ENTITY_SETTLE;
                }
            }
        }
        // Watched every tick, not just at the end: a probe that is there at all is found the tick it appears.
        Zombie existing = StatueArena.findProbe(ow);
        if (existing != null) {
            phaseB = true;
            evaluatePhaseB(ow, server, existing);
            return;
        }
        if (tick >= settleUntil) {
            step = Step.RUNNING;
            phaseStart = tick;
            buildPhaseA(ow);
        }
    }




    // ---------------------------------------------------------------------------------------------------
    // PHASE A — first run
    // ---------------------------------------------------------------------------------------------------

    private static void buildPhaseA(ServerLevel ow) {
        // Stone floor at Y-1, clear air Y..Y+3, SOLID ROOF at Y+4. The roof is load-bearing, not decoration:
        // with open sky above, an exposed pre-immunity sleeper takes the shade-seeking branch (walks off,
        // burns) instead of dozing where we put it, and nothing would ever freeze.
        for (int x = CX - HALF; x <= CX + HALF; x++) {
            for (int z = CZ - HALF; z <= CZ + HALF; z++) {
                ow.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR.defaultBlockState(), 3);
                }
                ow.setBlock(new BlockPos(x, Y + 4, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // spawn(), NOT create()+addFreshEntity: only spawn() drives the ENTITY_LOAD event that registers the
        // zombie with ZombieRegistry, and an unregistered zombie has no SmartZombie, no ZombieMood, and can
        // therefore never doze at all. MechTestArena spawns the same way for the same reason.
        Zombie z = EntityType.ZOMBIE.spawn(ow, new BlockPos(CX, Y, CZ), EntitySpawnReason.COMMAND);
        if (z == null) {
            DevVerdict.check(SUITE, "probe-spawned", false, "EntityType.ZOMBIE.spawn returned null at "
                    + CX + "," + Y + "," + CZ);
            finish(ow, null, null);
            return;
        }
        z.setCustomName(Component.literal(PROBE_NAME));
        z.setCustomNameVisible(true);
        z.setPersistenceRequired();
        probeId = z.getUUID();
        spawnedId = probeId;
        LethalBreed.LOGGER.info("[Statue] PHASE A: roofed arena @({}, {}, {}) built at t={}, probe {} spawned.",
                CX, Y, CZ, tick, probeId);
    }

    private static void phaseATick(ServerLevel ow, MinecraftServer server) {
        if (probeId == null) {
            return;
        }
        Zombie probe = StatueArena.probe(ow);
        int sincePhase = tick - phaseStart;
        if (sincePhase < DOZE_WINDOW) {
            latchDoze(probe, false);
            if (sincePhase % 60 == 0) {
                StatueArena.logDozeInputs(ow, probe, sincePhase);
            }
            return;
        }
        if (sincePhase == DOZE_WINDOW) {
            latchDoze(probe, false);
            DevVerdict.check(SUITE, "dozes", dozed,
                    "probe reached (mood.isSleeping() && isNoAi()) within " + DOZE_WINDOW + " ticks: " + dozed
                            + "; now noAi=" + (probe != null && probe.isNoAi())
                            + " sleeping=" + StatueArena.sleeping(probe));
            if (!dozed) {
                // MANDATORY precondition. Every later assertion is about RELEASING a hold that was never
                // taken, so each of them would pass vacuously. Abort instead — and remove the probe, so the
                // next run does not read this aborted state as "phase B".
                LethalBreed.LOGGER.error("[Statue] probe never froze — aborting; the release assertions would "
                        + "all pass vacuously against a zombie that was never held.");
                finish(ow, probe, server);
            }
            return;
        }
        if (sincePhase == DOZE_WINDOW + UNFORCE_DELAY) {
            ArenaBuilder.releaseChunks(ow, CX, CZ);
            LethalBreed.LOGGER.info("[Statue] chunks released; probe was noAi={} at release.",
                    probe != null && probe.isNoAi());
            step = Step.WAIT_UNLOAD;
            UNLOADED.start(tick);
            return;
        }
        // ---- Reactive chunk round-trip -----------------------------------------------------------------
        // NOT a fixed schedule, and that is not fussiness. The latency between setChunkForced(false) and the
        // chunk actually leaving memory is wildly variable on a headless server — measured on the sibling
        // placed-block rig at 2, ~35 and 272 ticks across runs, and once not at all inside 1200 (this server
        // writes chunks synchronously to an external disk). A fixed re-force tick therefore risks asserting
        // on the ORIGINAL, still-resident entity, which trivially still holds the NoAi it set itself.
        if (step == Step.WAIT_UNLOAD) {
            switch (UNLOADED.poll(tick, probe == null)) {
                case PENDING -> { }
                case MET -> {
                    LethalBreed.LOGGER.info("[Statue] probe left memory {} ticks after the unforce; re-forcing.",
                            UNLOADED.elapsed());
                    reforcedAt = tick;
                    ArenaBuilder.forceChunks(ow, CX, CZ);
                    step = Step.WAIT_RELOAD;
                    RELOADED.start(tick);
                }
                case TIMED_OUT -> {
                    DevVerdict.check(SUITE, "lifted-after-chunk-reload", false, UNLOADED.describe()
                            + " — ENTITY_UNLOAD never fired, so there is nothing to assert on. Harness "
                            + "failure, not a silent pass.");
                    finish(ow, probe, server);
                }
            }
            return;
        }
        if (step == Step.WAIT_RELOAD) {
            // Re-found BY UUID: the reload deserialised a brand new object and the old reference is a husk.
            Zombie back = StatueArena.probe(ow);
            if (RELOADED.poll(tick, back != null) == TickWait.Result.MET) {
                // Sampled on the FIRST tick the entity exists again — this is what came off disk. Waiting even
                // a few ticks would read a value the mood step had already re-applied: a working release is
                // followed by a fresh doze within tens of ticks, so a late sample cannot tell "never lifted"
                // from "lifted and dozed again", and would report a FAIL either way.
                // Prefer the ENTITY_LOAD sample (the NBT value); fall back to first sight only if, somehow,
                // no load event was seen for this round trip.
                noAiAtFirstSight = lastLoadTick > reforcedAt ? lastLoadNoAi : back.isNoAi();
                LethalBreed.LOGGER.info("[Statue] probe back after {} ticks; noAi from {} = {}",
                        RELOADED.elapsed(), lastLoadTick > reforcedAt ? "ENTITY_LOAD" : "first sight",
                        noAiAtFirstSight);
                step = Step.REDOZE;
                redozeUntil = tick + REDOZE_WINDOW;
            } else if (RELOADED.poll(tick, false) == TickWait.Result.TIMED_OUT) {
                DevVerdict.check(SUITE, "lifted-after-chunk-reload", false, RELOADED.describe()
                        + " after the re-force — harness failure, not a silent pass.");
                finish(ow, probe, server);
            }
            return;
        }
        if (step == Step.REDOZE && tick < redozeUntil) {
            if (probe != null && !probe.isNoAi()) {
                liftedAfterReload = true;
            }
            latchDoze(probe, true);
            return;
        }
        if (step == Step.REDOZE) {
            latchDoze(probe, true);
            boolean frozenNow = probe != null && probe.isNoAi() && StatueArena.sleeping(probe);
            DevVerdict.check(SUITE, "lifted-after-chunk-reload", !noAiAtFirstSight,
                    "the reloaded probe's noAi on the first tick it existed again = " + noAiAtFirstSight
                            + " (seen un-frozen during the settle window: " + liftedAfterReload + ") — "
                            + "ENTITY_UNLOAD must release the hold before the chunk is written, or the zombie "
                            + "comes back a statue");
            DevVerdict.check(SUITE, "re-freezes-after-reload", redozed,
                    "probe dozed again after the reload: " + redozed + "; frozen right now=" + frozenNow);
            DevVerdict.check(SUITE, "PHASE-A-COMPLETE", true,
                    "halting the server with the probe noAi=" + (probe != null && probe.isNoAi())
                            + " — SERVER_STOPPING must release the hold BEFORE saveAllChunks. "
                            + "Re-run LB_DEV_TEST=statue to read the save file back (phase B).");
            // Deliberately NOT despawned and NOT unfrozen: the save file is the assertion.
            done = true;
            DevVerdict.summary(SUITE, null); // null: we halt below ourselves, while the probe is still frozen
            LethalBreed.LOGGER.info("[Statue] halting with the probe FROZEN — this is the SERVER_STOPPING case.");
            server.halt(false);
        }
    }


    private static void latchDoze(Zombie probe, boolean afterReload) {
        if (probe == null) {
            return;
        }
        if (StatueArena.sleeping(probe) && probe.isNoAi()) {
            if (afterReload) {
                redozed = true;
            } else {
                dozed = true;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // PHASE B — second run: what the save file actually holds
    // ---------------------------------------------------------------------------------------------------

    private static void evaluatePhaseB(ServerLevel ow, MinecraftServer server, Zombie probe) {
        boolean sawLoad = lastLoadTick >= 0;
        DevVerdict.check(SUITE, "lifted-after-server-restart", sawLoad && !lastLoadNoAi,
                (sawLoad ? "probe came off disk with noAi=" + lastLoadNoAi + " (sampled at ENTITY_LOAD, t="
                        + lastLoadTick + "; it is " + probe.isNoAi() + " now, " + (tick - lastLoadTick)
                        + " ticks later, because a roofed day-time arena legitimately re-dozes it)"
                        : "no ENTITY_LOAD was observed for the probe — nothing to assert on")
                        + ". It was FROZEN when the previous run halted, so SERVER_STOPPING must have released "
                        + "the hold before saveAllChunks.");
        LethalBreed.LOGGER.info("[Statue] PHASE B complete; despawning the probe so the next run restarts at "
                + "phase A.");
        finish(ow, probe, server);
    }

    /** Remove the probe, drop the force-load and close the suite. */
    private static void finish(ServerLevel ow, Zombie probe, MinecraftServer server) {
        if (probe != null) {
            probe.remove(Entity.RemovalReason.DISCARDED);
        }
        ArenaBuilder.releaseChunks(ow, CX, CZ);
        done = true;
        DevVerdict.summary(SUITE, server);
    }


}
