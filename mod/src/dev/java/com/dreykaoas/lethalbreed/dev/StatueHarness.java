package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
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

    private static final String SUITE = "statue";
    private static final String PROBE_NAME = "LB_STATUE_PROBE";

    private static final int CX = 150;
    private static final int CZ = ArenaBuilder.VERIFY_BAND_Z + 40; // z=440
    private static final int Y = ArenaBuilder.VERIFY_Y;            // 101
    private static final int HALF = 6;

    // ---- Schedule (server ticks since the first END_SERVER_TICK) -------------------------------------
    /** Force-load + world rules. The probe scan cannot run here: setChunkForced only queues a ticket. */
    private static final int FORCE_TICK = 1;
    /** Phase detection: by now the arena chunks are resident, so "no probe" really means "no probe". */
    private static final int DETECT_TICK = 30;
    /** Phase A: the probe must be frozen by here (~13 s of dozing opportunity). */
    private static final int DOZE_TICK = 300;
    private static final int UNFORCE_TICK = 310;
    /** Ticks a reactive chunk wait may take before the rig FAILs rather than asserting on a stale object. */
    private static final int WAIT_TIMEOUT = 900;
    /** Ticks of dozing opportunity granted after the reload before {@code re-freezes-after-reload} is read. */
    private static final int REDOZE_WINDOW = 120;

    /** Phase A's chunk round-trip is reactive, not scheduled — see the comment at the WAIT_UNLOAD branch. */
    private enum Step { RUNNING, WAIT_UNLOAD, WAIT_RELOAD, REDOZE }

    private static int tick = -1;
    private static boolean phaseB = false;
    private static boolean done = false;

    private static Step step = Step.RUNNING;
    private static int waitStart = 0;
    private static int redozeUntil = 0;

    private static UUID probeId;
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
        if (!ProgressionConfig.devStatueTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
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
        probeId = z.getUUID();
        LethalBreed.LOGGER.info("[Statue] ENTITY_LOAD probe {} at t={} with noAi={} (straight from NBT).",
                probeId, tick, lastLoadNoAi);
    }

    public static void onTick(MinecraftServer server) {
        if (!ProgressionConfig.devStatueTest || !FabricLoader.getInstance().isDevelopmentEnvironment() || done) {
            return;
        }
        tick++;
        ServerLevel ow = server.overworld();
        if (tick == FORCE_TICK) {
            worldRules(ow, server);
            ArenaBuilder.forceChunks(ow, CX, CZ);
            return;
        }
        if (tick < DETECT_TICK) {
            return;
        }
        if (tick == DETECT_TICK) {
            detect(ow, server);
            return;
        }
        if (phaseB) {
            return; // phase B finished inside detect()
        }
        phaseATick(ow, server);
    }

    /** Roofed, lit, permanent DAY at a phase below {@code sunImmunePhase} — the exact conditions in which an
     *  idle zombie takes the shade-less {@code dozeInPlace} branch instead of a shade hunt. */
    private static void worldRules(ServerLevel ow, MinecraftServer server) {
        // peaceful (the dev server.properties default) removes every monster on the next tick.
        server.setDifficulty(Difficulty.HARD, true);
        WorldSpawnConfig.forceDayTime = false;
        ow.setDayTime(1000L);                                        // morning: isBrightOutside() == true
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server); // doDaylightCycle off — day stays day
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);   // the only zombie here is our probe
        // Phase 1: hostile spawns are allowed at all (phase 0 culls EVERY monster at ENTITY_LOAD), it is below
        // sunImmunePhase (5) so the day-sleep path is the pre-immunity one, and below dayAwakePhaseStart (10)
        // so the probe cannot roll into the awake minority and simply never doze.
        PhaseManager.get().setPhase(server, 1);
    }

    private static AABB arenaBox() {
        return new AABB(CX - HALF - 2, Y - 6, CZ - HALF - 2, CX + HALF + 2, Y + 12, CZ + HALF + 2);
    }

    private static Zombie findProbe(ServerLevel ow) {
        for (Zombie z : ow.getEntitiesOfClass(Zombie.class, arenaBox())) {
            Component name = z.getCustomName();
            if (name != null && PROBE_NAME.equals(name.getString())) {
                return z;
            }
        }
        return null;
    }

    private static void detect(ServerLevel ow, MinecraftServer server) {
        Zombie existing = findProbe(ow);
        if (existing != null) {
            phaseB = true;
            evaluatePhaseB(ow, server, existing);
            return;
        }
        buildPhaseA(ow);
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
        LethalBreed.LOGGER.info("[Statue] PHASE A: roofed arena @({}, {}, {}) built, probe {} spawned.",
                CX, Y, CZ, probeId);
    }

    private static void phaseATick(ServerLevel ow, MinecraftServer server) {
        if (probeId == null) {
            return;
        }
        Zombie probe = probe(ow);
        if (tick < DOZE_TICK) {
            latchDoze(probe, false);
            return;
        }
        if (tick == DOZE_TICK) {
            latchDoze(probe, false);
            DevVerdict.check(SUITE, "dozes", dozed,
                    "probe reached (mood.isSleeping() && isNoAi()) within " + DOZE_TICK + " ticks: " + dozed
                            + "; now noAi=" + (probe != null && probe.isNoAi())
                            + " sleeping=" + sleeping(probe));
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
        if (tick == UNFORCE_TICK) {
            ArenaBuilder.releaseChunks(ow, CX, CZ);
            LethalBreed.LOGGER.info("[Statue] chunks released; probe was noAi={} at release.",
                    probe != null && probe.isNoAi());
            step = Step.WAIT_UNLOAD;
            waitStart = tick;
            return;
        }
        // ---- Reactive chunk round-trip -----------------------------------------------------------------
        // NOT a fixed schedule, and that is not fussiness. The latency between setChunkForced(false) and the
        // chunk actually leaving memory is wildly variable on a headless server — measured on the sibling
        // placed-block rig at 2, ~35 and 272 ticks across runs, and once not at all inside 1200 (this server
        // writes chunks synchronously to an external disk). A fixed re-force tick therefore risks asserting
        // on the ORIGINAL, still-resident entity, which trivially still holds the NoAi it set itself.
        if (step == Step.WAIT_UNLOAD) {
            if (probe == null) {
                LethalBreed.LOGGER.info("[Statue] probe left memory {} ticks after the unforce; re-forcing.",
                        tick - waitStart);
                reforcedAt = tick;
                ArenaBuilder.forceChunks(ow, CX, CZ);
                step = Step.WAIT_RELOAD;
                waitStart = tick;
            } else if (tick - waitStart >= WAIT_TIMEOUT) {
                DevVerdict.check(SUITE, "lifted-after-chunk-reload", false,
                        "the probe's chunk never unloaded within " + WAIT_TIMEOUT + " ticks, so ENTITY_UNLOAD "
                                + "never fired and there is nothing to assert on — harness failure, not a "
                                + "silent pass.");
                finish(ow, probe, server);
            }
            return;
        }
        if (step == Step.WAIT_RELOAD) {
            // Re-found BY UUID: the reload deserialised a brand new object and the old reference is a husk.
            Zombie back = probe(ow);
            if (back != null) {
                // Sampled on the FIRST tick the entity exists again — this is what came off disk. Waiting even
                // a few ticks would read a value the mood step had already re-applied: a working release is
                // followed by a fresh doze within tens of ticks, so a late sample cannot tell "never lifted"
                // from "lifted and dozed again", and would report a FAIL either way.
                // Prefer the ENTITY_LOAD sample (the NBT value); fall back to first sight only if, somehow,
                // no load event was seen for this round trip.
                noAiAtFirstSight = lastLoadTick > reforcedAt ? lastLoadNoAi : back.isNoAi();
                LethalBreed.LOGGER.info("[Statue] probe back after {} ticks; noAi from {} = {}",
                        tick - waitStart, lastLoadTick > reforcedAt ? "ENTITY_LOAD" : "first sight",
                        noAiAtFirstSight);
                step = Step.REDOZE;
                redozeUntil = tick + REDOZE_WINDOW;
            } else if (tick - waitStart >= WAIT_TIMEOUT) {
                DevVerdict.check(SUITE, "lifted-after-chunk-reload", false,
                        "the probe never came back within " + WAIT_TIMEOUT + " ticks of the re-force — "
                                + "harness failure, not a silent pass.");
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
            boolean frozenNow = probe != null && probe.isNoAi() && sleeping(probe);
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
        if (sleeping(probe) && probe.isNoAi()) {
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

    private static Zombie probe(ServerLevel ow) {
        if (probeId == null) {
            return null;
        }
        Entity e = ow.getEntity(probeId);
        return e instanceof Zombie z && !z.isRemoved() ? z : null;
    }

    private static boolean sleeping(Zombie z) {
        if (z == null) {
            return false;
        }
        SmartZombie sz = GameState.REGISTRY.get(z.getId());
        return sz != null && sz.mood().isSleeping();
    }
}
