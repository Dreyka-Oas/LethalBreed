package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.dimension.WorldAiContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.List;

/**
 * Per-activation behaviours for ACTIVE specials. Each method self-contains one ability; the dispatch in
 * {@link SpecialBehavior} decides which fires and owns cooldown gating. Dev counters live on SpecialBehavior.
 */
public final class SpecialAbilities {
    private SpecialAbilities() {}

    /** BOMBER: burst, then splatter the wider gore ring. See {@link BomberAbility#bomb}. */
    public static void bomb(ServerLevel level, Zombie z, int fuseTicks) {
        BomberAbility.bomb(level, z, fuseTicks);
    }

    /**
     * Health one activation restores: what the configured Regeneration would have healed over its duration.
     * Vanilla regen heals 1 HP every {@code max(50 >> amp, 1)} ticks, hence the shift.
     */
    static float healAmount() {
        int period = Math.max(50 >> Math.max(0, SpecialVariantConfig.specialHealerRegenAmp), 1);
        return Math.max(0, SpecialVariantConfig.specialHealerRegenTicks) / (float) period;
    }

    /**
     * SCREAMER: hand the caller's target to nearby target-less smart zombies.
     *
     * <p>The rally also plants a memory, exactly as {@code SoundEventBus} does for a heard noise. Without it
     * the handover survived only until the recruit's next classify: {@code LodManager} re-runs its own
     * detection, finds the prey outside that zombie's {@code targetDetectRadius}, and — with no memory to
     * fall back on — drops straight to the terminal branch that clears everything and freezes. The rally
     * would then be undone within a couple of activations, which is why a Screamer never seemed to recruit
     * more than one or two.
     */
    public static void hurl(SmartZombie sz, Zombie z, LivingEntity tgt, WorldAiContext ctx) {
        long expire = z.level().getGameTime() + TargetingConfig.targetMemoryTicks;
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialScreamerRadius)) {
            // isAlive mirrors heal(): the grid can still hold a zombie for up to tickBuckets ticks after it
            // dies, and retargeting a corpse is pure waste that also inflates the dev counter.
            if (o != sz && o.entity().isAlive() && !o.hasTarget()) {
                o.entity().setTarget(tgt);
                o.pursuit().setTarget(tgt, tgt.getX(), tgt.getY(), tgt.getZ());
                if (TargetingConfig.targetMemoryTicks > 0) {
                    o.pursuit().rememberTarget(tgt.getX(), tgt.getY(), tgt.getZ(), expire);
                }
                SpecialBehavior.HURL_COUNT.incrementAndGet();
            }
        }
    }

    /**
     * HEALER: restore health to nearby living smart zombies.
     *
     * <p>This used to apply {@link MobEffects#REGENERATION} — and healed nothing at all. Vanilla's
     * {@code canBeAffected} rejects Regeneration for everything tagged {@code ignores_poison_and_regen},
     * which covers {@code #undead} and therefore every zombie; {@code addEffect} bails out before writing,
     * and {@code forceAddEffect} runs the same check first, so neither route works. The aura was a no-op for
     * its whole existence, hidden because the dev counter incremented regardless of the return value.
     *
     * <p>Forcing the effect through would mean a mixin exempting Regeneration globally, which would also let
     * every vanilla regeneration potion, beacon and lingering cloud heal zombies — far outside this variant's
     * remit. Healing directly is the mechanic that was actually meant.
     *
     * <p>{@code specialHealerRegenTicks} and {@code specialHealerRegenAmp} keep their names and their
     * arithmetic: the heal is what that Regeneration WOULD have delivered over its full duration, i.e.
     * {@code ticks / (50 >> amp)} health, so tuning either option still moves the number the same way.
     */
    public static void heal(SmartZombie sz, Zombie z, WorldAiContext ctx) {
        float amount = healAmount();
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialHealerRadius)) {
            if (o != sz && o.entity().isAlive()) {
                Zombie other = o.entity();
                // Count only healing that actually landed. A counter that ticks up on a full-health zombie is
                // exactly what let the no-op hide for so long, and the dev suite reads this counter.
                if (other.getHealth() < other.getMaxHealth() && amount > 0.0f) {
                    other.heal(amount);
                    SpecialBehavior.HEAL_COUNT.incrementAndGet();
                }
            }
        }
    }

    /** NECROMANCER: summon child zombies, capped against an already-dense local pack. */
    public static void summon(SmartZombie sz, ServerLevel level, Zombie z, WorldAiContext ctx) {
        if (ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialNecromancerDensityRadius).size()
                > SpecialVariantConfig.specialNecromancerDensityCap) {
            return;
        }
        // A summoner inside a pack is also capped by that pack's size. The density cap alone counts a radius,
        // not a roster: a Necromancer marching with its pack keeps summoning until 40 zombies stand within 12
        // blocks, and since this mod never despawns anything, the pack grows without bound for the rest of the
        // world's life.
        PackState pack = ctx.packManager().get(sz.pursuit().pack().packId());
        if (pack != null && pack.totalMembers() >= PackConfig.packMaxSize) {
            return;
        }
        int min = SpecialVariantConfig.specialNecromancerMinChildren;
        int max = Math.max(min, SpecialVariantConfig.specialNecromancerMaxChildren);
        int n = min + level.getRandom().nextInt(max - min + 1);
        int spread = SpecialVariantConfig.specialNecromancerSpread;
        for (int i = 0; i < n; i++) {
            Zombie child = ChildSpawner.spawnNear(level, z, spread);
            if (child != null) {
                // No chain-summoning, mirroring SpecialDeath's rule for Splitter children. A child rolls its
                // own special inside finalizeSpawn, and from the phase where this type exists that is a real
                // chance of drawing NECROMANCER — each second-generation summoner then wanders into its own
                // bubble where neither the density cap (a 12-block radius) nor the pack cap can see it, and
                // nothing in this mod ever despawns.
                SpecialRoller.assign(child, SpecialType.NONE);
                SpecialBehavior.SUMMON_COUNT.incrementAndGet();
            }
        }
    }
}
