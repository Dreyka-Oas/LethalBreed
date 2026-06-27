package com.dreykaoas.lethalbreed.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Spawn-time normalization for zombies: remove gear so the population is plain melee zombies. */
public final class SpawnControl {
    private SpawnControl() {}

    private static final EquipmentSlot[] STRIP = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void stripEquipment(Mob mob) {
        for (EquipmentSlot slot : STRIP) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.setDropChance(slot, 0.0f);
        }
    }
}
