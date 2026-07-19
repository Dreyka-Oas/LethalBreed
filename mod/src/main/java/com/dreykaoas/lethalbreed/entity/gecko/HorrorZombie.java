package com.dreykaoas.lethalbreed.entity.gecko;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A brand-new, GeckoLib-animated horror zombie: a distinct {@link EntityType} (NOT a re-skin of the vanilla
 * zombie), rendered from a hand-authored {@code .geo.json} model with grotesque, broken-bodied animations
 * (limp/drag walk, unhinging-jaw lunge, random convulsions, collapse-on-death).
 *
 * <p>It subclasses {@link Zombie} on purpose: it inherits the full vanilla zombie AI (target, break doors,
 * burn in day...) AND flows for free through the mod's existing {@code instanceof Zombie} hooks
 * ({@code EntityEventsInit} tracking, {@code SmartZombie} brain/mood/pursuit, sound attraction). The one place
 * that must know about it is {@code SpawnFilter}, which whitelists it so the phase-gated cull doesn't discard
 * it. Its bespoke GeckoLib render pipeline is completely separate from the vanilla-zombie model mixins
 * (belly swell etc.), which therefore never apply here — by design.
 */
public class HorrorZombie extends Zombie implements GeoEntity {
    // Animation names — must match the keys in geckolib/animations/entity/horror_zombie.animation.json.
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SPASM = RawAnimation.begin().thenPlay("spasm");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HorrorZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    /** Tankier and a touch faster than a plain zombie, and it never calls in reinforcements. */
    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<HorrorZombie>("main", 5, state -> {
                    // While a one-shot triggered clip (attack / spasm / death) is playing, let it finish
                    // instead of snapping back to the locomotion state on the very next frame.
                    if (state.controller().isPlayingTriggeredAnimation()) {
                        return PlayState.CONTINUE;
                    }
                    // Use the vanilla limb-swing state for "is walking": its threshold (~1e-5) triggers even
                    // for the slow variants, which GeckoLib's own isMoving() misses — those were sliding
                    // (playing idle) instead of walking. Fall back to isMoving() for safety.
                    boolean moving = state.animatable().walkAnimation.isMoving() || state.isMoving();
                    return state.setAndContinue(moving ? WALK : IDLE);
                })
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("spasm", SPASM)
                .triggerableAnim("death", DEATH));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            triggerAnim("main", "attack"); // server-side; GeckoLib syncs the trigger to tracking clients
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Occasional full-body convulsion on top of idle/walk (server-driven, ~ once every 9s per zombie).
        if (!level().isClientSide() && getRandom().nextInt(180) == 0) {
            triggerAnim("main", "spasm");
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide()) {
            triggerAnim("main", "death");
        }
        super.die(cause);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
