/**
 * Project: Lethal Breed
 * Responsibility: Accessor Mixin for Entity Fields
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
package oas.work.lethalbreed.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("world")
    World getWorld();

    @Accessor("age")
    int getAge();

    @Accessor("age")
    void setAge(int age);
}
