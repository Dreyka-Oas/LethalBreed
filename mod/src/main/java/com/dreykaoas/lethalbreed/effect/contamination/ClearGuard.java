package com.dreykaoas.lethalbreed.effect.contamination;

/**
 * Marks that the {@code LivingEntity.removeAllEffects()} call currently on this thread's stack came from
 * drinking milk, not from {@code /effect clear}.
 *
 * <p>Both routes are the same method — vanilla's {@code ClearAllStatusEffectsConsumeEffect.apply} is
 * literally {@code return livingEntity.removeAllEffects();}, contradicting the "different code path" the
 * mod's own javadoc claimed (audit #1). The two must behave oppositely: milk keeps the plague, the command
 * cures it. Since they are indistinguishable at the callee, the caller marks itself on the way in.
 *
 * <p>Thread-local because consume effects run on both the client and the server thread; server-only state
 * would be wrong on an integrated server where both live in one JVM. Armed and disarmed by a
 * {@code try}/{@code finally} in {@code MilkKeepsPlagueMixin}, so an exception cannot leave it set.
 */
public final class ClearGuard {
    private ClearGuard() {}

    private static final ThreadLocal<Boolean> MILK = new ThreadLocal<>();

    /** Arm the marker for the current thread. Always pair with {@link #disarm()} in a finally block. */
    public static void arm() {
        MILK.set(Boolean.TRUE);
    }

    /** Disarm and release the thread-local entry. */
    public static void disarm() {
        MILK.remove();
    }

    /** True while a milk-originated clear is in progress on this thread. */
    public static boolean isMilk() {
        return MILK.get() != null;
    }
}
