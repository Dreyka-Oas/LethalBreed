/**
 * Project: Lethal Breed
 * Responsibility: AI Goal for Low-Health Panic Behavior
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;

public class ZombiePanicGoal extends Goal {
    private final ZombieEntity zombie;
    private double fX, fY, fZ;
    private int timer = 0, screams = 0, cooldown = 0;

    public ZombiePanicGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (cooldown > 0) cooldown--;
        return cooldown <= 0 && zombie.getHealth() <= zombie.getMaxHealth() * ModConfig.INSTANCE.panic.healthThreshold;
    }

    @Override
    public void start() { this.timer = 0; this.screams = 0; }

    @Override
    public void tick() {
        timer++;
        zombie.setAttacking(true);
        if (timer % ModConfig.INSTANCE.panic.screamIntervalTicks == 0) {
            zombie.playSound(net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_HURT, 3.0f, 0.5f);
            screams++;
            
            // Alert allies (Scream hearing)
            var worldWorld = (net.minecraft.server.world.ServerWorld) zombie.getEntityWorld();
            var allies = worldWorld.getEntitiesByClass(ZombieEntity.class, 
                zombie.getBoundingBox().expand(ModConfig.INSTANCE.panic.allyAlertRange), e -> e != zombie && e.isAlive());
            for (ZombieEntity ally : allies) {
                HearingRegistry.register(ally.getId(), new Vec3d(zombie.getX(), zombie.getY(), zombie.getZ()));
            }
        }
        var world = zombie.getEntityWorld();
        var pack = world.getEntitiesByClass(ZombieEntity.class, zombie.getBoundingBox().expand(10), e -> e != zombie);
        if (pack.size() >= ModConfig.INSTANCE.panic.stopPackSize) { screams = ModConfig.INSTANCE.panic.stopPackSize; return; }

        LivingEntity t = zombie.getTarget();
        double dist = (t != null) ? zombie.squaredDistanceTo(t) : 400;
        if (dist < 64.0) {
            Vec3d tp = (t != null) ? new Vec3d(t.getX(), t.getY(), t.getZ()) : null;
            Vec3d v = (tp != null) ? NoPenaltyTargeting.findFrom(zombie, 16, 7, tp) : NoPenaltyTargeting.find(zombie, 16, 7);
            if (v != null) { fX = v.x; fY = v.y; fZ = v.z; zombie.getNavigation().startMovingTo(fX, fY, fZ, 1.3); }
        } else zombie.getNavigation().stop();
    }

    @Override
    public void stop() { 
        zombie.setAttacking(false); 
        zombie.getNavigation().stop();
        cooldown = ModConfig.INSTANCE.panic.cooldownTicks; 
    }

    @Override
    public boolean shouldContinue() { return screams < ModConfig.INSTANCE.panic.stopPackSize && zombie.getHealth() <= zombie.getMaxHealth() * ModConfig.INSTANCE.panic.continueHealthThreshold; }
}
