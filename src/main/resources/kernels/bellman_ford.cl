// LethalBreed — parallel Bellman-Ford relaxation for the flow field (Phase 6, GPU path).
//
// One work-item per grid cell. The host loops the kernel until no cell relaxes (convergence)
// or a max-iteration cap is hit. Cost grid is seeded with 0 at player/goal cells and a large
// value everywhere else; after convergence, dirX/dirZ point each cell downhill toward the goal.
//
// This MUST stay numerically identical to the CPU BellmanFordSolver:
//   - blockType: 0 = passable, 1 = solid impassable (skipped).
//   - extra[i]:  per-cell cost to ENTER cell i (break/build cost, 0 for plain air). Host-built from
//                the same Snapshot.extraCost the CPU reads, so break/bridge routing matches the CPU.
//   - orthoCost / diagCost: step costs (config-driven), shared with the CPU.
//   - No corner cutting: a diagonal move is rejected unless both shared orthogonal cells are passable.
// Action flags (break/build) are taken from the Snapshot on the host, not produced here.

#define IMPASSABLE  32767

__kernel void relax_step(
    __global short* cost,           // in/out: per-cell cost-to-goal  [W*H]
    __global const char* blockType, // in: 0 passable, 1 solid
    __global const int* extra,      // in: per-cell enter cost (break/build), 0 for air
    __global char*  dirX,           // out: -1 / 0 / +1
    __global char*  dirZ,           // out: -1 / 0 / +1
    const int W,
    const int H,
    const int orthoCost,
    const int diagCost,
    __global int* changed)          // out: set to 1 if any cell relaxed this pass
{
    int idx = get_global_id(0);
    if (idx >= W * H) return;

    int x = idx / H;
    int z = idx % H;

    char bt = blockType[idx];
    if (bt == 1) {              // solid impassable: never part of a path
        cost[idx] = IMPASSABLE;
        return;
    }

    short cur = cost[idx];
    short best = cur;
    char bestDX = 0;
    char bestDZ = 0;
    int ex = extra[idx];        // entering this cell costs ex (break/build), matches CPU

    for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int nx = x + dx;
            int nz = z + dz;
            if (nx < 0 || nx >= W || nz < 0 || nz >= H) continue;

            int nidx = nx * H + nz;
            if (blockType[nidx] == 1) continue;

            short ncost = cost[nidx];
            if (ncost >= IMPASSABLE) continue;

            int diag = (dx != 0 && dz != 0);
            if (diag) {                                  // no corner cutting (mirrors CPU)
                if (blockType[x * H + nz] == 1 || blockType[nx * H + z] == 1) continue;
            }

            int cand = (int) ncost + (diag ? diagCost : orthoCost) + ex;
            if (cand < best) {
                best = (short) min(cand, IMPASSABLE - 1);
                bestDX = (char) dx;      // step toward the lower-cost neighbour (matches CPU dirX = NDX[k])
                bestDZ = (char) dz;
            }
        }
    }

    if (best < cur) {
        cost[idx] = best;
        dirX[idx] = bestDX;
        dirZ[idx] = bestDZ;
        atomic_or(changed, 1);
    }
}
