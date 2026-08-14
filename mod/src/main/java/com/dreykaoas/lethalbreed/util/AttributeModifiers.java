package com.dreykaoas.lethalbreed.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One place to stamp a permanent {@code ADD_MULTIPLIED_BASE} attribute modifier that scales an entity's base
 * value by {@code factor} (stored as the {@code factor - 1.0} delta the operation expects). Used by the special
 * roller, splitter death and the per-zombie variation roll so they all key, scale and no-op the exact same way.
 */
public final class AttributeModifiers {
    private AttributeModifiers() {
    }

    /** Scale {@code attr}'s base by {@code factor}, keyed by a {@code lethalbreed:}-namespaced id path. */
    public static void multiplyBase(LivingEntity entity, Holder<Attribute> attr, String idPath, double factor) {
        multiplyBase(entity, attr, Identifier.fromNamespaceAndPath("lethalbreed", idPath), factor);
    }

    /** Scale {@code attr}'s base by {@code factor}, keyed by an explicit id. No-op if the entity lacks {@code attr}. */
    public static void multiplyBase(LivingEntity entity, Holder<Attribute> attr, Identifier id, double factor) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.addOrReplacePermanentModifier(
                new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /**
     * Scale {@code attr}'s running TOTAL by {@code factor}, so this modifier composes with the others instead
     * of summing against them.
     *
     * <p>{@link #multiplyBase} deltas all add together before being applied to the base once, which is right
     * for independent rolls but wrong for "take whatever this ended up as, and halve it". A -0.40 delta next
     * to a +0.25 variation roll yields 0.85 of base, not 0.60 of the varied size — and next to a +0.40 it
     * yields exactly the original size, cancelling outright.
     */
    public static void multiplyTotal(LivingEntity entity, Holder<Attribute> attr, String idPath, double factor) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.addOrReplacePermanentModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("lethalbreed", idPath), factor - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Drop a modifier this class stamped earlier. No-op if the entity lacks {@code attr} or never carried it. */
    public static void remove(LivingEntity entity, Holder<Attribute> attr, String idPath) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(Identifier.fromNamespaceAndPath("lethalbreed", idPath));
        }
    }
}
