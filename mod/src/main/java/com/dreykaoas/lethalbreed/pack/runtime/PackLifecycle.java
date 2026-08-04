package com.dreykaoas.lethalbreed.pack.runtime;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.pack.PackMergeRule;
import com.dreykaoas.lethalbreed.pack.PackState;

import java.util.List;
import java.util.function.LongFunction;

/**
 * What happens to a pack on the visit its turn comes up: where its centre is, whether it should swallow a
 * neighbour, and whether it should stop existing.
 *
 * <p>Split from {@code PackManager}, which owns the map, the ids and the visit cursor. These are the rules
 * about a pack's shape over time; that is the bookkeeping that keeps the roster honest.
 */
public final class PackLifecycle {
    private PackLifecycle() {}

    /** How the caller removes a pack from its own map once this class decides it is finished. */
    public interface Registry {
        PackState get(long id);

        void drop(long id);

        boolean holds(long id);
    }

    /**
     * Recompute the pack's position from its live members.
     *
     * <p>Recomputed rather than maintained incrementally: an incremental centroid drifts as members join and
     * leave, and a drifting centre silently changes who is judged to be straying — the pack would shed
     * members for a reason that is not in any rule. Bounded by {@code packMaxSize} reads.
     *
     * <p>A pack with no live member keeps the position it had: it is virtual, or everyone is on disk, and in
     * both cases {@code PackAdvance} owns where it is.
     */
    public static void recentroid(PackState pack) {
        if (pack.liveIds.isEmpty()) {
            return;
        }
        double sx = 0.0;
        double sz = 0.0;
        int n = 0;
        for (int i = 0; i < pack.liveIds.size(); i++) {
            SmartZombie m = GameState.REGISTRY.get(pack.liveIds.getInt(i));
            if (m == null || !m.isValid()) {
                continue;
            }
            sx += m.x();
            sz += m.z();
            n++;
        }
        if (n > 0) {
            pack.x = sx / n;
            pack.z = sz / n;
        }
    }

    /** True when the pack was dissolved and the caller must stop working on it this visit. */
    public static boolean dissolveIfSpent(PackState pack, long gameTime, Registry registry) {
        int total = pack.totalMembers();
        if (total >= PackConfig.packMinSize) {
            pack.belowMinSince = 0L;
        } else if (pack.belowMinSince == 0L) {
            pack.belowMinSince = gameTime;
        }
        long below = pack.belowMinSince == 0L ? 0L : gameTime - pack.belowMinSince;
        if (!PackMergeRule.shouldDissolve(total, below)) {
            return false;
        }
        for (int i = pack.liveIds.size() - 1; i >= 0; i--) {
            SmartZombie m = GameState.REGISTRY.get(pack.liveIds.getInt(i));
            if (m != null) {
                PackMembership.leave(m, pack);
            }
        }
        // Ghosts die with the pack. They are zombies we took out of the world ourselves, so dropping them
        // costs population — but the alternative is materialising members of a pack that no longer exists,
        // with nowhere to put them and no rule left to govern them.
        pack.ghosts.clear();
        registry.drop(pack.id);
        return true;
    }

    /** Fold {@code pack} into a compatible neighbour, or absorb one into it. At most one merge per visit —
     *  the survivor is re-examined when its own turn comes round. */
    public static void merge(PackState pack, List<PackState> ordered, Registry registry,
                             LongFunction<PackState> lookup) {
        for (PackState other : ordered) {
            if (other == pack || !registry.holds(other.id) || !registry.holds(pack.id)) {
                continue;
            }
            if (!PackMergeRule.shouldMerge(pack.x, pack.z, pack.headingX, pack.headingZ, pack.totalMembers(),
                    other.x, other.z, other.headingX, other.headingZ, other.totalMembers())) {
                continue;
            }
            long keep = PackMergeRule.survivor(pack.id, pack.totalMembers(), other.id, other.totalMembers());
            absorb(registry.get(keep), keep == pack.id ? other : pack, registry, lookup);
            return;
        }
    }

    private static void absorb(PackState survivor, PackState victim, Registry registry,
                               LongFunction<PackState> lookup) {
        for (int i = victim.liveIds.size() - 1; i >= 0; i--) {
            SmartZombie m = GameState.REGISTRY.get(victim.liveIds.getInt(i));
            if (m != null) {
                PackMembership.join(m, survivor, lookup);
            }
        }
        survivor.ghosts.addAll(victim.ghosts);
        survivor.detached += Math.max(0, victim.detached);
        // Emptied before dropping, so nothing is ever counted by both packs even for one instant.
        victim.ghosts.clear();
        victim.liveIds.clear();
        victim.detached = 0;
        registry.drop(victim.id);
    }
}
