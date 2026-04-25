package oas.work.lethalbreed.ai;

import oas.work.lethalbreed.config.ModConfig;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

public class TemporaryBlockTracker {
    private static final Map<TrackedBlock, Long> TRACKED_BLOCKS = new ConcurrentHashMap<>();

    private static class TrackedBlock {
        final BlockPos pos;
        final int worldDimensionId;

        TrackedBlock(BlockPos pos, int worldDimensionId) {
            this.pos = pos;
            this.worldDimensionId = worldDimensionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TrackedBlock that)) return false;
            return pos.equals(that.pos) && worldDimensionId == that.worldDimensionId;
        }

        @Override
        public int hashCode() {
            return 31 * pos.hashCode() + worldDimensionId;
        }
    }

    public static void track(BlockPos pos, int worldDimensionId) {
        if (!ModConfig.INSTANCE.movement.temporaryBlocks.enabled) return;
        TRACKED_BLOCKS.put(new TrackedBlock(pos, worldDimensionId), System.currentTimeMillis());
    }

    public static void onTick(ServerLevel world) {
        if (!ModConfig.INSTANCE.movement.temporaryBlocks.enabled) return;
        if (TRACKED_BLOCKS.isEmpty()) return;

        int dimensionId = world.dimensionType().hashCode();
        long currentTime = System.currentTimeMillis();
        long decayMs = ModConfig.INSTANCE.movement.temporaryBlocks.decayTicks * 50L;

        Iterator<Map.Entry<TrackedBlock, Long>> it = TRACKED_BLOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TrackedBlock, Long> entry = it.next();
            TrackedBlock tracked = entry.getKey();

            if (tracked.worldDimensionId != dimensionId) continue;

            if (currentTime - entry.getValue() > decayMs) {
                BlockPos pos = tracked.pos;
                BlockState state = world.getBlockState(pos);
                if (!state.isAir()) {
                    world.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
                it.remove();
            }
        }
    }

    public static void clear() {
        TRACKED_BLOCKS.clear();
    }
}






