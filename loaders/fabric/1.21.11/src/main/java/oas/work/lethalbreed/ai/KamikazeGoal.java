/**
 * Project: Lethal Breed
 * Responsibility: AI Goal for Self-Destructing Zombies
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;

public class KamikazeGoal extends Goal {
    private final ZombieEntity zombie;
    private int fuseTimer = -1;
    private boolean isPrimed = false;
    private float explosionBonus = 1.0f;

    public KamikazeGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return isPrimed || (zombie.getEquippedStack(EquipmentSlot.HEAD).getItem() == Items.TNT 
            && zombie.getTarget() != null && zombie.squaredDistanceTo(zombie.getTarget()) < 9.0);
    }

    @Override
    public void start() {
        if (isPrimed) return;
        isPrimed = true;
        fuseTimer = ModConfig.INSTANCE.ai.kamikazeFuseTicks;
        explosionBonus = 1.0f + zombie.getRandom().nextFloat();
        zombie.addCommandTag("lethal_primed");
        zombie.playSound(SoundEvents.ENTITY_TNT_PRIMED, 2.0f, explosionBonus);
        zombie.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (zombie.getTarget() != null) zombie.getLookControl().lookAt(zombie.getTarget());
        zombie.setVelocity(0, zombie.getVelocity().y, 0);
        if (fuseTimer > 0) {
            fuseTimer--;
            ExplosionLogic.spawnParticles(zombie, explosionBonus, fuseTimer);
            double j = (zombie.getRandom().nextDouble() - 0.5) * (0.1 * explosionBonus);
            zombie.refreshPositionAndAngles(zombie.getX() + j, zombie.getY(), zombie.getZ() + j, zombie.getYaw(), zombie.getPitch());
        } else ExplosionLogic.detonate(zombie, explosionBonus);
    }

    @Override
    public boolean shouldContinue() { return isPrimed; }
}
