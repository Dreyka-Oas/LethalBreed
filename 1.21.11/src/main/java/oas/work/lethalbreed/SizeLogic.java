package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class SizeLogic {
    private static final Random RANDOM = new Random();

    public static void randomizeStats(Zombie zombie, boolean allowMutant) {
        if (allowMutant && RANDOM.nextFloat() < ModConfig.INSTANCE.mutant.mutantChance) {
            MutantLogic.makeMutant(zombie);
        }

        double scale = ModConfig.INSTANCE.attributes.minScale + (ModConfig.INSTANCE.attributes.maxScale - ModConfig.INSTANCE.attributes.minScale) * RANDOM.nextDouble();
        double speed = ModConfig.INSTANCE.attributes.minSpeed + (ModConfig.INSTANCE.attributes.maxSpeed - ModConfig.INSTANCE.attributes.minSpeed) * RANDOM.nextDouble();
        double healthBonus = ModConfig.INSTANCE.attributes.healthBonusMin + (ModConfig.INSTANCE.attributes.healthBonusMax - ModConfig.INSTANCE.attributes.healthBonusMin) * RANDOM.nextDouble();

        // scale not supported
        
        double hBase = zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
        double finalHealth = hBase * scale * healthBonus;
        setAttribute(zombie, Attributes.MAX_HEALTH, finalHealth);
        zombie.setHealth((float) finalHealth);

        double dBase = zombie.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        setAttribute(zombie, Attributes.ATTACK_DAMAGE, dBase * scale * healthBonus);

        setAttribute(zombie, Attributes.MOVEMENT_SPEED, speed);
    }

    private static void setAttribute(Zombie zombie, Holder<Attribute> attr, double value) {
        AttributeInstance instance = zombie.getAttribute(attr);
        if (instance != null) instance.setBaseValue(value);
    }

    public static void reapplyStats(Zombie zombie) {
        if (zombie.getTags().contains("lethal_mutant")) {
            return;
        }

        double currentScale = 1.0;
        double newScale = ModConfig.INSTANCE.attributes.minScale + (ModConfig.INSTANCE.attributes.maxScale - ModConfig.INSTANCE.attributes.minScale) * (currentScale - ModConfig.INSTANCE.attributes.minScale) / (ModConfig.INSTANCE.attributes.maxScale - ModConfig.INSTANCE.attributes.minScale);
        newScale = Math.max(ModConfig.INSTANCE.attributes.minScale, Math.min(ModConfig.INSTANCE.attributes.maxScale, newScale));

        double speed = zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        double newSpeed = ModConfig.INSTANCE.attributes.minSpeed + (ModConfig.INSTANCE.attributes.maxSpeed - ModConfig.INSTANCE.attributes.minSpeed) * (speed - ModConfig.INSTANCE.attributes.minSpeed) / (ModConfig.INSTANCE.attributes.maxSpeed - ModConfig.INSTANCE.attributes.minSpeed);
        newSpeed = Math.max(ModConfig.INSTANCE.attributes.minSpeed, Math.min(ModConfig.INSTANCE.attributes.maxSpeed, newSpeed));

        // scale not supported
        setAttribute(zombie, Attributes.MOVEMENT_SPEED, newSpeed);

        double hBase = zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
        double hCurrent = zombie.getHealth();
        double ratio = hCurrent / hBase;
        double healthBonus = ModConfig.INSTANCE.attributes.healthBonusMin + (ModConfig.INSTANCE.attributes.healthBonusMax - ModConfig.INSTANCE.attributes.healthBonusMin) * (ratio - ModConfig.INSTANCE.attributes.healthBonusMin) / (ModConfig.INSTANCE.attributes.healthBonusMax - ModConfig.INSTANCE.attributes.healthBonusMin);
        healthBonus = Math.max(ModConfig.INSTANCE.attributes.healthBonusMin, Math.min(ModConfig.INSTANCE.attributes.healthBonusMax, healthBonus));
        double finalHealth = hBase * newScale * healthBonus;
        setAttribute(zombie, Attributes.MAX_HEALTH, finalHealth);
        zombie.setHealth((float) Math.min(finalHealth, zombie.getHealth()));

        double dBase = zombie.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        setAttribute(zombie, Attributes.ATTACK_DAMAGE, dBase * newScale * healthBonus);
    }
}










