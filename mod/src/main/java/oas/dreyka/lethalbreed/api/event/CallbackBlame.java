package oas.dreyka.lethalbreed.api.event;

import oas.dreyka.lethalbreed.LethalBreed;

import java.util.Set;

/**
 * Reports a listener's thrown exception once per listener class, so a broken addon writes one stack trace
 * for the life of the server rather than one per firing of the event.
 *
 * <p>Every {@code api/event} callback interface keeps its own {@code BLAMED} set: a listener class that
 * fails in two different callbacks is a different bug in each, and must be reported for both, so the sets
 * are never shared across interfaces even though the reporting logic is identical.
 */
final class CallbackBlame {
    private CallbackBlame() {}

    /**
     * @param blamed  the calling interface's own already-reported set
     * @param listener the listener that threw
     * @param t        what it threw
     * @param tag      names the callback kind in the log line, e.g. "mood" or "spawn cull"
     * @param outcome  what happens to the value the listener was asked about, e.g. "the mood it was asked
     *                 about is left as it stood"
     */
    static void report(Set<String> blamed, Object listener, Throwable t, String tag, String outcome) {
        String name = listener.getClass().getName();
        if (blamed.add(name)) {
            LethalBreed.LOGGER.error("[LethalBreed] " + tag + " listener {} threw, and is being reported "
                    + "once only; " + outcome, name, t);
        }
    }
}
