package com.dreykaoas.lethalbreed.phase;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;

import com.dreykaoas.lethalbreed.phase.PhaseConfig.PhaseDef;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Random;

/**
 * Builds and applies phase-scaled gear to a zombie at spawn: a random subset of armor pieces and a random
 * tool/weapon TYPE, whose MATERIAL tier and ENCHANT level rise with the phase. Weapons aren't a fixed list —
 * any of sword/axe/pickaxe/shovel/hoe at a tier in [0, maxTier] (biased toward the top at high phases).
 * Each equipped item gets a small drop chance ({@code phaseGearDropChance}, default 2%).
 */
public final class ZombieEquipper {
    private ZombieEquipper() {}

    // material ladder index 0→5; columns: helmet, chestplate, leggings, boots
    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
    };
    // material ladder index 0→5; columns: sword, axe, pickaxe, shovel, hoe (all the game's tools/weapons)
    private static final Item[][] TOOL = {
            {Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE},
            {Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_HOE},
            {Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE},
            {Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE},
            {Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE},
            {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE},
    };
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static void applyGear(Zombie z, Random r, PhaseDef p) {
        float drop = (float) ProgressionConfig.phaseGearDropChance;

        for (int s = 0; s < ARMOR_SLOTS.length; s++) {
            if (p.armorChance() > 0 && r.nextDouble() < p.armorChance()) {
                int tier = biasedTier(r, p.armorMaxTier());
                ItemStack st = new ItemStack(ARMOR[tier][s]);
                enchant(z, st, p.enchantLevel(), Enchantments.PROTECTION);
                z.setItemSlot(ARMOR_SLOTS[s], st);
                z.setDropChance(ARMOR_SLOTS[s], drop);
            }
        }

        if (p.weaponChance() > 0 && r.nextDouble() < p.weaponChance()) {
            int tier = biasedTier(r, p.weaponMaxTier());
            int type = r.nextInt(TOOL[tier].length);
            ItemStack st = new ItemStack(TOOL[tier][type]);
            enchant(z, st, p.enchantLevel(), Enchantments.SHARPNESS);
            z.setItemSlot(EquipmentSlot.MAINHAND, st);
            z.setDropChance(EquipmentSlot.MAINHAND, drop);
        }
    }

    /** Pick a tier in [0, maxTier], biased toward the top (best of two rolls) so high phases trend stronger. */
    private static int biasedTier(Random r, int maxTier) {
        if (maxTier <= 0) {
            return 0;
        }
        return Math.max(r.nextInt(maxTier + 1), r.nextInt(maxTier + 1));
    }

    private static void enchant(Zombie z, ItemStack st, int level, ResourceKey<Enchantment> key) {
        if (level <= 0) {
            return;
        }
        try {
            Holder<Enchantment> h = z.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            st.enchant(h, level);
        } catch (Throwable ignored) {
            // enchant registry unavailable in this context — skip silently (gear still applies)
        }
    }
}
