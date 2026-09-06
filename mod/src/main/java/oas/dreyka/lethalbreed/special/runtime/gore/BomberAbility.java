package oas.dreyka.lethalbreed.special.runtime.gore;

import oas.dreyka.lethalbreed.special.runtime.BomberBlast;
import oas.dreyka.lethalbreed.effect.ContaminationManager;
import oas.dreyka.lethalbreed.phase.PhaseManager;
import oas.dreyka.lethalbreed.util.Players;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Bomber's burst, and the gore it leaves behind. Split out of {@link SpecialAbilities} because it is
 * the only ability with an aftermath: the ring, the puddle and their particles all share one rolled
 * cocktail and one victim rule, and keeping them together is what guarantees the puddle can never touch
 * someone the explosion spared.
 */
public final class BomberAbility {
    private BomberAbility() {}

    /**
     * Particles must not drift: {@code sendParticles}' speed argument is a per-axis gaussian VELOCITY
     * multiplier, so any non-zero value scatters the cloud within a tick or two instead of leaving it
     * hanging over the gore. Position spread comes from the xyz offsets, not from here.
     */
    private static final double PARTICLE_SPEED = 0.0;

    /**
     * Burst, then splatter everything in the wider gore ring with infectious status effects.
     *
     * <p>The blast is only half of it. The splatter ring reaches {@code specialBomberSplatterMul} times
     * further, so retreating out of lethal range still leaves a victim inside the gore: distance buys hit
     * points, not a clean escape.
     *
     * @param fuseTicks how long this Bomber swelled; drives both the power and the splatter intensity
     */
    public static void bomb(ServerLevel level, Zombie z, int fuseTicks) {
        double ratio = BomberBlast.ratioOf(fuseTicks);
        double power = BomberBlast.powerFor(ratio);
        double splatR = BomberBlast.splatterRadius(power);
        double cx = z.getX(), cy = z.getY() + 0.5, cz = z.getZ();

        // Gather BEFORE the explosion: it kills and flings victims, and anyone it launched out of the ring
        // was still standing in the gore at the moment it burst.
        List<LivingEntity> caught = splatterVictims(level, cx, cy, cz, splatR, z);
        RandomSource rng = z.getRandom();

        // Rolled ONCE, for the blast and the puddle alike. A cocktail re-rolled per victim would mean two
        // players standing side by side reporting different symptoms from the same explosion, and a puddle
        // that contradicts the burst that created it. One Bomber, one poison. Blindness eligibility is judged
        // at the centre, the harshest point, so whether it is in the mix at all is a property of the Bomber
        // while who is close enough to get a long dose stays a property of distance.
        List<GoreCocktail.Dose> cocktail = GoreCocktail.roll(
                PhaseManager.current(), BomberBlast.intensity(ratio, 0.0, splatR), rng);

        level.explode(z, cx, cy, cz, (float) power, Level.ExplosionInteraction.NONE);
        z.discard();
        splatterCloud(level, cx, cy, cz, splatR);
        // The blast is over in a tick; the mess it made is not. Whoever walks back through the gore keeps
        // paying for it until the residue drains.
        GorePuddles.spawn(level, cx, cy, cz, ratio, splatR, cocktail);

        for (LivingEntity victim : caught) {
            // The AABB is a box; the ring is a sphere. Re-measure so corners do not get splattered.
            double intensity = BomberBlast.intensity(ratio, Math.sqrt(victim.distanceToSqr(cx, cy, cz)), splatR);
            if (intensity > 0.0) {
                GoreCocktail.apply(victim, cocktail, intensity);
                if (rng.nextDouble() < BomberBlast.infectChance(intensity)) {
                    ContaminationManager.contaminate(victim);
                }
            }
        }
    }

    /**
     * Everyone a gore radius may legitimately touch, shared by the burst and by {@link GorePuddles} so the
     * puddle can never splatter someone the explosion would have spared.
     *
     * <p>Zombies are excluded because they are the vector, not the victim; without that a Bomber bursting
     * inside its own pack would blanket that pack in Slowness. {@code Players.isTargetable} gates every other
     * way the mod touches a player (targeting, sound, flow field, mood, damage events) and must gate this
     * too: vanilla already shields a spectator from the blast, and without it a spectator flying past would
     * eat Nausea, Poison, Slowness and, past the blindness threshold, a black screen. It is also the only
     * infection path that needs no damage event.
     *
     * <p>The returned box is the radius' bounding cube, not the sphere: callers re-measure the real distance,
     * which they need anyway to scale the dose.
     *
     * @param source the bursting zombie to exclude, or {@code null} when there is none (a lingering puddle
     *               outlives the Bomber that left it)
     */
    public static List<LivingEntity> splatterVictims(ServerLevel level, double cx, double cy, double cz, double radius,
                                              Zombie source) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius),
                e -> e != source && e.isAlive() && !(e instanceof Zombie)
                        && !(e instanceof Player p && !Players.isTargetable(p)));
    }

    /**
     * Purely cosmetic: a burst of coloured particles at the blast centre, the same visual family vanilla uses
     * for splash-potion impact, so the infectious ring the explosion just applied invisibly to victims reads
     * as one. Scales with the splatter radius, so a long-fused Bomber's bigger gore ring looks bigger too.
     * Carries no gameplay. The alpha that makes the cloud visible at all is baked into
     * {@link BomberBlast#SPLATTER_COLOR_ARGB}.
     */
    private static void splatterCloud(ServerLevel level, double cx, double cy, double cz, double splatR) {
        int count = (int) Math.round(20 * Math.max(1.0, splatR / 3.0));
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, BomberBlast.SPLATTER_COLOR_ARGB),
                cx, cy, cz, count, splatR * 0.6, splatR * 0.3, splatR * 0.6, PARTICLE_SPEED);
    }

    /**
     * The lingering puddle's haze, emitted repeatedly by {@link GorePuddles} as the residue shrinks. Spread
     * flat, the vertical spread a tenth of the horizontal, so it reads as gore pooled on the ground rather
     * than a second airborne burst, and so its edge shows a player exactly where it is still unsafe to step.
     * Density is tied to area, not radius, so a shrinking puddle thins out instead of concentrating into an
     * ever-brighter dot.
     */
    public static void gorePuddleParticles(ServerLevel level, double cx, double cy, double cz, double radius) {
        int count = Math.max(1, (int) Math.round(3.0 * radius * radius));
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, BomberBlast.SPLATTER_COLOR_ARGB),
                cx, cy, cz, count, radius * 0.5, 0.05 * radius, radius * 0.5, PARTICLE_SPEED);
    }
}
