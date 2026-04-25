package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class MovementCoordinator {
    public static boolean tryBuild(ZombieEntity z, Vec3d t) {
        if (!z.isOnGround()) return false;
        World w = ((EntityAccessor)z).getWorld();
        Vec3d dir = t.subtract(new Vec3d(z.getX(), z.getY(), z.getZ()));
        if (dir.lengthSquared() < 0.01) return false;
        dir = dir.normalize();

        BlockPos curr = z.getBlockPos();
        BlockPos next = new BlockPos((int) (z.getX() + dir.x), (int) (z.getY() - 0.5), (int) (z.getZ() + dir.z));
        if (next.equals(curr)) return false;

        BlockPos floor = (t.y < z.getY() - 0.5) ? next.down(2) : next.down();
        if (w.getBlockState(floor).isAir() && VoidAnalyzer.isDeep(w, floor)) {
            z.getNavigation().stop(); z.setVelocity(0, 0, 0); z.velocityDirty = true;
            center(z, curr);
            BlockSetter.placeDirt(w, floor);
            return true;
        }
        return false;
    }

    private static void center(ZombieEntity z, BlockPos curr) {
        double sx = curr.getX() + 0.5, sz = curr.getZ() + 0.5;
        if (z.squaredDistanceTo(sx, z.getY(), sz) > 0.09) z.setPosition(sx, z.getY(), sz);
    }
}







