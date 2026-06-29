package com.dreykaoas.lethalbreed.dev;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.LethalBreed;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev-only, headless climb test arena. When {@link LethalBreedConfig#devClimbTest} is on, builds a wall
 * near world spawn with a stationary villager on top and a handful of zombies a short walk away, then
 * leaves {@link LethalBreedConfig#debugClimb} on so each zombie's approach/climb is logged. Lets us run a
 * dedicated server (no client, no manual commands) and read the behaviour straight from the log.
 */
public final class ClimbTest {
    private ClimbTest() {}

    public static void run(MinecraftServer server) {
        // Dev-env gate: this force-loads chunks, builds a wall, spawns mobs AND flips WorldSpawnConfig.
        // forceDayTime + ProgressionConfig.debugClimb at runtime. Far too destructive for a real world, so it
        // runs ONLY under gradle runServer (a development environment) even if the GUI toggle is left on.
        if (!ProgressionConfig.devClimbTest || !FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        ServerLevel level = server.overworld();
        int cx = 8;
        int cz = 8;

        // Force-load the arena chunks so entities tick even with no player online, and stop the zombies
        // burning (mod forces noon by default) by holding night + disabling the day-time rule.
        for (int dcx = -1; dcx <= 2; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                level.setChunkForced((cx >> 4) + dcx, (cz >> 4) + dcz, true);
            }
        }
        WorldSpawnConfig.forceDayTime = false;
        level.setDayTime(18000L);

        int gy = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz); // floor level of the test platform
        int wallH = 12; // tall wall to verify a high climb (was 4); villager sits at gy + wallH
        // Flat stone platform from x[cx-8..cx+4], z[cz-4..cz+4], clear air above — clean, slope-free geometry.
        for (int x = cx - 8; x <= cx + 4; x++) {
            for (int z = cz - 4; z <= cz + 4; z++) {
                level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int up = 0; up <= wallH + 3; up++) {
                    level.setBlock(new BlockPos(x, gy + up, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Wall near the east edge: 1 thick at x=cx+3, wallH tall, 7 wide along Z. Villager sits at gy+wallH.
        int wallX = cx + 3;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dyy = 0; dyy < wallH; dyy++) {
                level.setBlock(new BlockPos(wallX, gy + dyy, cz + dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        // Carve a 3-tall WINDOW through the centre climbing column (z=cz, y +4..+6). Regression cover for the
        // wall-scale's face-end scan: a zombie scaling here must climb PAST the gap (wall resumes above) to the
        // roof target, not hop off into the window. Off-centre columns stay solid for the plain-wall path.
        for (int dyy = 4; dyy <= 6; dyy++) {
            level.setBlock(new BlockPos(wallX, gy + dyy, cz), Blocks.AIR.defaultBlockState(), 3);
        }

        // Stationary, weightless, invulnerable villager target on top-centre of the wall.
        int villagerY = gy + wallH;
        Entity v = EntityType.VILLAGER.spawn(level, new BlockPos(wallX, villagerY, cz), EntitySpawnReason.COMMAND);
        if (v instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (v != null) {
            v.setInvulnerable(true);
            v.setNoGravity(true);
            v.setPos(wallX + 0.5, villagerY, cz + 0.5);
        }

        // Three zombies on the flat platform to the west — they should walk to the wall base then climb it.
        int spawned = 0;
        for (int i = 0; i < 3; i++) {
            Entity z = EntityType.ZOMBIE.spawn(level, new BlockPos(cx - 5 + i, gy, cz), EntitySpawnReason.COMMAND);
            if (z != null) {
                spawned++;
            }
        }

        ProgressionConfig.debugClimb = true;
        LethalBreed.LOGGER.info(
                "[ClimbTest] flat arena: floor y={}, wall x={} ({} tall, y {}..{}), villager @({},{},{}), {} zombies "
                        + "west. Climbing {} blocks reaches the villager. Watch [ClimbDbg].",
                gy, wallX, wallH, gy, gy + wallH - 1, wallX + 0.5, villagerY, cz + 0.5, spawned, wallH);
    }
}
