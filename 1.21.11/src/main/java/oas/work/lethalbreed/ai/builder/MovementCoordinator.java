package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import oas.work.lethalbreed.mixin.EntityAccessor;

public class MovementCoordinator {
    public static boolean tryBuild(Zombie z, Vec3 t) {
        if (!z.onGround()) return false;
        Level w = ((EntityAccessor)z).getWorld();
        Vec3 dir = t.subtract(new Vec3(z.getX(), z.getY(), z.getZ()));
        if (dir.lengthSqr() < 0.01) return false;
        dir = dir.normalize();

        BlockPos curr = z.blockPosition();
        BlockPos next = new BlockPos((int) (z.getX() + dir.x), (int) (z.getY() - 0.5), (int) (z.getZ() + dir.z));
        if (next.equals(curr)) return false;

        BlockPos floor = (t.y < z.getY() - 0.5) ? next.below(2) : next.below();
        if (w.getBlockState(floor).isAir() && VoidAnalyzer.isDeep(w, floor)) {
            z.getNavigation().stop(); z.setDeltaMovement(0, 0, 0); z.hurtMarked = true;
            center(z, curr);
            BlockSetter.placeDirt(w, floor);
            return true;
        }
        return false;
    }

    private static void center(Zombie z, BlockPos curr) {
        double sx = curr.getX() + 0.5, sz = curr.getZ() + 0.5;
        if (z.distanceToSqr(sx, z.getY(), sz) > 0.09) z.setPos(sx, z.getY(), sz);
    }
}









