package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.runtime.BombeurBlast;
import com.dreykaoas.lethalbreed.special.runtime.SpecialAbilities;
import com.dreykaoas.lethalbreed.special.runtime.SpecialDeath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicInteger;

/** Runtime behaviour for ACTIVE special zombies (per-activation, cooldown-gated) and DEATH specials. */
public final class SpecialBehavior {
    private SpecialBehavior() {}

    // Dev instrumentation (headless test harness reads these to confirm abilities fired).
    public static final AtomicInteger SUMMON_COUNT = new AtomicInteger();
    public static final AtomicInteger HURL_COUNT = new AtomicInteger();
    public static final AtomicInteger HEAL_COUNT = new AtomicInteger();

    /**
     * True while a Bombeur's fuse is burning — it has armed and is committed to detonating.
     *
     * <p>Read by {@code LODManager} to refuse FROZEN for such a zombie. {@code LodBucketPass} skips a FROZEN
     * zombie BEFORE it ever reaches {@code tick()}, so freezing a lit Bombeur stops its fuse mid-burn: the
     * belly stops swelling client-side and it becomes a dormant mine that detonates the instant a player
     * wanders back into range, however many minutes later. The deadline is absolute, so the fix is simply to
     * keep the zombie ticking until it goes off.
     */
    public static boolean fuseIsLit(Zombie z) {
        return z.getAttachedOrElse(SpecialAttachment.BOMBEUR_FUSE, 0) > 0;
    }

    /** Called every activation from {@code SmartZombie.tick}; each case self-gates on target + cooldown. */
    public static void tick(SmartZombie sz, ServerLevel level, WorldAIContext ctx) {
        SpecialType t = sz.pursuit().special();
        if (t.kind() != SpecialType.Kind.ACTIVE) {
            return;
        }
        Zombie z = sz.entity();
        LivingEntity tgt = z.getTarget();
        if (tgt == null) {
            tgt = sz.targetEntity(); // fall back to our own target (vanilla getTarget is set later in the tick)
        }
        switch (t) {
            case BOMBEUR -> {
                // Absolute deadline, not per-activation accumulation: this method only runs once every
                // `tickBuckets` ticks, so counting activations tied a gameplay tempo to a performance knob —
                // raising tickBuckets silently doubled the time before detonation.
                int fuse = z.getAttachedOrElse(SpecialAttachment.BOMBEUR_FUSE, 0);
                long now = level.getGameTime();
                if (fuse <= 0) {
                    double armRange = SpecialVariantConfig.specialBombeurArmRange;
                    boolean inRange = tgt != null && z.distanceToSqr(tgt) <= armRange * armRange;
                    if (!inRange) {
                        break;
                    }
                    fuse = BombeurBlast.fuseTicksFor(z.getRandom().nextDouble());
                    z.setAttached(SpecialAttachment.BOMBEUR_FUSE, fuse);
                    z.setAttached(SpecialAttachment.BOMBEUR_ARMED_AT, now);
                    if (LethalBreed.LOGGER.isDebugEnabled()) {
                        LethalBreed.LOGGER.debug("[LethalBreed] Bombeur armed at {} ({} blocks away, fuse={} ticks)",
                                tgt instanceof Player pl ? pl.getName().getString()
                                        : tgt.getClass().getSimpleName(),
                                Math.sqrt(z.distanceToSqr(tgt)), fuse);
                    }
                }
                long elapsed = now - z.getAttachedOrElse(SpecialAttachment.BOMBEUR_ARMED_AT, now);
                if (elapsed >= fuse) {
                    SpecialAbilities.bomb(level, z, fuse);
                } else {
                    // Derived, not accumulated — the belly swells linearly in real time, so a slowly
                    // inflating Bombeur reads as "long fuse", which is exactly "big explosion".
                    z.setAttached(SpecialAttachment.BOMBEUR_CHARGE, (float) elapsed / fuse);
                }
            }
            case HURLEUR -> {
                if (tgt != null && sz.pursuit().specialReady()) {
                    SpecialAbilities.hurl(sz, z, tgt, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            case SOIGNEUR -> {
                if (sz.pursuit().specialReady()) {
                    SpecialAbilities.heal(sz, z, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            case NECROMANCIEN -> {
                if (tgt != null && sz.pursuit().specialReady()) {
                    SpecialAbilities.summon(sz, level, z, ctx);
                    sz.pursuit().resetSpecialCd();
                }
            }
            default -> { }
        }
    }

    /** DEATH special: a Splitter spawns two small, non-special children. */
    public static void onDeath(Zombie z, ServerLevel level) {
        SpecialDeath.onDeath(z, level);
    }
}
