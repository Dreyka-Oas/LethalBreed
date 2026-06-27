package com.dreykaoas.lethalbreed;

import com.dreykaoas.lethalbreed.command.LethalSpawnCommand;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SpawnControl;
import com.dreykaoas.lethalbreed.entity.ZombieRegistry;
import com.dreykaoas.lethalbreed.tick.TickScheduler;
import com.dreykaoas.lethalbreed.util.AiConflictDetector;
import com.dreykaoas.lethalbreed.util.InstalledMods;
import com.dreykaoas.lethalbreed.util.Players;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for LethalBreed.
 *
 * <p>Phase 1 scope (current): bootstrap the runtime spine — register vanilla zombies into a
 * {@link ZombieRegistry}, drive them through a staggered {@link TickScheduler}, and maintain a
 * per-dimension {@link DimensionManager} (spatial grid now, flow field later). All work runs on
 * the server thread for now; off-thread compute (flow field, GPU) arrives in later phases behind
 * the thread-safety discipline described in plan.md.
 */
public final class LethalBreedMod implements ModInitializer {
    public static final String MOD_ID = "lethalbreed";
    public static final Logger LOGGER = LoggerFactory.getLogger("LethalBreed");

    private static final DimensionManager DIMENSIONS = new DimensionManager();
    private static final ZombieRegistry REGISTRY = new ZombieRegistry();
    private static final TickScheduler SCHEDULER = new TickScheduler(REGISTRY, DIMENSIONS);

    /** Turns on the dev perf recap (dev environment only). Set by /lethalspawn. */
    public static volatile boolean perfRecapActive = false;

    public static DimensionManager dimensions() {
        return DIMENSIONS;
    }

    public static ZombieRegistry registry() {
        return REGISTRY;
    }

    @Override
    public void onInitialize() {
        LethalBreedConfig.load();
        InstalledMods.detect();
        AiConflictDetector.checkModList();
        LOGGER.info("[LethalBreed] init — MC 1.21.11, Java 21 (Liberica NIK/GraalVM). Buckets={}, cell={}b",
                LethalBreedConfig.tickBuckets, LethalBreedConfig.spatialCellSize);

        // Register / unregister vanilla zombies as they load into a server level, applying spawn control.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (LethalBreedConfig.blockDrowned && entity.getType() == EntityType.DROWNED) {
                entity.discard();
                return;
            }
            // Track all zombie variants (plain Zombie, Husk, ZombieVillager, ZombifiedPiglin...).
            // Drowned is handled above (discarded when blockDrowned).
            if (entity instanceof Zombie zombie) {
                if (LethalBreedConfig.blockBabyZombies && zombie.isBaby()) {
                    zombie.discard();
                    return;
                }
                if (LethalBreedConfig.stripZombieEquipment) {
                    SpawnControl.stripEquipment(zombie);
                }
                AiConflictDetector.scanZombie(zombie); // once: detect foreign zombie-AI mods
                REGISTRY.add(zombie, world.dimension());
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Zombie) {
                REGISTRY.remove(entity.getId());
            }
        });

        // Loud sounds (block breaks) attract nearby zombies.
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (LethalBreedConfig.soundEnabled && Players.isTargetable(player) && world instanceof ServerLevel sl) {
                WorldAIContext ctx = DIMENSIONS.get(sl.dimension());
                double radius = LethalBreedConfig.soundBaseRadius * LethalBreedConfig.soundLoudMultiplier;
                ctx.soundBus().emit(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius);
            }
        });

        // Our smart zombies descend by digging — cancel fall damage so chasing a fallen target can't kill them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (LethalBreedConfig.preventFallDamage && entity instanceof Zombie && source.is(DamageTypeTags.IS_FALL)
                    && REGISTRY.get(entity.getId()) != null) {
                return false;
            }
            return true;
        });

        // Drive the scheduler once per server tick.
        ServerTickEvents.END_SERVER_TICK.register(SCHEDULER::onServerTick);

        // Dev/load-test command: /lethalspawn <entity> <count> [delaySeconds]
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LethalSpawnCommand.register(dispatcher));

        // Warm the GPU compute backend at boot (when enabled) so its detection line — GPU name or CPU
        // fallback — is logged once at startup instead of lazily on the first flow-field solve.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (LethalBreedConfig.useGpu) {
                com.dreykaoas.lethalbreed.gpu.GpuComputeManager.get().isAvailable();
            }
        });

        // Dev-only headless climb test arena (no-op unless LethalBreedConfig.devClimbTest is on).
        ServerLifecycleEvents.SERVER_STARTED.register(com.dreykaoas.lethalbreed.command.ClimbTest::run);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            REGISTRY.clear();
            DIMENSIONS.clear();
        });
    }
}
