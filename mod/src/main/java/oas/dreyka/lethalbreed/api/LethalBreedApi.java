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

    private static final List<String> AI_NAMESPACES = new CopyOnWriteArrayList<>(
            List.of("net.minecraft.", "oas.dreyka.lethalbreed"));

    /**
     * Declare that goals from {@code prefix} are yours and are not a conflict.
     *
     * <p>Without this, AiConflictDetector treats any goal outside vanilla and this mod as an incompatible
     * AI mod and, with the shipped {@code failOnAiConflict}, stops the server on the first zombie loaded.
     */
    public static void allowAiNamespace(String prefix) {
        if (prefix != null && !prefix.isEmpty() && !AI_NAMESPACES.contains(prefix)) {
            AI_NAMESPACES.add(prefix);
        }
    }

    public static boolean isAllowedAiNamespace(String className) {
        for (String p : AI_NAMESPACES) {
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
