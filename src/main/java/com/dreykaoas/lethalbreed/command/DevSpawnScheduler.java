package com.dreykaoas.lethalbreed.command;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dev-only spawn queue for the {@code /lethalspawn} load-test command. Requests are applied on the
 * server thread when their delay elapses, spawning a ring of entities around a center.
 */
public final class DevSpawnScheduler {
    private DevSpawnScheduler() {}

    private record Request(long dueTick, ResourceKey<Level> dimension, BlockPos center,
                           EntityType<?> type, int count, int radius) {}

    private static final List<Request> PENDING = new ArrayList<>();

    public static synchronized void schedule(long dueTick, ServerLevel level, BlockPos center,
                                             EntityType<?> type, int count, int radius) {
        PENDING.add(new Request(dueTick, level.dimension(), center, type, count, radius));
    }

    /** Called every server tick from the scheduler. */
    public static synchronized void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Request> it = PENDING.iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (now >= r.dueTick) {
                apply(server, r);
                it.remove();
            }
        }
    }

    private static void apply(MinecraftServer server, Request r) {
        ServerLevel level = server.getLevel(r.dimension);
        if (level == null) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int spawned = 0;
        for (int i = 0; i < r.count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double dist = rng.nextDouble() * r.radius;
            int x = r.center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = r.center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos pos = new BlockPos(x, r.center.getY(), z);
            Entity e = r.type.spawn(level, pos, EntitySpawnReason.COMMAND);
            if (e != null) {
                spawned++;
            }
        }
        LethalBreedMod.LOGGER.info("[LethalBreed] /lethalspawn -> spawned {}/{} {} in {}",
                spawned, r.count, BuiltInRegistries.ENTITY_TYPE.getKey(r.type), r.dimension.identifier());
    }
}
