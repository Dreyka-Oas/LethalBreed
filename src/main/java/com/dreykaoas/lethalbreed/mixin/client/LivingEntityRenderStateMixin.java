package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BellyChargeHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adds the BOMBEUR belly-charge field to every living render state. See {@link BellyChargeHolder}. */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements BellyChargeHolder {

    @Unique
    private float lethalbreed$bellyCharge;

    @Override
    public float lethalbreed$bellyCharge() {
        return lethalbreed$bellyCharge;
    }

    @Override
    public void lethalbreed$bellyCharge(float charge) {
        this.lethalbreed$bellyCharge = charge;
    }
}
