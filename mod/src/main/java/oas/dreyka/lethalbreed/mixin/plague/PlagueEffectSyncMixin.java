package oas.dreyka.lethalbreed.mixin.plague;

import oas.dreyka.lethalbreed.effect.LethalBreedEffects;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * Tells the clients watching an infected MOB that it carries the plague.
 *
 * <p>Vanilla only ever mentions a mob's effects twice: once to a client that starts tracking the entity
 * ({@code ServerEntity}'s pairing data) and, live, to whoever is riding it
 * ({@code LivingEntity.sendEffectToPassengers}). Nothing reaches a client already watching. So a mob
 * infected in front of a player stayed clean on every screen, and the render-side membership check read a
 * client copy nobody had told: the infected enderman kept its vanilla purple swirl instead of the black
 * dust, with no log and no crash, on every version that ever shipped this.
 *
 * <p>Only this mod's own effect is mirrored, so no vanilla effect gains a packet it never had, and players
 * are skipped because their own effects already sync.
 */
@Mixin(LivingEntity.class)
public abstract class PlagueEffectSyncMixin {

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void lethalbreed$sendAddedToTrackers(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        lethalbreed$sendToTrackers(instance);
    }

    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void lethalbreed$sendUpdatedToTrackers(MobEffectInstance instance, boolean rebuildAttributes,
                                                   Entity source, CallbackInfo ci) {
        lethalbreed$sendToTrackers(instance);
    }

    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void lethalbreed$sendRemovedToTrackers(Collection<MobEffectInstance> removed, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!lethalbreed$mirrors(self)) {
            return;
        }
        for (MobEffectInstance instance : removed) {
            if (lethalbreed$isPlague(instance)) {
                for (ServerPlayer player : PlayerLookup.tracking(self)) {
                    player.connection.send(
                            new ClientboundRemoveMobEffectPacket(self.getId(), instance.getEffect()));
                }
            }
        }
    }

    private void lethalbreed$sendToTrackers(MobEffectInstance instance) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!lethalbreed$mirrors(self) || !lethalbreed$isPlague(instance)) {
            return;
        }
        for (ServerPlayer player : PlayerLookup.tracking(self)) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(self.getId(), instance, false));
        }
    }

    private static boolean lethalbreed$mirrors(LivingEntity entity) {
        return !entity.level().isClientSide() && !(entity instanceof Player);
    }

    /** Compared on the effect itself, not on the holder: two holders of one effect are not the same object. */
    private static boolean lethalbreed$isPlague(MobEffectInstance instance) {
        return LethalBreedEffects.SUPER_CONTAMINATION != null
                && instance.getEffect().value() == LethalBreedEffects.SUPER_CONTAMINATION.value();
    }
}
