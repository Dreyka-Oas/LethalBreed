package com.dreykaoas.lethalbreed.entity;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.special.SpecialAttachment;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Per-zombie pursuit data: the current hunt target, short-term memory of a lost target, a heard-sound
 * point, spatial-grid membership, and the special-variant attachment + its action cooldown. Pure state +
 * accessors — no per-tick behaviour (that lives in {@link com.dreykaoas.lethalbreed.entity.move.ZombieBrain}).
 */
public final class ZombiePursuit {
    private final Zombie entity;

    // Current hunt target (any living entity; set by LODManager). Null during memory/sound pursuit.
    private LivingEntity targetEntity;
    private double tgtX, tgtY, tgtZ;
    private boolean hasTarget = false;

    // Short-term memory: last known target position after sight + sound are both lost.
    private double memX, memY, memZ;
    private long memoryExpire = Long.MIN_VALUE;
    private boolean memory;

    // Sound investigation point.
    private double soundX, soundY, soundZ;
    private boolean hasSound = false;

    // Spatial-grid bookkeeping.
    private long cellKey = 0L;
    private boolean inGrid = false;

    // Special-variant type (persistent attachment, set at spawn) + its action cooldown.
    private SpecialType special;
    private boolean specialResolved = false;
    private int specialCd = 0;

    public ZombiePursuit(Zombie entity) {
        this.entity = entity;
        this.special = SpecialType.fromId(entity.getAttached(SpecialAttachment.SPECIAL));
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

    public boolean hasTarget() { return hasTarget; }
    public LivingEntity targetEntity() { return targetEntity; }
    public double tgtX() { return tgtX; }
    public double tgtY() { return tgtY; }
    public double tgtZ() { return tgtZ; }

    /** Squared distance from the zombie to its current target point (live or remembered). */
    public double distanceToTargetSq() { return entity.distanceToSqr(tgtX, tgtY, tgtZ); }

    // --- short-term memory ---
    public void rememberTarget(double x, double y, double z, long expireTick) {
        this.memX = x;
        this.memY = y;
        this.memZ = z;
        this.memoryExpire = expireTick;
        this.memory = true;
    }

    public boolean hasMemory() { return memory; }
    public long memoryExpire() { return memoryExpire; }
    public void clearMemory() { this.memory = false; }

    /** Pursue the remembered last-known position — no live entity (no melee), just navigate/dig to the spot. */
    public void setMemoryTarget() {
        this.targetEntity = null;
        this.tgtX = memX;
        this.tgtY = memY;
        this.tgtZ = memZ;
        this.hasTarget = true;
    }

    // --- sound ---
    public void setSoundTarget(double x, double y, double z) {
        this.soundX = x;
        this.soundY = y;
        this.soundZ = z;
        this.hasSound = true;
    }

    public void clearSound() { this.hasSound = false; }
    public boolean hasSound() { return hasSound; }
    public double soundX() { return soundX; }
    public double soundY() { return soundY; }
    public double soundZ() { return soundZ; }

    // --- spatial grid ---
    public long cellKey() { return cellKey; }
    public boolean inGrid() { return inGrid; }

    public void setCell(long key, boolean inGrid) {
        this.cellKey = key;
        this.inGrid = inGrid;
    }

    // --- special variant ---
    public SpecialType special() { return special; }
    public boolean specialReady() { return specialCd <= 0; }
    public void resetSpecialCd() { specialCd = Math.max(1, ProgressionConfig.specialActionInterval); }
    public boolean isSpecialActive() { return special.kind() == SpecialType.Kind.ACTIVE; }

    /** Re-read the special type from the attachment (used after the test command forces a type). */
    public void refreshSpecial() {
        this.special = SpecialType.fromId(entity.getAttached(SpecialAttachment.SPECIAL));
    }

    /** First-tick resolve (attachment reliably present by now) + per-tick cooldown decrement. */
    public void tickSpecial() {
        if (!specialResolved) {
            refreshSpecial();
            specialResolved = true;
        }
        if (specialCd > 0) {
            specialCd--;
        }
    }
}
