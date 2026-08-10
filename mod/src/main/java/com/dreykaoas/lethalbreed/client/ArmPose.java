package com.dreykaoas.lethalbreed.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Sets one arm's rotation on a shared {@code HumanoidModel}. Shared by the client-only cosmetic pose
 * mixins ({@code PlayerModelZombieArmsMixin}, {@code ZombieSleepArmsMixin}) that each force both arms
 * into a fixed pose at the tail of {@code setupAnim}.
 *
 * <p>Deliberately kept outside the {@code mixin} package tree: {@code MixinConfigTest} requires every
 * {@code .java} file under {@code mixin} to be a declared mixin, and this is a plain helper, not a mixin.
 */
@Environment(EnvType.CLIENT)
public final class ArmPose {
    private ArmPose() {}

    public static void set(ModelPart arm, float xRot, float yRot, float zRot) {
        arm.xRot = xRot;
        arm.yRot = yRot;
        arm.zRot = zRot;
    }
}
