package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.List;

public class FleeExplosionGoal extends Goal {
    private final Zombie zombie;
    private Zombie threat;

    public FleeExplosionGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (zombie.getTags().contains("lethal_primed")) return false;

        double range = oas.work.lethalbreed.config.ModConfig.INSTANCE.panic.fleeExplosionRange;
        List<Zombie> list = zombie.level().getEntitiesOfClass(
            Zombie.class, zombie.getBoundingBox().inflate(range),
            z -> z.getTags().contains("lethal_primed")
        );

        if (!list.isEmpty()) {
            threat = list.get(0);
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        Vec3 threatPos = threat.position();
        Vec3 away = findFrom(zombie, 10, 5, threatPos);

        if (away != null) {
            zombie.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
        }
    }

    private static Vec3 findFrom(Zombie zombie, int i, int j, Vec3 vec3) {
        double x = zombie.getX() - vec3.x;
        double z = zombie.getZ() - vec3.z;
        double len = Math.sqrt(x*x + z*z);
        if (len == 0) return null;
        x /= len;
        z /= len;
        return new Vec3(zombie.getX() + x * i, zombie.getY(), zombie.getZ() + z * i);
    }

    @Override
    public boolean canContinueToUse() {
        return !zombie.getNavigation().isDone() && threat != null && threat.isAlive();
    }
}