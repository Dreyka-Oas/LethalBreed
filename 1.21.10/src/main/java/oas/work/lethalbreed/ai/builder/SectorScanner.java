package oas.work.lethalbreed.ai.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SectorScanner {
    public static BlockPos find(Level w, Zombie z, Vec3 tPos) {
        double dx = tPos.x - z.getX(), dz = tPos.z - z.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.2) return null;
        double nx = dx / len, nz = dz / len;
        double zombieHeight = z.getBbHeight();
        double scale = 1.0;
        double maxHeight = zombieHeight + scale * 0.5;
        for (double d = 0.4; d <= 1.2; d += 0.4) {
            for (double a : new double[]{0, -0.4, 0.4}) {
                double rx = nx * Math.cos(a) - nz * Math.sin(a);
                double rz = nx * Math.sin(a) + nz * Math.cos(a);
                for (double h = 0.0; h <= maxHeight; h += 0.5) {
                    BlockPos p = new BlockPos((int) (z.getX() + rx * d), (int) (z.getY() + h), (int) (z.getZ() + rz * d));
                    if (ObstructionAnalyzer.isBlocking(w, p)) return p;
                }
            }
        }
        return null;
    }
}







