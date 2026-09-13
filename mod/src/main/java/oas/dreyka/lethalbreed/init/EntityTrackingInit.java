package oas.dreyka.lethalbreed.init;

import oas.dreyka.lethalbreed.api.event.ZombieAdoptCallback;
import oas.dreyka.lethalbreed.config.domain.PackConfig;
import oas.dreyka.lethalbreed.config.domain.WorldSpawnConfig;
import oas.dreyka.lethalbreed.dimension.DimensionManager;
import oas.dreyka.lethalbreed.effect.ContaminationManager;
import oas.dreyka.lethalbreed.entity.SmartZombie;
import oas.dreyka.lethalbreed.entity.spawn.SpawnControl;
import oas.dreyka.lethalbreed.entity.spawn.SpawnFilter;
import oas.dreyka.lethalbreed.entity.ZombieRegistry;
import oas.dreyka.lethalbreed.pack.PackAttachment;
import oas.dreyka.lethalbreed.pack.rule.PackJoinRule;
import oas.dreyka.lethalbreed.spatial.TargetIndex;
import oas.dreyka.lethalbreed.util.AiConflictDetector;
import oas.dreyka.lethalbreed.util.target.VanillaTargetingGoals;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Zombie and prey bookkeeping tied to entity load and unload: registering a vanilla zombie as a
 * {@code SmartZombie}, indexing prey, and undoing both when the entity leaves the level.
 *
 * <p>Apart from {@link EntityEventsInit}, which wires the four event families up and nothing else. This one is
 * the largest by far and the only one whose failure mode is a leak, so it is worth reading on its own.
 */
final class EntityTrackingInit {
    private EntityTrackingInit() {}

    /** Register / unregister vanilla zombies as they load into a server level, applying spawn control. */
    static void register(ZombieRegistry registry, DimensionManager dimensions) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> onEntityLoad(registry, dimensions, entity, world));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            SpawnFilter.onEntityUnload(entity); // a dead hostile leaves the seen-UUID set with it
            // Prey leaves the index the moment it leaves the level. TargetIndex.refresh() also sweeps dead
            // entries defensively, but relying on that alone is how the SpatialGrid leak (P7-1) happened.
            if (TargetIndex.indexable(entity)) {
                dimensions.get(world.dimension()).targetIndex().forget(entity.getId());
            }
            if (entity instanceof Zombie) {
                SmartZombie sz = registry.remove(entity.getId());
                // Drop it from the spatial grid too, not just the registry. The only other grid-removal
                // path (LodBucketPass.untrack) is driven by iterating the registry, so a zombie removed
                // here was never visited again and its cell slot stayed for the rest of the session:
                // every death and every chunk unload leaked one, pinning entity -> level -> server, and
                // neighbour queries (sound, Screamer rally, Healer heal) kept matching those ghosts.
                if (sz != null && sz.pursuit().pack().inPack()) {
                    // The chunk beat the materialiser to it: the zombie goes to disk WITH its attachment and
                    // re-joins on the way back, so count it detached and write no ghost. A ghost is how a
                    // member exists twice, and nothing here despawns, so that would be permanent.
                    // A chunk turning HIDDEN ends tracking from PersistentEntitySectionManager.updateChunkStatus,
                    // which runs BEFORE anything sets a removal reason, so a null reason IS the chunk unload and
                    // the only case that comes back. Reading null as "not saved" sent it to leave(), which drops
                    // the member uncounted and strips the attachment before the section is written: the pack
                    // dissolved under a zombie merely on disk, and that zombie came back loose for good.
                    var reason = entity.getRemovalReason();
                    if (reason == null || reason.shouldSave()) {
                        dimensions.get(sz.dimension()).packManager().detach(sz);
                    } else {
                        dimensions.get(sz.dimension()).packManager().leave(sz);
                    }
                }
                if (sz != null) {
                    // Hand vanilla AI back BEFORE the mood object goes away. NoAI is persisted to NBT,
                    // the "we froze it" flag is not, so a dozing zombie unloaded while frozen would be
                    // saved as NoAI=true with nothing left to lift it.
                    sz.mood().releaseAiHold();
                    if (sz.pursuit().inGrid()) {
                        dimensions.get(sz.dimension()).spatialGrid().remove(sz);
                    }
                }
                VanillaTargetingGoals.drop(entity.getId()); // release any stripped-goal snapshot
            }
        });
    }

    /** One ENTITY_LOAD firing: phase-gated spawn filtering, blocked-variant discards, contamination
     *  re-tracking, target indexing, zombie registration and pack re-join. Moved out of the registration
     *  lambda verbatim, so the reasoning on each branch predates the extraction. */
    private static void onEntityLoad(ZombieRegistry registry, DimensionManager dimensions,
                                      Entity entity, ServerLevel world) {
        // Phase-gated hostile filtering. In phase 0 (classic) NOTHING hostile spawns; in phases 1..15 only
        // plain Zombie is allowed (every other hostile is culled). shouldCullOnLoad only ever answers true
        // the first time it sees a given entity, so a mob that already survived its spawn is never discarded
        // again just because its chunk reloaded, or because the phase/filter changed in the meantime.
        if (WorldSpawnConfig.nightSpawnEnabled && SpawnFilter.shouldCullOnLoad(entity)) {
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
        // Every zombie variant (Husk, ZombieVillager, ZombifiedPiglin...); drowned and babies left above.
        if (entity instanceof Zombie zombie) {
            // An addon with a zombie subclass of its own says so here, before anything is done to it: the
            // equipment strip below, the goal strip, the scheduler and the pack all follow from adoption.
            if (!ZombieAdoptCallback.EVENT.invoker().allowAdopt(zombie, true)) {
                return;
            }
            if (WorldSpawnConfig.stripZombieEquipment) {
                SpawnControl.stripEquipment(zombie);
            }
            // Vanilla despawns non-persistent MONSTER-category mobs once every player is far enough away
            // (random roll past 32 blocks, unconditional past 128), which would silently undo the whole
            // LOD/FROZEN system (TickScheduler/SpatialGrid), which exists specifically to keep the zombie
            // population alive-but-cheap while the player is elsewhere, not to have it vanish outright.
            zombie.setPersistenceRequired();
            AiConflictDetector.scanZombie(zombie, world); // once: detect foreign zombie-AI mods
            SmartZombie sz = registry.add(zombie, world.dimension());
            // A member that went to disk carrying its pack attachment (see EntityEventsInit's
            // ENTITY_UNLOAD handler, and PackAttachment's own javadoc) re-joins here, on the way back.
            // Without this, the attachment is written but never read, so a straggler never returns to
            // its pack: the pack's `detached` count never comes down, and it can outlive every real
            // member it ever had.
            if (PackConfig.packEnabled) {
                long packId = zombie.getAttachedOrElse(PackAttachment.PACK, PackJoinRule.NO_PACK);
                if (packId != PackJoinRule.NO_PACK) {
                    dimensions.get(world.dimension()).packManager().rejoin(sz, packId);
                }
            }
            // Deliberately NO "lift NoAI on load" repair here: ENTITY_LOAD fires for freshly-added
            // entities too, so it cancels a setNoAi(true) applied a line before addFreshEntity, which is
            // how this project's own dev harness builds its arenas (MechTestArena:64). Measured: with the
            // lift the headless `phasescale` case reported 0 zombies and FAILed; without it, PASS. Nothing
            // distinguishes one of our own statues from a map-maker's deliberately frozen prop, so the
            // repair cannot be made safe. The leak is closed on the
            // WRITE instead, by ZombieNoAiNotPersistedMixin: the releases on ENTITY_UNLOAD and
            // SERVER_STOPPING only fix the live entity and are too late for the save. An old statue in an
            // existing world is repaired by hand with
            //   /data merge entity @e[type=zombie,limit=1] {NoAI:0b}
        }
    }
}
