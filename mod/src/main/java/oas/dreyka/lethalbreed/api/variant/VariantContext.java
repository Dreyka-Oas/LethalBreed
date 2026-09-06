package oas.dreyka.lethalbreed.api.variant;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * What an ACTIVE variant is handed for one activation, in vanilla types only.
 *
 * <p>Valid for the length of the {@link VariantBehavior#tick} call and no longer. Keeping one is keeping a
 * reference to a zombie that may be unloaded by the next tick, and the object itself is not built to be
 * read from another thread.
 */
public interface VariantContext {

    Zombie zombie();

    ServerLevel level();

    /**
     * Who the zombie is after, or null when it is after nobody.
     *
     * <p>Not always the same answer as {@code zombie().getTarget()}: this mod picks a target before vanilla
     * writes it, so during an activation the mod's choice is the current one and vanilla's is last tick's.
     */
    LivingEntity target();

    /**
     * Whether the shared special cooldown has elapsed.
     *
     * <p>Shared, meaning one cooldown per zombie rather than one per variant: a zombie carries a single
     * variant, so the two amount to the same thing, and the interval is the player-facing
     * {@code specialActionInterval} option. Firing without asking makes your variant ignore a setting every
     * other one obeys.
     */
    boolean ready();

    /** Start the cooldown again. Call it after you have actually done something, never before. */
    void resetCooldown();
}
