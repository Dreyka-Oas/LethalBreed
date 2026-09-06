package oas.dreyka.lethalbreed.special.runtime;

import oas.dreyka.lethalbreed.api.variant.VariantContext;
import oas.dreyka.lethalbreed.dimension.WorldAiContext;
import oas.dreyka.lethalbreed.entity.SmartZombie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The one implementation of {@link VariantContext}: one per activation, handed to the variant and dropped.
 *
 * <p>It carries more than the interface shows. A third party gets vanilla types and nothing else, while the
 * eight shipped variants reach the {@link SmartZombie} and the {@link WorldAiContext} through the accessors
 * below, by testing for this class. That asymmetry is deliberate: those two are this mod's internals, they
 * change shape between versions, and publishing them would make every such change a break for somebody.
 */
public final class VariantTickContext implements VariantContext {

    private final SmartZombie smart;
    private final ServerLevel level;
    private final WorldAiContext ai;

    public VariantTickContext(SmartZombie smart, ServerLevel level, WorldAiContext ai) {
        this.smart = smart;
        this.level = level;
        this.ai = ai;
    }

    @Override
    public Zombie zombie() {
        return smart.entity();
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public LivingEntity target() {
        LivingEntity t = smart.entity().getTarget();
        // Vanilla's field is written later in the tick, so during an activation it still holds last tick's
        // answer while the mod's own choice is the current one.
        return t != null ? t : smart.targetEntity();
    }

    @Override
    public boolean ready() {
        return smart.pursuit().specialReady();
    }

    @Override
    public void resetCooldown() {
        smart.pursuit().resetSpecialCd();
    }

    /** For the shipped variants only, which run inside this mod and are versioned with it. */
    public SmartZombie smart() {
        return smart;
    }

    /** Same, see {@link #smart}. */
    public WorldAiContext ai() {
        return ai;
    }
}
