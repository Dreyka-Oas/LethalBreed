package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.mood.sleep.DozePose;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.util.Players;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Registers the entity-driven gameplay hooks: load/unload tracking, sound, damage, and death specials. */
public final class EntityEventsInit {
    private EntityEventsInit() {}

    public static void register(ZombieRegistry registry, DimensionManager dimensions) {
        EntityTrackingInit.register(registry, dimensions);
        registerSound(dimensions);
        registerDamage(registry);
        registerDeath(registry);
    }

    /** How far above a broken block a frozen sleeper may be and still be dropped by it. Two blocks covers
     *  a zombie standing on the block itself; past that the block was not what held it up. */
    private static final double DROP_RADIUS = 2.0;

    /** Loud sounds (block breaks) attract nearby zombies, and a block broken under a sleeper drops it. */
    private static void registerSound(DimensionManager dimensions) {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel sl)) {
                return;
            }
            WorldAiContext ctx = dimensions.get(sl.dimension());
            dropSleepers(ctx, pos);
            if (TargetingConfig.soundEnabled && Players.isTargetable(player)) {
                double radius = TargetingConfig.soundBaseRadius * TargetingConfig.soundLoudMultiplier;
                ctx.soundBus().emit(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
            }
        });
    }

    /**
     * Hand vanilla AI back to any sleeper the broken block was holding up, now rather than on its next
     * activation.
     *
     * <p>{@code DozePose} already refuses to keep a freeze on a zombie with nothing under it, but it only
     * gets asked once per activation, five ticks apart at the shipped bucket count. Mine the floor from under
     * a sleeper and it hangs there for a quarter of a second before gravity is handed back, which is short
     * but plainly wrong to watch. Doing it from the break itself costs nothing per tick: this runs when a
     * player breaks a block and never otherwise.
     */
    private static void dropSleepers(WorldAiContext ctx, BlockPos pos) {
        for (SmartZombie sz : ctx.spatialGrid().queryRadius(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DROP_RADIUS)) {
            if (sz.mood().holdsAiFreeze() && !DozePose.supported(sz.entity())) {
                sz.mood().releaseAiHold();
            }
        }
    }

    /** Cancel fall damage for our diggers, and spread Super Contamination on zombie-to-victim hits. */
    private static void registerDamage(ZombieRegistry registry) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (CombatMoveConfig.preventFallDamage && entity instanceof Zombie && source.is(DamageTypeTags.IS_FALL)
                    && registry.get(entity.getId()) != null) {
                return false;
            }
            // Super Contamination spreads when a zombie hits a non-zombie living entity (chance scales w/ phase).
            if (ContaminationConfig.contaminationEnabled && source.getEntity() instanceof Zombie
                    && !(entity instanceof Zombie)) {
                int phase = PhaseManager.current();
                double chance = Math.min(ContaminationConfig.contamMaxChance,
                        ContaminationConfig.contamBaseChance + phase * ContaminationConfig.contamPhaseScale);
                if (entity.getRandom().nextDouble() < chance) {
                    ContaminationManager.contaminate(entity);
                }
            }
            return true;
        });
    }

    /** Splitter (and other DEATH specials) act when the zombie dies; contaminated victims clear their plague
     *  state; a zombie that landed a direct kill may celebrate a cleared area. */
    private static void registerDeath(ZombieRegistry registry) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.level() instanceof ServerLevel sl)) {
                return;
            }
            if (entity instanceof Zombie z) {
                SpecialBehavior.onDeath(z, sl);
            }
            ContaminationManager.onDeath(entity, sl);
            // Victory celebration: if a tracked zombie dealt the direct killing blow on non-kin prey, let it
            // celebrate: ZombieMood.tryCelebrate no-ops unless the area is now clear of other prey.
            if (!(entity instanceof Zombie) && source.getEntity() instanceof Zombie killer) {
                com.dreykaoas.lethalbreed.entity.SmartZombie sz = registry.get(killer.getId());
                if (sz != null) {
                    sz.mood().tryCelebrate(sl);
                }
            }
        });
    }
}
