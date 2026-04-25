package oas.work.lethalbreed.ai;

import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;

public class KamikazeGoal extends Goal {
    private final Zombie zombie;
    private int fuseTimer = -1;
    private boolean isPrimed = false;
    private float explosionBonus = 1.0f;

    public KamikazeGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isPrimed || (zombie.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.TNT
            && zombie.getTarget() != null && zombie.distanceToSqr(zombie.getTarget()) < 9.0);
    }

    @Override
    public void start() {
        if (isPrimed) return;
        isPrimed = true;
        fuseTimer = ModConfig.INSTANCE.ai.kamikazeFuseTicks;
        explosionBonus = 1.0f + zombie.getRandom().nextFloat();
        KamikazeLogic.prime(zombie, explosionBonus);
    }

    @Override
    public void tick() {
        if (zombie.getTarget() != null) zombie.getLookControl().setLookAt(zombie.getTarget());
        zombie.setDeltaMovement(0, zombie.getDeltaMovement().y, 0);
        if (fuseTimer-- > 0) {
            ExplosionLogic.spawnParticles(zombie, explosionBonus, fuseTimer);
            KamikazeLogic.tickShake(zombie, explosionBonus);
        } else ExplosionLogic.detonate(zombie, explosionBonus);
    }

    @Override
    public boolean canContinueToUse() {
        return isPrimed;
    }
}
