package oas.work.lethalbreed.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class HearingRegistry {
    private static final ConcurrentHashMap<Integer, Vec3> SOUND_TARGETS = new ConcurrentHashMap<>();

    public static void register(int entityId, Vec3 pos) {
        SOUND_TARGETS.put(entityId, pos);
    }

    public static Vec3 get(int entityId) {
        return SOUND_TARGETS.get(entityId);
    }

    public static void clear(int entityId) {
        SOUND_TARGETS.remove(entityId);
    }

    public static void forEach(BiConsumer<? super Integer, ? super Vec3> action) {
        SOUND_TARGETS.forEach(action);
    }

    public static int size() {
        return SOUND_TARGETS.size();
    }
}