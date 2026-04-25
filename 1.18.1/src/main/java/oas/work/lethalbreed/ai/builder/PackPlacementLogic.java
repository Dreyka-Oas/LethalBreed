package oas.work.lethalbreed.ai.builder;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public class PackPlacementLogic {
    public static BlockPos getBetterConstructionPos(Zombie z) {
        BlockPos base = z.blockPosition();
        var world = z.getLevel();
        
        List<Zombie> nearby = world.getEntitiesOfClass(Zombie.class, 
            z.getBoundingBox().inflate(1.2), other -> other != z && other.getNavigation().isDone());

        if (nearby.isEmpty()) return base;

        BlockPos[] options = {base.north(), base.south(), base.east(), base.west()};
        for (BlockPos opt : options) {
            if (world.getBlockState(opt).getMaterial().isReplaceable() && world.getBlockState(opt.below()).isRedstoneConductor(world, opt.below())) {
                return opt;
            }
        }
        return base;
    }
}






