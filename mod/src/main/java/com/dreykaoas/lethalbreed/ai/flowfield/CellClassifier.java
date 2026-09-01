package com.dreykaoas.lethalbreed.ai.flowfield;

import com.dreykaoas.lethalbreed.block.MaterialRegistry;
import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Classifies one world column at the focus plane into a traversal type. Pure world reads (server
 * thread only), used by {@link FlowFieldSnapshotBuilder} to fill the snapshot cost arrays.
 */
public final class CellClassifier {
    private CellClassifier() {}

    // cell classification
    public static final byte PASSABLE = 0;
    public static final byte BREAKABLE = 1;
    public static final byte BUILDABLE = 2;
    public static final byte IMPASSABLE = 3;

    /**
     * Water deep enough to put a zombie's head under, at the plane it would walk on.
     *
     * <p>Without this, water is passable to the field: it does not block motion, so a flooded column reads as
     * a fine place to stand and the route goes straight through the lake. With it, a lake is a wall and a ford
     * is not, which is the whole difference the option is asking for: shallow water is crossed, deep water is
     * walked around.
     *
     * <p>The depth is a shipped number rather than the zombie's own height because the field is computed once
     * per dimension and read by every zombie in it. Two blocks is the ordinary zombie; a taller one wading a
     * two-deep ford still keeps its eyes clear, and one that walks into something deeper drowns, which is the
     * other half of the same option.
     */
    private static boolean drownsHere(ChunkAccess chunk, BlockPos.MutableBlockPos m, int wx, int wz, int focusY) {
        if (!CombatMoveConfig.cannotSwim) {
            return false;
        }
        int depth = Math.max(1, CombatMoveConfig.waterAvoidDepth);
        for (int dy = 0; dy < depth; dy++) {
            m.set(wx, focusY + dy, wz);
            if (chunk.isOutsideBuildHeight(m) || !chunk.getBlockState(m).getFluidState().is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Classify a column at the focus plane: PASSABLE if there is a standable spot in the vertical
     * window; else BREAKABLE if a breakable wall blocks the focus plane; else BUILDABLE if there is a
     * gap (clear feet/head, no ground); else IMPASSABLE.
     *
     * <p>Reads from the {@link ChunkAccess} the caller already resolved (once per 16-column run along
     * {@code cz}) instead of {@code ServerLevel.getBlockState}, which re-resolves the owning chunk on
     * every call. A non-null chunk already proves horizontal residency, so the per-position
     * {@code Level.isLoaded} guard the earlier ServerLevel-only implementation used is dropped. But
     * {@code isLoaded} also folded in a VERTICAL bounds check ({@code Level.isLoaded} is
     * {@code isOutsideBuildHeight ? false : hasChunk}), which a bare {@code chunk.getBlockState} does not:
     * a {@code y} outside the chunk's build height silently returns void air instead of signalling
     * "not there". {@code chunk.isOutsideBuildHeight} (inherited from
     * {@link net.minecraft.world.level.LevelHeightAccessor} via {@code BlockGetter}) is kept explicitly
     * below to reproduce that half of the guard.
     *
     * <p>Public only so the dev-source-set {@code ComputeSelfTest} can sweep a real world through this exact
     * method (the {@code classify-coverage} gate, the only automated check that reaches this class
     * directly). Nothing in {@code main} calls it from outside this package.
     */
    public static byte classify(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos m,
                                int wx, int wz, int focusY, int vtol) {
        if (drownsHere(chunk, m, wx, wz, focusY)) {
            return IMPASSABLE;
        }
        // Standable anywhere in the window?
        for (int y = focusY + vtol; y >= focusY - vtol; y--) {
            m.set(wx, y, wz);
            if (chunk.isOutsideBuildHeight(m)) {
                continue;
            }
            boolean feet = !chunk.getBlockState(m).blocksMotion();
            m.set(wx, y + 1, wz);
            boolean head = !chunk.getBlockState(m).blocksMotion();
            m.set(wx, y - 1, wz);
            boolean ground = chunk.getBlockState(m).blocksMotion();
            if (feet && head && ground) {
                return PASSABLE;
            }
        }

        // Not standable: examine the focus plane (where the zombie would walk).
        m.set(wx, focusY, wz);
        if (chunk.isOutsideBuildHeight(m)) {
            return IMPASSABLE;
        }
        BlockState feetState = chunk.getBlockState(m);
        boolean feetSolid = feetState.blocksMotion();
        m.set(wx, focusY + 1, wz);
        BlockState headState = chunk.getBlockState(m);
        boolean headSolid = headState.blocksMotion();
        m.set(wx, focusY - 1, wz);
        boolean groundSolid = chunk.getBlockState(m).blocksMotion();

        if (feetSolid || headSolid) {
            // A wall. Breakable only if every solid layer is breakable. `m` is reused rather than allocating
            // two fresh BlockPos per wall column: isBreakable only reads the position (getDestroySpeed) and
            // never retains it.
            m.set(wx, focusY, wz);
            boolean feetOk = !feetSolid || MaterialRegistry.isBreakable(level, m, feetState);
            m.set(wx, focusY + 1, wz);
            boolean headOk = !headSolid || MaterialRegistry.isBreakable(level, m, headState);
            return (feetOk && headOk) ? BREAKABLE : IMPASSABLE;
        }
        if (!groundSolid) {
            // Clear feet+head but nothing to stand on within tolerance → bridge it.
            return BUILDABLE;
        }
        return IMPASSABLE;
    }

}
