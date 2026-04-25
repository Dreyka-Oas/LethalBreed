package oas.work.lethalbreed.ai.builder;

import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.core.BlockPos;
import java.util.List;

public class PackPlacementLogic {
    public static BlockPos getBetterConstructionPos(Zombie z) {
        BlockPos base = z.blockPosition();
        var world = z.level();

        List<Zombie> nearby = world.getEntitiesOfClass(
            Zombie.class, z.getBoundingBox().inflate(1.2),
            other -> other != z && other.getNavigation().isDone()
        );

        if (nearby.isEmpty()) return base;

        BlockPos[] options = {base.north(), base.south(), base.east(), base.west()};
        for (BlockPos opt : options) {
            if (world.getBlockState(opt).canBeReplaced() && world.getBlockState(opt.below()).isSolid()) {
                return opt;
            }
        }
        return base;
    }
}