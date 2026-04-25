package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ObstructionAnalyzer {
    private static final ConcurrentHashMap<BlockPos, CachedBlock> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TICK_DURATION = 1;
    private static final int MAX_CACHE_SIZE = 10000;
    private static final AtomicLong currentTick = new AtomicLong(0);

    private static class CachedBlock {
        final BlockState state;
        final long tick;
        final boolean blocking;

        CachedBlock(BlockState state, long tick) {
            this.state = state;
            this.tick = tick;
            this.blocking = !state.isAir() && (state.shouldBlockVision(null, BlockPos.ORIGIN) || !state.getCollisionShape(null, BlockPos.ORIGIN).isEmpty() || state.isFullCube(null, BlockPos.ORIGIN));
        }
    }

    public static void onTick(long tick) {
        currentTick.set(tick);
        if (BLOCK_CACHE.size() > MAX_CACHE_SIZE) {
            long minTick = tick - CACHE_TICK_DURATION * 2;
            BLOCK_CACHE.entrySet().removeIf(e -> e.getValue().tick < minTick);
        }
    }

    public static BlockPos getObstruction(World world, BlockPos base, ZombieEntity z) {
        double zombieHeight = z.getHeight();
        double scale = z.getScale();
        for (int i = 0; i <= Math.ceil(zombieHeight + scale); i++) {
            BlockPos checkPos = base.up(i);
            if (isBlocking(world, checkPos)) return checkPos;
        }
        return null;
    }

    public static BlockPos getStuckBlock(World world, ZombieEntity z, Vec3d targetPos) {
        return SectorScanner.find(world, z, targetPos);
    }

    public static BlockPos getHorizontal(World world, ZombieEntity z, Vec3d targetPos) {
        if (targetPos == null) return null;
        double dx = targetPos.x - z.getX(), dz = targetPos.z - z.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return null;
        
        Vec3d dir = new Vec3d(dx / len * 2.0, 0, dz / len * 2.0);
        double zombieHeight = z.getHeight();
        double scale = z.getScale();
        double maxHeight = zombieHeight + scale * 0.5;
        for (double h = 0.0; h <= maxHeight; h += 0.5) {
            var start = new net.minecraft.util.math.Vec3d(z.getX(), z.getY() + h, z.getZ());
            var res = world.raycast(new net.minecraft.world.RaycastContext(start, start.add(dir), 
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, z));
            if (res.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && isBlocking(world, res.getBlockPos())) return res.getBlockPos();
        }
        return null;
    }

    public static boolean isBlocking(World world, BlockPos pos) {
        long tick = currentTick.get();
        CachedBlock cached = BLOCK_CACHE.get(pos);
        
        if (cached != null && cached.tick >= tick - CACHE_TICK_DURATION) {
            return cached.blocking;
        }

        BlockState state = world.getBlockState(pos);
        boolean blocking = !state.isAir() && (state.shouldBlockVision(world, pos) || !state.getCollisionShape(world, pos).isEmpty() || state.isFullCube(world, pos));
        
        if (BLOCK_CACHE.size() < MAX_CACHE_SIZE) {
            BLOCK_CACHE.put(pos, new CachedBlock(state, tick));
        }
        
        return blocking;
    }
}




