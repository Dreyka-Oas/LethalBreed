package com.dreykaoas.lethalbreed.tick;

import com.dreykaoas.lethalbreed.dimension.DimensionManager;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Every-tick (not bucket-gated) drives for zombies mid-action. Climbers finish jump-pillars and
 * swimmers drive rise/dive each tick so those impulses beat per-tick goals (FloatGoal lift, etc.).
 * Both share the same drain shape, so it lives in one parameterised {@link #drive} helper.
 */
final class EveryTickPass {
    private final DimensionManager dimensions;

    EveryTickPass(DimensionManager dimensions) {
        this.dimensions = dimensions;
    }

    /** Finish in-progress jump-pillars every tick so the jump+place looks natural. */
    void processClimbers(MinecraftServer server, Set<SmartZombie> climbers) {
        drive(server, climbers, SmartZombie::isClimbing,
                (sz, level) -> sz.climbStep(level, dimensions.get(sz.dimension())));
    }

    /** Drive rise/dive for in-water zombies every tick so the dive impulse beats the FloatGoal lift. */
    void processSwimmers(MinecraftServer server, Set<SmartZombie> swimmers) {
        drive(server, swimmers, SmartZombie::isSwimming,
                (sz, level) -> sz.swimStep(level, dimensions.get(sz.dimension())));
    }

    private void drive(MinecraftServer server, Set<SmartZombie> set,
                       Predicate<SmartZombie> stillActive, BiConsumer<SmartZombie, ServerLevel> step) {
        if (set.isEmpty()) {
            return;
        }
        Iterator<SmartZombie> it = set.iterator();
        while (it.hasNext()) {
            SmartZombie sz = it.next();
            if (!sz.isValid() || !stillActive.test(sz)) {
                it.remove();
                continue;
            }
            ServerLevel level = server.getLevel(sz.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            step.accept(sz, level);
            if (!stillActive.test(sz)) {
                it.remove();
            }
        }
    }
}
