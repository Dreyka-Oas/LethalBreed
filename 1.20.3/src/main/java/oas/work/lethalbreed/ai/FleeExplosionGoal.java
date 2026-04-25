package oas.work.lethalbreed.ai;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import oas.work.lethalbreed.config.ModConfig;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public class FleeExplosionGoal extends Goal {
    private final ZombieEntity zombie;
    private ZombieEntity threat;

    public FleeExplosionGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (zombie.getCommandTags().contains("lethal_primed")) return false;
        
        double range = ModConfig.INSTANCE.panic.fleeExplosionRange;
        var list = zombie.getWorld().getEntitiesByClass(ZombieEntity.class, zombie.getBoundingBox().expand(range), 
            z -> z.getCommandTags().contains("lethal_primed"));
            
        if (!list.isEmpty()) {
            threat = list.get(0);
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        Vec3d threatPos = new Vec3d(threat.getX(), threat.getY(), threat.getZ());
        Vec3d away = NoPenaltyTargeting.findFrom(zombie, 10, 5, threatPos);
        
        if (away != null) {
            zombie.getNavigation().startMovingTo(away.x, away.y, away.z, 1.5);
        }
    }

    @Override
    public boolean shouldContinue() {
        return !zombie.getNavigation().isIdle() && threat != null && threat.isAlive();
    }
}








