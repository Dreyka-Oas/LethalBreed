package com.dreykaoas.lethalbreed.dev.arena.pack;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.config.ConfigOverride;
import com.dreykaoas.lethalbreed.config.domain.engine.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.arena.ArenaBuilder;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.phase.PhaseManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gamerules.GameRules;

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
    private int formMaxPacked;
    private int formMinDistinct = Integer.MAX_VALUE;
    private int loneMaxPacked;
    private double marchStartDist = -1.0;
    private double marchBestDist = Double.MAX_VALUE;
    private double marchMaxSpread;
    private boolean marchSampled;
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
        // Phase 0 culls every hostile at ENTITY_LOAD, so an arena built there measures an empty corridor.
        PhaseManager.get().setPhase(server, 1);
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        cfg.set("forceDayTime", false);
        ow.setDayTime(18000L);            // night: no day-sleep, so migration is free to run
        cfg.set("packEnabled", true);
        cfg.set("packMigrationEnabled", true);
        cfg.set("packDecisionDivisor", 1);  // decide every activation: converge inside a 300-tick window
        cfg.set("packsPerTick", 8);
        cfg.set("packFormMinSize", 3);
        PackArena.build(ow);

        switch (stage) {
            case 0 -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX - 12, 2, 12));
            case 1 -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX, 1, 1));
            case 2 -> spawned.addAll(PackArena.spawnRow(ow, PackArena.CX - 8, 2, 8));
            default -> {
                wallPlaced = PackArena.buildWall(ow, WALL_X);
                spawned.addAll(PackArena.spawnRow(ow, PackArena.CX, 2, 8));
            }
        }
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        switch (stage) {
            case 0 -> {
                formMaxPacked = Math.max(formMaxPacked, PackArena.packed(spawned));
                int distinct = PackArena.distinctPacks(spawned, packIds);
                if (distinct > 0) {
                    formMinDistinct = Math.min(formMinDistinct, distinct);
                }
            }
            case 1 -> loneMaxPacked = Math.max(loneMaxPacked, PackArena.packed(spawned));
            case 2 -> observeMarch(ow);
            default -> wallMinStanding = Math.min(wallMinStanding, PackArena.wallStanding(ow, WALL_X));
        }
    }

    /** Force the pack east down the corridor, then watch it close on that point and hold together. */
    private void observeMarch(ServerLevel ow) {
        PackState pack = firstPack(ow);
        if (pack == null) {
            return;
        }
        pack.destX = PackArena.CX + 120;
        pack.destZ = PackArena.CZ;
        pack.dwellUntil = 0L;

        double d = Math.hypot(pack.destX - pack.x, pack.destZ - pack.z);
        if (marchStartDist < 0.0) {
            marchStartDist = d;
        }
        int live = 0;
        double spread = 0.0;
        for (Zombie z : spawned) {
            SmartZombie sz = GameState.REGISTRY.get(z.getId());
            if (sz == null || !sz.isValid()) {
                continue;
            }
            live++;
            spread = Math.max(spread, Math.hypot(sz.x() - pack.x, sz.z() - pack.z));
        }
        // Only a sample where EVERY member is still alive may set the record. A depopulated arena otherwise
        // scores a tiny spread and a closing distance, and reads better than one that works.
        if (live == spawned.size() && !spawned.isEmpty()) {
            marchSampled = true;
            marchBestDist = Math.min(marchBestDist, d);
            marchMaxSpread = Math.max(marchMaxSpread, spread);
        }
    }

    private PackState firstPack(ServerLevel ow) {
        for (PackState p : GameState.DIMENSIONS.get(ow.dimension()).packManager().all()) {
            return p;
        }
        return null;
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        switch (stage) {
            case 0 -> {
                check("formation-adhesion", formMaxPacked >= 3,
                        formMaxPacked + "/12 zombies ont porté un id de meute au pic");
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
            ArenaBuilder.releaseChunks(ow, PackArena.CX, PackArena.CZ);
        }
    }

    private void evaluateMarch() {
        check("marche-echantillonnee", marchSampled,
                marchSampled ? "au moins un tick avec les 8 membres vivants"
                        : "jamais échantillonné un tick avec les 8 membres vivants");
        double closed = marchSampled ? marchStartDist - marchBestDist : 0.0;
        check("marche-rapprochement", marchSampled && closed >= 20.0,
                "centre rapproché de " + DevVerdict.fmt(closed) + " blocs (>= 20 attendu, départ "
                        + DevVerdict.fmt(marchStartDist) + ")");
        check("marche-cohesion", marchSampled && marchMaxSpread <= 40.0,
                "écart max au centre " + DevVerdict.fmt(marchMaxSpread) + " blocs (<= packBreakRadius 40)");
    }
}
