package com.dreykaoas.lethalbreed.ai.flowfield;


/**
 * The 8-neighbour offsets and the no-corner-cutting rule, shared by the CPU {@link BellmanFordSolver} (both its
 * relaxation and its direction-extraction pass) and the {@link FlowFieldChecks} optimality checker so they all
 * enumerate neighbours and reject diagonal corner-cuts the exact same way. Pure geometry — no field values are
 * shared, so the checker still independently re-derives costs. (The GPU kernel re-declares the same constants in
 * OpenCL, which cannot import Java.)
 */
final class Neighbors8 {
    private Neighbors8() {
    }

    static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    static boolean isDiagonal(int k) {
        return DX[k] != 0 && DZ[k] != 0;
    }

    /** A diagonal step {@code (cx,cz) → (nx,nz)} is blocked unless BOTH orthogonally-adjacent cells are passable
     *  (never cut across a solid corner). Orthogonal steps are always geometrically allowed. */
    static boolean cornerBlocked(boolean[] passable, int cx, int cz, int nx, int nz, int depth, int k) {
        return isDiagonal(k) && (!passable[cx * depth + nz] || !passable[nx * depth + cz]);
    }
}
