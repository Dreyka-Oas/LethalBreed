package oas.dreyka.lethalbreed.ai.flowfield.cpu;


import oas.dreyka.lethalbreed.ai.flowfield.FlowField;
import oas.dreyka.lethalbreed.ai.flowfield.Neighbors8;
import oas.dreyka.lethalbreed.ai.flowfield.Snapshot;
import oas.dreyka.lethalbreed.config.domain.engine.FlowConfig;


import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * WORKER THREAD: solve a {@link Snapshot} with PARALLEL Bellman-Ford relaxation, the multi-core CPU
 * backup used whenever the GPU is absent/disabled. Each iteration every passable cell pulls the
 * cheapest cost from its 8 neighbours (across the {@link SolvePool} threads); repeat until a
 * fixpoint (nothing improved) or the safety cap. This is the same algorithm as the GPU
 * {@code relax_step} kernel and converges to the identical shortest-cost field as a sequential
 * Dijkstra (non-negative weights). No Minecraft access.
 *
 * <p>Race-free without locks: each parallel task writes only its OWN cell ({@code cost[i]}, disjoint
 * across tasks). Neighbour reads may be a tick stale within an iteration (Gauss-Seidel): benign for
 * Bellman-Ford, at worst one extra iteration. The {@code submit(...).join()} barrier between
 * iterations publishes all writes to the next pass.
 */
final class BellmanFordSolver {
    private BellmanFordSolver() {}

    static FlowField compute(Snapshot s) {
        final int width = s.width, depth = s.depth, n = width * depth;
        final boolean[] passable = s.passable;
        final int[] extra = s.extraCost;
        final short[] cost = new short[n];
        Arrays.fill(cost, FlowField.IMPASSABLE);
        final byte[] dirX = new byte[n];
        final byte[] dirZ = new byte[n];
        // Step costs are config-driven and shared with the GPU kernel so both solvers yield the same field.
        final int orth = Math.max(1, FlowConfig.flowOrthoCost);
        final int diagCost = Math.max(orth, FlowConfig.flowDiagonalCost);
        for (int seed : s.seedCells) {
            cost[seed] = 0;
        }

        // width+depth+2 is the diameter of OPEN ground, not of the graph: a corridor or a cave winds, and its
        // diameter is O(width*depth). Truncating there left unreached cells at IMPASSABLE, the navigator fell
        // back to walking straight at the target, and the CPU and the GPU truncated at different places, so
        // parity only held on grids that converged early. The real bound is the cell count; the fixpoint break
        // below still ends an open-ground solve in the same handful of passes it always did.
        int maxIter = Math.max(width + depth + 2, n);
        for (int iter = 0; iter < maxIter; iter++) {
            AtomicBoolean changed = new AtomicBoolean(false);
            SolvePool.get().submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
                if (!passable[i]) {
                    return;
                }
                int cx = i / depth;
                int cz = i % depth;
                int cur = cost[i];
                int best = cur;
                for (int k = 0; k < 8; k++) {
                    int nx = cx + Neighbors8.DX[k];
                    int nz = cz + Neighbors8.DZ[k];
                    if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                        continue;
                    }
                    int nc = cost[nx * depth + nz];
                    if (nc >= FlowField.IMPASSABLE) {
                        continue;
                    }
                    if (Neighbors8.cornerBlocked(passable, cx, cz, nx, nz, depth, k)) {
                        continue; // no corner cutting
                    }
                    int cand = nc + (Neighbors8.isDiagonal(k) ? diagCost : orth) + extra[i]; // entering i costs extra[i]
                    if (cand < best) {
                        best = cand;
                    }
                }
                if (best < cur) {
                    cost[i] = (short) Math.min(best, FlowField.IMPASSABLE - 1);
                    changed.set(true);
                }
            })).join();
            if (!changed.get()) {
                break; // fixpoint
            }
        }

        // Direction extraction: each cell steps to the neighbour minimising `neighbourCost + stepCost`,
        // the SAME quantity the relaxation above minimises, so the emitted step provably realises the cell's
        // own converged cost. Minimising the raw neighbour cost instead (what this pass used to do) is a
        // different criterion whenever diagonals cost more than orthogonals, which they do by default
        // (ortho=10, diag=14): a diagonal neighbour cheaper by less than diag-ortho wins on raw cost yet
        // yields a strictly longer route. That also made routing hardware-dependent, since the GPU kernel
        // records argmin(cost + step) as a by-product of relaxing. Descent is preserved for free: the winner
        // satisfies cost[target] = cost[i] - step - extra[i] < cost[i].
        // Parallel: every cell writes only its own dirX[i]/dirZ[i], so no races.
        SolvePool.get().submit(() -> IntStream.range(0, n).parallel().forEach(i -> {
            if (cost[i] >= FlowField.IMPASSABLE || cost[i] == 0) {
                return;
            }
            int cx = i / depth;
            int cz = i % depth;
            int bestTotal = Integer.MAX_VALUE;
            int bdx = 0, bdz = 0;
            for (int k = 0; k < 8; k++) {
                int nx = cx + Neighbors8.DX[k];
                int nz = cz + Neighbors8.DZ[k];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                    continue;
                }
                int ni = nx * depth + nz;
                if (cost[ni] >= FlowField.IMPASSABLE) {
                    continue;
                }
                if (Neighbors8.cornerBlocked(passable, cx, cz, nx, nz, depth, k)) {
                    continue;
                }
                int total = cost[ni] + (Neighbors8.isDiagonal(k) ? diagCost : orth);
                if (total < bestTotal) {
                    bestTotal = total;
                    bdx = Neighbors8.DX[k];
                    bdz = Neighbors8.DZ[k];
                }
            }
            dirX[i] = (byte) bdx;
            dirZ[i] = (byte) bdz;
        })).join();

        return new FlowField(s.originX, s.originZ, width, depth, cost, dirX, dirZ);
    }
}
