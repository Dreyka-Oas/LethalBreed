package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zombie hearing for world interactions. Vanilla emits a {@link GameEvent} (the Sculk-sensor system) for every
 * block interaction; we tap that single dispatch on {@link ServerLevel} and feed the loud ones into the mod's
 * sound bus, exactly like the player-break hook in {@code EntityEventsInit#registerSound}. A zombie then pursues
 * the source position via short-term memory.
 *
 * <p>Covered events: {@code BLOCK_PLACE} (placing a block), {@code BLOCK_OPEN}/{@code BLOCK_CLOSE} (chests,
 * doors, trapdoors, shulker boxes...). Block breaking already routes through {@code PlayerBlockBreakEvents},
 * so {@code BLOCK_DESTROY} is intentionally NOT re-emitted here to avoid a double sound. A zombie source is
 * ignored so zombies don't hear their own digging/door-bashing (mirrors {@code SoundEventBus#tickEntities}).
 */
@Mixin(ServerLevel.class)
public abstract class WorldSoundEventMixin {

    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void lethalbreed$emitInteractionSound(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context,
                                                  CallbackInfo ci) {
        if (!TargetingConfig.soundEnabled) {
            return;
        }
        GameEvent ev = event.value();
        if (ev != GameEvent.BLOCK_PLACE.value()
                && ev != GameEvent.BLOCK_OPEN.value()
                && ev != GameEvent.BLOCK_CLOSE.value()) {
            return; // only these interactions make a noise zombies chase
        }
        Entity source = context.sourceEntity();
        if (source instanceof Zombie) {
            return; // zombies never hunt their own kind's noise
        }
        ServerLevel level = (ServerLevel) (Object) this;
        WorldAIContext ctx = GameState.DIMENSIONS.get(level.dimension());
        // Interactions are a normal-volume sound (same tier as footsteps), not a loud break.
        ctx.soundBus().emit(pos.x, pos.y, pos.z, TargetingConfig.soundBaseRadius);
    }
}
