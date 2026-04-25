package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;

public class ZombiePanicGoal extends Goal {
    private final Zombie zombie;
    private int timer = 0, screams = 0, cooldown = 0;

    public ZombiePanicGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) cooldown--;
        return cooldown <= 0 && zombie.getHealth() <= zombie.getMaxHealth() * ModConfig.INSTANCE.panic.healthThreshold;
    }

    @Override
    public void start() { this.timer = 0; this.screams = 0; }

    @Override
    public void tick() {
        timer++;
        zombie.setAggressive(true);
        if (timer % ModConfig.INSTANCE.panic.screamIntervalTicks == 0) {
            PanicLogic.alertAllies(zombie); screams++;
        }
        if (PanicLogic.getPackSize(zombie) >= ModConfig.INSTANCE.panic.stopPackSize) {
            screams = ModConfig.INSTANCE.panic.stopPackSize; return;
        }
        PanicMovement.execute(zombie);
    }

    @Override
    public void stop() {
        zombie.setAggressive(false); zombie.getNavigation().stop();
        cooldown = ModConfig.INSTANCE.panic.cooldownTicks;
    }

    @Override
    public boolean canContinueToUse() {
        return screams < ModConfig.INSTANCE.panic.stopPackSize &&
               zombie.getHealth() <= zombie.getMaxHealth() * ModConfig.INSTANCE.panic.continueHealthThreshold;
    }
}