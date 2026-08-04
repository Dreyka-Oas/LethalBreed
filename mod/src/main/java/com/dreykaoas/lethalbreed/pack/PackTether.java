package com.dreykaoas.lethalbreed.pack;

/**
 * What one zombie knows about its pack: which one, how long it has been straying, and where the pack wants
 * it to walk.
 *
 * <p>Held by {@code ZombiePursuit} rather than merged into it, because it is a distinct concern with a
 * distinct lifetime — pursuit data is rewritten every activation by the target scan, this survives across
 * them — and because keeping it a separate, Minecraft-free class means the membership bookkeeping can be
 * unit-tested without a world.
 *
 * <p><b>The waypoint deliberately does not live in the pursuit memory slot.</b> That slot already has five
 * writers that do not arbitrate: the seen target, the sound bus, the shade search, the flee drop and the
 * doze suppression. A pack waypoint there would be wiped by the first cow the pack walks past, hijacked by
 * any wounded zombie's distress rally, and would make every member match the "investigating a noise"
 * predicate — the exact thing that keeps a zombie awake through the day and burning in the sun.
 */
public final class PackTether {

    private long packId = PackJoinRule.NO_PACK;
    private int strayCount;
    /** Own activation counter. Deliberately NOT the brain's: that one drives the LOD tick throttle, and a
     *  second consumer incrementing it would silently change how often distant zombies run their AI. */
    private int decisions;
    private double wpX;
    private double wpY;
    private double wpZ;
    private boolean hasWaypoint;

    public long packId() { return packId; }

    public boolean inPack() { return packId != PackJoinRule.NO_PACK; }

    public int strayCount() { return strayCount; }

    /** True on one activation in {@code divisor}, on this zombie's own schedule. */
    public boolean dueToDecide(int divisor) {
        return divisor <= 1 || (decisions++ % divisor) == 0;
    }

    public void setStrayCount(int n) { this.strayCount = n; }

    /**
     * Join or leave a pack.
     *
     * <p>Leaving drops the waypoint as well. A zombie no longer in a pack must not keep walking to that
     * pack's rendezvous — and worse, a stale waypoint would hold it out of FROZEN forever, so it would tick
     * at full price for the rest of the world's life while walking somewhere nobody is going.
     */
    public void setPackId(long id) {
        this.packId = id;
        this.strayCount = 0;
        if (id == PackJoinRule.NO_PACK) {
            hasWaypoint = false;
        }
    }

    public void setWaypoint(double x, double y, double z) {
        this.wpX = x;
        this.wpY = y;
        this.wpZ = z;
        this.hasWaypoint = true;
    }

    public void clearWaypoint() { this.hasWaypoint = false; }

    public boolean hasWaypoint() { return hasWaypoint; }

    public double wpX() { return wpX; }

    public double wpY() { return wpY; }

    public double wpZ() { return wpZ; }
}
