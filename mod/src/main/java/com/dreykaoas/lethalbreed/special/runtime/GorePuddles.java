package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The gore a Bomber leaves on the ground: a shrinking puddle that keeps dosing whoever stands in it after
 * the blast is long over. Fleeing the explosion no longer settles it — the ground itself stays hostile until
 * the residue drains.
 *
 * <p><b>Why this is not an {@code AreaEffectCloud}.</b> Vanilla's lingering cloud gates its victims on
 * {@code isAffectedByPotions()} alone, with no way to spare an ally. A Bomber bursting inside its own pack
 * would blanket that pack in Slowness — the variant would sabotage the horde it fights for. The cloud also
 * only knows how to apply {@code MobEffectInstance}s, so it could never carry this mod's contamination, and
 * it applies one flat dose regardless of where in the radius you stand, discarding the distance falloff that
 * is the whole point of {@link BomberBlast#intensity}. Ticking the puddle here reuses the burst's own victim
 * filter, its own falloff curve and its own gore application, so the puddle and the blast can never drift
 * apart.
 *
 * <p>State is deliberately transient. A puddle lives seconds, and persisting it would mean an attachment,
 * a codec and a reload path for something that has always expired before any realistic save. It is dropped
 * wholesale at {@code SERVER_STOPPED} so a closed world's {@link ServerLevel} is not pinned into the next
 * session, the same contract {@code ContaminationManager.onServerStopped} honours.
 */
public final class GorePuddles {
    private GorePuddles() {}

    /**
     * One puddle. {@code ratio} is the fuse ratio of the Bomber that left it, carried so the dose keeps
     * scaling with how long that zombie swelled — a puddle from a long fuse stays nastier, not just wider.
     */
    private static final class Puddle {
        final ServerLevel level;
        final double x, y, z;
        final double radius0;
        final double ratio;
        final int durationTicks;
        /** The cocktail the Bomber rolled as it burst — inherited, never re-rolled. */
        final List<GoreCocktail.Dose> cocktail;
        /**
         * Who this puddle has already rolled infection against. The puddle re-doses every
         * {@code PUDDLE_REAPPLY_TICKS}, so rolling per dose would turn a few seconds of standing in one into
         * a certainty; one roll per victim per puddle keeps infection a risk rather than a formality.
         * Keyed by UUID, not by entity, so a victim who leaves and returns is still remembered without this
         * set pinning a dead entity for the puddle's lifetime.
         */
        final Set<UUID> infectionRolled = new HashSet<>();
        int age;

        Puddle(ServerLevel level, double x, double y, double z, double radius0, double ratio, int durationTicks,
               List<GoreCocktail.Dose> cocktail) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius0 = radius0;
            this.ratio = ratio;
            this.durationTicks = durationTicks;
            this.cocktail = cocktail;
        }
    }

    private static final List<Puddle> ACTIVE = new ArrayList<>();

    /** Emit the puddle's particles this often. Every tick would be a needless packet storm for a haze. */
    private static final int PARTICLE_INTERVAL_TICKS = 5;

    /**
     * Leave a puddle where a Bomber burst. No-op for a degenerate radius or duration, so a server that has
     * configured the splatter away does not accumulate invisible zero-size entries.
     */
    public static void spawn(ServerLevel level, double x, double y, double z, double ratio, double splatterRadius,
                             List<GoreCocktail.Dose> cocktail) {
        double radius0 = BomberBlast.puddleRadius(splatterRadius);
        int duration = BomberBlast.puddleDurationTicks(ratio);
        if (radius0 <= 0.0 || duration <= 0 || cocktail.isEmpty()) {
            return;
        }
        ACTIVE.add(new Puddle(level, x, y, z, radius0, ratio, duration, cocktail));
    }

    /** END_SERVER_TICK: age every puddle, dose whoever is standing in one, and drop the expired. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        ACTIVE.removeIf(p -> {
            // Age first: a puddle created this tick should not immediately dose at full strength AND render,
            // and shrinking from age 0 keeps puddleRadiusAt's "reaches 0 exactly at expiry" contract honest.
            p.age++;
            if (p.age >= p.durationTicks) {
                return true;
            }
            double radius = BomberBlast.puddleRadiusAt(p.radius0, p.age, p.durationTicks);
            if (radius <= 0.0) {
                return true;
            }
            if (p.age % PARTICLE_INTERVAL_TICKS == 0) {
                SpecialAbilities.gorePuddleParticles(p.level, p.x, p.y, p.z, radius);
            }
            if (p.age % BomberBlast.PUDDLE_REAPPLY_TICKS == 0) {
                dose(p, radius);
            }
            return false;
        });
    }

    /** Apply one round of residue to everyone inside the puddle right now. */
    private static void dose(Puddle p, double radius) {
        for (LivingEntity victim : SpecialAbilities.splatterVictims(p.level, p.x, p.y, p.z, radius, null)) {
            double dist = Math.sqrt(victim.distanceToSqr(p.x, p.y, p.z));
            double intensity = BomberBlast.puddleIntensity(p.ratio, dist, radius);
            if (intensity <= 0.0) {
                continue;
            }
            GoreCocktail.apply(victim, p.cocktail, intensity);
            if (SpecialVariantConfig.specialBomberPuddleInfect && p.infectionRolled.add(victim.getUUID())
                    && victim.getRandom().nextDouble() < BomberBlast.infectChance(intensity)) {
                // add() returning true is the whole gate: it means this is the first time this puddle has
                // considered this victim. Placed before the chance roll on purpose — a victim who fails the
                // roll has had their chance and must not get another every second.
                ContaminationManager.contaminate(victim);
            }
        }
    }

    /** SERVER_STOPPED: drop every puddle so the closed world's level is not held into the next session. */
    public static void onServerStopped() {
        ACTIVE.clear();
    }
}
