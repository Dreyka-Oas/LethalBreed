package oas.work.lethalbreed.ai.builder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
            this.blocking = !state.isAir() && (state.isViewBlocking(null, BlockPos.ZERO) || !state.getCollisionShape(null, BlockPos.ZERO).isEmpty() || state.isCollisionShapeFullBlock(null, BlockPos.ZERO));
        }
    }

    public static void onTick(long tick) {
        currentTick.set(tick);
        if (BLOCK_CACHE.size() > MAX_CACHE_SIZE) {
            long minTick = tick - CACHE_TICK_DURATION * 2;
            BLOCK_CACHE.entrySet().removeIf(e -> e.getValue().tick < minTick);
        }
    }

    public static BlockPos getObstruction(Level world, BlockPos base, Zombie z) {
        double zombieHeight = z.getBbHeight();
        double scale = 1.0;
        for (int i = 0; i <= Math.ceil(zombieHeight + scale); i++) {
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
        
        Vec3 dir = new Vec3(dx / len * 2.0, 0, dz / len * 2.0);
        double zombieHeight = z.getBbHeight();
        double scale = 1.0;
        double maxHeight = zombieHeight + scale * 0.5;
        for (double h = 0.0; h <= maxHeight; h += 0.5) {
            var start = new net.minecraft.world.phys.Vec3(z.getX(), z.getY() + h, z.getZ());
            var res = world.clip(new net.minecraft.world.level.ClipContext(start, start.add(dir), 
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, z));
            if (res.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && isBlocking(world, res.getBlockPos())) return res.getBlockPos();
        }
        return null;
    }

    public static boolean isBlocking(Level world, BlockPos pos) {
        long tick = currentTick.get();
        CachedBlock cached = BLOCK_CACHE.get(pos);
        
        if (cached != null && cached.tick >= tick - CACHE_TICK_DURATION) {
            return cached.blocking;
        }

        BlockState state = world.getBlockState(pos);
        boolean blocking = !state.isAir() && (state.isViewBlocking(world, pos) || !state.getCollisionShape(world, pos).isEmpty() || state.isCollisionShapeFullBlock(world, pos));
        
        if (BLOCK_CACHE.size() < MAX_CACHE_SIZE) {
            BLOCK_CACHE.put(pos, new CachedBlock(state, tick));
        }
        
        return blocking;
    }
}







