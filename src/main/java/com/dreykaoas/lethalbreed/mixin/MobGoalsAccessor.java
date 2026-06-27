package com.dreykaoas.lethalbreed.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s {@code goalSelector} field. The field is declared on {@code Mob}, so a
 * {@code @Shadow} from a {@code Zombie}-targeted mixin cannot resolve it — an accessor on the declaring
 * class is the reliable way to reach an inherited field.
 */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
    @Accessor("goalSelector")
    GoalSelector lethalbreed$goalSelector();

    @Accessor("targetSelector")
    GoalSelector lethalbreed$targetSelector();
}
