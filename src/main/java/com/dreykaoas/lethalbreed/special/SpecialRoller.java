package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.util.AttributeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
        if (!ProgressionConfig.specialEnabled) {
            return;
        }
        double chance = Math.min(ProgressionConfig.specialMaxChance,
                ProgressionConfig.specialBaseChance + phase * ProgressionConfig.specialPhaseScale);
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
        z.setAttached(SpecialAttachment.SPECIAL, type.id());
        if (type == SpecialType.NONE) {
            return;
        }
        if (ProgressionConfig.specialShowName) {
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

    private static void applyPassive(Zombie z, SpecialType type) {
        switch (type) {
            case SPRINTEUR -> {
                infinite(z, MobEffects.SPEED, 1);
                mul(z, Attributes.MOVEMENT_SPEED, "spc_speed", 1.35);
            }
            case BONDISSEUR -> infinite(z, LethalBreedEffects.LEAP, 2);
            case JUGGERNAUT -> {
                mul(z, Attributes.SCALE, "spc_scale", 1.4);
                mul(z, Attributes.MAX_HEALTH, "spc_hp", 2.0);
                z.setHealth(z.getMaxHealth());
                infinite(z, MobEffects.RESISTANCE, 1);
                ironArmor(z);
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

    private static void ironArmor(Zombie z) {
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        z.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            z.setDropChance(s, 0.0f);
        }
    }
}
