package oas.dreyka.lethalbreed.entity.move.gait.climb;

/**
 * The ascent bookkeeping with the world taken out: numbers in, decisions out. {@link PillarColumn} reads
 * the entity and the settings and hands the values over, so the rules that say when an ascent may start,
 * when a rung has stopped gaining height and how long the give-up cooldown still has to run are reachable
 * from a plain test. Same split as {@code pack.rule} against {@code pack.runtime}.
 */
final class ClimbProgress {

    private boolean running = false;
    private int age = 0;
    private double startY = 0.0;
    private int topY = 0;     // highest block-Y reached this ascent (for the stall watchdog)
    private int rungAge = 0;  // activations since the last full-block height gain
    private int climbCd = 0;  // post-give-up cooldown before another ascent may start

    boolean running() {
        return running;
    }

    int age() {
        return age;
    }

    void cancel() {
        running = false;
    }

    /** Decrement the give-up cooldown each activation (called from the bucketed tick). */
    void tickCooldown() {
        if (climbCd > 0) {
            climbCd--;
        }
    }

    /** Arm a fresh ascent, or refuse when one is running, the cooldown is up, or the zombie is airborne. */
    boolean start(boolean onGround, double y, int blockY) {
        if (running || climbCd > 0 || !onGround) {
            return false;
        }
        running = true;
        age = 0;
        startY = y;
        topY = blockY;
        rungAge = 0;
        return true;
    }

    /** Bail while inactive, drop out when the owner is gone, otherwise age the ascent one tick. */
    boolean beginStep(boolean ownerValid) {
        if (!running) {
            return false;
        }
        if (!ownerValid) {
            running = false;
            return false;
        }
        age++;
        return true;
    }

    /**
     * Advance the stall watchdog from the current block-Y: a new rung resets it, otherwise it ages. True once
     * the current rung has made no height gain for longer than {@code maxRungAge} activations (support cannot
     * land, a lip overhangs, a ceiling is in the way), so the caller aborts where it would otherwise climb in
     * place.
     */
    boolean stalled(int blockY, int maxRungAge) {
        if (blockY > topY) {
            topY = blockY;
            rungAge = 0;
        } else {
            rungAge++;
        }
        return rungAge > maxRungAge;
    }

    /** Height risen since this ascent began. */
    double risen(double y) {
        return y - startY;
    }

    /** End the ascent normally: topped out, or reached height. */
    void finish() {
        running = false;
    }

    /** Abort the ascent (height cap, stall, ceiling) and arm the give-up cooldown so the dispatcher does not
     *  immediately retry it and falls back to ordinary ground movement instead. */
    void giveUp(int cooldownTicks) {
        running = false;
        climbCd = cooldownTicks;
    }
}
