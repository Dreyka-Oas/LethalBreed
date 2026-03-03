/**
 * Project: Lethal Breed
 * Responsibility: Sector Scanning for Obstructions
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SectorScanner {
    public static BlockPos find(World w, ZombieEntity z, Vec3d tPos) {
        double dx = tPos.x - z.getX(), dz = tPos.z - z.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2) return null;
        double nx = dx / len, nz = dz / len;
        for (double d = 0.4; d <= 1.2; d += 0.4) {
            for (double a : new double[]{0, -0.4, 0.4}) {
                double rx = nx * Math.cos(a) - nz * Math.sin(a);
                double rz = nx * Math.sin(a) + nz * Math.cos(a);
                for (double h = 0.1; h < z.getHeight() + 0.5; h += 0.7) {
                    BlockPos p = BlockPos.ofFloored(z.getX() + rx * d, z.getY() + h, z.getZ() + rz * d);
                    if (ObstructionAnalyzer.isBlocking(w, p)) return p;
                }
            }
        }
        return null;
    }
}
