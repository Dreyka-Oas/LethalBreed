package oas.work.lethalbreed.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import java.util.EnumSet;

public class ZombieBuildGoal extends Goal {
    private final Zombie zombie;
    private final oas.work.lethalbreed.ai.builder.BuildStateMachine logic;

    public ZombieBuildGoal(Zombie zombie) {
        this.zombie = zombie;
        this.logic = new oas.work.lethalbreed.ai.builder.BuildStateMachine(zombie);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return logic.canUse();
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