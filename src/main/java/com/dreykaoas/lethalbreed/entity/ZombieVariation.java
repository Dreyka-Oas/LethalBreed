package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Random;

/**
 * Gives each zombie a modest, individual flavour: random size, strength, speed and leap power. Rolls
 * are seeded by the entity UUID so they are deterministic (stable across reloads) and applied via
 * permanent attribute modifiers with fixed ids (idempotent — no compounding on chunk reload).
 */
public final class ZombieVariation {
    private ZombieVariation() {}

    private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_scale");
    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_speed");
    private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("lethalbreed", "rand_damage");

    public static void apply(Zombie z) {
        if (!LethalBreedConfig.enableVariation) {
            return;
        }
        Random r = seeded(z, 0L);
        applyMultiplier(z, Attributes.SCALE, SCALE_ID, roll(r, LethalBreedConfig.varScaleMin, LethalBreedConfig.varScaleMax));
        applyMultiplier(z, Attributes.MOVEMENT_SPEED, SPEED_ID, roll(r, LethalBreedConfig.varSpeedMin, LethalBreedConfig.varSpeedMax));
        applyMultiplier(z, Attributes.ATTACK_DAMAGE, DAMAGE_ID, roll(r, LethalBreedConfig.varDamageMin, LethalBreedConfig.varDamageMax));
    }

    /** Deterministic leap-power factor for this zombie. */
    public static double leapFactor(Zombie z) {
        if (!LethalBreedConfig.enableVariation) {
            return 1.0;
        }
        return roll(seeded(z, 777L), LethalBreedConfig.varLeapMin, LethalBreedConfig.varLeapMax);
    }

    private static void applyMultiplier(LivingEntity e, Holder<Attribute> attr, Identifier id, double factor) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) {
            inst.addOrReplacePermanentModifier(
                    new AttributeModifier(id, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static Random seeded(Zombie z, long salt) {
        return new Random(z.getUUID().getMostSignificantBits() ^ z.getUUID().getLeastSignificantBits() ^ salt);
    }

    private static double roll(Random r, double min, double max) {
        return min + r.nextDouble() * (max - min);
    }
}
