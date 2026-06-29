package com.dreykaoas.lethalbreed.dev.special;

import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.dev.ArenaBuilder;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.dreykaoas.lethalbreed.dev.special.SpecialTestCase.SPACING;
import static com.dreykaoas.lethalbreed.dev.special.SpecialTestCase.Y;

/** Builds the sheltered night arena and drops one forced special zombie per type. */
public final class SpecialTestArena {
    private SpecialTestArena() {}

    /** Build the arena and append the created cases to {@code cases}. */
    public static void build(ServerLevel ow, MinecraftServer server, List<SpecialTestCase> cases) {
        server.setDifficulty(Difficulty.HARD, true);
        ow.setDayTime(18000L);                              // midnight — no sun-burn
        // No natural spawns — else stray monsters give the "lone" test zombies false targets (SPAWN_MOBS was
        // RULE_DOMOBSPAWNING in older mappings).
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
        ow.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS, false, server);
        ContaminationConfig.contaminationEnabled = false;     // keep cows alive for the per-special checks
        TargetingConfig.targetDetectRadius = 10.0;        // tight so a "lone" zombie stays target-less

        SpecialType[] types = {
                SpecialType.SPRINTEUR, SpecialType.BONDISSEUR, SpecialType.JUGGERNAUT, SpecialType.FOUISSEUR,
                SpecialType.TOXIQUE, SpecialType.GIVRE, SpecialType.CRACHEUR, SpecialType.BOMBEUR,
                SpecialType.HURLEUR, SpecialType.SOIGNEUR, SpecialType.NECROMANCIEN, SpecialType.SPLITTER,
        };

        for (int i = 0; i < types.length; i++) {
            int cx = i * SPACING + 30;
            BlockPos pos = new BlockPos(cx, Y, 0);
            ArenaBuilder.forceChunks(ow, cx);
            buildPlatform(ow, cx);

            Zombie z = EntityType.ZOMBIE.spawn(ow, pos, EntitySpawnReason.COMMAND);
            if (z == null) {
                continue;
            }
            z.setPersistenceRequired();
            SpecialType type = types[i];
            SpecialRoller.assign(z, type);

            Cow cow = spawnCow(ow, cx, type, z);
            Zombie extra = spawnExtra(ow, cx, type);
            cases.add(new SpecialTestCase(type, z, cow, extra, pos));
        }
        LethalBreed.LOGGER.info("[SpecialTest] arena built — {} cases", cases.size());
    }

    /** Per-case sheltered platform. GLOWSTONE roof = fully lit → no hostile mobs spawn on it. */
    private static void buildPlatform(ServerLevel ow, int cx) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 11; dz++) {
                ow.setBlock(new BlockPos(cx + dx, Y - 1, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
                ow.setBlock(new BlockPos(cx + dx, Y + 4, dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }
    }

    private static Cow spawnCow(ServerLevel ow, int cx, SpecialType type, Zombie z) {
        // Cow target position differs per type so the ability's trigger condition can be met.
        int cowZ = type == SpecialType.CRACHEUR ? 7 : (type == SpecialType.HURLEUR ? -2 : 2);
        Cow cow = EntityType.COW.spawn(ow, new BlockPos(cx, Y, cowZ), EntitySpawnReason.COMMAND);
        if (cow != null) {
            cow.setNoAi(true);
            cow.setInvulnerable(true);        // survive melee so its applied effects stay observable
            cow.setPersistenceRequired();
        }
        z.setTarget(cow);
        if (type == SpecialType.CRACHEUR) {
            z.setNoAi(true); // stay put so the target stays at range (else it closes to melee, no spit)
        }
        return cow;
    }

    private static Zombie spawnExtra(ServerLevel ow, int cx, SpecialType type) {
        Zombie extra = null;
        if (type == SpecialType.HURLEUR) {
            // 9 blocks from the howler (< its 24 radius) but 11 from the cow (> detect 10) → stays targetless.
            extra = EntityType.ZOMBIE.spawn(ow, new BlockPos(cx, Y, 9), EntitySpawnReason.COMMAND);
        } else if (type == SpecialType.SOIGNEUR) {
            extra = EntityType.ZOMBIE.spawn(ow, new BlockPos(cx + 1, Y, 0), EntitySpawnReason.COMMAND);
            if (extra != null) {
                extra.setHealth(4.0f); // hurt so Regen is observable
            }
        }
        if (extra != null) {
            extra.setPersistenceRequired();
        }
        return extra;
    }
}
