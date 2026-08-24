package com.dreykaoas.lethalbreed.entity.mood.sleep;

import com.dreykaoas.lethalbreed.probe.DevProbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Pure search helper: finds the nearest shaded, standable refuge around a point. No instance state. */
public final class ShelterFinder {
    private ShelterFinder() {}

    /** A block of vertical distance costs this many blocks of horizontal distance in the nearness score, so a
     *  zombie ducks under the CLOSEST roof at (or near) its own level and never climbs far for cover. */
    private static final int V_WEIGHT = 3;

    /** Search a 3D neighbourhood around {@code origin} for the nearest standable block whose column is NOT under
     *  open sky (a roofed refuge). Unlike a flat same-Y scan (which misses almost all real cover), this sweeps a
     *  vertical band too, so overhangs, doorways, sloped terrain and building interiors one step up/down all
     *  count. Returns the closest such foot position (vertical distance weighted), or null when none is in range.
     *  Does NOT path-check: the brain breaks/pillars/carves its way to the returned target. */
    public static BlockPos findShade(ServerLevel level, BlockPos origin, int radius) {
        if (DevProbe.on()) {
            DevProbe.sink.count(DevProbe.SHELTER_SCAN, DevProbe.GLOBAL);
        }
        int vBand = Math.max(4, radius / 2);
        // Two candidates, because the nearest shaded block is almost always the drip line, the first column
        // under the edge of the roof. Vanilla navigation counts a walk as arrived within about half a block,
        // so a zombie sent to the drip line stops astride it: its foot block is still open to the sky, it never
        // counts as sheltered, it never dozes, and it burns one block short of cover. Measured in the shade rig
        // as a zombie standing at x=153 for 300 ticks with the roof starting at x=154. So aim a block deeper
        // whenever a deeper block exists, and keep the edge only as the fallback for thin cover (a doorway, a
        // one-block overhang) where no deeper block does.
        BlockPos inner = null;
        double innerScore = Double.MAX_VALUE;
        BlockPos edge = null;
        double edgeScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double horiz = (double) dx * dx + (double) dz * dz;
                if (horiz >= innerScore) {
                    continue; // even at the same level this column can't beat the current best, skip it whole
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                for (int dy = -vBand; dy <= vBand; dy++) {
                    double score = horiz + (double) V_WEIGHT * dy * dy;
                    if (score >= innerScore) {
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
                    if (score < edgeScore) {
                        edgeScore = score;
                        edge = m.immutable();
                    }
                    if (surroundedByCover(level, m)) {
                        innerScore = score;
                        inner = m.immutable();
                    }
                }
            }
        }
        return inner != null ? inner : edge;
    }

    /** True when the four cardinal neighbours are roofed too, i.e. this is cover rather than its edge. */
    private static boolean surroundedByCover(ServerLevel level, BlockPos.MutableBlockPos m) {
        int x = m.getX();
        int y = m.getY();
        int z = m.getZ();
        boolean covered = !level.canSeeSky(m.set(x + 1, y, z))
                && !level.canSeeSky(m.set(x - 1, y, z))
                && !level.canSeeSky(m.set(x, y, z + 1))
                && !level.canSeeSky(m.set(x, y, z - 1));
        m.set(x, y, z);
        return covered;
    }
}
