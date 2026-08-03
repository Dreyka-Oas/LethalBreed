package com.dreykaoas.lethalbreed.dev.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import static com.dreykaoas.lethalbreed.dev.mechanics.MechTestState.Y;

/** World rules and floor geometry shared by the mechanics areas. */
public final class MechTestArena {
    private MechTestArena() {}

    /**
     * Pin the sky for the whole suite. The dev arenas share one persistent world, so every one of these is a
     * value some other rig may have left behind rather than a fresh default — {@code peaceful} is the dev
     * {@code server.properties} default and would delete every monster on the next tick.
     */
    public static void worldRules(ServerLevel ow, MinecraftServer server) {
        server.setDifficulty(Difficulty.HARD, true);
        ow.setDayTime(1000L);                                         // DAY — the sun-burn stage needs it
        ow.getGameRules().set(GameRules.SPAWN_MOBS, false, server);   // only our props exist
        ow.getGameRules().set(GameRules.ADVANCE_TIME, false, server); // and it stays day for the whole run
    }

    /** Solid floor at {@code Y-1} over an 7×11 pad centred on {@code cx}, optionally lit and roofed. */
    public static void floor(ServerLevel ow, int cx, boolean roof) {
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
