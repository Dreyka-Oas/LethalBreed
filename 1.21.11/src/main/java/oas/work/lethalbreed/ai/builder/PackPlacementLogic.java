package oas.work.lethalbreed.ai.builder;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public class PackPlacementLogic {
    public static BlockPos getBetterConstructionPos(Zombie z) {
        BlockPos base = z.blockPosition();
        var world = z.level();
        
        List<Zombie> nearby = world.getEntitiesOfClass(Zombie.class, 
            z.getBoundingBox().inflate(1.2), other -> other != z && other.getNavigation().isDone());

        if (nearby.isEmpty()) return base;

        BlockPos[] options = {base.north(), base.south(), base.east(), base.west()};
        for (BlockPos opt : options) {
            if (world.getBlockState(opt).isAir() && world.getBlockState(opt.below()).isRedstoneConductor(world, opt.below())) {
                return opt;
            }
        }
        return base;
    }
}








