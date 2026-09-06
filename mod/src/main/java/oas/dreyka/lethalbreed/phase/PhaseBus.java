package oas.dreyka.lethalbreed.phase;

import oas.dreyka.lethalbreed.LethalBreed;
import oas.dreyka.lethalbreed.api.PhaseChanged;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who is told when the night progression moves, and the only place that tells them.
 *
 * <p>The list used to sit on {@code LethalBreedApi} next to a public {@code firePhaseChanged}, so anything
 * holding the published jar could announce a phase the server had not moved to and every listener would
 * believe it. Subscribing is part of the published surface; announcing is not, and this class is out of an
 * addon's reach because the package is not {@code api}. {@link PhaseManager} is its only caller.
 */
public final class PhaseBus {
    private PhaseBus() {}

    // Copy-on-write because a listener may subscribe from another addon's entrypoint while a world load is
    // already firing, and because reads outnumber writes by the life of the server to a handful.
    private static final List<PhaseChanged> LISTENERS = new CopyOnWriteArrayList<>();

    public static void subscribe(PhaseChanged listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Announce a move that has already happened, so a listener reading {@code PhaseManager.current()} sees
     * the new value rather than the one being left.
     *
     * <p>Same phase, no announcement: a fresh world opens at 0 with the field already at 0, and waking
     * every addon to say nothing changed is worse than silence. A listener that throws is isolated, since
     * one bad addon must take down neither the server nor the listeners queued behind it.
     */
    public static void fire(int from, int to) {
        if (from == to) {
            return;
        }
        for (PhaseChanged listener : LISTENERS) {
            try {
                listener.onPhaseChanged(from, to);
            } catch (Throwable t) {
                LethalBreed.LOGGER.error("[LethalBreed] addon phase listener failed: {}", t.toString());
            }
        }
    }
}
