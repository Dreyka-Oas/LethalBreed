package com.dreykaoas.lethalbreed.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

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

    /**
     * Force-load and carve a flat, roofed, lit corridor centred on ({@code cx}, {@code cz}): solid floor at
     * {@code y - 1}, clear air from {@code y} to {@code y + 3}, glowstone lid at {@code y + 4}.
     *
     * <p>The lid is what makes an arena's result mean something: it removes sun-burn as a confounder, so a
     * zombie that fails to reach its target failed at pathing rather than at surviving.
     *
     * @param halfZ  half-width across Z; the corridor spans {@code cz - halfZ .. cz + halfZ}
     * @param west   how far the corridor extends west of {@code cx}
     * @param east   how far it extends east of {@code cx}
     */
    public static void roofedCorridor(ServerLevel ow, int cx, int cz, int y, int halfZ, int west, int east) {
        forceChunks(ow, cx, cz);
        for (int x = cx - west; x <= cx + east; x++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                ow.setBlock(new BlockPos(x, y - 1, cz + dz), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    ow.setBlock(new BlockPos(x, y + dy, cz + dz), Blocks.AIR.defaultBlockState(), 3);
                }
                ow.setBlock(new BlockPos(x, y + 4, cz + dz), Blocks.GLOWSTONE.defaultBlockState(), 3);
            }
        }
    }

    /** Base Z of the verification band the new harnesses build in — disjoint from the legacy z≈0 arenas. */
    public static final int VERIFY_BAND_Z = 400;

    /** Arena floor Y shared by the verification-band harnesses (matches the legacy arenas' Y=101). */
    public static final int VERIFY_Y = 101;
}
