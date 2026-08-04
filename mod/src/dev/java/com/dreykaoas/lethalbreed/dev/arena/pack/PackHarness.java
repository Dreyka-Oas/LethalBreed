package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;

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
 * <p>Four stages, deliberately including one that must produce <b>nothing</b>: a lone zombie forming no pack
 * is the requested behaviour ("if there is nobody around it is pointless"), and a rig that only ever checks
 * for packs being created would pass just as happily on code that packs everything indiscriminately.
 *
 * <p>Every observation is latched over the whole window rather than sampled at the evaluation tick — a pack
 * can merge, a member can be re-homed, and an instantaneous reading catches whichever moment it lands on.
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

    private PackHarness() {
        super("pack",
                new Stage("formation", FORM_BUILD, FORM_EVAL),
                new Stage("isolement", LONE_BUILD, LONE_EVAL),
                new Stage("marche", MARCH_BUILD, MARCH_EVAL),
                new Stage("pas-de-casse", WALL_BUILD, WALL_EVAL));
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
            default -> {
                wallPlaced = PackArena.buildWall(ow, WALL_X);
                spawned.addAll(PackArena.spawnRow(ow, PackArena.CX, 2, 8));
            }
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
            default -> wallMinStanding = Math.min(wallMinStanding, PackArena.wallStanding(ow, WALL_X));
        }
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        switch (stage) {
            case 0 -> {
                check("formation-adhesion", formMaxPacked >= 3,
                        formMaxPacked + "/12 zombies ont porté un id de meute au pic ; au creux, le mod n'en connaissait plus que "
                                + (formMinRegistered == Integer.MAX_VALUE ? "?" : formMinRegistered) + "/12");
                check("formation-une-seule-grappe", formMinDistinct >= 1 && formMinDistinct <= 2,
                        "au mieux " + (formMinDistinct == Integer.MAX_VALUE ? "aucune" : formMinDistinct)
                                + " meute(s) distincte(s) pour une grappe de 12");
            }
            case 1 -> check("isolement-aucune-meute", loneMaxPacked == 0,
                    "un zombie seul : " + loneMaxPacked + " adhésion(s) — 0 attendu");
            case 2 -> evaluateMarch();
            default -> check("pas-de-casse", wallMinStanding >= wallPlaced,
                    "mur : " + (wallMinStanding == Integer.MAX_VALUE ? "jamais mesuré" : wallMinStanding)
                            + "/" + wallPlaced + " blocs debout au minimum");
        }
        PackArena.clear(spawned);
        if (stage == 3) {
            PackArena.forceCorridor(ow, false);
            ArenaBuilder.releaseChunks(ow, PackArena.CX, PackArena.CZ);
        }
    }

    private void evaluateMarch() {
        check("marche-echantillonnee", march.sampled(),
                (march.sampled() ? "au moins un tick" : "jamais échantillonné un tick")
                        + " avec les " + marchExpected + " membres suivis vivants");
        check("marche-rapprochement", march.sampled() && march.closed() >= PackMarchProbe.MIN_CLOSED,
                "centre rapproché de " + DevVerdict.fmt(march.closed()) + " blocs (>= "
                        + (int) PackMarchProbe.MIN_CLOSED + " attendu, départ "
                        + DevVerdict.fmt(march.startDist()) + ")");
        check("marche-cohesion", march.sampled() && march.maxSpread() <= PackMarchProbe.MAX_SPREAD,
                "écart max au centre " + DevVerdict.fmt(march.maxSpread()) + " blocs (<= packBreakRadius "
                        + (int) PackMarchProbe.MAX_SPREAD + ")");
    }
}
