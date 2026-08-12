package com.dreykaoas.lethalbreed.dev.compute;

import com.dreykaoas.lethalbreed.ai.flowfield.FlowField;
import com.dreykaoas.lethalbreed.ai.flowfield.Snapshot;
import com.dreykaoas.lethalbreed.config.domain.engine.FlowConfig;

/**
 * Pure correctness predicates over a solved {@link FlowField} / its {@link Snapshot}, used only by
 * {@link ComputeSelfTest}.
 *
 * <p><b>Why this is a dev-local REIMPLEMENTATION and not a moved copy.</b> The shipped jar used to carry this
 * as {@code ai.flowfield.FlowFieldChecks}, deleted as part of removing the accidental API surface: it read
 * {@code Neighbors8.DX/DZ/isDiagonal/cornerBlocked}, and {@code Neighbors8} is (correctly) package-private
 * final — widening it just so a dev-only checker could reach it would grow the shipped API to serve dev code,
 * the opposite of why this whole harness moved out of {@code src/main} in the first place. The 8-neighbour /
 * no-corner-cut geometry below is therefore re-derived independently from {@link Snapshot}'s public
 * accessors, exactly as the deleted class's own javadoc already noted the checker does for cost values ("the
 * checker still independently re-derives costs") — this just extends that independence to the geometry too.
 */
final class FlowFieldSelfChecks {
    private FlowFieldSelfChecks() {}

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    private static boolean isDiagonal(int k) {
        return DX[k] != 0 && DZ[k] != 0;
    }

    /** A diagonal step is blocked unless BOTH orthogonally-adjacent cells are passable (never cut a corner). */
    private static boolean cornerBlocked(boolean[] passable, int cx, int cz, int nx, int nz, int depth, int k) {
        return isDiagonal(k) && (!passable[cx * depth + nz] || !passable[nx * depth + cz]);
    }

    /** Seed corner (0,0) must be 0; the far corner (w-1,d-1) must be reachable and positive. */
    static boolean cpuSanity(FlowField f, int w, int d) {
        return f.costAt(0, 0) == 0
                && f.costAt(w - 1, d - 1) > 0
                && f.costAt(w - 1, d - 1) < FlowField.IMPASSABLE;
    }

    /** Every reachable non-seed cell must sample a direction that steps to a STRICTLY cheaper neighbour —
     *  a valid descending gradient toward a goal. Tie-break-independent, so it holds for both backends. */
    static boolean directionsDescend(FlowField f, int w, int d) {
        int[] dir = new int[2];
        for (int cx = 0; cx < w; cx++) {
            for (int cz = 0; cz < d; cz++) {
                int here = f.costAt(cx, cz);
                if (here <= 0 || here >= FlowField.IMPASSABLE) {
                    continue; // seed (0) or unreachable: no direction expected
                }
                if (!f.sampleInto(cx, cz, dir)) {
                    return false; // a reachable non-seed cell must yield a direction
                }
                if (f.costAt(cx + dir[0], cz + dir[1]) >= here) {
                    return false; // the step must reduce cost (descend toward the goal)
                }
            }
        }
        return true;
    }

    /** The cost field must satisfy the Bellman-Ford optimality condition: every passable non-seed reachable
     *  cell equals the cheapest {@code neighbourCost + step + enterCost} over its valid (non-corner-cut)
     *  neighbours. If any cell could still be relaxed, the field is suboptimal. Uses the same step costs and
     *  corner rule as the solvers, read from {@link FlowConfig}. */
    static boolean costFieldOptimal(Snapshot s, FlowField f) {
        int w = s.width(), d = s.depth();
        boolean[] pass = s.passable();
        int[] extra = s.extraCost();
        int orth = Math.max(1, FlowConfig.flowOrthoCost);
        int diag = Math.max(orth, FlowConfig.flowDiagonalCost);
        for (int cx = 0; cx < w; cx++) {
            for (int cz = 0; cz < d; cz++) {
                if (!pass[cx * d + cz]) {
                    continue;
                }
                int here = f.costAt(cx, cz);
                if (here == 0 || here >= FlowField.IMPASSABLE) {
                    continue; // seed or unreachable — no relaxation constraint
                }
                int best = Integer.MAX_VALUE;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + DX[k], nz = cz + DZ[k];
                    if (nx < 0 || nx >= w || nz < 0 || nz >= d) {
                        continue;
                    }
                    if (cornerBlocked(pass, cx, cz, nx, nz, d, k)) {
                        continue; // no corner cutting
                    }
                    int nc = f.costAt(nx, nz);
                    if (nc >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    best = Math.min(best, nc + (isDiagonal(k) ? diag : orth) + extra[cx * d + cz]);
                }
                if (best != here) {
                    return false; // could be relaxed (suboptimal) or inconsistent
                }
            }
        }
        return true;
    }

    /** Returns {mismatchCount, firstIndex, aCost, bCost} comparing two cost fields cell-by-cell. */
    static int[] compareCost(Snapshot s, FlowField a, FlowField b) {
        int mism = 0, firstIdx = -1, aV = 0, bV = 0;
        for (int cx = 0; cx < s.width(); cx++) {
            for (int cz = 0; cz < s.depth(); cz++) {
                int ca = a.costAt(cx, cz);
                int cb = b.costAt(cx, cz);
                if (ca != cb) {
                    if (firstIdx < 0) {
                        firstIdx = cx * s.depth() + cz;
                        aV = ca;
                        bV = cb;
                    }
                    mism++;
                }
            }
        }
        return new int[]{mism, firstIdx, aV, bV};
    }
}
