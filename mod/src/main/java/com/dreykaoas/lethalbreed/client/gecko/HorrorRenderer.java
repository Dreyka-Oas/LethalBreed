package com.dreykaoas.lethalbreed.client.gecko;

import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Shared GeckoLib renderer for every horror-zombie variant. They all use the same {@link HorrorZombie} class;
 * the distinct look comes purely from the {@link GeoModel} handed to each registration (one per variant id),
 * so a single renderer covers the whole roster. {@code R} follows the GeckoLib 5.x pattern (a vanilla
 * {@link EntityRenderState} that GeckoLib makes implement {@link GeoRenderState} at runtime).
 */
public class HorrorRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<HorrorZombie, R> {
    public HorrorRenderer(EntityRendererProvider.Context context, GeoModel<HorrorZombie> model) {
        super(context, model);
    }
}
