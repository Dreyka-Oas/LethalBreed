package com.dreykaoas.lethalbreed.pack;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;

/**
 * Whether one zombie forms a pack, joins one, or walks away from the one it is in.
 *
 * <p>Takes no Minecraft type on purpose: the caller reduces its world question to a few primitives about the
 * neighbourhood, so every branch — including the degenerate ones a running server would only show by
 * accident — is scriptable in a unit test. Same split as {@code PlacedBlockPolicy} and
 * {@code ContaminationRoll}.
 *
 * <p><b>The lowest-entity-id election is what keeps this cheap.</b> A cluster of loose zombies is visited
 * one member at a time, spread over several ticks, with no shared scratch state between visits. Letting each
 * of them form a pack would turn one cluster into N packs of one, which would then have to be merged back.
 * Instead only the smallest id in the neighbourhood forms; the rest see that pack on their own next visit
 * and join it. One pack per cluster, no second pass, no lock.
 */
public final class PackJoinRule {
    private PackJoinRule() {}

    /** No pack. Pack ids are handed out from 1, so 0 is free to mean "loose". */
    public static final long NO_PACK = 0L;

    public enum Kind { NONE, FORM, JOIN, LEAVE }

    /** {@code packId} is meaningful for {@link Kind#JOIN} only; the other kinds carry {@link #NO_PACK}. */
    public record Decision(Kind kind, long packId) {}

    private static final Decision NOTHING = new Decision(Kind.NONE, NO_PACK);

    /**
     * Whether a member reloading from disk is too far from its pack's current position to still rejoin it.
     *
     * <p>Squared distance in, so the caller never takes a square root just to compare against a radius. A
     * pack keeps wandering while one of its members sits on disk — sometimes for well over a thousand ticks,
     * per this project's own measurements — so "still carries the old pack id" is not by itself proof the
     * member belongs where the pack is now.
     */
    public static boolean outsideRejoinRadius(double distToPackSq, double radius) {
        double r = Math.max(0, radius);
        return distToPackSq > r * r;
    }

    /**
     * Decide what this zombie does about pack membership this activation.
     *
     * @param myPackId          its current pack, or {@link #NO_PACK}
     * @param myEntityId        its entity id — the tiebreaker for the formation election
     * @param myPackSize        membership of {@code myPackId} when settled; when loose, the caller's known
     *                          size for the candidate pack, or 0 when it has none to offer
     * @param distToCentroidSq  squared distance to its pack centroid; ignored when loose
     * @param strayCount        consecutive activations already spent outside the break radius
     * @param nPackId           pack of each neighbour, {@link #NO_PACK} when loose
     * @param nEntityId         entity id of each neighbour
     * @param nDistSq           squared distance to each neighbour — reserved for future weighting, unused
     *                          today, and kept in the signature so the caller's scratch arrays stay stable
     * @param n                 how many entries of the three arrays are populated (they are reused scratch,
     *                          so their length is a capacity and not a count)
     */
    public static Decision decide(long myPackId, int myEntityId, int myPackSize,
                                  double distToCentroidSq, int strayCount,
                                  long[] nPackId, int[] nEntityId, double[] nDistSq, int n) {
        if (!PackConfig.packEnabled) {
            return NOTHING;
        }
        if (myPackId != NO_PACK) {
            // A settled member never defects to a neighbouring pack: two adjacent packs would otherwise
            // drain into each other every pass. Leaving first, joining on a later activation, is the only
            // way across — and the merge rule handles the case where the two should become one anyway.
            return leaves(distToCentroidSq, strayCount) ? new Decision(Kind.LEAVE, NO_PACK) : NOTHING;
        }
        if (n < PackConfig.packMinNeighbours) {
            // Nobody around. This is the short-circuit that makes a sparse world free: no allocation, no
            // pack object, no bookkeeping for a zombie that has nothing to group with.
            return NOTHING;
        }
        long best = bestNeighbourPack(nPackId, n);
        if (best != NO_PACK) {
            return isFull(best, nPackId, n, myPackSize) ? NOTHING : new Decision(Kind.JOIN, best);
        }
        return formsNewPack(myEntityId, nEntityId, n) ? new Decision(Kind.FORM, NO_PACK) : NOTHING;
    }

    /** The stray counter the caller should carry into the next activation. */
    public static int nextStrayCount(double distToCentroidSq, int strayCount) {
        double r = PackConfig.packBreakRadius;
        return distToCentroidSq > r * r ? strayCount + 1 : 0;
    }

    private static boolean leaves(double distToCentroidSq, int strayCount) {
        return nextStrayCount(distToCentroidSq, strayCount) >= Math.max(1, PackConfig.packStrayActivations);
    }

    /**
     * The pack most represented among the neighbours, ties broken by the smallest id.
     *
     * <p>Counted by a plain O(n²) double loop rather than a map: {@code packScanCap} bounds n at 16 by
     * default, so this is at most 256 long comparisons with zero allocation — cheaper than the map it would
     * take to be asymptotically better, and this runs on the server thread.
     */
    private static long bestNeighbourPack(long[] nPackId, int n) {
        long best = NO_PACK;
        int bestCount = 0;
        for (int i = 0; i < n; i++) {
            long id = nPackId[i];
            if (id == NO_PACK) {
                continue;
            }
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (nPackId[j] == id) {
                    count++;
                }
            }
            if (count > bestCount || (count == bestCount && best != NO_PACK && id < best)) {
                best = id;
                bestCount = count;
            }
        }
        return best;
    }

    /** True when the candidate pack has no room left. Membership is inferred from the neighbours actually
     *  seen plus the caller's own count, which is a floor, never an overestimate — so a full pack is always
     *  detected and a half-empty one is never wrongly refused. */
    private static boolean isFull(long packId, long[] nPackId, int n, int knownSize) {
        int seen = 0;
        for (int i = 0; i < n; i++) {
            if (nPackId[i] == packId) {
                seen++;
            }
        }
        return Math.max(seen, knownSize) >= PackConfig.packMaxSize;
    }

    private static boolean formsNewPack(int myEntityId, int[] nEntityId, int n) {
        if (n + 1 < PackConfig.packFormMinSize) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (nEntityId[i] < myEntityId) {
                return false;
            }
        }
        return true;
    }
}
