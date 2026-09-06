package oas.dreyka.lethalbreed.api.variant;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * What your variant does. Every method is empty by default, so you write the one your
 * {@link SpecialVariant.Kind} calls for and leave the others alone.
 *
 * <p>All of it runs on the server thread. One instance is shared by every zombie carrying the variant, so
 * anything you keep in a field is shared too: per-zombie state belongs on the zombie, through a Fabric data
 * attachment of your own.
 */
public interface VariantBehavior {

    /**
     * PASSIVE: called once, as the zombie finishes spawning, before it is tracked by any client. Where a
     * stat modifier goes.
     */
    default void onSpawn(Zombie zombie) {}

    /**
     * Undo exactly what {@link #onSpawn} stamped, because the zombie is being given another variant.
     *
     * <p>Rarer than it sounds and worth writing anyway: a Splitter's child runs its own roll before being
     * forced back to no variant at all, and whatever the first roll stamped stays on it otherwise. Undo your
     * own marks and nothing else, so a buff the zombie got from somewhere else survives the relabelling.
     */
    default void onUnassign(Zombie zombie) {}

    /**
     * ACTIVE: called on the zombie's own activation, which is not every game tick. The zombie is staggered
     * across buckets for performance, so counting calls measures a config knob rather than time. Read the
     * game time when you need a delay, and use {@link VariantContext#ready} for the shared cooldown.
     */
    default void tick(VariantContext ctx) {}

    /**
     * DEATH: called as the zombie dies, while it is still in the world, so anything spawned here can be put
     * where it stood.
     */
    default void onDeath(Zombie zombie, ServerLevel level) {}
}
