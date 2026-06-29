package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.entity.SpawnControl;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.util.AiConflictDetector;
import com.dreykaoas.lethalbreed.util.Players;
import com.dreykaoas.lethalbreed.util.VanillaTargetingGoals;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Registers the entity-driven gameplay hooks: load/unload tracking, sound, damage, and death specials. */
public final class EntityEventsInit {
    private EntityEventsInit() {}

    public static void register(ZombieRegistry registry, DimensionManager dimensions) {
        registerTracking(registry);
        registerSound(dimensions);
        registerDamage(registry);
        registerDeath();
    }

    /** Register / unregister vanilla zombies as they load into a server level, applying spawn control. */
    private static void registerTracking(ZombieRegistry registry) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Discard blocked drowned/babies BEFORE tracking, so we don't contamination-track an entity we
            // then toss this same load.
            if (WorldSpawnConfig.blockDrowned && entity.getType() == EntityType.DROWNED) {
                entity.discard();
                return;
            }
            if (WorldSpawnConfig.blockBabyZombies && entity instanceof Zombie zb && zb.isBaby()) {
                zb.discard();
                return;
            }
            ContaminationManager.onLoad(entity); // re-track contaminated
            // Track all zombie variants (plain Zombie, Husk, ZombieVillager, ZombifiedPiglin...).
            // Drowned + babies are handled above (discarded when blocked).
            if (entity instanceof Zombie zombie) {
                if (WorldSpawnConfig.stripZombieEquipment) {
                    SpawnControl.stripEquipment(zombie);
                }
                AiConflictDetector.scanZombie(zombie); // once: detect foreign zombie-AI mods
                registry.add(zombie, world.dimension());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Zombie) {
                registry.remove(entity.getId());
                VanillaTargetingGoals.drop(entity.getId()); // release any stripped-goal snapshot
            }
        });
    }

    /** Loud sounds (block breaks) attract nearby zombies. */
    private static void registerSound(DimensionManager dimensions) {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (TargetingConfig.soundEnabled && Players.isTargetable(player) && world instanceof ServerLevel sl) {
                WorldAIContext ctx = dimensions.get(sl.dimension());
                double radius = TargetingConfig.soundBaseRadius * TargetingConfig.soundLoudMultiplier;
                ctx.soundBus().emit(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
            }
        });
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

    /** Splitter (and other DEATH specials) act when the zombie dies; contaminated victims zombify. */
    private static void registerDeath() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel sl) {
                if (entity instanceof Zombie z) {
                    SpecialBehavior.onDeath(z, sl);
                }
                ContaminationManager.onDeath(entity, sl);
            }
        });
    }
}
