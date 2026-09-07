package oas.dreyka.lethalbreed.api.event;

import oas.dreyka.lethalbreed.LethalBreed;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mood a zombie holds for one activation, after the shipped transitions have had their say.
 *
 * <p>Fired once per activation, at the single place the mood is written, so a listener that wants a creature
 * of its own asleep in broad daylight, or one that never flees, returns the mood it wants instead of the one
 * proposed. Listeners chain: each is handed the standing answer and returns the one it wants, so a listener
 * with nothing to say about a given zombie returns {@code proposed}.
 *
 * <p>It substitutes, it does not extend. The five moods are what the brain, the sound bus, the level of
 * detail and the save path all test by identity, so a sixth one would have nowhere to be read. Returning
 * null is treated as abstaining rather than as a mood.
 */
@FunctionalInterface
public interface MoodCallback {

    Event<MoodCallback> EVENT = EventFactory.createArrayBacked(MoodCallback.class,
            listeners -> (zombie, proposed) -> {
                MoodState mood = proposed;
                for (MoodCallback listener : listeners) {
                    try {
                        MoodState answer = listener.mood(zombie, mood);
                        if (answer != null) {
                            mood = answer;
                        }
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return mood;
            });

    /**
     * Called on the server thread, once per activation of the zombie.
     *
     * @param zombie   the zombie whose mood has just been settled
     * @param proposed the mood the shipped transitions arrived at
     * @return the mood to hold. Return {@code proposed} to abstain.
     */
    MoodState mood(Zombie zombie, MoodState proposed);

    /** Listener classes already reported, so a broken addon writes one stack trace rather than one per
     *  activation of every zombie in the world. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(MoodCallback listener, Throwable t) {
        String name = listener.getClass().getName();
        if (BLAMED.add(name)) {
            LethalBreed.LOGGER.error("[LethalBreed] mood listener {} threw, and is being reported once only; "
                    + "the mood it was asked about is left as it stood", name, t);
        }
    }
}
