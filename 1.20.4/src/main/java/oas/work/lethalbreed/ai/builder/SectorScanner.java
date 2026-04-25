package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public class SectorScanner {
    public static BlockPos find(Level w, Zombie z, Vec3 tPos) {
        double dx = tPos.x - z.getX(), dz = tPos.z - z.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2) return null;
        double nx = dx / len, nz = dz / len;
        double zombieHeight = z.getBbHeight();
        for (double d = 0.4; d <= 1.2; d += 0.4) {
            for (double a : new double[]{0, -0.4, 0.4}) {
                double rx = nx * Math.cos(a) - nz * Math.sin(a);
                double rz = nx * Math.sin(a) + nz * Math.cos(a);
                for (double h = 0.0; h <= zombieHeight; h += 0.5) {
                    BlockPos p = BlockPos.containing(z.getX() + rx * d, z.getY() + h, z.getZ() + rz * d);
                    if (ObstructionAnalyzer.isBlocking(w, p)) return p;
                }
            }
        }
        return null;
    }
}