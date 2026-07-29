package com.dreykaoas.lethalbreed.entity.mood;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Pure search helper: finds the nearest shaded, standable refuge around a point. No instance state. */
public final class ShelterFinder {
    private ShelterFinder() {}

    /** A block of vertical distance costs this many blocks of horizontal distance in the nearness score, so a
     *  zombie ducks under the CLOSEST roof at (or near) its own level rather than climbing far for cover. */
    private static final int V_WEIGHT = 3;

    /** Search a 3D neighbourhood around {@code origin} for the nearest standable block whose column is NOT under
     *  open sky (a roofed refuge). Unlike a flat same-Y scan — which misses almost all real cover — this sweeps a
     *  vertical band too, so overhangs, doorways, sloped terrain and building interiors one step up/down all
     *  count. Returns the closest such foot position (vertical distance weighted), or null when none is in range.
     *  Does NOT path-check: the brain breaks/pillars/carves its way to the returned target. */
    public static BlockPos findShade(ServerLevel level, BlockPos origin, int radius) {
        int vBand = Math.max(4, radius / 2);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double horiz = (double) dx * dx + (double) dz * dz;
                if (horiz >= bestScore) {
                    continue; // even at the same level this column can't beat the current best — skip it whole
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                for (int dy = -vBand; dy <= vBand; dy++) {
                    double score = horiz + (double) V_WEIGHT * dy * dy;
                    if (score >= bestScore) {
                        continue;
                    }
                    m.set(x, origin.getY() + dy, z);
                    if (level.canSeeSky(m)) {
                        continue; // open to the sky here → not shade
                    }
                    // Require a solid floor + a 2-high air gap so a mob can actually stand there.
                    if (!level.getBlockState(m.below()).isSolid()
                            || !level.getBlockState(m).isAir()
                            || !level.getBlockState(m.above()).isAir()) {
                        continue;
                    }
                    bestScore = score;
                    best = m.immutable();
                }
            }
        }
        return best;
    }
}
