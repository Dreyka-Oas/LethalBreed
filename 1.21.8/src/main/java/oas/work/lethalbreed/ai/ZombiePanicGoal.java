package oas.work.lethalbreed.ai;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import oas.work.lethalbreed.config.ModConfig;
import java.util.EnumSet;
public class ZombiePanicGoal extends Goal {
    private final ZombieEntity zombie;
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
            PanicLogic.alertAllies(zombie); screams++;
        }
        if (PanicLogic.getPackSize(zombie) >= ModConfig.INSTANCE.panic.stopPackSize) {
            screams = ModConfig.INSTANCE.panic.stopPackSize; return;
        }
        PanicMovement.execute(zombie);
    }
    @Override
    public void stop() { 
        zombie.setAttacking(false); zombie.getNavigation().stop();
        cooldown = ModConfig.INSTANCE.panic.cooldownTicks; 
    }
    @Override
    public boolean shouldContinue() {
        return screams < ModConfig.INSTANCE.panic.stopPackSize && 
               zombie.getHealth() <= zombie.getMaxHealth() * ModConfig.INSTANCE.panic.continueHealthThreshold;
    }
}




