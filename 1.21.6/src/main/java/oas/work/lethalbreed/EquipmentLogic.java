package oas.work.lethalbreed;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import oas.work.lethalbreed.config.ModConfig;
import java.util.Random;

public class EquipmentLogic {
    private static final Random RANDOM = new Random();

    public static void randomizeEquipment(Zombie zombie) {
        if (zombie.getTags().contains("lethal_mutant")) {
            return;
        }

        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.kamikazeChance) {
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
            return;
        }

        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.weaponChance) {
            ItemStack stack = new ItemStack(getRandomWeapon());
            zombie.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        }

        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.armorHeadChance) {
            randomizeArmor(zombie, EquipmentSlot.HEAD, getHeads());
        }
        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.armorChestChance) {
            randomizeArmor(zombie, EquipmentSlot.CHEST, getChestplates());
        }
        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.armorLegsChance) {
            randomizeArmor(zombie, EquipmentSlot.LEGS, getLeggings());
        }
        if (RANDOM.nextFloat() < ModConfig.INSTANCE.equipment.armorFeetChance) {
            randomizeArmor(zombie, EquipmentSlot.FEET, getBoots());
        }
    }

    private static void randomizeArmor(Zombie z, EquipmentSlot slot, Item[] pool) {
        ItemStack stack = new ItemStack(pool[RANDOM.nextInt(pool.length)]);
        z.setItemSlot(slot, stack);
    }

    private static Item getRandomWeapon() {
        Item[] w = {
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, 
            Items.STONE_SWORD, Items.WOODEN_SWORD,
            Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE, 
            Items.NETHERITE_PICKAXE, Items.IRON_PICKAXE
        };
        return w[RANDOM.nextInt(w.length)];
    }

    private static Item[] getHeads() {
        return new Item[]{
            Items.NETHERITE_HELMET, Items.DIAMOND_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, 
            Items.CHAINMAIL_HELMET, Items.LEATHER_HELMET, Items.TURTLE_HELMET
        };
    }

    private static Item[] getChestplates() {
        return new Item[]{
            Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE, 
            Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_CHESTPLATE
        };
    }

    private static Item[] getLeggings() {
        return new Item[]{
            Items.NETHERITE_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.IRON_LEGGINGS, 
            Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.LEATHER_LEGGINGS
        };
    }

    private static Item[] getBoots() {
        return new Item[]{
            Items.NETHERITE_BOOTS, Items.DIAMOND_BOOTS, Items.IRON_BOOTS, 
            Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.LEATHER_BOOTS
        };
    }
}