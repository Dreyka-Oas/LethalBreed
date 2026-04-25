package oas.work.lethalbreed.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class ClosestVisibleTargetGoal extends Goal {
    private final Zombie zombie;

    public ClosestVisibleTargetGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    private boolean check() {
        boolean forced = zombie.getTags().contains("lethal_force_update");
        if (!forced && (zombie.getId() + zombie.tickCount) % 5 != 0) return false;
        if (forced) zombie.removeTag("lethal_force_update");
        
        LivingEntity closest = TargetFinder.find(zombie);
        if (closest != null && closest != zombie.getTarget()) {
            zombie.setTarget(closest);
            return true;
        }
        return false;
    }

    @Override
    public boolean canUse() { return check(); }

    @Override
    public void tick() { check(); }
}









