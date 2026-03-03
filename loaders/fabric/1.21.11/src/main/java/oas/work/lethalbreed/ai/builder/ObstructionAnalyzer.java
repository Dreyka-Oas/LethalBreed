/**
 * Project: Lethal Breed
 * Responsibility: World Scanning for AI Path Obstructions
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ObstructionAnalyzer {
    public static BlockPos getObstruction(World world, BlockPos base, ZombieEntity z) {
        int limit = (int) Math.ceil(z.getHeight()) + 1;
        for (int i = 2; i <= limit; i++) {
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
        for (double h = 0.2; h < z.getHeight(); h += 0.8) {
            var start = new net.minecraft.util.math.Vec3d(z.getX(), z.getY() + h, z.getZ());
            var res = world.raycast(new net.minecraft.world.RaycastContext(start, start.add(dir), 
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, z));
            if (res.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && isBlocking(world, res.getBlockPos())) return res.getBlockPos();
        }
        return null;
    }

    public static boolean isBlocking(World world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        return !s.isAir() && (s.shouldBlockVision(world, pos) || !s.getCollisionShape(world, pos).isEmpty() || s.isFullCube(world, pos));
    }
}
