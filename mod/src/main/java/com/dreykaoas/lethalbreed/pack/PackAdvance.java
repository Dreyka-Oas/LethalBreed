package com.dreykaoas.lethalbreed.pack;

/**
 * How far a coarsely-simulated pack has moved since it was last looked at.
 *
 * <p><b>Terrain is ignored on purpose.</b> Reading a single block in a chunk that is not resident forces
 * that chunk to generate; doing it per pack per tick is the cost trap that kills this kind of system. A
 * pack out of view therefore travels in a straight line over oceans and mountains alike, and is snapped
 * back onto the real surface when it materialises. The honest corollary is that no "avoid water" option is
 * implementable here — better to say so than to ship a knob that cannot work.
 *
 * <p><b>Prorating by elapsed ticks is not a refinement, it is the correctness condition.</b> Packs are
 * visited round-robin, so the interval between two visits of the same pack grows with the number of packs
 * alive. Advancing a fixed step per visit would make every pack's speed silently depend on the world's pack
 * population.
 */
public final class PackAdvance {
    private PackAdvance() {}

    /** Below this much progress between two visits, a pack counts as making no headway. Loose enough to
     *  ignore the jitter of a recomputed centroid, tight enough to catch a pack pinned against a wall. */
    public static final double HEADWAY_EPSILON = 0.25;

    /**
     * Move {@code pos} toward the destination and report whether the pack has arrived.
     *
     * @param pos           two-slot {x, z}, mutated in place — no allocation in a per-tick loop
     * @param blocksPerTick coarse travel speed
     * @param elapsedTicks  ticks since this pack was last advanced; zero or negative moves nothing
     * @param arriveDistance how close counts as there
     */
    public static boolean step(double[] pos, int destX, int destZ,
                               double blocksPerTick, long elapsedTicks, double arriveDistance) {
        double dx = destX - pos[0];
        double dz = destZ - pos[1];
        double remaining = Math.sqrt(dx * dx + dz * dz);
        if (remaining <= arriveDistance) {
            // Covers the exactly-on-target case too, which would otherwise divide by a zero length and
            // write NaN coordinates straight into the save file.
            return true;
        }
        if (elapsedTicks <= 0 || blocksPerTick <= 0.0) {
            // A clock that went backwards (a resumed save, an operator /time set) must not drag the pack
            // backwards along its own route.
            return false;
        }
        double travel = Math.min(remaining - arriveDistance, blocksPerTick * elapsedTicks);
        pos[0] += dx / remaining * travel;
        pos[1] += dz / remaining * travel;
        return remaining - travel <= arriveDistance;
    }
}
