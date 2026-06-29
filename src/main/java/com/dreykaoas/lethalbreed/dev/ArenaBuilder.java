package com.dreykaoas.lethalbreed.dev;

import net.minecraft.server.level.ServerLevel;

/** Shared arena-construction helpers for the headless dev test harnesses. */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    /** Force-load the 3x3 chunk block around column {@code cx} (z in [-1,1]) so it ticks without a player. */
    public static void forceChunks(ServerLevel ow, int cx) {
        int chunkX = cx >> 4;
        for (int chx = chunkX - 1; chx <= chunkX + 1; chx++) {
            for (int chz = -1; chz <= 1; chz++) {
                ow.setChunkForced(chx, chz, true);
            }
        }
    }
}
