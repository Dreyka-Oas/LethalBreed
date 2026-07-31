package com.dreykaoas.lethalbreed.block;

/**
 * The lifetime rules for a zombie-placed block as pure functions. **No Minecraft imports, and none may be
 * added** — that is the whole point of this class, exactly as for {@code ContaminationRoll}:
 * {@link PlacedBlockTracker} touches {@code Level}/{@code BlockPos}/{@code Blocks} and so cannot be loaded
 * by a headless unit test, which left the bridging arithmetic — the expiry test, the abandon threshold and
 * the crack ramp — permanently untested.
 *
 * <p>Every rule here takes the RAW configured lifetime and applies the {@link #lifetime(long)} floor itself,
 * so the clamp cannot be re-lost by a caller that forgets it: {@code placedBlockLifetimeTicks} is
 * operator-settable and a 0 would otherwise divide by zero in the crack ramp.
 *
 * <p>Add a lifetime rule HERE and call it from {@link PlacedBlockTracker} — never inline a fresh copy.
 */
public final class PlacedBlockPolicy {
    private PlacedBlockPolicy() {}

    /** How many lifetimes a placement may sit in an unloaded chunk before the tracker gives up on it.
     *  Never drop an expired-but-unloaded placement immediately: we can neither read its state nor
     *  destroy it while the chunk is gone, so dropping it there leaves the block in the world forever —
     *  a zombie's dirt bridge over a ravine would become permanent terrain, which is exactly what the
     *  tracker exists to prevent. Holding the entry lets us destroy the block the moment the chunk comes
     *  back, however much later that is. The multiplier only bounds the map against areas a player
     *  explores once and never revisits. */
    public static final long ABANDON_FACTOR = 10L;

    /** The effective lifetime: the configured value floored at 1 tick. {@code placedBlockLifetimeTicks} is
     *  operator-settable, and a 0 (or negative) would make every placement instantly expired and divide by
     *  zero in {@link #crackStage}. */
    public static long lifetime(long configured) {
        return Math.max(1, configured);
    }

    /** True once a placement has outlived its lifetime and must be destroyed (chunk permitting). */
    public static boolean expired(long age, long configuredLifetime) {
        return age >= lifetime(configuredLifetime);
    }

    /** True once a placement has spent {@link #ABANDON_FACTOR} lifetimes straight in an unloaded chunk and
     *  the tracker gives up on it rather than growing without bound. Deliberately far above
     *  {@link #expired}: an over-age placement whose chunk is gone is HELD, not dropped. */
    public static boolean abandoned(long age, long configuredLifetime) {
        return age >= lifetime(configuredLifetime) * ABANDON_FACTOR;
    }

    /** Clamp an already-scaled tenths value (progress×10 / age×10÷lifetime) to a 0..9 crack stage. Shared
     *  with the progressive break path through {@code CrackingBlock.stage}, so both ramp the vanilla overlay
     *  the exact same way. */
    public static int stage(double tenths) {
        return (int) Math.max(0, Math.min(9, tenths));
    }

    /** The vanilla crumbling-overlay stage, ramped 0..9 across the placement's lifetime. Never returns 10:
     *  an age at or past the lifetime means the block is being destroyed this tick anyway. */
    public static int crackStage(long age, long configuredLifetime) {
        return stage((age * 10L) / (double) lifetime(configuredLifetime));
    }
}
