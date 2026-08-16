package com.dreykaoas.lethalbreed.mixin.client;

import com.dreykaoas.lethalbreed.client.BombeurBellySmoothing;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the BOMBEUR belly-smoothing state (last displayed value + timestamp) to every client-side
 * {@code Zombie}. See {@link BombeurBellySmoothing} for why this must live here rather than on the
 * per-frame render state.
 */
@Environment(EnvType.CLIENT)
@Mixin(Zombie.class)
public class ZombieBellySmoothingMixin implements BombeurBellySmoothing {

    @Unique
    private float lethalbreed$smoothedBellyCharge;

    @Unique
    private long lethalbreed$smoothedBellyChargeLastNanos;

    @Override
    public float lethalbreed$smoothedBellyCharge() {
        return lethalbreed$smoothedBellyCharge;
    }

    @Override
    public void lethalbreed$smoothedBellyCharge(float charge) {
        this.lethalbreed$smoothedBellyCharge = charge;
    }

    @Override
    public long lethalbreed$smoothedBellyChargeLastNanos() {
        return lethalbreed$smoothedBellyChargeLastNanos;
    }

    @Override
    public void lethalbreed$smoothedBellyChargeLastNanos(long nanos) {
        this.lethalbreed$smoothedBellyChargeLastNanos = nanos;
    }
}
