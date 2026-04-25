package oas.work.lethalbreed.ai.builder;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import java.util.EnumSet;

public class ZombieBuildGoal extends Goal {
    private final ZombieEntity zombie;
    private final BuildStateMachine logic;

    public ZombieBuildGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.logic = new BuildStateMachine(zombie);
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return logic.canStart();
    }

    @Override
    public void tick() {
        logic.tick();
    }

    @Override
    public void stop() {
        logic.reset();
    }
}






