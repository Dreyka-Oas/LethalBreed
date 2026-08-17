package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.dev.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackJoinRule;
import com.dreykaoas.lethalbreed.pack.PackManager;
import com.dreykaoas.lethalbreed.pack.PackState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies the pack instinct and the migration it drives.
 *
 * <p>Five stages, deliberately including one that must produce <b>nothing</b>: a lone zombie forming no pack
 * is the requested behaviour ("if there is nobody around it is pointless"), and a rig that only ever checks
 * for packs being created would pass just as happily on code that packs everything indiscriminately.
 *
 * <p>Every observation is latched over the whole window rather than sampled at the evaluation tick — a pack
 * can merge, a member can be re-homed, and an instantaneous reading catches whichever moment it lands on.
 *
 * <p><b>The {@code rejoin} stage does not reproduce a real chunk unload</b> — same limitation as
 * the dematerialise round trip, and for the same reason: this corridor is force-loaded, so it never happens on its
 * own, and the real trigger has been measured elsewhere in this project at up to 1200 ticks, which is not a
 * budget a deterministic gate can afford. It instead drives {@link PackManager#detach} and
 * {@link PackManager#rejoin} directly — the exact two calls {@code EntityEventsInit}'s {@code ENTITY_UNLOAD}
 * net and {@code ENTITY_LOAD} handler make — against a real, live {@link PackState}. What it proves: the
 * bookkeeping those two calls do (the {@code detached} count, the live roster, the tether, the persistent
 * attachment) is internally consistent, both when the member is still near its pack and when the pack has
 * wandered past {@code packRejoinRadius} while the member was away. What it does NOT prove: that
 * {@code ENTITY_LOAD} is reached at all on a genuine reload — that remains the one unproven link, same as
 * the dematerialise round trip's.
 */
public final class PackHarness extends TickPhasedHarness {

    public static final PackHarness INSTANCE = new PackHarness();

    private static final int FORM_BUILD = 5;
    private static final int FORM_EVAL = 300;
    private static final int LONE_BUILD = 320;
    private static final int LONE_EVAL = 600;
    private static final int MARCH_BUILD = 620;
    private static final int MARCH_EVAL = 1400;
    private static final int WALL_BUILD = 1420;
    private static final int WALL_EVAL = 2100;
    private static final int REJOIN_BUILD = 2120;
    private static final int REJOIN_EVAL = 2400;

    private static final int WALL_X = PackArena.CX + 60;

    private final List<Zombie> spawned = new ArrayList<>();
    private final Set<Long> packIds = new HashSet<>();

    // Latched observations. Sentinels chosen so "never measured" can never beat a real reading.
    private int formMinRegistered = Integer.MAX_VALUE;
    private int formMaxPacked;
    private int formMinDistinct = Integer.MAX_VALUE;
    private int loneMaxPacked;
    private final PackMarchProbe march = new PackMarchProbe();
    /** Members the registry held at build. The march is judged against these, not against how many were asked for. */
    private int marchExpected;
    private int wallPlaced;
    private int wallMinStanding = Integer.MAX_VALUE;

    private boolean rejoinMeasured;
    private boolean rejoinDetachCounted;
    private boolean rejoinNearSucceeded;
    private boolean rejoinFarRefused;

    private PackHarness() {
        super("pack",
                new Stage("formation", FORM_BUILD, FORM_EVAL),
                new Stage("isolation", LONE_BUILD, LONE_EVAL),
                new Stage("marche", MARCH_BUILD, MARCH_EVAL),
                new Stage("pas-de-casse", WALL_BUILD, WALL_EVAL),
                new Stage("rejoin", REJOIN_BUILD, REJOIN_EVAL));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devPackTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        PackSetup.prepare(stage, ow, server, cfg);
        PackArena.build(ow);

        switch (stage) {
            case 0 -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX - 12, 2, 12));
            case 1 -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX, 1, 1));
            case 2 -> {
                spawned.addAll(PackArena.spawnRow(ow, PackArena.CX - 8, 2, 8));
                marchExpected = PackArena.registered(spawned);
            }
            case 3 -> {
                wallPlaced = PackArena.buildWall(ow, WALL_X);
                spawned.addAll(PackArena.spawnRow(ow, PackArena.CX, 2, 8));
            }
            default -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX - 4, 2, 4));
        }
        PackSetup.report(stage, spawned);
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        switch (stage) {
            case 0 -> {
                formMinRegistered = Math.min(formMinRegistered, PackArena.registered(spawned));
                formMaxPacked = Math.max(formMaxPacked, PackArena.packed(spawned));
                int distinct = PackArena.distinctPacks(spawned, packIds);
                if (distinct > 0) {
                    formMinDistinct = Math.min(formMinDistinct, distinct);
                }
            }
            case 1 -> loneMaxPacked = Math.max(loneMaxPacked, PackArena.packed(spawned));
            case 2 -> march.observe(ow, spawned, marchExpected);
            case 3 -> wallMinStanding = Math.min(wallMinStanding, PackArena.wallStanding(ow, WALL_X));
            default -> {
                if (!rejoinMeasured) {
                    measureRejoin(ow);
                }
            }
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        switch (stage) {
            case 0 -> {
                check("formation-adhesion", formMaxPacked >= 3,
                        formMaxPacked + "/12 zombies carried a pack id at the peak; at the trough the mod knew of only "
                                + (formMinRegistered == Integer.MAX_VALUE ? "?" : formMinRegistered) + "/12");
                check("formation-une-seule-grappe", formMinDistinct >= 1 && formMinDistinct <= 2,
                        "au mieux " + (formMinDistinct == Integer.MAX_VALUE ? "aucune" : formMinDistinct)
                                + " meute(s) distincte(s) pour une grappe de 12");
            }
            case 1 -> check("isolation-no-pack", loneMaxPacked == 0,
                    "a lone zombie: " + loneMaxPacked + " membership(s) — 0 expected");
            case 2 -> evaluateMarch();
            case 3 -> check("pas-de-casse", wallMinStanding >= wallPlaced,
                    "wall: " + (wallMinStanding == Integer.MAX_VALUE ? "never measured" : wallMinStanding)
                            + "/" + wallPlaced + " blocs debout au minimum");
            default -> evaluateRejoin();
        }
        PackArena.clear(spawned);
        if (stage == 4) {
            PackArena.forceCorridor(ow, false);
            ArenaBuilder.releaseChunks(ow, PackArena.CX, PackArena.CZ);
        }
    }

    /**
     * Drive {@code PackManager.detach}/{@code rejoin} directly on a real, still-alive member of a real pack —
     * once, as soon as one has formed. See the class javadoc for what this does and does not prove.
     */
    private void measureRejoin(ServerLevel ow) {
        SmartZombie sz = firstPacked(spawned);
        if (sz == null) {
            return; // pack hasn't formed yet this tick — try again next tick, up to REJOIN_EVAL
        }
        long packId = sz.pursuit().pack().packId();
        PackManager manager = GameState.DIMENSIONS.get(ow.dimension()).packManager();
        PackState pack = manager.get(packId);
        if (pack == null) {
            return;
        }
        rejoinMeasured = true;

        // 1. Simulate "the chunk beat the materialiser to it": the member is still right here, but the
        //    manager believes it went to disk.
        int detachedBefore = pack.detached;
        manager.detach(sz);
        rejoinDetachCounted = pack.detached == detachedBefore + 1 && !pack.liveIds.contains(sz.id());

        // 2. It "comes back" without having moved — well inside packRejoinRadius. Must be accepted, the
        //    detached count must come back down, and it must be live again in every one of the three views.
        boolean nearAccepted = manager.rejoin(sz, packId);
        rejoinNearSucceeded = nearAccepted
                && pack.detached == detachedBefore
                && pack.liveIds.contains(sz.id())
                && sz.pursuit().pack().packId() == packId;

        // 3. Detach it again, but this time the pack has wandered far past packRejoinRadius while it was
        //    away (simulated by moving the tracked centroid, not the member — exactly what a real migration
        //    would have done over the same interval). Must be refused, tag cleared, detached count unchanged
        //    from what a refused rejoin should leave it at.
        double origX = pack.x;
        double origZ = pack.z;
        pack.x = origX + PackConfig.packRejoinRadius * 4 + 100.0;
        pack.z = origZ;
        manager.detach(sz);
        boolean farAccepted = manager.rejoin(sz, packId);
        rejoinFarRefused = !farAccepted
                && pack.detached == detachedBefore
                && sz.pursuit().pack().packId() == PackJoinRule.NO_PACK;
        pack.x = origX;
        pack.z = origZ;
    }

    private static SmartZombie firstPacked(List<Zombie> zombies) {
        for (Zombie z : zombies) {
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz != null && sz.pursuit().pack().inPack()) {
                return sz;
            }
        }
        return null;
    }

    private void evaluateRejoin() {
        check("rejoin-mesure", rejoinMeasured,
                rejoinMeasured ? "a pack formed, the test ran" : "no pack formed within the window — nothing measured");
        check("rejoin-detache-comptabilise", rejoinDetachCounted,
                "detach() must remove the member from liveIds and increment detached by 1");
        check("rejoin-proche-reussi", rejoinNearSucceeded,
                "a member that came back without moving must rejoin: detached restored, liveIds and tether updated");
        check("rejoin-loin-refuse", rejoinFarRefused,
                "a member that came back after the pack drifted beyond packRejoinRadius ("
                        + PackConfig.packRejoinRadius + " blocks) must be refused and its attachment cleared");
    }

    private void evaluateMarch() {
        check("marche-echantillonnee", march.sampled(),
                (march.sampled() ? "at least one tick" : "never sampled a tick")
                        + " avec les " + marchExpected + " membres suivis vivants");
        check("marche-rapprochement", march.sampled() && march.closed() >= PackMarchProbe.MIN_CLOSED,
                "centre closed by " + DevVerdict.fmt(march.closed()) + " blocks (>= "
                        + (int) PackMarchProbe.MIN_CLOSED + " expected, start "
                        + DevVerdict.fmt(march.startDist()) + ")");
        check("marche-cohesion", march.sampled() && march.maxSpread() <= PackMarchProbe.MAX_SPREAD,
                "max spread from centre " + DevVerdict.fmt(march.maxSpread()) + " blocks (<= packBreakRadius "
                        + (int) PackMarchProbe.MAX_SPREAD + ")");
    }
}
