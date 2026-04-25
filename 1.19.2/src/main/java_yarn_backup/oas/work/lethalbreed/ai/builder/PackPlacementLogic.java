package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public class PackPlacementLogic {
    public static BlockPos getBetterConstructionPos(ZombieEntity z) {
        BlockPos base = z.getBlockPos();
        var world = z.getWorld();
        
        List<ZombieEntity> nearby = world.getEntitiesByClass(ZombieEntity.class, 
            z.getBoundingBox().expand(1.2), other -> other != z && other.getNavigation().isIdle());

        if (nearby.isEmpty()) return base;

        BlockPos[] options = {base.north(), base.south(), base.east(), base.west()};
        for (BlockPos opt : options) {
            if (world.getBlockState(opt).getMaterial().isReplaceable() && world.getBlockState(opt.down()).isSolidBlock(world, opt.down())) {
                return opt;
            }
        }
        return base;
    }
}






