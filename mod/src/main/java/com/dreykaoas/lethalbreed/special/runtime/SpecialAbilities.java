package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.PackConfig;
import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.config.domain.TargetingConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import com.dreykaoas.lethalbreed.pack.PackState;
import com.dreykaoas.lethalbreed.dimension.WorldAIContext;
import com.dreykaoas.lethalbreed.entity.SmartZombie;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.special.SpecialRoller;
import com.dreykaoas.lethalbreed.special.SpecialType;
import com.dreykaoas.lethalbreed.util.Players;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Per-activation behaviours for ACTIVE specials. Each method self-contains one ability; the dispatch in
 * {@link SpecialBehavior} decides which fires and owns cooldown gating. Dev counters live on SpecialBehavior.
 */
public final class SpecialAbilities {
    private SpecialAbilities() {}

    /**
     * BOMBEUR: burst, then splatter everything in the wider gore ring with infectious status effects.
     *
     * <p>The blast is only half of it. The splatter ring reaches {@code specialBombeurSplatterMul} times
     * further, so retreating out of lethal range still leaves a victim inside the gore — distance buys
     * hit points, not a clean escape.
     *
     * @param fuseTicks how long this Bombeur swelled; drives both the power and the splatter intensity
     */
    public static void bomb(ServerLevel level, Zombie z, int fuseTicks) {
        double ratio = BombeurBlast.ratioOf(fuseTicks);
        double power = BombeurBlast.powerFor(ratio);
        double splatR = BombeurBlast.splatterRadius(power);
        double cx = z.getX(), cy = z.getY() + 0.5, cz = z.getZ();

        // Gather BEFORE the explosion: it kills and flings victims, and anyone it launched out of the ring
        // was still standing in the gore at the moment it burst.
        // Players.isTargetable gates every other way the mod touches a player (targeting, sound, flow field,
        // mood, damage events). The splatter must respect it too: vanilla already shields a spectator from the
        // blast, and without this a spectator flying past would eat Nausea, Poison, Slowness and — past
        // intensity 0.75 — a black screen. It is also the only infection path that needs no damage event.
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - splatR, cy - splatR, cz - splatR, cx + splatR, cy + splatR, cz + splatR),
                e -> e != z && e.isAlive() && !(e instanceof Zombie)
                        && !(e instanceof Player p && !Players.isTargetable(p)));
        RandomSource rng = z.getRandom();

        level.explode(z, cx, cy, cz, (float) power, Level.ExplosionInteraction.NONE);
        z.discard();
        splatterCloud(level, cx, cy, cz, splatR);

        for (LivingEntity victim : caught) {
            // The AABB is a box; the ring is a sphere. Re-measure so corners don't get splattered.
            double intensity = BombeurBlast.intensity(ratio, Math.sqrt(victim.distanceToSqr(cx, cy, cz)), splatR);
            if (intensity > 0.0) {
                splatter(victim, intensity, rng);
            }
        }
    }

    /**
     * Particles must not drift: {@code sendParticles}' speed argument is a per-axis gaussian VELOCITY
     * multiplier, so any non-zero value scatters the cloud within a tick or two instead of leaving it
     * hanging over the gore. Position spread comes from the xyz offsets, not from here.
     */
    private static final double SPLATTER_PARTICLE_SPEED = 0.0;

    /**
     * Purely cosmetic: a burst of coloured particles at the blast centre, the same visual family vanilla uses
     * for splash-potion impact, so the infectious ring the explosion just applied invisibly to victims reads
     * as one. Scales with the splatter radius — a long-fused Bombeur's bigger gore ring looks bigger too.
     * Carries no gameplay: nothing here touches damage, effects or targeting. The alpha that makes the cloud
     * visible at all is baked into {@link BombeurBlast#SPLATTER_COLOR_ARGB} — see that constant.
     */
    private static void splatterCloud(ServerLevel level, double cx, double cy, double cz, double splatR) {
        int count = (int) Math.round(20 * Math.max(1.0, splatR / 3.0));
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, BombeurBlast.SPLATTER_COLOR_ARGB),
                cx, cy, cz, count, splatR * 0.6, splatR * 0.3, splatR * 0.6, SPLATTER_PARTICLE_SPEED);
    }

    /**
     * Apply one victim's share of the gore. Zombies are filtered out by the caller: they are the vector, not
     * the victim — and {@code contaminate()} refuses them anyway.
     */
    private static void splatter(LivingEntity victim, double intensity, RandomSource rng) {
        victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, BombeurBlast.nauseaTicks(intensity), 0));
        victim.addEffect(new MobEffectInstance(MobEffects.POISON,
                BombeurBlast.poisonTicks(intensity), BombeurBlast.poisonAmp(intensity)));
        victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                BombeurBlast.slowTicks(intensity), BombeurBlast.slowAmp(intensity)));
        int blind = BombeurBlast.blindTicks(intensity);
        if (blind > 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blind, 0));
        }
        if (rng.nextDouble() < BombeurBlast.infectChance(intensity)) {
            ContaminationManager.contaminate(victim);
        }
    }

    /**
     * Health one activation restores: what the configured Regeneration would have healed over its duration.
     * Vanilla regen heals 1 HP every {@code max(50 >> amp, 1)} ticks, hence the shift.
     */
    static float healAmount() {
        int period = Math.max(50 >> Math.max(0, SpecialVariantConfig.specialSoigneurRegenAmp), 1);
        return Math.max(0, SpecialVariantConfig.specialSoigneurRegenTicks) / (float) period;
    }

    /**
     * HURLEUR: hand the caller's target to nearby target-less smart zombies.
     *
     * <p>The rally also plants a memory, exactly as {@code SoundEventBus} does for a heard noise. Without it
     * the handover survived only until the recruit's next classify: {@code LODManager} re-runs its own
     * detection, finds the prey outside that zombie's {@code targetDetectRadius}, and — with no memory to
     * fall back on — drops straight to the terminal branch that clears everything and freezes. The rally
     * would then be undone within a couple of activations, which is why a Hurleur never seemed to recruit
     * more than one or two.
     */
    public static void hurl(SmartZombie sz, Zombie z, LivingEntity tgt, WorldAIContext ctx) {
        long expire = z.level().getGameTime() + TargetingConfig.targetMemoryTicks;
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialHurleurRadius)) {
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
     * SOIGNEUR: restore health to nearby living smart zombies.
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
     * <p>{@code specialSoigneurRegenTicks} and {@code specialSoigneurRegenAmp} keep their names and their
     * arithmetic: the heal is what that Regeneration WOULD have delivered over its full duration, i.e.
     * {@code ticks / (50 >> amp)} health, so tuning either option still moves the number the same way.
     */
    public static void heal(SmartZombie sz, Zombie z, WorldAIContext ctx) {
        float amount = healAmount();
        for (SmartZombie o : ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialSoigneurRadius)) {
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

    /** NECROMANCIEN: summon child zombies, capped against an already-dense local pack. */
    public static void summon(SmartZombie sz, ServerLevel level, Zombie z, WorldAIContext ctx) {
        if (ctx.spatialGrid().queryRadius(z.getX(), z.getY(), z.getZ(),
                SpecialVariantConfig.specialNecromancienDensityRadius).size()
                > SpecialVariantConfig.specialNecromancienDensityCap) {
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
        int min = SpecialVariantConfig.specialNecromancienMinChildren;
        int max = Math.max(min, SpecialVariantConfig.specialNecromancienMaxChildren);
        int n = min + level.getRandom().nextInt(max - min + 1);
        int spread = SpecialVariantConfig.specialNecromancienSpread;
        for (int i = 0; i < n; i++) {
            Zombie child = ChildSpawner.spawnNear(level, z, spread);
            if (child != null) {
                // No chain-summoning, mirroring SpecialDeath's rule for Splitter children. A child rolls its
                // own special inside finalizeSpawn, and from the phase where this type exists that is a real
                // chance of drawing NECROMANCIEN — each second-generation summoner then wanders into its own
                // bubble where neither the density cap (a 12-block radius) nor the pack cap can see it, and
                // nothing in this mod ever despawns.
                SpecialRoller.assign(child, SpecialType.NONE);
                SpecialBehavior.SUMMON_COUNT.incrementAndGet();
            }
        }
    }
}
