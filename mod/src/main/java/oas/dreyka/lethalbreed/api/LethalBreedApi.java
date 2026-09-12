package oas.dreyka.lethalbreed.api;

import oas.dreyka.lethalbreed.phase.PhaseBus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The whole surface another mod needs. Everything here is safe to call from a {@link LethalBreedAddon}
 * entrypoint; nothing here needs a server to exist yet.
 */
public final class LethalBreedApi {
    private LethalBreedApi() {}

    private static final List<String> BUILT_IN_NAMESPACES = List.of("net.minecraft.", "oas.dreyka.lethalbreed");

    /** Only what addons claimed. Kept apart from the two built-ins above because the two lists answer
     *  different questions: everything here is a goal somebody owns and wants left alone, while a vanilla
     *  target goal is exactly what {@code forceNearestTarget} exists to take away. */
    private static final List<String> CLAIMED_NAMESPACES = new CopyOnWriteArrayList<>();

    /**
     * Declare that goals from {@code prefix} are yours and are not a conflict.
     *
     * <p>Without this, AiConflictDetector treats any goal outside vanilla and this mod as an incompatible
     * AI mod and, with the shipped {@code failOnAiConflict}, stops the server on the first zombie loaded.
     *
     * <p>A claimed goal is also KEPT when {@code forceNearestTarget} empties the target selector, so an
     * addon's own targeting survives. Its verdict and this mod's nearest-prey pick then both write the
     * target, each overwriting the other; claiming the namespace is how an addon accepts that.
     */
    public static void allowAiNamespace(String prefix) {
        if (prefix != null && !prefix.isEmpty() && !CLAIMED_NAMESPACES.contains(prefix)) {
            CLAIMED_NAMESPACES.add(prefix);
        }
    }

    /** Whether this goal counts as a conflict. Vanilla and this mod's own goals never do. */
    public static boolean isAllowedAiNamespace(String className) {
        return isClaimedAiNamespace(className) || startsWithAny(className, BUILT_IN_NAMESPACES);
    }

    /** Whether an addon explicitly claimed this goal, which is narrower than {@link #isAllowedAiNamespace}
     *  and is the question {@code VanillaTargetingGoals} has to ask before removing anything. */
    public static boolean isClaimedAiNamespace(String className) {
        return startsWithAny(className, CLAIMED_NAMESPACES);
    }

    private static boolean startsWithAny(String className, List<String> prefixes) {
        for (String p : prefixes) {
            if (className.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Be told when the night progression moves, with the phase left behind and the one just reached.
     *
     * <p>Called on the server thread, once the new phase is authoritative, so reading anything that scales
     * with it inside the listener gives the new value. Opening a world announces the phase it comes back
     * at, unless that is the phase already in memory, which is the case a fresh world at 0 falls into.
     *
     * <p>There is deliberately no way to announce one. A phase belongs to the server and a listener that
     * heard an invented change would act on a world that never moved.
     */
    public static void onPhaseChanged(PhaseChanged listener) {
        PhaseBus.subscribe(listener);
    }
}
