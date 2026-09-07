package oas.dreyka.lethalbreed.mixin;

import oas.dreyka.lethalbreed.util.MovementLock;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a {@link MovementLock} hold alive across vanilla's periodic reset of the control flags.
 *
 * <p>{@code Mob.tick} calls {@code updateControlFlags} every fifth tick, and that method does not toggle
 * anything conditionally: it writes MOVE, JUMP and LOOK back from scratch out of "am I ridden" and "am I in a
 * boat". A mob that neither is gets MOVE handed back, whoever took it away and for whatever reason. So a lock
 * set once lasts five ticks at the outside, and the mob is free again for the rest of its stay.
 *
 * <p>What that looked like from outside: an armed Bomber must stand still for its whole fuse, and it did,
 * except that one run in three it walked a third of a block. The wander goal starts on a
 * {@code nextInt(120)} roll, so whether it caught one of those windows was luck, which read as a flaky test
 * rig rather than as a lock that never held.
 *
 * <p>TAIL rather than HEAD or a cancel: vanilla still gets to decide all three flags normally, including the
 * two this mod has no opinion about, and only MOVE is taken back afterwards. The injection lands before the
 * next {@code goalSelector.tick}, so no goal ever sees the gap.
 */
@Mixin(Mob.class)
public abstract class MobControlFlagsMixin {

    @Inject(method = "updateControlFlags", at = @At("TAIL"))
    private void lethalbreed$keepMovementHeld(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (MovementLock.held(self)) {
            MovementLock.hold(self);
        }
    }
}
