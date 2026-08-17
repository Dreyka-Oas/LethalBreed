package com.dreykaoas.lethalbreed.mixin;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.util.Players;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zombie hearing for player item drops. When a player tosses (Q / Ctrl-Q) or otherwise drops an item into the
 * world, it funnels through {@link ServerPlayer#drop(net.minecraft.world.item.ItemStack, boolean, boolean)} —
 * the terminal override that spawns the {@link ItemEntity}. We tap it at RETURN and feed a normal-volume sound
 * (same tier as a footstep or a block place, NOT a loud break) into the mod's sound bus at the dropped item's
 * position, so nearby zombies pursue the spot via short-term memory — exactly like the block-place hook in
 * {@code WorldSoundEventMixin}. A null return means nothing actually left the hand (empty slot). Creative and
 * spectator players make no noise (mirrors the footstep and block-break rules).
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerItemDropSoundMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void lethalbreed$emitDropSound(CallbackInfoReturnable<ItemEntity> cir) {
        if (!TargetingConfig.soundEnabled) {
            return;
        }
        ItemEntity dropped = cir.getReturnValue();
        if (dropped == null) {
            return; // nothing actually dropped (empty slot / cancelled)
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!Players.isTargetable(self)) {
            return; // creative/spectator make no noise (config)
        }
        if (!(self.level() instanceof ServerLevel level)) {
            return; // defensive; a ServerPlayer's level is always a ServerLevel
        }
        WorldAiContext ctx = GameState.DIMENSIONS.get(level.dimension());
        ctx.soundBus().emit(dropped.getX(), dropped.getY(), dropped.getZ(), TargetingConfig.soundBaseRadius);
    }
}
