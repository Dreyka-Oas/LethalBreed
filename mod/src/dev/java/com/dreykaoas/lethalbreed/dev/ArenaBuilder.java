package com.dreykaoas.lethalbreed.dev;

import net.minecraft.server.level.ServerLevel;

/** Shared arena-construction helpers for the headless dev test harnesses. */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    /** Force-load the 3x3 chunk block around column {@code cx} (z in [-1,1]) so it ticks without a player. */
    public static void forceChunks(ServerLevel ow, int cx) {
        forceChunks(ow, cx, 0);
    }

    /**
     * Force-load the 3x3 chunk block around the arbitrary column ({@code cx}, {@code cz}) so it ticks without
     * a player. The {@link #forceChunks(ServerLevel, int)} overload above is exactly this with {@code cz = 0}
     * (its z chunks [-1,1] are the three chunks around block z=0), so the legacy arenas are unaffected.
     *
     * <p>New harnesses site their arenas in the disjoint band z in [400,500] (see {@link #VERIFY_BAND_Z}) so
     * that a mis-selected double-run can never overwrite the legacy z≈0 arenas — the flag forcing in
     * {@link DevTestSelector} is the primary guard, this is the belt-and-braces one.
     */
    public static void forceChunks(ServerLevel ow, int cx, int cz) {
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        for (int chx = chunkX - 1; chx <= chunkX + 1; chx++) {
            for (int chz = chunkZ - 1; chz <= chunkZ + 1; chz++) {
                ow.setChunkForced(chx, chz, true);
            }
        }
    }

    /** Release the 3x3 force-load taken by {@link #forceChunks(ServerLevel, int, int)}. */
    public static void releaseChunks(ServerLevel ow, int cx, int cz) {
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        for (int chx = chunkX - 1; chx <= chunkX + 1; chx++) {
            for (int chz = chunkZ - 1; chz <= chunkZ + 1; chz++) {
                ow.setChunkForced(chx, chz, false);
            }
        }
    }

    /** Base Z of the verification band the new harnesses build in — disjoint from the legacy z≈0 arenas. */
    public static final int VERIFY_BAND_Z = 400;

    /** Arena floor Y shared by the verification-band harnesses (matches the legacy arenas' Y=101). */
    public static final int VERIFY_Y = 101;
}
