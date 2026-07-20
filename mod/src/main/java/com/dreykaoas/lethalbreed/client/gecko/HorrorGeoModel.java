package com.dreykaoas.lethalbreed.client.gecko;

import com.dreykaoas.lethalbreed.entity.HorrorModels;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorRenderData;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorReplacedZombie;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@link GeoModel} for the whole zombie roster: it returns a DIFFERENT geometry + texture per instance by
 * reading the {@link HorrorRenderData#MODEL} ticket off the render state (fed by
 * {@link HorrorZombieRenderer#addRenderData}); all instances share ONE animation file (GeckoLib loads a single
 * {@code getAnimationResource} for the singleton animatable), with clips namespaced {@code <id>_...} inside it.
 *
 * <p>Path building is DELEGATED to GeckoLib's own {@link DefaultedEntityGeoModel} (one per model id + one for
 * the shared animation file), so the resource identifiers match GeckoLib's prefix/suffix normalisation exactly
 * — a hand-built path like {@code geckolib/animations/entity/horror.animation.json} does NOT normalise to the
 * same registry key GeckoLib scans it under, which is why building them by hand crashed with "Unable to find
 * animation file".
 */
public class HorrorGeoModel extends GeoModel<HorrorReplacedZombie> {
    // Shared animation file: geckolib/animations/entity/horror.animation.json (all models' clips live here).
    private final DefaultedEntityGeoModel<HorrorReplacedZombie> anim =
            new DefaultedEntityGeoModel<>(Identifier.fromNamespaceAndPath("lethalbreed", "horror"));
    // Per-model geometry + texture path providers, indexed like HorrorModels.IDS.
    private final List<DefaultedEntityGeoModel<HorrorReplacedZombie>> perModel = new ArrayList<>();

    public HorrorGeoModel() {
        for (String id : HorrorModels.IDS) {
            perModel.add(new DefaultedEntityGeoModel<>(Identifier.fromNamespaceAndPath("lethalbreed", id)));
        }
    }

    private int idx(GeoRenderState state) {
        int m = state.getOrDefaultGeckolibData(HorrorRenderData.MODEL, 0);
        return (m < 0 || m >= perModel.size()) ? 0 : m;
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return perModel.get(idx(state)).getModelResource(state);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return perModel.get(idx(state)).getTextureResource(state);
    }

    @Override
    public Identifier getAnimationResource(HorrorReplacedZombie animatable) {
        return anim.getAnimationResource(animatable);
    }
}
