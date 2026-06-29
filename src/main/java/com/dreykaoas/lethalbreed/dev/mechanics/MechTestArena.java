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
        LethalBreed.LOGGER.info("[MechTest] arena built");
    }

    /** Sun-burn area: OPEN sky (floor only), idle husk + zombie → both must catch fire. */
    private static void buildSunburn(ServerLevel ow, MechTestState s) {
        ArenaBuilder.forceChunks(ow, 30);
        floor(ow, 30, false);
        s.husk = EntityType.HUSK.spawn(ow, new BlockPos(30, Y, 0), EntitySpawnReason.COMMAND);
        if (s.husk != null) {
            s.husk.setPersistenceRequired();
            s.husk.setNoAi(true); // stay on the open platform (don't wander into shade/void)
        }
        s.sunZombie = EntityType.ZOMBIE.spawn(ow, new BlockPos(32, Y, 0), EntitySpawnReason.COMMAND);
        if (s.sunZombie != null) {
            s.sunZombie.setPersistenceRequired();
            s.sunZombie.setNoAi(true);
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

    /** Contamination area: roofed. A zombie infects a vulnerable cow → it dies → zombifies. */
    private static void buildContamination(ServerLevel ow, MechTestState s) {
        ContaminationConfig.contaminationEnabled = true;
        ContaminationConfig.contamBaseChance = 1.0;   // infect on the first hit
        ContaminationConfig.contamDamageBase = 5.0;   // kill the cow well within the window
        ContaminationConfig.contamDamageInterval = 10;
        ContaminationManager.INFECT_COUNT.set(0);
        ContaminationManager.ZOMBIFY_COUNT.set(0);
        ArenaBuilder.forceChunks(ow, 150);
        floor(ow, 150, true);
        s.contamPos = new BlockPos(150, Y, 0);
        Cow cow = EntityType.COW.spawn(ow, new BlockPos(150, Y, 1), EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            // Deterministic: infect directly (the on-hit spread is the ALLOW_DAMAGE hook, exercised in play),
            // then the ramping DoT must kill it and the death must zombify it.
            ContaminationManager.contaminate(cow);
        }
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
