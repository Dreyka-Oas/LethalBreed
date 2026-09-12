package oas.dreyka.lethalbreed.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a zombie is taken over by this mod's brain at all.
 *
 * <p>Every {@code instanceof Zombie} that loads is adopted: husk, drowned, zombie villager, and any subclass
 * another mod happens to extend {@code Zombie} with. That default is right, because the whole horde is what
 * the mod is for and a variant nobody adopted would stand there running vanilla AI in the middle of a raid.
 * But it leaves an addon with a zombie of its own no way to say "this one is mine", and the symptom is not
 * subtle: its goals are stripped, its navigation is driven by the flow field, and its pack marches it away
 * from whatever its author wrote it to do.
 *
 * <p>Each listener is handed the decision as it stands and returns the one it wants, so a listener with no
 * opinion about a given zombie returns {@code proposed} and changes nothing. Refusing is a full opt-out: the
 * zombie keeps its own AI, joins no pack, is never scheduled, and none of the mod's behaviours touch it. It
 * is still a monster in a world the mod culls monsters from, so an addon refusing adoption almost always
 * wants {@link SpawnCullCallback} as well.
 *
 * <p>Asked once per load, not once per spawn: a zombie coming back from disk is asked again, because the
 * addon that claimed it may not have been present the last time this world was open.
 */
@FunctionalInterface
public interface ZombieAdoptCallback {

    Event<ZombieAdoptCallback> EVENT = EventFactory.createArrayBacked(ZombieAdoptCallback.class,
            listeners -> (zombie, proposed) -> {
                boolean adopt = proposed;
                for (ZombieAdoptCallback listener : listeners) {
                    try {
                        adopt = listener.allowAdopt(zombie, adopt);
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return adopt;
            });

    /**
     * Called on the server thread, as the zombie loads, before anything is done to it.
     *
     * @param zombie   the zombie about to be taken over
     * @param proposed what happens if this listener says nothing: true to adopt
     * @return false to leave this zombie entirely alone, true to adopt it. Return {@code proposed} to abstain.
     */
    boolean allowAdopt(Zombie zombie, boolean proposed);

    /** Listener classes already reported, so a broken addon writes one stack trace rather than one per
     *  zombie load for the life of the server. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(ZombieAdoptCallback listener, Throwable t) {
        CallbackBlame.report(BLAMED, listener, t, "zombie adoption",
                "the zombie it was asked about is being adopted");
    }
}
