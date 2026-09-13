package oas.dreyka.lethalbreed.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether one creature is prey for one zombie, asked before distance, sight or stickiness are considered.
 *
 * <p>This is how another mod's creature stops being hunted, or starts being hunted when the shipped rules
 * would have passed it over. Each listener is handed the verdict as it stands and returns the one it wants,
 * so a listener with no opinion about a given candidate returns {@code proposed} and changes nothing.
 *
 * <p><b>This fires on the broad phase.</b> Several hundred times a tick on a busy server, on the server
 * thread, inside the scan that already decides how much a horde costs. With no listener it is Fabric's empty
 * invoker, which is a static call and measures at nothing. With one listener it is that listener's cost,
 * multiplied by every candidate every zombie looks at. Read an attachment, compare an entity type, return.
 * Anything that allocates, walks a chunk or takes a lock belongs somewhere else.
 *
 * <p>It has its own event rather than sharing one with {@link SpawnCullCallback} for exactly that reason: an
 * addon that only wants a say in what gets culled at load must not be woken up on every candidate of every
 * scan.
 */
@FunctionalInterface
public interface TargetCandidateCallback {

    Event<TargetCandidateCallback> EVENT = EventFactory.createArrayBacked(TargetCandidateCallback.class,
            listeners -> (self, candidate, proposed) -> {
                boolean valid = proposed;
                for (TargetCandidateCallback listener : listeners) {
                    try {
                        valid = listener.isValidPrey(self, candidate, valid);
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return valid;
            });

    /**
     * Called on the server thread, once per candidate per scan.
     *
     * @param self      the hunter asking
     * @param candidate the creature being considered
     * @param proposed  what the shipped rules answered: true if this is prey
     * @return true to allow the hunt, false to forbid it. Return {@code proposed} to abstain.
     */
    boolean isValidPrey(Mob self, LivingEntity candidate, boolean proposed);

    /** Listener classes already reported. This fires per candidate, so one broken addon would otherwise
     *  write a stack trace several hundred times a tick. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(TargetCandidateCallback listener, Throwable t) {
        CallbackBlame.report(BLAMED, listener, t, "target candidate",
                "the verdict it was asked about is left as it stood");
    }
}
