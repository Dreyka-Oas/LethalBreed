package com.dreykaoas.lethalbreed.dev.harness;

/**
 * Wait for a condition to hold, polled once per server tick, with a budget after which the harness gives up
 * LOUDLY instead of carrying on against state it never actually reached.
 *
 * <p><b>Why this exists.</b> Several rigs guessed at chunk timings — "20 ticks is long enough for the chunk to
 * drop", "by tick 30 the arena is resident". Those numbers are not measurements. The sibling placed-block rig
 * clocked the same operation at 2, ~35 and 272 ticks across runs, and once not at all inside 1200, because this
 * server writes chunks synchronously to an external disk. A guessed delay that comes up short does not fail —
 * it silently measures the wrong thing and reports PASS.
 *
 * <p>Take no Minecraft type on purpose: the caller reduces its world question to one boolean per tick, so every
 * boundary of this rule is unit-testable without a server.
 *
 * <pre>
 * wait.start(tick);                                  // arm when the action is issued
 * switch (wait.poll(tick, probe(ow) == null)) {      // one boolean per tick
 *     case PENDING  -> { }                           // keep waiting
 *     case MET      -> ...                           // the state really arrived
 *     case TIMED_OUT-> check(name, false, wait.describe());   // never a silent pass
 * }
 * </pre>
 */
public final class TickWait {

    public enum Result {
        /** Not there yet, and there is budget left. */
        PENDING,
        /** The condition held. {@link #elapsed()} says how long it took. */
        MET,
        /** The budget ran out first. The measurement this wait guards is impossible, not merely late. */
        TIMED_OUT
    }

    private final String what;
    private final int timeoutTicks;

    private int startTick = -1;
    private int elapsed = -1;
    private Result verdict = Result.PENDING;

    /**
     * @param what         what is being waited for, phrased to read inside "waited N ticks for ..." — it ends
     *                     up in the FAIL detail an operator reads months later
     * @param timeoutTicks budget in ticks, counted from {@link #start}; must be positive
     */
    public TickWait(String what, int timeoutTicks) {
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException("timeout for '" + what + "' must be positive, got " + timeoutTicks);
        }
        this.what = what;
        this.timeoutTicks = timeoutTicks;
    }

    /** Arm (or re-arm) the wait at {@code tick}, clearing any previous verdict. */
    public void start(int tick) {
        startTick = tick;
        elapsed = -1;
        verdict = Result.PENDING;
    }

    /**
     * Feed this tick's answer.
     *
     * <p>The condition is tested BEFORE the deadline: one satisfied on the very last allowed tick counts as
     * satisfied, otherwise the most common near-miss would read as a harness failure. Once decided, the verdict
     * is frozen — a caller that polls once more after acting must not re-trigger, and a condition arriving after
     * we gave up does not retroactively rescue a measurement that was already abandoned.
     *
     * @throws IllegalStateException if the wait was never armed — a silent PENDING would hide that forever
     */
    public Result poll(int tick, boolean condition) {
        if (startTick < 0) {
            throw new IllegalStateException("TickWait for '" + what + "' polled before start()");
        }
        if (verdict != Result.PENDING) {
            return verdict;
        }
        int waited = tick - startTick;
        if (condition) {
            elapsed = waited;
            verdict = Result.MET;
        } else if (waited >= timeoutTicks) {
            elapsed = waited;
            verdict = Result.TIMED_OUT;
        }
        return verdict;
    }

    /** Ticks between {@link #start} and the deciding poll, or -1 while still pending. */
    public int elapsed() {
        return elapsed;
    }

    /** One line for a FAIL detail: what was awaited, the budget, and what actually happened. */
    public String describe() {
        return switch (verdict) {
            case MET -> "waited " + elapsed + " ticks for " + what + " (budget " + timeoutTicks + ")";
            case TIMED_OUT -> "gave up after " + timeoutTicks + " ticks waiting for " + what
                    + " — the state under test was never reached, so nothing here was measured";
            case PENDING -> "still waiting for " + what + " (budget " + timeoutTicks + ")";
        };
    }
}
