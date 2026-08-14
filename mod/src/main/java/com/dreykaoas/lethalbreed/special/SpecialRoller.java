package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.List;
import java.util.Random;

/**
 * Rolls a {@link SpecialType} for a zombie at spawn (chance scaling with the phase, harder types unlocked at
 * higher phases), stores it on the persistent attachment, sets the floating name, and applies PASSIVE buffs
 * immediately (so they're present from spawn and persist in NBT). ACTIVE/DEATH types do their work later
 * ({@link SpecialBehavior}).
 */
public final class SpecialRoller {
    private SpecialRoller() {}

    public static void roll(Zombie z, Random r, int phase) {
        if (!SpecialVariantConfig.specialEnabled) {
            return;
        }
        double chance = Math.min(SpecialVariantConfig.specialMaxChance,
                SpecialVariantConfig.specialBaseChance + phase * SpecialVariantConfig.specialPhaseScale);
        if (r.nextDouble() >= chance) {
            return;
        }
        List<SpecialType> pool = SpecialType.available(phase);
        if (pool.isEmpty()) {
            return;
        }
        SpecialType type = pickWeighted(pool, r);
        assign(z, type);
    }

    /** Force a specific type (used by the test command and Splitter children = NONE). */
    public static void assign(Zombie z, SpecialType type) {
        // Strip whatever the previous type had already stamped. A Splitter child is spawned, runs the whole
        // finalizeSpawn chain — including its OWN special roll — and only THEN gets assign(NONE). Returning
        // early left those passives in place: a child re-labelled "none" kept Resistance II, double health and
        // a spc_scale of +0.40 that exactly cancels the -0.40 of split_small, so the "small child" came out
        // full size, twice as tough, and (with specialShowName) still wearing a "Juggernaut" nametag.
        SpecialType previous = SpecialType.fromId(z.getAttached(SpecialAttachment.SPECIAL));
        if (previous != type && previous != SpecialType.NONE) {
            clearPassive(z, previous);
        }
        z.setAttached(SpecialAttachment.SPECIAL, type.id());
        if (type == SpecialType.NONE) {
            return;
        }
        if (SpecialVariantConfig.specialShowName) {
            z.setCustomName(Component.literal(type.frName()));
            z.setCustomNameVisible(true);
        }
        applyPassive(z, type);
    }

    private static SpecialType pickWeighted(List<SpecialType> pool, Random r) {
        int total = 0;
        for (SpecialType t : pool) {
            total += t.weight();
        }
        int pick = r.nextInt(Math.max(1, total));
        for (SpecialType t : pool) {
            pick -= t.weight();
            if (pick < 0) {
                return t;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Undo exactly what {@link #applyPassive} stamps for {@code type} — and nothing else, so a buff the
     *  zombie drew from the random effect pool survives being re-labelled. */
    private static void clearPassive(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTEUR -> {
                z.removeEffect(MobEffects.SPEED);
                AttributeModifiers.remove(z, Attributes.MOVEMENT_SPEED, "spc_speed");
            }
            case BONDISSEUR -> z.removeEffect(LethalBreedEffects.LEAP);
            case JUGGERNAUT -> {
                AttributeModifiers.remove(z, Attributes.SCALE, "spc_scale");
                AttributeModifiers.remove(z, Attributes.MAX_HEALTH, "spc_hp");
                z.removeEffect(MobEffects.RESISTANCE);
                // applyPassive topped the pool up to the inflated maximum; shrink back into the new one.
                z.setHealth(Math.min(z.getHealth(), z.getMaxHealth()));
            }
            default -> { /* ACTIVE / DEATH stamp nothing at assign time */ }
        }
        if (z.hasCustomName()) {
            z.setCustomName(null);
            z.setCustomNameVisible(false);
        }
    }

    private static void applyPassive(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTEUR -> {
                infinite(z, MobEffects.SPEED, SpecialVariantConfig.specialSprinteurSpeedAmp);
                mul(z, Attributes.MOVEMENT_SPEED, "spc_speed", SpecialVariantConfig.specialSprinteurSpeedMul);
            }
            case BONDISSEUR -> infinite(z, LethalBreedEffects.LEAP, SpecialVariantConfig.specialBondisseurLeapAmp);
            case JUGGERNAUT -> {
                // Bulky tank via size/HP/resistance only — no armor (zombies never wear gear).
                mul(z, Attributes.SCALE, "spc_scale", SpecialVariantConfig.specialJuggernautScale);
                mul(z, Attributes.MAX_HEALTH, "spc_hp", SpecialVariantConfig.specialJuggernautHealthMul);
                z.setHealth(z.getMaxHealth());
                infinite(z, MobEffects.RESISTANCE, SpecialVariantConfig.specialJuggernautResistanceAmp);
            }
            default -> { /* ACTIVE / DEATH: handled at runtime */ }
        }
    }

    private static void infinite(Zombie z, Holder<MobEffect> effect, int amp) {
        LethalBreedEffects.applyInfinite(z, effect, amp);
    }

    private static void mul(Zombie z, Holder<Attribute> attr, String idPath, double factor) {
        AttributeModifiers.multiplyBase(z, attr, idPath, factor);
    }
}
