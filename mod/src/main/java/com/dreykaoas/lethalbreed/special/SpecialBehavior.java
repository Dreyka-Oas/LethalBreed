package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.runtime.SpecialAbilities;
import com.dreykaoas.lethalbreed.special.runtime.SpecialDeath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.concurrent.atomic.AtomicInteger;

/** Runtime behaviour for ACTIVE special zombies (per-activation, cooldown-gated) and DEATH specials. */
public final class SpecialBehavior {
    private SpecialBehavior() {}

    // Dev instrumentation (headless test harness reads these to confirm abilities fired).
    public static final AtomicInteger SUMMON_COUNT = new AtomicInteger();
    public static final AtomicInteger HURL_COUNT = new AtomicInteger();
    public static final AtomicInteger HEAL_COUNT = new AtomicInteger();

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
                // Belly-swell fuse: once the target is within 3 blocks the zombie commits — the belly
                // charge ramps (synced to clients for the render-side inflation) and it explodes at full.
                float charge = z.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f);
                boolean armed = charge > 0.0f;
                double armRange = SpecialVariantConfig.specialBombeurArmRange;
                boolean inRange = tgt != null && z.distanceToSqr(tgt) <= armRange * armRange;
                if (armed || inRange) {
                    charge += (float) SpecialVariantConfig.specialBombeurFusePerTick;
                    if (charge >= 1.0f) {
                        SpecialAbilities.bomb(level, z);
                    } else {
                        z.setAttached(SpecialAttachment.BOMBEUR_CHARGE, charge);
                    }
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
                    SpecialAbilities.summon(level, z, ctx);
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
