package com.dreykaoas.lethalbreed.client.gecko;

import com.dreykaoas.lethalbreed.entity.HorrorModelAttachment;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorRenderData;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorReplacedZombie;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.monster.zombie.Zombie;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * The GeckoLib renderer registered for {@code EntityType.ZOMBIE} (replacing the vanilla zombie renderer). Every
 * zombie flows through it; {@link #addRenderData} copies this zombie's per-instance values (its model index +
 * BOMBEUR belly charge) from Fabric attachments into the render-state tickets, and {@link HorrorGeoModel} +
 * {@link HorrorReplacedZombie} then select that instance's geometry/texture/animation. Ordinary zombies carry
 * model 0 ({@code vanilla_look}) and render as a plain zombie. {@code R} follows the GeckoLib 5.x pattern (a
 * vanilla {@link EntityRenderState} that GeckoLib makes implement {@link GeoRenderState} at runtime).
 */
public class HorrorZombieRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoReplacedEntityRenderer<HorrorReplacedZombie, Zombie, R> {

    /** Per-zombie ground-translation latch (render thread only). The client position DOES change per tick for
     *  nearby zombies (verified), so a short latch bridges the odd gap without ever getting stuck moving. */
    private static final java.util.WeakHashMap<Zombie, double[]> MOVE = new java.util.WeakHashMap<>(); // [x, z, tick, movingUntil]

    public HorrorZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new HorrorGeoModel(), HorrorReplacedZombie.INSTANCE);
    }

    @Override
    public void addRenderData(HorrorReplacedZombie animatable, Zombie entity, R state, float partialTick) {
        super.addRenderData(animatable, entity, state, partialTick);
        state.addGeckolibData(HorrorRenderData.MODEL, entity.getAttachedOrElse(HorrorModelAttachment.MODEL, 0));
        state.addGeckolibData(HorrorRenderData.BELLY, entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f));
        // Server-authoritative state (idle/walk/pillar) — the CLIMB (BUILDING) + intent signal.
        state.addGeckolibData(HorrorRenderData.STATE, entity.getAttachedOrElse(HorrorModelAttachment.ANIM_STATE, 0));
        state.addGeckolibData(HorrorRenderData.SPEED, entity.walkAnimation.speed(partialTick));
        // Accumulated walk distance (from the entity's own limb-swing, which reliably advances) — drives the
        // ground-locked leg swing in adjustModelBonesForRender.
        state.addGeckolibData(HorrorRenderData.WALK_POS, entity.walkAnimation.position());
        state.addGeckolibData(HorrorRenderData.AIRBORNE, !entity.onGround() || entity.getDeltaMovement().y > 0.08);

        // Actual ground translation this tick, latched a few ticks. WALK whenever the body really moves —
        // regardless of the AI state — so a zombie that is still creeping forward while flagged BREAKING (or
        // whose state momentarily flickers) never freezes into idle and slides.
        long now = entity.tickCount;
        double[] m = MOVE.get(entity);
        if (m == null) {
            m = new double[] {entity.getX(), entity.getZ(), now, 0};
            MOVE.put(entity, m);
        }
        if ((long) m[2] != now) {
            double dx = entity.getX() - m[0];
            double dz = entity.getZ() - m[1];
            if (dx * dx + dz * dz > 4.0E-6) { // ~0.002 blocks/tick
                m[3] = now + 4;
            }
            m[0] = entity.getX();
            m[1] = entity.getZ();
            m[2] = now;
        }
        state.addGeckolibData(HorrorRenderData.MOVING, now < (long) m[3]);
    }

    /**
     * GROUND-LOCK the legs. After the clips are applied, overwrite leg_r/leg_l rotation with a swing that is a
     * pure function of DISTANCE walked ({@code walkAnimationPos}) — exactly how vanilla mobs avoid sliding — so
     * the feet track the ground at ANY speed and during leaps, instead of a free-running clip that moon-walks.
     * Skipped while climbing (BUILDING plays the all-fours jump) and while standing (idle clip keeps the legs).
     */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> pass, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(pass, snapshots);
        if (pass.getOrDefaultGeckolibData(HorrorRenderData.STATE, 0) == ZombieState.BUILDING.ordinal()) {
            return; // climbing → the all-fours jump clip poses the legs
        }
        float amt = Math.min(1.0f, pass.getOrDefaultGeckolibData(HorrorRenderData.SPEED, 0.0f));
        if (amt < 0.02f) {
            return; // standing still — leave the idle clip's legs alone
        }
        float pos = pass.getOrDefaultGeckolibData(HorrorRenderData.WALK_POS, 0.0f);
        float swing = (float) Math.cos(pos * 0.6662f) * amt * 1.15f; // radians (~66° at full amplitude)
        BakedGeoModel model = pass.model();
        setLegSwing(model, snapshots, "leg_r", swing);
        setLegSwing(model, snapshots, "leg_l", -swing);
    }

    /** Overwrite a leg bone's X rotation (via the frame snapshot) with base + ground-locked swing. */
    private void setLegSwing(BakedGeoModel model, BoneSnapshots snapshots, String bone, float swing) {
        float base = model.getBone(bone).map(b -> b.baseRotX()).orElse(0.0f);
        snapshots.get(bone).ifPresent(s -> s.setRotX(base + swing));
    }
}
