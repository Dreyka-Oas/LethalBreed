package oas.work.lethalbreed;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import oas.work.lethalbreed.config.ModConfig;
import java.util.Random;

public class SizeLogic {
    private static final Random RANDOM = new Random();

    public static void randomizeStats(Zombie zombie) {
        if (zombie.getTags().contains("lethal_mutant")) {
            return;
        }

        if (RANDOM.nextFloat() < ModConfig.INSTANCE.mutant.mutantChance) {
            MutantLogic.makeMutant(zombie);
        }

        double scale = ModConfig.INSTANCE.attributes.minScale + 
            (ModConfig.INSTANCE.attributes.maxScale - ModConfig.INSTANCE.attributes.minScale) * RANDOM.nextDouble();
        double speed = ModConfig.INSTANCE.attributes.minSpeed + 
            (ModConfig.INSTANCE.attributes.maxSpeed - ModConfig.INSTANCE.attributes.minSpeed) * RANDOM.nextDouble();
        double healthBonus = ModConfig.INSTANCE.attributes.healthBonusMin + 
            (ModConfig.INSTANCE.attributes.healthBonusMax - ModConfig.INSTANCE.attributes.healthBonusMin) * RANDOM.nextDouble();

        AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double baseHealth = 22.0;
            double newHealth = baseHealth * healthBonus;
            maxHealth.setBaseValue(newHealth);
            zombie.setHealth((float) newHealth);
        }

        AttributeInstance attackDamage = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            double baseDamage = 3.0;
            attackDamage.setBaseValue(baseDamage * scale * healthBonus);
        }

        AttributeInstance movementSpeed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(speed);
        }
    }
}