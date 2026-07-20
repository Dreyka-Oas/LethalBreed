package com.dreykaoas.lethalbreed.entity.gecko;

import com.dreykaoas.lethalbreed.entity.HorrorModels;
import com.dreykaoas.lethalbreed.entity.ZombieState;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The GeckoLib "replaced entity" stand-in for {@code minecraft:zombie}: a single shared animatable that lets a
 * vanilla zombie be rendered with one of our hand-authored {@code .geo.json} models instead of the vanilla
 * model — WITHOUT introducing a new entity type. Which model an individual zombie wears is decided per-frame
 * from the {@link HorrorRenderData#MODEL} render-state ticket (fed by the renderer from the entity's
 * {@link com.dreykaoas.lethalbreed.entity.HorrorModelAttachment}); this one controller drives every model's
 * locomotion by picking that model's namespaced clips.
 *
 * <p>Clips live in one shared {@code horror.animation.json}, named {@code <id>_idle/walk/attack/spasm/death}.
 * One-shots (attack/spasm/death) are registered as triggerable per model and fired from the server via
 * {@link #triggerAnim(net.minecraft.world.entity.Entity, String, String)} using {@link #clip(int, String)}.
 */
public final class HorrorReplacedZombie implements GeoReplacedEntity {
    public static final HorrorReplacedZombie INSTANCE = new HorrorReplacedZombie();
    /** Locomotion layer (legs/body): idle/walk/jump, never interrupted. */
    public static final String LOCO = "loco";
    /** Action layer (arms/jaw): attack/hurt one-shots played OVER locomotion. Server triggerAnim targets this. */
    public static final String CONTROLLER = "action";

    private static final RawAnimation[] IDLE = new RawAnimation[HorrorModels.COUNT];
    private static final RawAnimation[] WALK = new RawAnimation[HorrorModels.COUNT];
    private static final RawAnimation[] JUMP = new RawAnimation[HorrorModels.COUNT];
    static {
        for (int i = 0; i < HorrorModels.COUNT; i++) {
            String id = HorrorModels.IDS[i];
            IDLE[i] = RawAnimation.begin().thenLoop(id + "_idle");
            WALK[i] = RawAnimation.begin().thenLoop(id + "_walk");
            JUMP[i] = RawAnimation.begin().thenPlayAndHold(id + "_jump");
        }
    }

    private static final ZombieState[] STATES = ZombieState.values();

    /** The server states during which the zombie is translating on the ground → play WALK. */
    private static boolean isMovingState(ZombieState s) {
        return switch (s) {
            case PURSUING_PLAYER, PURSUING_SOUND, FLEEING, SHELTERING, DESCENDING -> true;
            default -> false; // IDLE, BREAKING (mining in place), CELEBRATING → idle; BUILDING handled as JUMP
        };
    }

    /** Triggerable one-shot clip name for a model index + action, e.g. {@code ecorche_attack}. */
    public static String clip(int model, String action) {
        int m = (model < 0 || model >= HorrorModels.COUNT) ? 0 : model;
        return HorrorModels.IDS[m] + "_" + action;
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private HorrorReplacedZombie() {}

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.ZOMBIE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // LOCOMOTION layer — legs/body/head. Runs EVERY frame and is NEVER interrupted by an action, so the
        // legs keep walking even while the zombie is mid-attack (walk + attack happen at once).
        controllers.add(new AnimationController<>(LOCO, 5, test -> {
            int m = test.getDataOrDefault(HorrorRenderData.MODEL, 0);
            if (m < 0 || m >= HorrorModels.COUNT) {
                m = 0;
            }
            int stOrd = test.getDataOrDefault(HorrorRenderData.STATE, 0);
            ZombieState st = (stOrd >= 0 && stOrd < STATES.length) ? STATES[stOrd] : ZombieState.IDLE;
            // The on-all-fours leap pose is ONLY for a genuine vertical CLIMB (server state BUILDING/pillaring).
            // Chase leaps/hops are NOT given this held pose — they keep the walk cycle so the legs move with the
            // hop instead of freezing into a static pose that reads as a slide while the body glides forward.
            if (st == ZombieState.BUILDING) {
                test.setControllerSpeed(1.0f);
                return test.setAndContinue(JUMP[m]);
            }
            // WALK whenever actually translating OR in a moving state (cadence tracks real speed); else idle.
            if (test.getDataOrDefault(HorrorRenderData.MOVING, false) || isMovingState(st)) {
                float ws = test.getDataOrDefault(HorrorRenderData.SPEED, 0.3f);
                test.setControllerSpeed(ws > 0.02f ? Math.max(0.5f, Math.min(2.5f, ws / 0.3f)) : 1.0f);
                return test.setAndContinue(WALK[m]);
            }
            test.setControllerSpeed(1.0f);
            return test.setAndContinue(IDLE[m]);
        }));

        // ACTION layer — arms/jaw only (leg bones were stripped from these clips), played on TOP of locomotion.
        // Plays a triggered attack/hurt to its end, then STOPs, releasing its bones back to the walking legs.
        AnimationController<HorrorReplacedZombie> action = new AnimationController<>(CONTROLLER, 3, test ->
                test.controller().isPlayingTriggeredAnimation() ? PlayState.CONTINUE : PlayState.STOP);
        for (int i = 0; i < HorrorModels.COUNT; i++) {
            String id = HorrorModels.IDS[i];
            action.triggerableAnim(id + "_attack", RawAnimation.begin().thenPlay(id + "_attack"))
                  .triggerableAnim(id + "_hurt", RawAnimation.begin().thenPlay(id + "_hurt"));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
