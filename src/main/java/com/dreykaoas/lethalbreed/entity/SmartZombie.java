package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.LethalBreedMod;
import com.dreykaoas.lethalbreed.ai.LODLevel;
import com.dreykaoas.lethalbreed.ai.ZombieState;
import com.dreykaoas.lethalbreed.block.BlockOperationQueue;
import com.dreykaoas.lethalbreed.block.MaterialRegistry;
import com.dreykaoas.lethalbreed.config.LethalBreedConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/**
 * Mod-side wrapper around a vanilla {@link Zombie}. Drives target-agnostic pursuit: each zombie hunts
 * its nearest valid living target (set by the LOD/target manager), navigates to it, pillars up to
 * elevated targets, and breaks/bridges obstacles directly in the way. Runs on the server thread.
 */
public final class SmartZombie {
    private final Zombie entity;
    private final ResourceKey<Level> dimension;
    private final int bucketIndex;
    private final double leapFactor;

    private ZombieState state = ZombieState.IDLE;
    private LODLevel lod = LODLevel.HIGH;

    // Spatial-grid bookkeeping.
    private long cellKey = 0L;
    private boolean inGrid = false;

    // Sound investigation target.
    private double soundX, soundY, soundZ;
    private boolean hasSound = false;

    // Throttling.
    private int activations;
    private int sinceNav;
    private int climbCd;
    private int leapCd;

    // Stuck detection (block ops only when not making horizontal progress toward the target).
    private double lastHorizDistSq = -1.0;
    private int stuckTicks = 0;

    // Pillar-up state (also processed every tick by the scheduler while active). Used when the target is
    // above but there is NO flush wall to scale: the zombie builds a dirt column straight up beneath
    // itself. Unlike wall-climb a structure IS left, but it stands on it (no strand) and the tracker
    // auto-removes it after placedBlockLifetimeTicks.
    private boolean pillaring = false;
    private int pillarAge = 0;
    private double pillarStartY = 0.0;
    private int pillarColX = 0;
    private int pillarColZ = 0;
    private int pillarStandY = 0; // block-Y the zombie last jumped from (support is laid here)

    // Water state: while in water the scheduler drives rise/dive every tick (swimStep), not the bucketed tick.
    private boolean swimming = false;

    // Current hunt target (any living entity; set by LODManager).
    private LivingEntity targetEntity;
    private double tgtX, tgtY, tgtZ;
    private boolean hasTarget = false;

    private static int debugTick = 0;
    private int dbgN = 0;

    public SmartZombie(Zombie entity, ResourceKey<Level> dimension, int bucketIndex) {
        this.entity = entity;
        this.dimension = dimension;
        this.bucketIndex = bucketIndex;
        this.leapFactor = ZombieVariation.leapFactor(entity);
    }

    public Zombie entity() {
        return entity;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public int id() {
        return entity.getId();
    }

    public int bucketIndex() {
        return bucketIndex;
    }

    public boolean isValid() {
        return entity.isAlive() && !entity.isRemoved();
    }

    public double x() {
        return entity.getX();
    }

    public double y() {
        return entity.getY();
    }

    public double z() {
        return entity.getZ();
    }

    public ZombieState state() {
        return state;
    }

    public void setState(ZombieState state) {
        this.state = state;
    }

    public LODLevel lod() {
        return lod;
    }

    public void setLod(LODLevel lod) {
        this.lod = lod;
    }

    // --- spatial grid bookkeeping ---
    public long cellKey() {
        return cellKey;
    }

    public boolean inGrid() {
        return inGrid;
    }

    public void setCell(long key, boolean inGrid) {
        this.cellKey = key;
        this.inGrid = inGrid;
    }

    // --- sound ---
    public void setSoundTarget(double x, double y, double z) {
        this.soundX = x;
        this.soundY = y;
        this.soundZ = z;
        this.hasSound = true;
    }

    public void clearSound() {
        this.hasSound = false;
    }

    public boolean hasSound() {
        return hasSound;
    }

    // --- target ---
    public void setTarget(LivingEntity e, double x, double y, double z) {
        this.targetEntity = e;
        this.tgtX = x;
        this.tgtY = y;
        this.tgtZ = z;
        this.hasTarget = true;
    }

    public void clearTarget() {
        this.targetEntity = null;
        this.hasTarget = false;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    // --- short-term memory (last known target position after sight + sound are both lost) ---
    private double memX, memY, memZ;
    private long memoryExpire = Long.MIN_VALUE;
    private boolean memory;

    /** Refresh the remembered last-known position (called every tick a live target is detected). */
    public void rememberTarget(double x, double y, double z, long expireTick) {
        this.memX = x;
        this.memY = y;
        this.memZ = z;
        this.memoryExpire = expireTick;
        this.memory = true;
    }

    public boolean hasMemory() {
        return memory;
    }

    public long memoryExpire() {
        return memoryExpire;
    }

    public void clearMemory() {
        this.memory = false;
    }

    /** Pursue the remembered last-known position — no live entity (no melee), just navigate/dig to the spot. */
    public void setMemoryTarget() {
        this.targetEntity = null;
        this.tgtX = memX;
        this.tgtY = memY;
        this.tgtZ = memZ;
        this.hasTarget = true;
    }

    /** Squared distance from the zombie to its current target point (live or remembered). */
    public double distanceToTargetSq() {
        return entity.distanceToSqr(tgtX, tgtY, tgtZ);
    }

    /** Distance-tier throttle: returns true on 1 of every {@code divisor} activations of this zombie. */
    public boolean dueThisActivation(int divisor) {
        if (divisor <= 1) {
            return true;
        }
        return (activations++ % divisor) == 0;
    }

    public void tick(ServerLevel level, WorldAIContext ctx) {
        if (!isValid()) {
            return;
        }
        int bx = entity.blockPosition().getX();
        int bz = entity.blockPosition().getZ();
        ctx.spatialGrid().update(this, bx, bz);

        if (lod == LODLevel.FROZEN) {
            return;
        }

        if (climbCd > 0) {
            climbCd--;
        }
        if (pillaring) {
            return; // mid jump-pillar; the scheduler's per-tick climbStep finishes it (no nav/leap fighting it)
        }

        if (!hasTarget) {
            // No entity target — fall back to investigating a heard sound, else idle.
            if (hasSound && navigateToSound()) {
                state = ZombieState.PURSUING_SOUND;
            } else {
                state = ZombieState.IDLE;
            }
            return;
        }

        // Make the zombie actually attack our chosen target (damage animals/villagers/mobs, not just approach).
        if (LethalBreedConfig.attackAllTargets && targetEntity != null && targetEntity.isAlive()
                && entity.getTarget() != targetEntity) {
            entity.setTarget(targetEntity);
        }

        double dx = tgtX - entity.getX();
        double dz = tgtZ - entity.getZ();
        double dy = tgtY - entity.getY();
        double horizSq = dx * dx + dz * dz;

        // In water the zombie never builds — pillaring up a dirt column while floating is nonsense. The
        // actual rise/dive is driven EVERY tick by the scheduler's swim pass (swimStep), not here on the
        // bucket cadence, otherwise the per-tick FloatGoal out-pushes our sparse dive impulse. Here we just
        // flag it and skip the whole ground block-op/climb pipeline.
        if (LethalBreedConfig.floatInWater && entity.isInWater()) {
            pillaring = false;
            swimming = true;
            state = ZombieState.PURSUING_PLAYER;
            return;
        }
        swimming = false;

        // Vanilla melee fires automatically when adjacent (we set the vanilla target above). We keep
        // running so a zombie 1 block short of the target can still bridge/climb the final block when
        // stuck — block ops are no-ops on clear ground, so meleeing isn't disrupted.

        // Occasional leap/pounce to catch a target at mid-range.
        if (leapCd > 0) {
            leapCd--;
        }
        if (LethalBreedConfig.leapEnabled && !pillaring && entity.onGround() && leapCd <= 0) {
            double horiz = Math.sqrt(horizSq);
            if (horiz >= LethalBreedConfig.leapMinRange && horiz <= LethalBreedConfig.leapMaxRange
                    && Math.abs(dy) < 3.0 && entity.getRandom().nextFloat() < LethalBreedConfig.leapChance) {
                double inv = 1.0 / horiz;
                double ndx = dx * inv;
                double ndz = dz * inv;
                // Only leap if there's ground to land on — never leap into a gap / off a short bridge.
                if (leapHasLanding(level, ndx, ndz)) {
                    entity.setDeltaMovement(ndx * LethalBreedConfig.leapHorizontalSpeed * leapFactor,
                            jumpVelocity(LethalBreedConfig.leapUpward * leapFactor),
                            ndz * LethalBreedConfig.leapHorizontalSpeed * leapFactor);
                    entity.hurtMarked = true;
                    leapCd = LethalBreedConfig.leapCooldownActivations;
                    state = ZombieState.PURSUING_PLAYER;
                    return; // let the leap arc carry it this activation
                }
            }
        }

        // Block ops only when genuinely STUCK (no horizontal progress) — so the zombie walks, auto-steps
        // a 1-block ledge and jumps a 1-wide gap normally instead of needlessly building/breaking.
        boolean progressing = lastHorizDistSq < 0.0 || horizSq < lastHorizDistSq - 0.25;
        stuckTicks = progressing ? 0 : stuckTicks + 1;
        lastHorizDistSq = horizSq;
        boolean stuck = stuckTicks >= LethalBreedConfig.stuckActivations;

        // Head toward the target. When it is perched well above us (wall/tower/pillar) its exact aerial
        // position is unreachable by the vanilla pathfinder, which then finds no path and the zombie just
        // stands and stares. Aim at the BASE of the target's column (our own Y) so we actually walk up to
        // it and close the distance — once within climb range the pillar-climb takes over.
        double navY = (dy > 1.0) ? entity.getY() : tgtY;
        navTo(tgtX, navY, tgtZ);
        state = ZombieState.PURSUING_PLAYER;

        // Arrived: close enough to hit and with line of sight → let vanilla melee finish it. Doing block
        // ops (digging/bridging/breaking) next to a reachable target is the needless behaviour to kill.
        boolean canHit = targetEntity != null && targetEntity.isAlive()
                && horizSq <= LethalBreedConfig.meleeStopRange * LethalBreedConfig.meleeStopRange
                && Math.abs(dy) <= LethalBreedConfig.meleeStopHeight
                && entity.getSensing().hasLineOfSight(targetEntity);

        if (LethalBreedConfig.debugClimb && (dbgN++ % 4 == 0)) {
            LethalBreedMod.LOGGER.info(
                    "[ClimbDbg] z{} y={} tgtY={} horiz={} dy={} stuck={}({}) climb={} cd={} ground={}",
                    entity.getId(), f1(entity.getY()), f1(tgtY), f1(Math.sqrt(horizSq)), f1(dy),
                    stuck, stuckTicks, pillaring, climbCd, entity.onGround());
        }

        // HIGH or MEDIUM zombies climb to reach an elevated target (a target at 32-64 blocks must still be
        // pursued vertically). LOW/FROZEN stay ground-only to bound cost. Ascent is ALWAYS a real
        // jump-and-place pillar (like a player) — never a setPos levitation, wall or no wall.
        boolean canClimbLod = lod == LODLevel.HIGH || lod == LODLevel.MEDIUM;
        double climbR = LethalBreedConfig.climbHorizRadius;
        boolean targetOverhead = dy >= LethalBreedConfig.climbThreshold && horizSq <= climbR * climbR;
        // Symmetric to the climb trigger: the target is below us and roughly under our feet → descend NOW.
        // The vanilla pathfinder refuses to walk off any drop taller than its fall limit, so a zombie above
        // a lower target just stands at the edge and stares unless we drive the descent ourselves.
        boolean targetUnderfoot = dy <= -LethalBreedConfig.descendThreshold && horizSq <= climbR * climbR;

        // Climb the moment the target is perched close above and we can't reach it — do NOT wait for the
        // "stuck" heuristic. A vanilla zombie jittering/auto-jumping at the wall base rarely registers as
        // stuck, which is why pursuit alone never started a climb (only a hit knocking it still did).
        if (canClimbLod && !canHit && targetOverhead) {
            initiatePillar(ctx);
        } else if (canClimbLod && !canHit && targetUnderfoot) {
            // Target below and near → carve straight down toward it (no stuck wait, mirrors the climb).
            descendStep(level, ctx, stepSign(dx), stepSign(dz));
        } else if (stuck && canClimbLod && !canHit) {
            // Stuck on flat ground with a non-overhead target → it's a lateral obstacle or a descent.
            if (dy <= -LethalBreedConfig.descendThreshold) {
                descendStep(level, ctx, stepSign(dx), stepSign(dz));
            } else {
                handleObstaclesToward(level, ctx, bx, bz, stepSign(dx), stepSign(dz));
            }
        }
    }

    private boolean navigateToSound() {
        double dx = soundX - entity.getX();
        double dz = soundZ - entity.getZ();
        double arrive = LethalBreedConfig.soundArriveDistance;
        if (dx * dx + dz * dz <= arrive * arrive) {
            clearSound();
            return false;
        }
        navTo(soundX, soundY, soundZ);
        return true;
    }

    /** Re-path only when the previous path finished or the re-issue interval elapsed. */
    private void navTo(double x, double y, double z) {
        PathNavigation nav = entity.getNavigation();
        if (nav.isDone() || sinceNav >= LethalBreedConfig.navReissueInterval) {
            nav.moveTo(x, y, z, LethalBreedConfig.navSpeed);
            sinceNav = 0;
        } else {
            sinceNav++;
        }
    }

    /**
     * Begin building a dirt column up toward a target perched above (wall, tower, overhang or open gap).
     * {@link #pillarStep} drives a real jump-and-place cycle each tick — like a player pillaring up — so the
     * zombie stands on what it builds and never levitates. The column is auto-removed by the tracker.
     */
    private void initiatePillar(WorldAIContext ctx) {
        if (pillaring || climbCd > 0 || !entity.onGround()) {
            return;
        }
        pillaring = true;
        pillarAge = 0;
        pillarStartY = entity.getY();
        // Lock the column to where we start so the whole pillar rises straight up one fixed XZ cell.
        pillarColX = entity.blockPosition().getX();
        pillarColZ = entity.blockPosition().getZ();
        pillarStandY = entity.blockPosition().getY();
        state = ZombieState.BUILDING;
    }

    /**
     * Per-tick while {@link #pillaring}: a real jump-and-place cycle (no levitation). On the ground the
     * zombie is told to jump; once airborne and clear of the block it left, a dirt support is dropped into
     * that cell so it lands one block higher. Repeats until it reaches the target's height, the height cap,
     * or a ceiling. {@code setJumping} drives a true physics jump on the mob's next movement tick (a
     * deltaMovement.y set here is cancelled by that tick — see the {@code use-setpos} skill).
     */
    private void pillarStep(ServerLevel level, WorldAIContext ctx) {
        if (!pillaring) {
            return;
        }
        if (!isValid()) {
            pillaring = false;
            return;
        }
        pillarAge++;

        double dyToTarget = hasTarget ? (tgtY - entity.getY()) : -1.0;
        double hx = tgtX - entity.getX();
        double hz = tgtZ - entity.getZ();
        double h = Math.sqrt(hx * hx + hz * hz);

        if (LethalBreedConfig.debugClimb && (pillarAge % 3 == 1)) {
            LethalBreedMod.LOGGER.info("[ClimbDbg] z{} PILLAR y={} dyTgt={} horiz={} age={} risen={} ground={}",
                    entity.getId(), f1(entity.getY()), f1(dyToTarget), f1(h), pillarAge,
                    f1(entity.getY() - pillarStartY), entity.onGround());
        }

        // Reached the target's height → hop forward off the column toward the target and stop.
        if (!hasTarget || dyToTarget < 1.0) {
            if (h > 0.001) {
                entity.setDeltaMovement(hx / h * 0.4, jumpVelocity(0.42), hz / h * 0.4);
                entity.hurtMarked = true;
            }
            entity.setJumping(false);
            pillaring = false;
            return;
        }
        // Height budget spent, or a solid ceiling blocks further rise → give up; the column stays (and is
        // auto-removed by the tracker). The zombie stands on what it built.
        boolean ceiling = level.getBlockState(BlockPos.containing(
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.25, entity.getZ())).blocksMotion();
        if (entity.getY() - pillarStartY >= LethalBreedConfig.pillarMaxHeight || ceiling) {
            entity.setJumping(false);
            pillaring = false;
            climbCd = LethalBreedConfig.climbGiveUpCooldown;
            return;
        }

        // Stop navigation so a path doesn't drag the zombie off its spot.
        entity.getNavigation().stop();

        // Face the target so the zombie looks where it is climbing (not staring sideways mid-jump).
        if (h > 1.0e-2) {
            float yaw = (float) (Mth.atan2(hz, hx) * (180.0 / Math.PI)) - 90.0f;
            entity.setYRot(yaw);
            entity.yBodyRot = yaw;
            entity.yHeadRot = yaw;
        }

        if (entity.onGround()) {
            // Grounded on the column: record this rung and launch a jump. setJumping(true) from
            // END_SERVER_TICK does NOT make a mob jump (verified — it stays grounded); a direct upward
            // velocity impulse survives into the next tick's travel() and lifts it ~1.1 blocks. Zero the
            // horizontal component so the hop is straight up onto the support block.
            pillarColX = entity.blockPosition().getX();
            pillarColZ = entity.blockPosition().getZ();
            pillarStandY = entity.blockPosition().getY();
            entity.setDeltaMovement(0.0, jumpVelocity(LethalBreedConfig.pillarJumpPower), 0.0);
            entity.hurtMarked = true;
        } else {
            // Airborne and clear of the block we left → drop a support into that cell so we land one higher.
            if (entity.getY() >= pillarStandY + 1.0) {
                ctx.blockOps().enqueuePlace(new BlockPos(pillarColX, pillarStandY, pillarColZ));
            }
        }
    }

    /** Scheduler entry point each tick for a zombie that is ascending. Drives the jump-and-place pillar. */
    public void climbStep(ServerLevel level, WorldAIContext ctx) {
        if (pillaring) {
            pillarStep(level, ctx);
        }
    }

    public boolean isClimbing() {
        return pillaring;
    }

    public boolean isSwimming() {
        return swimming;
    }

    /**
     * Per-tick while in water. The zombie can't drown, so by default it just lets the {@link
     * net.minecraft.world.entity.ai.goal.FloatGoal} keep it bobbing at the surface. But when its target is
     * itself submerged below, it dives after it — applying a downward impulse EVERY tick so it overcomes
     * the FloatGoal's per-tick lift (the bucketed tick pass could not). Never places blocks in water.
     */
    public void swimStep(ServerLevel level, WorldAIContext ctx) {
        if (!swimming) {
            return;
        }
        if (!isValid() || !entity.isInWater()) {
            swimming = false;
            return;
        }
        pillaring = false;

        // Use the target's LIVE position (the cached tgt is only refreshed on the bucket cadence, which made
        // the zombie chase a stale point and look like it swam "anywhere").
        boolean haveLive = targetEntity != null && targetEntity.isAlive();
        double txx = haveLive ? targetEntity.getX() : tgtX;
        double tyy = haveLive ? targetEntity.getY() : tgtY;
        double tzz = haveLive ? targetEntity.getZ() : tgtZ;
        boolean targetBelow = haveLive && targetEntity.isInWater() && tyy < entity.getY() - 0.5;

        // Drive the swim directly instead of via the path navigation — the water pathfinder kept failing to
        // settle and the zombie spun in circles. Stop nav, face the target, ease toward it.
        entity.getNavigation().stop();

        double hx = txx - entity.getX();
        double hz = tzz - entity.getZ();
        double hlen = Math.sqrt(hx * hx + hz * hz);
        int sdx = stepSign(hx);
        int sdz = stepSign(hz);

        if (hlen > 1.0e-2) {
            float yaw = (float) (Mth.atan2(hz, hx) * (180.0 / Math.PI)) - 90.0f;
            entity.setYRot(yaw);
            entity.yBodyRot = yaw;
            entity.yHeadRot = yaw;
        }

        // Horizontal: ease toward the target (blend with current velocity so it accelerates/decelerates
        // smoothly instead of teleport-gliding at a fixed speed). Zero the drive within ~0.6 blocks.
        net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
        double desiredX = hlen > 0.6 ? hx / hlen * LethalBreedConfig.waterSwimSpeed : 0.0;
        double desiredZ = hlen > 0.6 ? hz / hlen * LethalBreedConfig.waterSwimSpeed : 0.0;
        double nvx = v.x * 0.6 + desiredX * 0.4;
        double nvz = v.z * 0.6 + desiredZ * 0.4;
        // Vertical: dive after a submerged target, else surface gently and hold at the top.
        double vy = targetBelow ? -LethalBreedConfig.waterDiveSpeed
                : (entity.isUnderWater() ? LethalBreedConfig.waterRiseSpeed : 0.0);

        entity.setDeltaMovement(nvx, vy, nvz);
        entity.hurtMarked = true;
        waterBreakToward(level, ctx, sdx, sdz, targetBelow);
    }

    /** Carve solid blocks between the zombie and its target while swimming (water itself isn't solid, so
     *  this only breaks real obstacles). When diving it also opens the floor cell directly below. */
    private void waterBreakToward(ServerLevel level, WorldAIContext ctx, int sdx, int sdz, boolean diving) {
        int bx = entity.blockPosition().getX();
        int by = entity.blockPosition().getY();
        int bz = entity.blockPosition().getZ();
        if (sdx != 0 || sdz != 0) {
            tryWaterBreak(level, ctx, bx + sdx, by, bz + sdz);
            tryWaterBreak(level, ctx, bx + sdx, by + 1, bz + sdz);
        }
        if (diving) {
            tryWaterBreak(level, ctx, bx, by - 1, bz);
        }
    }

    private void tryWaterBreak(ServerLevel level, WorldAIContext ctx, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        if (s.blocksMotion() && MaterialRegistry.isBreakable(level, p, s)) {
            ctx.breakManager().request(p, entity);
        }
    }

    /**
     * Descend toward a lower target. Prefers to just walk: flat ground ahead is walked across, a short
     * safe drop ({@link LethalBreedConfig#safeDropBlocks}) is stepped off for free. Only when neither is
     * possible does it carve a forward STAIRCASE (break the step-down blocks) or build a stair over a
     * genuine void — so it never breaks a floor it could stand on, nor digs itself into an unsafe fall.
     */
    private void descendStep(ServerLevel level, WorldAIContext ctx, int sdx, int sdz) {
        int bx = entity.blockPosition().getX();
        int by = entity.blockPosition().getY();
        int bz = entity.blockPosition().getZ();

        // 0) Cheapest descent: a nearby edge with a short SAFE drop (<= safeDropBlocks) — just step/drop off
        //    it for free, no digging. Handles the common "lower area one or two blocks down" case.
        if (tryWalkableStepDown(level, bx, by, bz)) {
            state = ZombieState.DESCENDING;
            return;
        }

        int ax = bx + sdx;
        int az = bz + sdz;

        // 1) Forward staircase toward the target's column: the cell one step down has a solid floor →
        //    take that 1-block step, breaking any breakable head/feet block in the way. This walks the
        //    zombie DOWN through solid terrain one safe step at a time toward a lower target.
        if (sdx != 0 || sdz != 0) {
            BlockPos head = new BlockPos(ax, by, az);
            BlockPos feet = new BlockPos(ax, by - 1, az);
            BlockPos floor = new BlockPos(ax, by - 2, az);
            BlockState hd = level.getBlockState(head);
            BlockState ft = level.getBlockState(feet);
            BlockState fl = level.getBlockState(floor);
            boolean headClear = hd.isAir() || !hd.blocksMotion();
            boolean feetClear = ft.isAir() || !ft.blocksMotion();

            // Flat walkable ground ahead (solid floor + clear body): the descent is further on — walk to it,
            // never break a floor we could stand on.
            if (ft.blocksMotion() && headClear
                    && !level.getBlockState(new BlockPos(ax, by + 1, az)).blocksMotion()) {
                entity.getNavigation().moveTo(ax + 0.5, by, az + 0.5, LethalBreedConfig.navSpeed);
                state = ZombieState.DESCENDING;
                return;
            }
            if (fl.blocksMotion()) {
                if (!headClear && MaterialRegistry.isBreakable(level, head, hd)) {
                    ctx.breakManager().request(head, entity);
                    state = ZombieState.DESCENDING;
                    return;
                }
                if (!feetClear && MaterialRegistry.isBreakable(level, feet, ft)) {
                    ctx.breakManager().request(feet, entity);
                    state = ZombieState.DESCENDING;
                    return;
                }
                // Clean 1-block step down with a floor → walk to it (keeps the zombie on the stair, no leap).
                entity.getNavigation().moveTo(ax + 0.5, by - 1, az + 0.5, LethalBreedConfig.navSpeed);
                state = ZombieState.DESCENDING;
                return;
            }
            // Forward cell is a drop (no floor one step down).
            if (feetClear && headClear) {
                int fall = fallDistanceInto(level, ax, by, az, LethalBreedConfig.safeDropBlocks);
                if (fall <= LethalBreedConfig.safeDropBlocks) {
                    // Short safe drop → step off the edge toward the target for free.
                    entity.getNavigation().moveTo(ax + 0.5, by - fall, az + 0.5, LethalBreedConfig.navSpeed);
                    state = ZombieState.DESCENDING;
                    return;
                }
                // Deep void TOWARD the target → BUILD a descending step in the target's direction (NOT straight
                // down, which would carve a shaft away from where it wants to go). Place the support FIRST and
                // WAIT; only step once it exists (the block-op queue drains after the tick, so moving now would
                // walk the zombie into thin air before the support is there).
                entity.getNavigation().stop();
                BlockPos land = new BlockPos(ax, by - 2, az);
                BlockState ld = level.getBlockState(land);
                if (ld.isAir() || !ld.blocksMotion()) {
                    ctx.blockOps().enqueuePlace(land);
                    state = ZombieState.BUILDING;
                    return;
                }
                entity.getNavigation().moveTo(ax + 0.5, by - 1, az + 0.5, LethalBreedConfig.navSpeed);
                state = ZombieState.BUILDING;
                return;
            }
            // Body space ahead blocked by an unbreakable obstacle → fall through to the straight-down carve.
        }

        // 2) Carve straight DOWN through our own floor toward a target below — one block per activation, but
        //    ONLY when the resulting fall is safe (a solid landing within safeDropBlocks under the block we
        //    remove). The zombie drops a short safe distance onto the new floor and repeats next activation,
        //    walking itself down a self-dug shaft to ANY depth without fall damage. Stop nav so the vanilla
        //    pathfinder doesn't drag it off the column hunting an impossible roundabout path.
        BlockPos under = new BlockPos(bx, by - 1, bz);
        BlockState us = level.getBlockState(under);
        if (us.blocksMotion() && MaterialRegistry.isBreakable(level, under, us)) {
            // Fall the zombie takes after `under` is removed = distance from level (by-1) down to next solid.
            int fall = fallDistanceInto(level, bx, by - 1, bz, LethalBreedConfig.safeDropBlocks);
            if (fall <= LethalBreedConfig.safeDropBlocks) {
                entity.getNavigation().stop();
                ctx.breakManager().request(under, entity);
                state = ZombieState.DESCENDING;
                return;
            }
        }

        // 3) Target directly below over a deep void (the "1 block of floor over a pit" case, or the forward
        //    path was walled off): can't drop straight safely. Don't strand — BUILD a descending staircase,
        //    laying a support one level down-and-forward then stepping onto it, repeating into a safe diagonal
        //    stair down to ANY depth, even out over open air (the inverse of the pillar-up). Auto-removes.
        int dirx = sdx;
        int dirz = sdz;
        if (dirx == 0 && dirz == 0) {
            // Target is directly below us → pick the dominant horizontal axis toward it to stair along.
            if (Math.abs(tgtX - entity.getX()) >= Math.abs(tgtZ - entity.getZ())) {
                dirx = (tgtX >= entity.getX()) ? 1 : -1;
            } else {
                dirz = (tgtZ >= entity.getZ()) ? 1 : -1;
            }
        }
        int sx = bx + dirx;
        int sz = bz + dirz;
        BlockPos sHead = new BlockPos(sx, by, sz);
        BlockPos sFeet = new BlockPos(sx, by - 1, sz);
        BlockPos sLand = new BlockPos(sx, by - 2, sz); // one step below our current floor (by-1)
        BlockState sHd = level.getBlockState(sHead);
        BlockState sFt = level.getBlockState(sFeet);
        BlockState sLd = level.getBlockState(sLand);
        // Clear the body space for the step if a breakable block blocks it.
        if (sHd.blocksMotion() && MaterialRegistry.isBreakable(level, sHead, sHd)) {
            ctx.breakManager().request(sHead, entity);
            state = ZombieState.DESCENDING;
            return;
        }
        if (sFt.blocksMotion() && MaterialRegistry.isBreakable(level, sFeet, sFt)) {
            ctx.breakManager().request(sFeet, entity);
            state = ZombieState.DESCENDING;
            return;
        }
        boolean headClear = sHd.isAir() || !sHd.blocksMotion();
        boolean feetClear = sFt.isAir() || !sFt.blocksMotion();
        if (headClear && feetClear) {
            // Stop pursuit nav so it can't walk us off the ledge into the void while we build the step.
            entity.getNavigation().stop();
            if (sLd.isAir() || !sLd.blocksMotion()) {
                // No floor at the lower step yet → lay it FIRST and WAIT. Do NOT step this activation: the
                // block op queue drains after the tick, so moving now would walk the zombie into thin air
                // before the support exists (it would just fall — "placed nothing where it went to descend").
                ctx.blockOps().enqueuePlace(sLand);
                state = ZombieState.BUILDING;
                return;
            }
            // The step now has a floor (placed last activation, or already solid) → walk down onto it.
            entity.getNavigation().moveTo(sx + 0.5, by - 1, sz + 0.5, LethalBreedConfig.navSpeed);
            state = ZombieState.BUILDING;
        }
    }

    /** Break a breakable block, or bridge a gap, directly ahead toward (sdx,sdz). */
    private void handleObstaclesToward(ServerLevel level, WorldAIContext ctx, int bx, int bz, int sdx, int sdz) {
        if (sdx == 0 && sdz == 0) {
            return;
        }
        int y = entity.blockPosition().getY();
        int ax = bx + sdx;
        int az = bz + sdz;
        BlockOperationQueue ops = ctx.blockOps();

        BlockPos head = new BlockPos(ax, y + 1, az);
        BlockPos feet = new BlockPos(ax, y, az);
        BlockState hs = level.getBlockState(head);
        BlockState fs = level.getBlockState(feet);

        if (hs.blocksMotion() && MaterialRegistry.isBreakable(level, head, hs)) {
            ctx.breakManager().request(head, entity);
            state = ZombieState.BREAKING;
            return;
        }
        if (fs.blocksMotion() && MaterialRegistry.isBreakable(level, feet, fs)) {
            ctx.breakManager().request(feet, entity);
            state = ZombieState.BREAKING;
            return;
        }
        if (!fs.blocksMotion()) {
            // A short, walkable ledge ahead is not a pit — let the zombie step/drop down it for free
            // instead of filling it with dirt. Only bridge a true gap with no nearby landing.
            if (fallDistanceInto(level, ax, y, az, LethalBreedConfig.safeDropBlocks) <= LethalBreedConfig.safeDropBlocks) {
                return;
            }
            BlockPos ground = new BlockPos(ax, y - 1, az);
            BlockState gs = level.getBlockState(ground);
            if (gs.isAir() || !gs.blocksMotion()) {
                ops.enqueuePlace(ground);
                state = ZombieState.BUILDING;
            }
        }
    }

    /** True if there is solid ground near where a leap would land (so we don't jump into a gap). */
    private boolean leapHasLanding(ServerLevel level, double ndx, double ndz) {
        int dist = 3;
        int lx = Mth.floor(entity.getX() + ndx * dist);
        int lz = Mth.floor(entity.getZ() + ndz * dist);
        int ly = Mth.floor(entity.getY());
        for (int yy = ly + 1; yy >= ly - 3; yy--) {
            if (level.getBlockState(new BlockPos(lx, yy, lz)).blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fall the zombie would take stepping into column (x,z) from feet-level {@code y}: 0 = flat ground
     * straight ahead, 1 = a one-block step-down, etc. {@link Integer#MAX_VALUE} when no solid landing is
     * found within {@code max} below — a genuine pit / unsafe fall it should bridge or stair instead.
     * The scanned column is clear above the landing by construction, so the fall path is unobstructed.
     */
    private static int fallDistanceInto(ServerLevel level, int x, int y, int z, int max) {
        for (int yy = y - 1; yy >= y - 1 - max; yy--) {
            if (level.getBlockState(new BlockPos(x, yy, z)).blocksMotion()) {
                return (y - 1) - yy;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Walk off an adjacent safe step-down instead of digging straight through our own floor. Scans the
     * four cardinal neighbours for a body-clear column that drops a short, safe distance; on the first
     * hit it paths there and returns true. Returns false when boxed in (no nearby walkable descent) — the
     * caller then falls back to digging straight down.
     */
    private boolean tryWalkableStepDown(ServerLevel level, int bx, int by, int bz) {
        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = bx + d[0];
            int nz = bz + d[1];
            if (level.getBlockState(new BlockPos(nx, by, nz)).blocksMotion()) {
                continue; // feet space into that column is blocked
            }
            if (level.getBlockState(new BlockPos(nx, by + 1, nz)).blocksMotion()) {
                continue; // head space is blocked
            }
            int fall = fallDistanceInto(level, nx, by, nz, LethalBreedConfig.safeDropBlocks);
            if (fall >= 1 && fall <= LethalBreedConfig.safeDropBlocks) {
                entity.getNavigation().moveTo(nx + 0.5, by - fall, nz + 0.5, LethalBreedConfig.navSpeed);
                return true;
            }
        }
        return false;
    }

    private static int stepSign(double d) {
        return d > 0.5 ? 1 : (d < -0.5 ? -1 : 0);
    }

    /**
     * An upward jump impulse with the live Jump Boost effect folded in — so a zombie given the potion (or any
     * mod adding the effect) jumps higher dynamically, exactly like vanilla {@code getJumpPower()} adds
     * {@code 0.1 * (amplifier + 1)}. Never hard-codes the boost; reads the current effect each jump.
     */
    private double jumpVelocity(double base) {
        MobEffectInstance jump = entity.getEffect(MobEffects.JUMP_BOOST);
        return jump != null ? base + 0.1 * (jump.getAmplifier() + 1) : base;
    }

    private static String f1(double v) {
        return String.format("%.1f", v);
    }
}
