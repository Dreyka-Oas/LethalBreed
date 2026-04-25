package oas.work.lethalbreed.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import java.util.EnumSet;

public class ClosestVisibleTargetGoal extends Goal {
    private final ZombieEntity zombie;

    public ClosestVisibleTargetGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.TARGET));
    }

    private boolean check() {
        boolean forced = zombie.getCommandTags().contains("lethal_force_update");
        if (!forced && (zombie.getId() + zombie.age) % 5 != 0) return false;
        if (forced) zombie.removeCommandTag("lethal_force_update");
        
        LivingEntity closest = TargetFinder.find(zombie);
        if (closest != null && closest != zombie.getTarget()) {
            zombie.setTarget(closest);
            return true;
        }
        return false;
    }

    @Override
    public boolean canStart() { return check(); }

    @Override
    public void tick() { check(); }
}







