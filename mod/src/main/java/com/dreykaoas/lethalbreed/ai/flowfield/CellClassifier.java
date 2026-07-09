package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.block.MaterialRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classifies one world column at the focus plane into a traversal type. Pure world reads (server
 * thread only) — used by {@link FlowFieldSnapshotBuilder} to fill the snapshot cost arrays.
 */
final class CellClassifier {
    private CellClassifier() {}

    // cell classification
    static final byte PASSABLE = 0;
    static final byte BREAKABLE = 1;
    static final byte BUILDABLE = 2;
    static final byte IMPASSABLE = 3;

    /**
     * Classify a column at the focus plane: PASSABLE if there is a standable spot in the vertical
     * window; else BREAKABLE if a breakable wall blocks the focus plane; else BUILDABLE if there is a
     * gap (clear feet/head, no ground); else IMPASSABLE.
     */
    static byte classify(ServerLevel level, BlockPos.MutableBlockPos m,
                         int wx, int wz, int focusY, int vtol) {
        // Standable anywhere in the window?
        for (int y = focusY + vtol; y >= focusY - vtol; y--) {
            m.set(wx, y, wz);
            if (!level.isLoaded(m)) {
                continue;
            }
            boolean feet = !level.getBlockState(m).blocksMotion();
            m.set(wx, y + 1, wz);
            boolean head = !level.getBlockState(m).blocksMotion();
            m.set(wx, y - 1, wz);
            boolean ground = level.getBlockState(m).blocksMotion();
            if (feet && head && ground) {
                return PASSABLE;
            }
        }

        // Not standable: examine the focus plane (where the zombie would walk).
        m.set(wx, focusY, wz);
        if (!level.isLoaded(m)) {
            return IMPASSABLE;
        }
        BlockState feetState = level.getBlockState(m);
        boolean feetSolid = feetState.blocksMotion();
        m.set(wx, focusY + 1, wz);
        BlockState headState = level.getBlockState(m);
        boolean headSolid = headState.blocksMotion();
        m.set(wx, focusY - 1, wz);
        boolean groundSolid = level.getBlockState(m).blocksMotion();

        if (feetSolid || headSolid) {
            // A wall. Breakable only if every solid layer is breakable.
            boolean feetOk = !feetSolid || MaterialRegistry.isBreakable(level, new BlockPos(wx, focusY, wz), feetState);
            boolean headOk = !headSolid || MaterialRegistry.isBreakable(level, new BlockPos(wx, focusY + 1, wz), headState);
            return (feetOk && headOk) ? BREAKABLE : IMPASSABLE;
        }
        if (!groundSolid) {
            // Clear feet+head but nothing to stand on within tolerance → bridge it.
            return BUILDABLE;
        }
        return IMPASSABLE;
    }
}
