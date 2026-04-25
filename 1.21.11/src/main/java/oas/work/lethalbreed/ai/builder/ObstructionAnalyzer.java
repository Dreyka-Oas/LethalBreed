package oas.work.lethalbreed.ai.builder;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.HitResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ObstructionAnalyzer {
    private static final ConcurrentHashMap<BlockPos, CachedBlock> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TICK_DURATION = 1;
    private static final int MAX_CACHE_SIZE = 10000;
    private static final AtomicLong currentTick = new AtomicLong(0);

    private static class CachedBlock {
        final boolean blocking;
        final long tick;

        CachedBlock(boolean blocking, long tick) {
            this.blocking = blocking;
            this.tick = tick;
        }
    }

    public static void onTick(long tick) {
        currentTick.set(tick);
        if (tick % 100 == 0) { // Periodic cleanup
            long minTick = tick - CACHE_TICK_DURATION;
            BLOCK_CACHE.entrySet().removeIf(e -> e.getValue().tick < minTick);
        }
    }

    public static BlockPos getObstruction(Level world, BlockPos base, Zombie z) {
        double zombieHeight = z.getBbHeight();
        double scale = 1.0; // Standard scale for 1.20.2
        for (int i = 0; i <= Math.ceil(zombieHeight); i++) {
            BlockPos checkPos = base.above(i);
            if (isBlocking(world, checkPos)) return checkPos;
        }
        return null;
    }

    public static BlockPos getStuckBlock(Level world, Zombie z, Vec3 targetPos) {
        return SectorScanner.find(world, z, targetPos);
    }

    public static BlockPos getHorizontal(Level world, Zombie z, Vec3 targetPos) {
        if (targetPos == null) return null;
        double dx = targetPos.x - z.getX(), dz = targetPos.z - z.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return null;

        Vec3 dir = new Vec3(dx / len * 1.5, 0, dz / len * 1.5);
        double zombieHeight = z.getBbHeight();
        for (double h = 0.0; h <= zombieHeight; h += 0.5) {
            Vec3 start = new Vec3(z.getX(), z.getY() + h, z.getZ());
            HitResult res = world.clip(new ClipContext(start, start.add(dir), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, z));
            if (res.getType() == HitResult.Type.BLOCK && isBlocking(world, ((BlockHitResult)res).getBlockPos())) return ((BlockHitResult)res).getBlockPos();
        }
        return null;
    }

    public static boolean isBlocking(Level world, BlockPos pos) {
        if (world == null || pos == null) return false;
        long tick = currentTick.get();
        CachedBlock cached = BLOCK_CACHE.get(pos);

        if (cached != null && cached.tick >= tick) {
            return cached.blocking;
        }

        BlockState state = world.getBlockState(pos);
        boolean blocking = !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();

        if (BLOCK_CACHE.size() < MAX_CACHE_SIZE) {
            BLOCK_CACHE.put(pos, new CachedBlock(blocking, tick));
        }

        return blocking;
    }
}