package oas.dreyka.lethalbreed.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a zombie is allowed into a pack, founding one included.
 *
 * <p>An addon keeps its own variant a loner by returning false, or lets one join that the shipped rules would
 * have turned away by returning true. Each listener is handed the verdict as it stands and returns the one it
 * wants, so a listener with no opinion about a given zombie returns {@code proposed} and changes nothing.
 *
 * <p>Founding is asked through the same question, with {@link #FOUNDING} as the pack id, because a founder
 * that is refused must leave no pack behind: a roster with no member would still be visited, recentroided and
 * dissolved, which is a pack in every way that costs anything. Pack ids start at one, so nothing real can
 * collide with it.
 *
 * <p><b>Coming back is not joining.</b> A member returning from disk, and one whose chunk unloaded before it
 * could be snapshotted, both put back a membership that already existed, so neither asks. A veto there would
 * tear a pack apart on a chunk boundary rather than stop one from forming, and an addon cannot keep a zombie
 * out of the pack it was already in when the world was last saved.
 */
@FunctionalInterface
public interface PackJoinCallback {

    /** The pack id handed to the listeners when the zombie is about to found one rather than join it. */
    long FOUNDING = 0L;

    Event<PackJoinCallback> EVENT = EventFactory.createArrayBacked(PackJoinCallback.class,
            listeners -> (zombie, packId, proposed) -> {
                boolean join = proposed;
                for (PackJoinCallback listener : listeners) {
                    try {
                        join = listener.allowJoin(zombie, packId, join);
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return join;
            });

    /**
     * Called on the server thread, once per attempt to put a zombie into a pack.
     *
     * @param zombie   the zombie about to join
     * @param packId   the pack it would join, or {@link #FOUNDING} when it would be the founder of a new one
     * @param proposed what happens if this listener says nothing: true to join
     * @return false to keep this zombie out, true to let it in. Return {@code proposed} to abstain.
     */
    boolean allowJoin(Zombie zombie, long packId, boolean proposed);

    /** Listener classes already reported, so a broken addon writes one stack trace rather than one per
     *  attempted join for the life of the server. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(PackJoinCallback listener, Throwable t) {
        CallbackBlame.report(BLAMED, listener, t, "pack join",
                "the verdict it was asked about is left as it stood");
    }
}
