package oas.dreyka.lethalbreed.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Sets one limb's rotation on a shared {@code HumanoidModel}. Shared by the client-only cosmetic pose
 * mixins ({@code PlayerModelZombieArmsMixin}, {@code ZombieSleepArmsMixin}, {@code ZombieClingPoseMixin})
 * that each force parts into a fixed pose at the tail of {@code setupAnim}. Arms for the first two, arms
 * and legs and head for the cling, hence the name.
 *
 * <p>Deliberately kept outside the {@code mixin} package tree: {@code MixinConfigTest} requires every
 * {@code .java} file under {@code mixin} to be a declared mixin, and this is a plain helper, not a mixin.
 */
@Environment(EnvType.CLIENT)
public final class LimbPose {
    private LimbPose() {}

    public static void set(ModelPart limb, float xRot, float yRot, float zRot) {
        limb.xRot = xRot;
        limb.yRot = yRot;
        limb.zRot = zRot;
    }
}
