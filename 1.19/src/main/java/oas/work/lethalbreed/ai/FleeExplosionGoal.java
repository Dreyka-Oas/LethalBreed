package oas.work.lethalbreed.ai;

import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

public class FleeExplosionGoal extends Goal {
    private final Zombie zombie;
    private Zombie threat;

    public FleeExplosionGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (zombie.getTags().contains("lethal_primed")) return false;
        
        double range = ModConfig.INSTANCE.panic.fleeExplosionRange;
        var list = zombie.getLevel().getEntitiesOfClass(Zombie.class, zombie.getBoundingBox().inflate(range), 
            z -> z.getTags().contains("lethal_primed"));
            
        if (!list.isEmpty()) {
            threat = list.get(0);
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        Vec3 threatPos = new Vec3(threat.getX(), threat.getY(), threat.getZ());
        Vec3 away = DefaultRandomPos.getPosAway(zombie, 10, 5, threatPos);
        
        if (away != null) {
            zombie.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !zombie.getNavigation().isDone() && threat != null && threat.isAlive();
    }
}







