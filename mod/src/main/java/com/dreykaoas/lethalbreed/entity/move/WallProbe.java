package com.dreykaoas.lethalbreed.entity.move;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Block-geometry probes for {@link WallClimb}: whether a flush wall stands in a cardinal direction, which
 * cardinal to scale on a diagonal approach, and whether the face has genuinely ended above. Pure reads — no
 * state, no entity — so the climb state machine stays focused on motion.
 */
final class WallProbe {
    private WallProbe() {}

    /** A head-high motion-blocking block one step in (dx,dz) from {@code pos} — a climbable wall face. */
    static boolean headWall(ServerLevel level, BlockPos pos, int dx, int dz) {
        return level.getBlockState(
                new BlockPos(pos.getX() + dx, pos.getY() + 1, pos.getZ() + dz)).blocksMotion();
    }

    /** Pick the cardinal toward a head-high wall, probing the dominant axis first ({@code xDom}) so a diagonal
     *  approach onto a corner scales the face it is mostly heading into. Returns {dx,dz} or null if no wall. */
    static int[] pickWallDir(ServerLevel level, BlockPos pos, int dxs, int dzs, boolean xDom) {
        int[] x = (dxs != 0 && headWall(level, pos, dxs, 0)) ? new int[]{dxs, 0} : null;
        int[] z = (dzs != 0 && headWall(level, pos, 0, dzs)) ? new int[]{0, dzs} : null;
        if (xDom) {
            return x != null ? x : z;
        }
        return z != null ? z : x;
    }

    /** The face has genuinely ENDED (real wall crest) only when it is clear ALL the way up toward the target —
     *  scan from head height up to {@code scanUp}. A clear cell with the wall RESUMING above is a window /
     *  recess, not the top, so the climb continues straight past it; an overhang juts back in above, so a
     *  higher cell stays solid and also reads as "not ended". Returns true only when nothing solid remains. */
    static boolean faceEnded(ServerLevel level, BlockPos pos, int wallDx, int wallDz, int scanUp) {
        for (int up = 1; up <= scanUp; up++) {
            if (level.getBlockState(
                    new BlockPos(pos.getX() + wallDx, pos.getY() + up, pos.getZ() + wallDz)).blocksMotion()) {
                return false;
            }
        }
        return true;
    }
}
