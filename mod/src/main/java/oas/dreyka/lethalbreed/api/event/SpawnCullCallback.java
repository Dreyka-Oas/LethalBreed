package oas.dreyka.lethalbreed.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Veto on the removal of a hostile mob.
 *
 * <p>This mod owns the whole hostile population: below phase 1 nothing hostile lives, and above it only a
 * plain zombie does. That rule is the point of the mod and it is not going away, but it also removes every
 * boss, every miniboss and every hostile any other mod adds, on their first load, before their author's
 * code ever runs. This event is how that author says no.
 *
 * <p>Each listener is handed the decision as it stands and returns the decision it wants, so one that has
 * no opinion on a given mob returns {@code proposed} and changes nothing. Chaining rather than
 * first-refusal-wins is what lets a second addon overrule the first, which is the only way two addons can
 * disagree about the same mob without one of them having to know the other exists. The question is only
 * asked about a mob the rules have already condemned, so a listener costs nothing on the mobs that were
 * going to live anyway.
 *
 * <p>A datapack that only wants to spare a fixed list of entity types wants the {@code
 * lethalbreed:spawn_protected} tag instead, which needs no code at all.
 */
@FunctionalInterface
public interface SpawnCullCallback {

    Event<SpawnCullCallback> EVENT = EventFactory.createArrayBacked(SpawnCullCallback.class,
            listeners -> (entity, phase, proposed) -> {
                boolean cull = proposed;
                for (SpawnCullCallback listener : listeners) {
                    try {
                        cull = listener.allowCull(entity, phase, cull);
                    } catch (Throwable t) {
                        blame(listener, t);
                    }
                }
                return cull;
            });

    /**
     * Called on the server thread, on the mob's first load, before it is discarded.
     *
     * @param entity   the mob the rules want gone
     * @param phase    the phase in force, which is half of why the rules want it gone
     * @param proposed what happens if this listener says nothing: true to cull
     * @return false to keep this mob alive, true to let it go. Return {@code proposed} to abstain.
     */
    boolean allowCull(Entity entity, int phase, boolean proposed);

    /** Listener classes already reported. This fires per hostile load, so the same broken addon would
     *  otherwise write one stack trace per mob for the life of the server. */
    Set<String> BLAMED = ConcurrentHashMap.newKeySet();

    private static void blame(SpawnCullCallback listener, Throwable t) {
        CallbackBlame.report(BLAMED, listener, t, "spawn cull", "the mob it was asked about is being culled");
    }
}
