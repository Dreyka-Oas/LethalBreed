package oas.dreyka.lethalbreed.mixin.plague;

import oas.dreyka.lethalbreed.effect.ContaminationManager;
import oas.dreyka.lethalbreed.effect.contamination.PlagueRegen;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The plague fights the body's recovery: while a player is symptomatic, natural food-based health regeneration
 * randomly stutters. Each heal tick has a chance to be skipped, and that chance climbs with the plague level, so a
 * heavily-infected player at full hunger still heals, just far less reliably. The skip is random (never a hard
 * block) and capped below 100%, so recovery is always technically possible, only slow and erratic at high levels.
 *
 * <p>Both of {@code FoodData.tick}'s regen branches (fast saturation heal and slow food-level heal) route through
 * the same {@code ServerPlayer.heal(float)} call, so redirecting that one call covers both.
 */
@Mixin(net.minecraft.world.food.FoodData.class)
public class PlagueBlocksRegenMixin {

    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void lethalbreed$plagueStutterRegen(ServerPlayer player, float amount) {
        float skipChance = PlagueRegen.skipChance(ContaminationManager.plagueLevel(player));
        if (skipChance > 0.0f && player.getRandom().nextFloat() < skipChance) {
            return; // this heal tick is lost to the sickness
        }
        player.heal(amount);
    }
}
