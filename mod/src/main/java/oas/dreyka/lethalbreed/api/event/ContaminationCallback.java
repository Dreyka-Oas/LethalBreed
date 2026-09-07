package oas.dreyka.lethalbreed.api.event;

import oas.dreyka.lethalbreed.LethalBreed;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a creature catches the plague, asked at the single door the infection comes through.
 *
 * <p>An addon immunises its own creature by returning false, or exposes one the shipped rules would have
 * spared by returning true. Each listener is handed the verdict as it stands and returns the one it wants, so
 * a listener with no opinion about a given victim returns {@code proposed} and changes nothing.
 *
 * <p>Two things are settled before the question is asked and are not a listener's to overturn: a zombie never
 * catches what it carries, and a victim already infected is not infected twice. What is left is the option
 * and the listeners, in that order, which is why {@code proposed} is what {@code contaminationEnabled} says
 * rather than a flat true.
 *
 * <p>It sits on {@code ContaminationLifecycle.contaminate} and therefore covers every way in, the zombie bite
 * and {@code /lethaldev level} alike, that last one because forcing a level infects the victim through this
 * same call first.
 */
@FunctionalInterface
public interface ContaminationCallback {

    Event<ContaminationCallback> EVENT = EventFactory.createArrayBacked(ContaminationCallback.class,
            listeners -> (victim, proposed) -> {
                boolean infect = proposed;
                for (ContaminationCallback listener : listeners) {
                    try {
                        infect = listener.allowContamination(victim, infect);
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return infect;
            });

    /**
     * Called on the server thread, once per attempt to infect an eligible victim.
     *
     * @param victim   the creature about to catch it, never a zombie and never already infected
     * @param proposed what happens if this listener says nothing: true to infect
     * @return false to immunise, true to infect. Return {@code proposed} to abstain.
     */
    boolean allowContamination(LivingEntity victim, boolean proposed);

    /** Listener classes already reported, so a broken addon writes one stack trace rather than one per
     *  bite for the life of the server. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(ContaminationCallback listener, Throwable t) {
        String name = listener.getClass().getName();
        if (BLAMED.add(name)) {
            LethalBreed.LOGGER.error("[LethalBreed] contamination listener {} threw, and is being reported "
                    + "once only; the verdict it was asked about is left as it stood", name, t);
        }
    }
}
