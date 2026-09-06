package oas.dreyka.lethalbreed.api;

import oas.dreyka.lethalbreed.LethalBreed;

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

    private static final List<PhaseChanged> PHASE_LISTENERS = new CopyOnWriteArrayList<>();

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

    public static void onPhaseChanged(PhaseChanged listener) {
        PHASE_LISTENERS.add(listener);
    }

    /** Called by PhaseManager once the new phase is authoritative. A listener that throws must not take the
     *  server down with it, so each one is isolated. */
    public static void firePhaseChanged(int from, int to) {
        for (PhaseChanged l : PHASE_LISTENERS) {
            try {
                l.onPhaseChanged(from, to);
            } catch (Throwable t) {
                LethalBreed.LOGGER.error("[LethalBreed] addon phase listener failed: {}", t.toString());
            }
        }
    }
}
