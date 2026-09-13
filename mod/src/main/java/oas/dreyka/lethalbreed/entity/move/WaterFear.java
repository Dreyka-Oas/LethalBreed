package oas.dreyka.lethalbreed.entity.move;

import oas.dreyka.lethalbreed.config.domain.CombatMoveConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import oas.dreyka.lethalbreed.entity.SmartZombie;

import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Zombies that cannot swim: water is a wall to their pathfinder, and one that goes under it drowns.
 *
 * <p>Vanilla gives them the opposite deal. A zombie sinks, walks the bottom indefinitely because undead do
 * not breathe, and after thirty seconds turns into a Drowned. Handing them a {@code FloatGoal} instead, so
 * they swim the surface after you, goes further the same way. Both make water a road; here it is a moat.
 *
 * <p><b>The drowning clock is the entity's own air supply.</b> Nothing in vanilla ever moves a zombie's, so
 * the field is free, it is already per-entity and already saved with the entity, and using it means this
 * feature adds no map that a shutdown would have to purge and no attachment to register.
 */
public final class WaterFear {
    private WaterFear() {}

    /** Whether the swimming behaviour is in force at all. Read by the two places that drive it, the vanilla
     *  {@code FloatGoal} added at spawn and the mod's own swim entry, so the rule lives once. */
    public static boolean swims() {
        return CombatMoveConfig.floatInWater && !CombatMoveConfig.cannotSwim;
    }

    /** What {@link SmartZombie#airLeft()} holds while the zombie has its head in the air. */
    private static final int BREATHING = Integer.MIN_VALUE;

    /**
     * Spend air while the zombie's head is under water, walk it back out, and hurt it harder the longer it
     * stays under.
     *
     * <p>Measured against the eyes, not the feet: a zombie standing in shallow water is wading, not drowning,
     * and drowning it there would make a one-block puddle lethal. That is the whole rule, and it is what makes
     * a ford different from a lake without anything having to know how tall the zombie is.
     *
     * <p><b>Nothing here touches the pathfinder.</b> Three ways of doing that were tried and all three refuse
     * a ford as surely as a lake: a malus is one number per fluid for the whole entity, {@code canFloat(false)}
     * stops the navigator entering water at all, and refusing the next path node when it is deep left a zombie
     * stalled in one block of water at the second step. Depth belongs to a place, so the avoidance lives in
     * {@code CellClassifier}, which routes a journey round a lake. Close in, where the field is not consulted,
     * a zombie that walks into a lake drowns in it, and that is the other half of the same option.
     *
     * <p><b>The damage climbs.</b> A flat figure is a health check, not a drowning: these zombies gain health
     * with the phases, and one deep enough into them would stand on the bottom of a lake shrugging off a fixed
     * chip for as long as anyone cared to watch. Each activation under adds one more step of
     * {@code drownDamage}, so the total after n steps grows as n squared and the time to die stays bounded
     * whatever the health pool. It also walks straight through vanilla's invulnerability window, which lets a
     * bigger hit than the last one through by the difference.
     *
     * <p>The clock is the mod's own counter. Vanilla's air supply looked like a free field and is not: the
     * game refills it every tick for anything undead, so a countdown written there never reached zero and the
     * first run of the water suite reported a zombie sitting in three blocks of water at full health.
     *
     * @param activationTicks how many ticks this activation stands for, so the clock runs in real time
     *        whatever the scheduler's cadence is
     */
    public static void tickWater(ServerLevel level, SmartZombie owner, int activationTicks) {
        if (!CombatMoveConfig.cannotSwim) {
            return;
        }
        Zombie zombie = owner.entity();
        if (!zombie.isEyeInFluid(FluidTags.WATER)) {
            owner.setAirLeft(BREATHING);
            if (zombie.onGround()) {
                owner.setDryFooting(zombie.blockPosition().asLong());
            }
            return;
        }
        drown(level, zombie, owner, Math.max(1, activationTicks));
    }

    /** True while the zombie's head is under water: the one test that separates a ford from a lake, and it
     *  needs no idea how tall the zombie is. */
    public static boolean submerged(Zombie zombie) {
        return CombatMoveConfig.cannotSwim && zombie.isEyeInFluid(FluidTags.WATER);
    }

    /** Head back to the last dry footing. Nothing to do if it has never had one, which is the zombie that
     *  was put in the water rather than one that walked in: that one has nowhere to go and drowns. */
    public static void retreat(Zombie zombie, SmartZombie owner) {
        long packed = owner.dryFooting();
        if (packed == Long.MIN_VALUE) {
            return;
        }
        BlockPos dry = BlockPos.of(packed);
        zombie.getNavigation().moveTo(dry.getX() + 0.5, dry.getY(), dry.getZ() + 0.5, CombatMoveConfig.waterRetreatSpeed);
    }

    private static void drown(ServerLevel level, Zombie zombie, SmartZombie owner, int step) {
        int air = owner.airLeft();
        if (air == BREATHING) {
            air = Math.max(1, CombatMoveConfig.drownGraceTicks);
        }
        air -= step;
        owner.setAirLeft(air);
        if (air > 0) {
            return;
        }
        int stepsUnder = 1 + (-air) / step;
        zombie.hurtServer(level, level.damageSources().drown(),
                (float) (CombatMoveConfig.drownDamage * stepsUnder));
    }
}
