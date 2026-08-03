package com.dreykaoas.lethalbreed.dev;

/**
 * Drives repeated unforce → reload round trips and counts only the ones that PROVABLY happened.
 *
 * <p><b>The defect it removes.</b> The plague rig unforced its arena, waited a fixed 20 ticks, re-forced, and
 * incremented a reload counter. On a server where the drop takes 272 ticks — measured on the sibling rig — not
 * one of those five "reloads" occurred: the entity never left memory, was never re-deserialised, and the
 * assertion that no ghost entry accumulated per reload passed while exercising nothing at all.
 *
 * <p><b>Why an id and not a boolean.</b> "Gone, then present again" is only evidence of a reload; it is not
 * proof. An entity can drop out of the level's lookup and come back as the SAME object. What a real
 * deserialisation always produces is a NEW object, and therefore a new runtime entity id — the counter is
 * monotonic. So the caller passes the witness's id each tick ({@link #ABSENT} when it is not there) and a round
 * trip counts only when the id CHANGED across it. A same-id return is recorded as {@link #staleReturns()} and
 * the cycle is retried, because nothing was exercised.
 *
 * <p>A cycle whose chunk never drops (or never returns) is {@link #abandoned()}, never counted, and the machine
 * stops — retrying is pointless when the disk is the bottleneck. Either way the force-load is restored, so a
 * give-up cannot leave the arena unloaded under the evaluation that follows.
 *
 * <p>{@link #completed()} is the number a verdict must quote: a check about reload behaviour with zero real
 * round trips behind it is vacuous and has to FAIL rather than pass on an empty set.
 *
 * <p>Takes no Minecraft type — one int per tick — so every timing and every degenerate case this has to survive
 * is scriptable in a unit test.
 */
public final class ChunkCycles {

    /** Pass this as the witness id when the entity is not resident. */
    public static final int ABSENT = -1;

    public enum Action {
        /** Nothing to do this tick. */
        NONE,
        /** Caller must drop the force-load now. */
        RELEASE,
        /** Caller must take the force-load back now. */
        REFORCE
    }

    private enum Phase { IDLE, AWAIT_GONE, AWAIT_BACK, FINISHED }

    private final int rounds;
    private final TickWait gone;
    private final TickWait back;

    private Phase phase = Phase.IDLE;
    private int idBeforeUnload = ABSENT;
    private int completed;
    private int abandoned;
    private int staleReturns;

    /**
     * @param rounds       how many proven round trips to obtain; must be positive
     * @param timeoutTicks per-half budget — how long a drop, and then a return, may take before this cycle is
     *                     abandoned. Size it from the slowest observed drop on the target hardware, not from
     *                     the typical one: the whole point is to survive the slow case rather than mismeasure it
     */
    public ChunkCycles(int rounds, int timeoutTicks) {
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds must be positive, got " + rounds);
        }
        this.rounds = rounds;
        this.gone = new TickWait("the witness to leave memory after the unforce", timeoutTicks);
        this.back = new TickWait("the witness to be deserialised again after the re-force", timeoutTicks);
    }

    /**
     * Advance one tick.
     *
     * @param witnessId the witness entity's runtime id RIGHT NOW, or {@link #ABSENT}. Re-find it by UUID every
     *                  tick and never cache the object: a reload builds a new entity and the old reference is a
     *                  husk that keeps answering with its pre-unload state
     * @return the world action the caller must perform this tick
     */
    public Action step(int tick, int witnessId) {
        switch (phase) {
            case FINISHED -> {
                return Action.NONE;
            }
            case IDLE -> {
                idBeforeUnload = witnessId;
                phase = Phase.AWAIT_GONE;
                gone.start(tick);
                return Action.RELEASE;
            }
            case AWAIT_GONE -> {
                if (witnessId != ABSENT) {
                    idBeforeUnload = witnessId;   // keep the freshest identity we saw before it vanished
                }
                return awaitGone(tick, witnessId);
            }
            case AWAIT_BACK -> {
                return awaitBack(tick, witnessId);
            }
        }
        return Action.NONE;
    }

    private Action awaitGone(int tick, int witnessId) {
        return switch (gone.poll(tick, witnessId == ABSENT)) {
            case PENDING -> Action.NONE;
            case MET -> {
                phase = Phase.AWAIT_BACK;
                back.start(tick);
                yield Action.REFORCE;
            }
            // Never dropped. Put the force-load back so the arena is resident for the evaluation, and stop:
            // the next cycle would wait on the same disk and lose the same budget again.
            case TIMED_OUT -> {
                abandoned++;
                phase = Phase.FINISHED;
                yield Action.REFORCE;
            }
        };
    }

    private Action awaitBack(int tick, int witnessId) {
        return switch (back.poll(tick, witnessId != ABSENT)) {
            case PENDING -> Action.NONE;
            case MET -> {
                if (witnessId == idBeforeUnload) {
                    // Same object: it was missing from the lookup, not deserialised. Nothing was exercised, so
                    // this must not count — go round again rather than bank a round trip that never happened.
                    staleReturns++;
                } else {
                    completed++;
                }
                yield completed >= rounds ? finish() : nextRound(tick);
            }
            // Gone and never back: the force-load is already held, so there is nothing to restore.
            case TIMED_OUT -> {
                abandoned++;
                phase = Phase.FINISHED;
                yield Action.NONE;
            }
        };
    }

    private Action finish() {
        phase = Phase.FINISHED;
        return Action.NONE;
    }

    private Action nextRound(int tick) {
        phase = Phase.AWAIT_GONE;
        gone.start(tick);
        return Action.RELEASE;
    }

    /** Round trips where the witness left memory AND came back as a DIFFERENT object. The only honest count. */
    public int completed() {
        return completed;
    }

    /** Cycles given up on because the chunk never dropped, or never returned. */
    public int abandoned() {
        return abandoned;
    }

    /** Times the witness came back carrying its old id — present again, but never re-deserialised. */
    public int staleReturns() {
        return staleReturns;
    }

    /** True once no further action will ever be requested. */
    public boolean finished() {
        return phase == Phase.FINISHED;
    }

    /** For the verdict detail: how much of the requested coverage was actually obtained. */
    public String diagnosis() {
        return completed + " real round trip(s) of " + rounds + " requested, " + abandoned + " abandoned"
                + (staleReturns > 0 ? ", " + staleReturns + " returned as the same object (no reload)" : "")
                + (abandoned > 0 ? " (" + gone.describe() + ")" : "");
    }
}
