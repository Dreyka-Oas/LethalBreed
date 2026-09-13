package oas.dreyka.lethalbreed.special;

import oas.dreyka.lethalbreed.api.variant.SpecialVariant;
import oas.dreyka.lethalbreed.api.variant.SpecialVariantRegistry;
import oas.dreyka.lethalbreed.api.variant.VariantBehavior;
import oas.dreyka.lethalbreed.api.variant.VariantContext;
import oas.dreyka.lethalbreed.special.runtime.SpecialDeath;
import oas.dreyka.lethalbreed.special.runtime.VariantTickContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Map;

/**
 * The mod's own eight variants entering the registry through the door an addon uses.
 *
 * <p>Nothing downstream can tell them apart from a stranger's afterwards: the roll, the save and the tick
 * all read the registry. That is the point of doing it this way rather than special-casing the enum, and it
 * is also the only honest test of whether the door is wide enough.
 *
 * <p>{@link SpecialType} stays. It still holds the ids, the kinds and the routing to the config fields, and
 * the four switches that read it are untouched. What it no longer is, is the list the game consults.
 */
public final class ShippedVariants {
    private ShippedVariants() {}

    /** Called once from {@code BootstrapInit}, before any addon entry point and before any entity exists. */
    public static void register() {
        for (SpecialType type : SpecialType.values()) {
            if (type != SpecialType.NONE) {
                SpecialVariantRegistry.register(variantFor(type));
            }
        }
        // The ids worlds were saved with before the vocabulary was translated. Aliased rather than
        // registered, so nothing writes one back and a world re-saves itself onto the English ids as its
        // chunks cycle, which is what SpecialType's own table has always done for the enum.
        for (Map.Entry<String, SpecialType> legacy : SpecialType.legacyIds().entrySet()) {
            SpecialVariantRegistry.alias(legacy.getKey(), legacy.getValue().id());
        }
    }

    private static SpecialVariant variantFor(SpecialType type) {
        return new SpecialVariant(type.id(), kindOf(type), type::unlockPhase, type::weight,
                new ShippedBehavior(type));
    }

    private static SpecialVariant.Kind kindOf(SpecialType type) {
        return switch (type.kind()) {
            case PASSIVE -> SpecialVariant.Kind.PASSIVE;
            case ACTIVE -> SpecialVariant.Kind.ACTIVE;
            case DEATH -> SpecialVariant.Kind.DEATH;
        };
    }

    /**
     * Every shipped variant's behaviour, routed by its type.
     *
     * <p>One class rather than eight, because the four switches it delegates to are already written and
     * already tested. Splitting them into eight behaviour objects would be a rewrite of working code for
     * the sake of symmetry, and symmetry is not what an addon author needs from this.
     */
    private record ShippedBehavior(SpecialType type) implements VariantBehavior {

        @Override
        public void onSpawn(Zombie zombie) {
            SpecialTraits.apply(zombie, type);
        }

        @Override
        public void onUnassign(Zombie zombie) {
            SpecialTraits.clear(zombie, type);
        }

        @Override
        public void tick(VariantContext ctx) {
            // The shipped switch wants the SmartZombie and the world's AI context, neither of which is on
            // the published interface. It is never handed anything else, so a context from elsewhere simply
            // does nothing rather than throwing at a stranger who built their own.
            if (ctx instanceof VariantTickContext c) {
                SpecialBehavior.shippedTick(type, c.smart(), c.level(), c.ai());
            }
        }

        @Override
        public void onDeath(Zombie zombie, ServerLevel level) {
            SpecialDeath.onDeath(zombie, level);
        }
    }
}
