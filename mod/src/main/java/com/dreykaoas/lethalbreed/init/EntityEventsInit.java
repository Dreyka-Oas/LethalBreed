package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.entity.SpawnControl;
import com.dreykaoas.lethalbreed.entity.SpawnFilter;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.util.AiConflictDetector;
import com.dreykaoas.lethalbreed.util.Players;
import com.dreykaoas.lethalbreed.util.VanillaTargetingGoals;
import com.dreykaoas.lethalbreed.entity.gecko.HorrorZombie;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/** Registers the entity-driven gameplay hooks: load/unload tracking, sound, damage, and death specials. */
public final class EntityEventsInit {
    private EntityEventsInit() {}

    public static void register(ZombieRegistry registry, DimensionManager dimensions) {
        registerTracking(registry);
        registerSound(dimensions);
        registerDamage(registry);
        registerDeath(registry);
    }

    /** Register / unregister vanilla zombies as they load into a server level, applying spawn control. */
    private static void registerTracking(ZombieRegistry registry) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Phase-gated hostile filtering. In phase 0 (classic) NOTHING hostile spawns; in phases 1..15 only
            // plain Zombie is allowed (every other hostile is culled). Applies only to freshly-added entities,
            // not chunk-reloads (isAddedToLevel true == first add). We gate on the type filter regardless.
            if (WorldSpawnConfig.nightSpawnEnabled && SpawnFilter.shouldCull(entity)) {
                entity.discard();
                return;
            }
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
            // A biped slain by a horror zombie rises as a horror zombie of the same strain — always, not
            // just on the difficulties where vanilla converts villagers. The horde grows.
            if (source.getEntity() instanceof HorrorZombie hz && !(entity instanceof Zombie) && isBiped(entity)) {
                raiseAsHorror(hz, entity, sl);
            }
            // Victory celebration: if a tracked zombie dealt the direct killing blow on non-kin prey, let it
            // celebrate — ZombieMood.tryCelebrate no-ops unless the area is now clear of other prey.
            if (!(entity instanceof Zombie) && source.getEntity() instanceof Zombie killer) {
                com.dreykaoas.lethalbreed.entity.SmartZombie sz = registry.get(killer.getId());
                if (sz != null) {
                    sz.mood().tryCelebrate(sl);
                }
            }
        });
    }

    /** Humanoid "bipeds" that rise when a horror zombie kills them (villagers, illagers, piglins, witches). */
    private static boolean isBiped(Entity e) {
        return e instanceof AbstractVillager || e instanceof AbstractIllager
                || e instanceof AbstractPiglin || e instanceof Witch;
    }

    /** Spawn a horror zombie of the killer's variant on the fallen victim's spot, facing the victim's way. */
    private static void raiseAsHorror(HorrorZombie killer, LivingEntity victim, ServerLevel level) {
        @SuppressWarnings("unchecked")
        EntityType<HorrorZombie> type = (EntityType<HorrorZombie>) killer.getType();
        HorrorZombie risen = type.spawn(level, victim.blockPosition(), EntitySpawnReason.CONVERSION);
        if (risen != null) {
            risen.absSnapTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0f);
        }
    }
}
