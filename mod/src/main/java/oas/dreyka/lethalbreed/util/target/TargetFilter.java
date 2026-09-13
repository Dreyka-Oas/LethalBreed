package oas.dreyka.lethalbreed.util.target;

import oas.dreyka.lethalbreed.api.event.TargetCandidateCallback;
import oas.dreyka.lethalbreed.config.domain.TargetingConfig;
import oas.dreyka.lethalbreed.util.Players;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * What a zombie is allowed to hunt at all, before distance, sight or stickiness are considered.
 *
 * <p>Kept apart from {@link TargetSelector} because it is the one rule set a reader comes looking for
 * ("why does it ignore X?") and because it is called once per candidate in the broad phase, so it must
 * stay allocation-free and cheap.
 */
public final class TargetFilter {
    private TargetFilter() {}

    /**
     * The shipped answer, then whatever the listeners make of it.
     *
     * <p>A dead or removed candidate never reaches them: it is not a rule anybody could sensibly want to
     * override, and it is the one case the scan hits most often.
     */
    public static boolean isValid(Mob self, LivingEntity e) {
        if (e == self || !e.isAlive() || e.isRemoved()) {
            return false;
        }
        return TargetCandidateCallback.EVENT.invoker().isValidPrey(self, e, byRules(e));
    }

    private static boolean byRules(LivingEntity e) {
        if (e instanceof Zombie) {
            return false; // own kind (zombie / husk / zombie villager / zombified piglin)
        }
        if (e instanceof EnderDragon || e instanceof WitherBoss) {
            return false; // bosses
        }
        if (e.getBbHeight() > TargetingConfig.targetMaxPreyHeight) {
            return false; // too tall (giants, large modded mobs): never attack these
        }
        if (e instanceof ArmorStand) {
            return false; // not a creature
        }
        if (e instanceof Player p) {
            return Players.isTargetable(p); // creative and spectator excluded
        }
        if (TargetingConfig.targetPlayersOnly) {
            return false; // players-only mode: reject every non-player living entity
        }
        return !e.isSpectator();
    }
}
