package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.ArenaBuilder;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

import static com.dreykaoas.lethalbreed.dev.mechanics.MechTestState.Y;

/** Builds the three mechanics test areas: sun-burn, phase-gear, and contamination. */
public final class MechTestArena {
    private MechTestArena() {}

    public static void build(ServerLevel ow, MinecraftServer server, MechTestState s) {
        server.setDifficulty(Difficulty.HARD, true);
        ow.setDayTime(1000L); // DAY — for the sun-burn check
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server); // hold day

        buildSunburn(ow, s);
        buildPhaseGear(ow, server, s);
        buildContamination(ow, s);
        buildFleeRally(ow, s);
        LethalBreed.LOGGER.info("[MechTest] arena built");
    }

    /** Sun-burn area: OPEN sky (floor only), idle husk + zombie → both must catch fire. */
    private static void buildSunburn(ServerLevel ow, MechTestState s) {
        ArenaBuilder.forceChunks(ow, 30);
        floor(ow, 30, false);
        // Guarantee OPEN sky over the props. The dev tests share one persistent run/world, and another test's
        // arena can sit at these coordinates (the special-test platform's case #0 is also at x=30 and carries a
        // glowstone roof at Y+4) — a leftover roof would block canSeeSky and silently kill the sun-burn. Clear
        // the column above the floor so the check is self-contained regardless of prior runs.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 6; dz++) {
                for (int dy = 0; dy <= 8; dy++) {
                    ow.setBlock(new BlockPos(30 + dx, Y + dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Use create()+addFreshEntity, NOT EntityType.spawn: vanilla Zombie.finalizeSpawn rolls a baby ~5%
        // of the time (getSpawnAsBabyOdds), and our own blockBabyZombies then DISCARDS that baby on load —
        // the prop vanishes before it can sun-burn, a flaky false FAIL unrelated to the burn mechanic.
        // create() skips finalizeSpawn entirely, so the prop is always a plain adult.
        s.husk = EntityType.HUSK.create(ow, EntitySpawnReason.COMMAND);
        if (s.husk != null) {
            s.husk.setPos(30.5, Y, 0.5);
            s.husk.setPersistenceRequired();
            s.husk.setNoAi(true); // stay on the open platform (don't wander into shade/void)
            ow.addFreshEntity(s.husk);
        }
        s.sunZombie = EntityType.ZOMBIE.create(ow, EntitySpawnReason.COMMAND);
        if (s.sunZombie != null) {
            s.sunZombie.setPos(32.5, Y, 0.5);
            s.sunZombie.setPersistenceRequired();
            s.sunZombie.setNoAi(true);
            ow.addFreshEntity(s.sunZombie);
        }
    }

    /** Phase-gear area: roofed (no burn). Phase 15 → armored, enchanted, tanky. */
    private static void buildPhaseGear(ServerLevel ow, MinecraftServer server, MechTestState s) {
        PhaseManager.get().setPhase(server, 15);
        ArenaBuilder.forceChunks(ow, 90);
        floor(ow, 90, true);
        s.gearPos = new BlockPos(90, Y, 0);
        for (int i = 0; i < 20; i++) {
            Zombie z = EntityType.ZOMBIE.spawn(ow, new BlockPos(88 + i % 4, Y, i % 5), EntitySpawnReason.COMMAND);
            if (z != null) {
                z.setPersistenceRequired();
            }
        }
    }

    /** Contamination area: roofed. A zombie infects a vulnerable cow → the ramping DoT kills it. */
    private static void buildContamination(ServerLevel ow, MechTestState s) {
        ContaminationConfig.contaminationEnabled = true;
        ContaminationConfig.contamBaseChance = 1.0;   // infect on the first hit
        ContaminationConfig.contamDamageMin = 5.0;    // kill the cow well within the window
        ContaminationConfig.contamDamageMax = 5.0;
        ContaminationConfig.contamIntervalMinSec = 0.5;
        ContaminationConfig.contamIntervalMaxSec = 0.5;
        // Force symptoms to surface immediately so the DoT applies within the test window (no 5–10 day wait).
        ContaminationConfig.contamSymptomMinDays = 0.0;
        ContaminationConfig.contamSymptomMaxDays = 0.0;
        ContaminationConfig.contamSymptomMinPct = 100.0;
        ContaminationConfig.contamSymptomMaxPct = 100.0;
        ContaminationManager.INFECT_COUNT.set(0);
        ContaminationManager.DEATH_COUNT.set(0);
        ArenaBuilder.forceChunks(ow, 150);
        floor(ow, 150, true);
        s.contamPos = new BlockPos(150, Y, 0);
        Cow cow = EntityType.COW.spawn(ow, new BlockPos(150, Y, 1), EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            // Deterministic: infect directly (the on-hit spread is the ALLOW_DAMAGE hook, exercised in play),
            // then the ramping DoT must kill it.
            ContaminationManager.contaminate(cow);
        }
    }

    /**
     * Flee + distress-rally area: roofed (no burn). A wounded zombie (HP below the flee fraction) with a live
     * threat 15 blocks away must drop its hunt, retreat, and — once ≥ distressDistance from the threat — scream
     * for help. Idle helper zombies within the rally radius must then pick up sound-memory of the fleer's spot.
     * The threat is a Cow (zombies never target cows, and a NoAi cow is silent), so the ONLY sound event in the
     * world is the distress emit — any helper memory therefore proves the rally fired, not incidental noise.
     */
    private static void buildFleeRally(ServerLevel ow, MechTestState s) {
        com.dreykaoas.lethalbreed.entity.ZombieMood.DISTRESS_COUNT.set(0);
        ArenaBuilder.forceChunks(ow, 210);
        floor(ow, 210, true);
        // Widen the roofed floor toward the fleer (x=225) and helpers so nobody walks off into the void/shade.
        for (int x = 207; x <= 229; x++) {
            for (int dz = -4; dz <= 6; dz++) {
                ow.setBlock(new BlockPos(x, Y - 1, dz), Blocks.STONE.defaultBlockState(), 3);
                ow.setBlock(new BlockPos(x, Y + 4, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }

        // Threat: a silent, stationary cow the fleer "was last hurt by".
        Cow threat = EntityType.COW.create(ow, EntitySpawnReason.COMMAND);
        if (threat != null) {
            threat.setPos(210.5, Y, 0.5);
            threat.setNoAi(true);
            threat.setPersistenceRequired();
            ow.addFreshEntity(threat);
        }

        // Fleer: wounded adult (HP well below fleeHealthFraction=1/3 of 20) with the cow as its damager memory.
        Zombie fleer = EntityType.ZOMBIE.create(ow, EntitySpawnReason.COMMAND);
        if (fleer != null) {
            fleer.setPos(225.5, Y, 0.5);
            fleer.setPersistenceRequired();
            fleer.setHealth(4.0f); // 20% < 33% → enters FLEEING as soon as a threat is in range
            if (threat != null) {
                fleer.setLastHurtByMob(threat);
            }
            ow.addFreshEntity(fleer);
        }
        s.fleer = fleer;

        // Idle helpers: targetless zombies within distressRallyRadius (32) of the fleer. Spawn via spawn() so
        // the mod's ENTITY_LOAD hook registers them (needed for their pursuit to receive the rally memory).
        List<Zombie> helpers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Zombie h = EntityType.ZOMBIE.spawn(ow, new BlockPos(223 + i % 2, Y, 3 + i), EntitySpawnReason.COMMAND);
            if (h != null) {
                h.setPersistenceRequired();
                helpers.add(h);
            }
        }
        s.rallyHelpers = helpers;
    }

    private static void floor(ServerLevel ow, int cx, boolean roof) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 6; dz++) {
                ow.setBlock(new BlockPos(cx + dx, Y - 1, dz), Blocks.STONE.defaultBlockState(), 3);
                if (roof) {
                    ow.setBlock(new BlockPos(cx + dx, Y + 4, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
                }
            }
        }
    }
}
