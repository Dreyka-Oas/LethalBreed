package oas.work.lethalbreed;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import oas.work.lethalbreed.config.ModConfig;

public class ZombieLogic {
    public static AttributeSupplier.Builder injectAttributes(AttributeSupplier.Builder builder) {
        return builder.add(Attributes.FOLLOW_RANGE, ModConfig.INSTANCE.attributes.zombieFollowRange);
    }
}








