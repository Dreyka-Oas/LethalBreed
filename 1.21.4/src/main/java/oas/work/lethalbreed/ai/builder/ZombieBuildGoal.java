package oas.work.lethalbreed.ai.builder;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;

public class ZombieBuildGoal extends Goal {
    private final Zombie zombie;
    private final BuildStateMachine logic;

    public ZombieBuildGoal(Zombie zombie) {
        this.zombie = zombie;
        this.logic = new BuildStateMachine(zombie);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
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







