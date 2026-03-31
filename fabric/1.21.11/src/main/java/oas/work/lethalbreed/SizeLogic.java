package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import java.util.Random;

public class SizeLogic {
    private static final Random RANDOM = new Random();

    public static void randomizeStats(ZombieEntity zombie, boolean allowMutant) {
        if (allowMutant && RANDOM.nextFloat() < ModConfig.INSTANCE.mutant.mutantChance) {
            MutantLogic.makeMutant(zombie);
        }

        double scale = ModConfig.INSTANCE.attributes.minScale + (ModConfig.INSTANCE.attributes.maxScale - ModConfig.INSTANCE.attributes.minScale) * RANDOM.nextDouble();
        double speed = ModConfig.INSTANCE.attributes.minSpeed + (ModConfig.INSTANCE.attributes.maxSpeed - ModConfig.INSTANCE.attributes.minSpeed) * RANDOM.nextDouble();
        double healthBonus = ModConfig.INSTANCE.attributes.healthBonusMin + (ModConfig.INSTANCE.attributes.healthBonusMax - ModConfig.INSTANCE.attributes.healthBonusMin) * RANDOM.nextDouble();

        setAttribute(zombie, EntityAttributes.SCALE, scale);
        
        double hBase = zombie.getAttributeBaseValue(EntityAttributes.MAX_HEALTH);
        double finalHealth = hBase * scale * healthBonus;
        setAttribute(zombie, EntityAttributes.MAX_HEALTH, finalHealth);
        zombie.setHealth((float) finalHealth);

        double dBase = zombie.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE);
        setAttribute(zombie, EntityAttributes.ATTACK_DAMAGE, dBase * scale * healthBonus);

        setAttribute(zombie, EntityAttributes.MOVEMENT_SPEED, speed);
    }

    private static void setAttribute(ZombieEntity zombie, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double value) {
        EntityAttributeInstance instance = zombie.getAttributeInstance(attr);
        if (instance != null) instance.setBaseValue(value);
    }
}