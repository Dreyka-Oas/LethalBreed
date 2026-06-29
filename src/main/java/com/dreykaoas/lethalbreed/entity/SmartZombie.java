package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.config.domain.WorldSpawnConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.move.ZombieBrain;
import com.dreykaoas.lethalbreed.util.VanillaTargetingGoals;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Mod-side wrapper around a vanilla {@link Zombie}. Thin shell that owns the entity reference and identity,
 * and composes the per-zombie {@link ZombiePursuit} (target/memory/sound/grid/special state) with the
 * {@link ZombieBrain} (per-tick orchestration + movement dispatch). The leaf movement units in
 * {@code entity.move} read this back through the delegating accessors below.
 */
public final class SmartZombie {
    private final Zombie entity;
    private final ResourceKey<Level> dimension;

    private ZombieState state = ZombieState.IDLE;
    private LODLevel lod = LODLevel.HIGH;

    private final ZombiePursuit pursuit;
    private final ZombieBrain brain;

    public SmartZombie(Zombie entity, ResourceKey<Level> dimension) {
        this.entity = entity;
        this.dimension = dimension;
        this.pursuit = new ZombiePursuit(entity);
        this.brain = new ZombieBrain(this);
    }

    public ZombiePursuit pursuit() { return pursuit; }

    /**
     * Reconcile this zombie's vanilla target-selection goals to {@link TargetingConfig#forceNearestTarget}
     * so the option takes effect LIVE on an already-spawned zombie. ON → strip the vanilla goals so our
     * "nearest living entity" pick is authoritative; OFF → re-add the exact goals we stripped. Cheap and
     * idempotent (a no-op once already in the wanted state); called each classify. Runs post-spawn so the
     * entity id used by {@link VanillaTargetingGoals} is stable.
     */
    public void reconcileTargetingGoals() {
        if (TargetingConfig.forceNearestTarget) {
            VanillaTargetingGoals.strip(entity);
        } else {
            VanillaTargetingGoals.restore(entity);
        }
    }

    // --- identity ---
    public Zombie entity() { return entity; }
    public ResourceKey<Level> dimension() { return dimension; }
    public int id() { return entity.getId(); }
    public boolean isValid() { return entity.isAlive() && !entity.isRemoved(); }
    public double x() { return entity.getX(); }
    public double y() { return entity.getY(); }
    public double z() { return entity.getZ(); }

    // --- state / lod ---
    public ZombieState state() { return state; }
    public void setState(ZombieState state) { this.state = state; }
    public LODLevel lod() { return lod; }
    public void setLod(LODLevel lod) { this.lod = lod; }

    // --- target accessors read by the movement leaf units (delegate to pursuit) ---
    public boolean hasTarget() { return pursuit.hasTarget(); }
    public LivingEntity targetEntity() { return pursuit.targetEntity(); }
    public double tgtX() { return pursuit.tgtX(); }
    public double tgtY() { return pursuit.tgtY(); }
    public double tgtZ() { return pursuit.tgtZ(); }

    // --- per-tick behaviour (delegate to brain) ---
    public void tick(ServerLevel level, WorldAIContext ctx) { brain.tick(level, ctx); }
    public void climbStep(ServerLevel level, WorldAIContext ctx) { brain.climbStep(level, ctx); }
    public boolean isClimbing() { return brain.isClimbing(); }
    public void swimStep(ServerLevel level, WorldAIContext ctx) { brain.swimStep(level, ctx); }
    public boolean isSwimming() { return brain.isSwimming(); }
    public boolean dueThisActivation(int divisor) { return brain.dueThisActivation(divisor); }

    /**
     * Make EVERY zombie burn in daylight, directly — not via the vanilla {@code isSunSensitive} gate (which
     * Husk overrides off). Day + open sky + not in water/rain + not fire-immune → set it alight for 8s.
     * Re-checked each activation; the {@code getRemainingFireTicks>0} guard avoids re-stacking.
     */
    public void applySunBurn(ServerLevel level) {
        if (!WorldSpawnConfig.forceAllZombiesSunBurn || entity.fireImmune()) {
            return;
        }
        if (entity.getRemainingFireTicks() > 0 || !level.isBrightOutside() || entity.isInWaterOrRain()) {
            return;
        }
        if (level.canSeeSky(entity.blockPosition())) {
            entity.setRemainingFireTicks(WorldSpawnConfig.sunBurnDurationTicks); // default 160 = 8s, like vanilla
        }
    }
}
