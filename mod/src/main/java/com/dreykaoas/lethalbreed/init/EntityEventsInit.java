package com.dreykaoas.lethalbreed.init;

import com.dreykaoas.lethalbreed.config.domain.CombatMoveConfig;
import com.dreykaoas.lethalbreed.config.domain.ContaminationConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.entity.SpawnControl;
import com.dreykaoas.lethalbreed.entity.SpawnFilter;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import com.dreykaoas.lethalbreed.spatial.TargetIndex;
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
        registerTracking(registry, dimensions);
        registerSound(dimensions);
        registerDamage(registry);
        registerDeath(registry);
    }

    /** Register / unregister vanilla zombies as they load into a server level, applying spawn control. */
    private static void registerTracking(ZombieRegistry registry, DimensionManager dimensions) {
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
            // Index anything huntable that isn't a zombie or a player, so target acquisition never has to
            // walk the horde to discard it (see TargetIndex). Registered AFTER the discard branches above,
            // so a culled entity is never indexed in the first place.
            if (TargetIndex.indexable(entity)) {
                dimensions.get(world.dimension()).targetIndex().track((net.minecraft.world.entity.LivingEntity) entity);
            }
            // Track all zombie variants (plain Zombie, Husk, ZombieVillager, ZombifiedPiglin...).
            // Drowned + babies are handled above (discarded when blocked).
            if (entity instanceof Zombie zombie) {
                if (WorldSpawnConfig.stripZombieEquipment) {
                    SpawnControl.stripEquipment(zombie);
                }
                // Vanilla despawns non-persistent MONSTER-category mobs once every player is far enough away
                // (random roll past 32 blocks, unconditional past 128) — that would silently undo the whole
                // LOD/FROZEN system (TickScheduler/SpatialGrid), which exists specifically to keep the zombie
                // population alive-but-cheap while the player is elsewhere, not to have it vanish outright.
                zombie.setPersistenceRequired();
                AiConflictDetector.scanZombie(zombie, world); // once: detect foreign zombie-AI mods
                registry.add(zombie, world.dimension());
                // Deliberately NO "lift NoAI on load" repair here. It was tried and reverted: ENTITY_LOAD
                // fires for freshly-added entities too, not just chunk reloads, so it cancelled a
                // setNoAi(true) applied by the caller a line before addFreshEntity — which is exactly how
                // this project's own dev harness builds its arenas (MechTestArena:64 "stay on the open
                // platform (don't wander into shade/void)"). Measured: with the lift in place the headless
                // `phasescale` case reported 0 zombies and FAILed; without it, PASS (16 tanky, hp 65.5-317.5).
                // Nothing distinguishes one of our old statues from a map-maker's deliberately frozen prop,
                // so the repair cannot be made safe. Audit #2 is prevented at the source instead: the freeze
                // is released on ENTITY_UNLOAD and on SERVER_STOPPING (before saveAllChunks), so no new
                // statue is ever written. A world already carrying one can be repaired by hand with
                //   /data merge entity @e[type=zombie,limit=1] {NoAI:0b}
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            // Prey leaves the index the moment it leaves the level. TargetIndex.refresh() also sweeps dead
            // entries defensively, but relying on that alone is how the SpatialGrid leak (P7-1) happened.
            if (TargetIndex.indexable(entity)) {
                dimensions.get(world.dimension()).targetIndex().forget(entity.getId());
            }
            if (entity instanceof Zombie) {
                SmartZombie sz = registry.remove(entity.getId());
                // Drop it from the spatial grid too, not just the registry. The only other grid-removal
                // path (LodBucketPass.untrack) is driven by iterating the registry, so a zombie removed
                // here was never visited again and its cell slot stayed for the rest of the session —
                // every death and every chunk unload leaked one, pinning entity -> level -> server, and
                // neighbour queries (sound, Hurleur rally, Soigneur heal) kept matching those ghosts.
                if (sz != null) {
                    // Hand vanilla AI back BEFORE the mood object goes away. NoAI is persisted to NBT,
                    // the "we froze it" flag is not — so a dozing zombie unloaded while frozen would be
                    // saved as NoAI=true with nothing left to lift it (audit #2).
                    sz.mood().releaseAiHold();
                    if (sz.pursuit().inGrid()) {
                        dimensions.get(sz.dimension()).spatialGrid().remove(sz);
                    }
                }
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
}
