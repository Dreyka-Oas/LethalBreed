package com.dreykaoas.lethalbreed.entity.mood;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Pure search helper: finds the nearest shaded, standable refuge around a point. No instance state. */
public final class ShelterFinder {
    private ShelterFinder() {}

    /** Search a horizontal ring around {@code origin} for the nearest standable block whose column is NOT under
     *  open sky (a shaded refuge). Returns the closest such foot position, or null when none is within range. */
    public static BlockPos findShade(ServerLevel level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                // Column is shaded if the sky is blocked at the origin's own height there.
                m.set(x, origin.getY(), z);
                if (level.canSeeSky(m)) {
                    continue;
                }
                // Require a solid floor + head clearance so a mob can actually stand there.
                if (!level.getBlockState(m.below()).isSolid()
                        || !level.getBlockState(m).isAir()
                        || !level.getBlockState(m.above()).isAir()) {
                    continue;
                }
                double distSq = origin.distSqr(m);
                if (distSq < bestSq) {
                    bestSq = distSq;
                    best = m.immutable();
                }
            }
        }
        return best;
    }
}
