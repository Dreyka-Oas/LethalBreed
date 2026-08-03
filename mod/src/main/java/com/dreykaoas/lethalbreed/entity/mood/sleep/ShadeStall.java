package com.dreykaoas.lethalbreed.entity.mood.sleep;

/**
 * Watchdog for a shade-seek that has stopped making progress.
 *
 * <p><b>The deadlock it breaks.</b> A day-sleeping zombie remembers the shade it found and walks there, and
 * while that memory is held {@code ZombieMood.handleDaySleep} deliberately returns early — the brain is already
 * pathing, breaking and pillaring its way over. But nothing checked that the walk was still going anywhere. The
 * headless shade rig caught the consequence: a zombie stopped one block short of the roof and stood at
 * {@code (153,101,460)} for the rest of the run, {@code hasTarget=true} and {@code seeking=true} at every
 * sample from t+40 to t+320. It never arrived, so it never dozed; it never lost the memory, so it never
 * re-planned. Outside the rig's rain it would have burned to death standing next to the shade it had found.
 *
 * <p>Arrival is the normal case — two runs in three walked in fine — so this must not touch a zombie that is
 * still closing. It watches one number, the squared distance to the target, and declares a stall only when
 * that number has failed to IMPROVE for a whole patience window. Any real progress re-arms it, so a slow walk
 * with pauses is safe; drifting further away is not progress and does not buy patience.
 *
 * <p>Same shape as {@code PillarStall}, which guards the climb for the same reason. Plain numbers in, boolean
 * out — no Minecraft type, so the rule is unit-tested at every boundary.
 */
public final class ShadeStall {

    private final int patienceTicks;

    private double bestDistSq = Double.MAX_VALUE;
    private long lastProgressAt = Long.MIN_VALUE;

    /** @param patienceTicks how long the distance may fail to improve before the seek is abandoned */
    public ShadeStall(int patienceTicks) {
        if (patienceTicks <= 0) {
            throw new IllegalArgumentException("patience must be positive, got " + patienceTicks);
        }
        this.patienceTicks = patienceTicks;
    }

    /**
     * Feed the current squared distance to the shade target.
     *
     * @return true when the seek has made no progress for {@code patienceTicks} and should be abandoned, so
     *         the memory is dropped and the search allowed to run again under its normal cooldown
     */
    public boolean stalled(long now, double distSqToTarget) {
        if (lastProgressAt == Long.MIN_VALUE) {
            lastProgressAt = now;
            bestDistSq = distSqToTarget;
            return false;
        }
        if (distSqToTarget < bestDistSq) {
            bestDistSq = distSqToTarget;
            lastProgressAt = now;
            return false;
        }
        return now - lastProgressAt >= patienceTicks;
    }

    /** Forget this seek. Call when a new shade target is acquired, or the seek ends. */
    public void reset() {
        bestDistSq = Double.MAX_VALUE;
        lastProgressAt = Long.MIN_VALUE;
    }
}
