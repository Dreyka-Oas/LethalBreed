package oas.work.lethalbreed;
import oas.work.lethalbreed.config.ModConfig;

import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;

public class ZombieLogic {
    public static DefaultAttributeContainer.Builder injectAttributes(DefaultAttributeContainer.Builder builder) {
        return builder.add(EntityAttributes.FOLLOW_RANGE, ModConfig.INSTANCE.attributes.zombieFollowRange);
    }
}





