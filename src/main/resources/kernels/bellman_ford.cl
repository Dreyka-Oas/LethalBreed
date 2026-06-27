// LethalBreed — parallel Bellman-Ford relaxation for the flow field (Phase 6, GPU path).
//
// One work-item per grid cell. The host loops the kernel until no cell relaxes (convergence)
// or a max-iteration cap is hit. Cost grid is seeded with 0 at player/goal cells and a large
// value everywhere else; after convergence, dirX/dirZ point each cell downhill toward the goal.
//
// blockType encoding:
//   0 = air / passable     (move cost 1)
//   1 = solid impassable   (skipped)
//   2 = breakable          (cost BREAK_BASE * relative hardness, host-precomputed into `cost` seed)
//   3 = buildable gap      (cost BUILD_COST, marks BUILD_NEEDED)
//
// flags bitmask (output): bit0 BUILD_NEEDED, bit1 BREAK_NEEDED, bit2 JUMP_NEEDED.

#define IMPASSABLE  32767
#define FLAG_BUILD  1
#define FLAG_BREAK  2
#define FLAG_JUMP   4

__kernel void relax_step(
    __global short* cost,       // in/out: per-cell cost-to-goal  [W*H]
    __global const char* blockType,
    __global char*  dirX,       // out: -1 / 0 / +1
    __global char*  dirZ,       // out: -1 / 0 / +1
    __global char*  flags,      // out: per-cell action flags
    const int W,
    const int H,
    __global int* changed)      // out: set to 1 if any cell relaxed this pass
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
    char bestFlag = 0;

    // 8-neighbourhood. Diagonals cost slightly more (~1.41 scaled to integers as +1).
    for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int nx = x + dx;
            int nz = z + dz;
            if (nx < 0 || nx >= W || nz < 0 || nz >= H) continue;

            int nidx = nx * H + nz;
            char nbt = blockType[nidx];
            if (nbt == 1) continue;

            short ncost = cost[nidx];
            if (ncost >= IMPASSABLE) continue;

            // Traversal cost from this cell's own block type.
            int step = (dx != 0 && dz != 0) ? 2 : 1;      // diagonal vs orthogonal
            char flag = 0;
            if (bt == 2) { step += 50; flag = FLAG_BREAK; } // breaking through this cell
            else if (bt == 3) { step += 100; flag = FLAG_BUILD; }

            int cand = (int) ncost + step;
            if (cand < best) {
                best = (short) min(cand, IMPASSABLE - 1);
                bestDX = (char) (-dx);   // point toward the lower-cost neighbour
                bestDZ = (char) (-dz);
                bestFlag = flag;
            }
        }
    }

    if (best < cur) {
        cost[idx] = best;
        dirX[idx] = bestDX;
        dirZ[idx] = bestDZ;
        flags[idx] = bestFlag;
        atomic_or(changed, 1);
    }
}
