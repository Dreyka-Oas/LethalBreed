package com.dreykaoas.lethalbreed.dev.caps;

import com.dreykaoas.lethalbreed.config.domain.ProgressionConfig;
import com.dreykaoas.lethalbreed.dev.DevVerdict;
import com.dreykaoas.lethalbreed.dev.config.ConfigOverride;
import com.dreykaoas.lethalbreed.dev.config.DevTestConfig;
import com.dreykaoas.lethalbreed.dev.harness.TickPhasedHarness;
import com.dreykaoas.lethalbreed.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;

/**
 * Proves the no-one-shot promise where it actually has to hold: on the finished attributes of zombies spawned
 * at absurd phases.
 *
 * <p>This exists because every cheaper check is a proxy. The unit tests cover the curve and the correction
 * factor, but neither sees the real multiplication chain — the variation roll, the phase roll, a rolled
 * Strength landing in the base, a Juggernaut's health multiplier — and it is precisely that chain which
 * produced a player being two-shot through full netherite. So this spawns real zombies through the real
 * {@code finalizeSpawn} path and reads what came out.
 *
 * <p>Phase 15 is measured too, and deliberately: the ceilings must be a pure addition below the knee, so a
 * regression that quietly re-tunes the phases people are actually playing would show up here as a phase-15
 * damage figure that no longer matches the un-capped curve.
 */
public final class AttributeCapHarness extends TickPhasedHarness {

    public static final AttributeCapHarness INSTANCE = new AttributeCapHarness();

    /** How many zombies per phase. Every stat is a random roll, so one sample proves nothing; this is enough
     *  for the widest rolls (Strength at max amplifier) to show up reliably. */
    private static final int SAMPLE = 60;

    private static final int X = 400;
    private static final int Z = 400;
    private static final int Y = 101;

    private static final int[] PHASES = {15, 100, 1_000};
    /** Index of the stage that lands real bites on an armoured victim rather than reading attributes. */
    private static final int BITE_STAGE = 3;
    /** How many separate bites to land. Damage varies per zombie, so the worst of a batch is the figure
     *  that matters — a single bite could easily sample a weak roll and look safe. */
    private static final int BITES = 24;

    private final List<Zombie> spawned = new ArrayList<>();
    private final List<Cow> victims = new ArrayList<>();
    private int phaseForStage;
    /** Worst health loss from a single real bite through full-netherite protection. */
    private double worstBite;
    private int biteSamples;

    private AttributeCapHarness() {
        super("caps",
                new Stage("phase-15", 20, 40),
                new Stage("phase-100", 60, 80),
                new Stage("phase-1000", 100, 120),
                new Stage("morsure-reelle", 140, 170));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devCapsTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
        if (stage == BITE_STAGE) {
            buildBiteTest(ow, server);
            return;
        }
        phaseForStage = PHASES[stage];
        PhaseManager.get().setPhase(server, phaseForStage);
        spawned.clear();
        for (int i = 0; i < SAMPLE; i++) {
            Zombie z = EntityType.ZOMBIE.create(ow, EntitySpawnReason.NATURAL);
            if (z == null) {
                continue;
            }
            z.snapTo(X + (i % 10), Y, Z + (i / 10), 0f, 0f);
            // finalizeSpawn is what applies every multiplier this harness exists to measure. Spawning without
            // it would produce a vanilla zombie and a green result that means nothing.
            z.finalizeSpawn(ow, ow.getCurrentDifficultyAt(BlockPos.containing(z.position())),
                    EntitySpawnReason.NATURAL, null);
            z.setPersistenceRequired();
            ow.addFreshEntity(z);
            spawned.add(z);
        }
    }

    /**
     * The end-to-end check the attribute readings cannot give: a real zombie landing a real bite on a victim
     * wearing full-netherite protection, measured in health actually lost.
     *
     * <p>Everything above reads {@code ATTACK_DAMAGE} and then computes what armour would do to it. That is a
     * mirror of vanilla's formula, and a mirror can be wrong — which is exactly the class of mistake that
     * produced the original report. This lands the hit instead and reads the health bar.
     *
     * <p>Armour is granted as ATTRIBUTES, not equipment. Handing a mob armour pieces leaves its armour value
     * at zero, so equipping a victim would have quietly measured an unarmoured one and reported a much worse
     * number as if it were the netherite case.
     */
    private void buildBiteTest(ServerLevel ow, MinecraftServer server) {
        phaseForStage = 1_000;
        PhaseManager.get().setPhase(server, phaseForStage);
        worstBite = 0.0;
        biteSamples = 0;

        for (int i = 0; i < BITES; i++) {
            Cow victim = EntityType.COW.create(ow, EntitySpawnReason.COMMAND);
            Zombie biter = EntityType.ZOMBIE.create(ow, EntitySpawnReason.NATURAL);
            if (victim == null || biter == null) {
                continue;
            }
            victim.snapTo(X + i * 3, Y, Z + 40, 0f, 0f);
            armour(victim);
            victim.setHealth(victim.getMaxHealth());
            ow.addFreshEntity(victim);

            biter.snapTo(X + i * 3, Y, Z + 41, 0f, 0f);
            biter.finalizeSpawn(ow, ow.getCurrentDifficultyAt(BlockPos.containing(biter.position())),
                    EntitySpawnReason.NATURAL, null);
            biter.setPersistenceRequired();
            ow.addFreshEntity(biter);

            double before = victim.getHealth();
            // The real attack path: attribute lookup, damage source, armour absorption, enchantment hooks.
            if (biter.doHurtTarget(ow, victim)) {
                worstBite = Math.max(worstBite, before - victim.getHealth());
                biteSamples++;
            }
            spawned.add(biter);
            victims.add(victim);
        }
    }

    /** Full netherite, expressed as attributes: armour 20, toughness 12, and a player's 20 health so that
     *  "hits to kill" means the same thing it means for the person who reported this. */
    private static void armour(Cow victim) {
        var armor = victim.getAttribute(Attributes.ARMOR);
        var tough = victim.getAttribute(Attributes.ARMOR_TOUGHNESS);
        var health = victim.getAttribute(Attributes.MAX_HEALTH);
        if (armor != null) {
            armor.setBaseValue(20.0);
        }
        if (tough != null) {
            tough.setBaseValue(12.0);
        }
        if (health != null) {
            health.setBaseValue(20.0);
        }
    }

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        // Nothing to watch: every attribute this harness measures is stamped once at finalizeSpawn and never
        // moves again. The observation window exists only so the zombies are fully in the world when read.
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
        if (stage == BITE_STAGE) {
            evaluateBiteTest();
            return;
        }
        double maxDmg = 0.0, maxHp = 0.0, maxSpd = 0.0;
        for (Zombie z : spawned) {
            maxDmg = Math.max(maxDmg, z.getAttributeValue(Attributes.ATTACK_DAMAGE));
            maxHp = Math.max(maxHp, z.getAttributeValue(Attributes.MAX_HEALTH));
            maxSpd = Math.max(maxSpd, z.getAttributeValue(Attributes.MOVEMENT_SPEED));
        }
        // A tolerance, not a fudge: the correction is a float-rounded multiplier applied to a double, so
        // landing on 18.900000000000002 is correct behaviour and must not read as an escape.
        double eps = 1e-6;
        String p = "phase-" + phaseForStage;
        DevVerdict.check("caps", p + "/degats", maxDmg <= ProgressionConfig.phaseDamageCap + eps,
                "max=" + DevVerdict.fmt(maxDmg) + " plafond=" + DevVerdict.fmt(ProgressionConfig.phaseDamageCap)
                        + " coupsNetherite=" + hitsToKillInNetherite(maxDmg));
        DevVerdict.check("caps", p + "/pv", maxHp <= ProgressionConfig.phaseHealthCap + eps,
                "max=" + DevVerdict.fmt(maxHp) + " plafond=" + DevVerdict.fmt(ProgressionConfig.phaseHealthCap));
        DevVerdict.check("caps", p + "/vitesse", maxSpd <= ProgressionConfig.phaseSpeedCap + eps,
                "max=" + DevVerdict.fmt(maxSpd) + " plafond=" + DevVerdict.fmt(ProgressionConfig.phaseSpeedCap));
        // The promise the player actually made this request about, stated in the unit they care about.
        DevVerdict.check("caps", p + "/pas-de-one-shot", hitsToKillInNetherite(maxDmg) >= 3,
                "il faut " + hitsToKillInNetherite(maxDmg) + " coups pour tuer en netherite complete");

        // Enforcing twice must be a no-op, not an undo. A pass that reads the already-corrected value would
        // conclude the attribute is within its cap, drop its own correction, and let the raw value spring
        // back — a self-defeating cap that only fails on whichever zombies get enforced twice.
        double reDmg = 0.0, reHp = 0.0, reSpd = 0.0;
        for (Zombie z : spawned) {
            com.dreykaoas.lethalbreed.entity.AttributeCaps.enforce(z);
            reDmg = Math.max(reDmg, z.getAttributeValue(Attributes.ATTACK_DAMAGE));
            reHp = Math.max(reHp, z.getAttributeValue(Attributes.MAX_HEALTH));
            reSpd = Math.max(reSpd, z.getAttributeValue(Attributes.MOVEMENT_SPEED));
        }
        // Diagnostic: name the offender and every modifier on its health, so a failure is evidence rather
        // than a number. Only fires when something actually escaped.
        for (Zombie z : spawned) {
            double hp = z.getAttributeValue(Attributes.MAX_HEALTH);
            if (hp > ProgressionConfig.phaseHealthCap + eps) {
                var inst = z.getAttribute(Attributes.MAX_HEALTH);
                StringBuilder sb = new StringBuilder();
                sb.append("hp=").append(DevVerdict.fmt(hp)).append(" base=")
                        .append(DevVerdict.fmt(inst == null ? -1 : inst.getBaseValue()))
                        .append(" special=").append(com.dreykaoas.lethalbreed.special.SpecialType.fromId(z.getAttached(com.dreykaoas.lethalbreed.special.SpecialAttachment.SPECIAL)));
                if (inst != null) {
                    for (var m : inst.getModifiers()) {
                        sb.append(" | ").append(m.id().getPath()).append('=').append(DevVerdict.fmt(m.amount()))
                                .append('/').append(m.operation());
                    }
                }
                com.dreykaoas.lethalbreed.LethalBreed.LOGGER.info("[LB-Verify] caps/DIAG {}", sb);
            }
        }
        DevVerdict.check("caps", p + "/idempotent",
                reDmg <= ProgressionConfig.phaseDamageCap + eps
                        && reHp <= ProgressionConfig.phaseHealthCap + eps
                        && reSpd <= ProgressionConfig.phaseSpeedCap + eps,
                "apres 2e passe degats=" + DevVerdict.fmt(reDmg) + " pv=" + DevVerdict.fmt(reHp)
                        + " vitesse=" + DevVerdict.fmt(reSpd));

        for (Zombie z : spawned) {
            z.discard();
        }
        spawned.clear();
    }

    private void evaluateBiteTest() {
        int hits = worstBite <= 0.0 ? Integer.MAX_VALUE : (int) Math.ceil(20.0 / worstBite);
        DevVerdict.check("caps", "morsure-reelle/echantillons", biteSamples >= BITES / 2,
                "morsures atterries=" + biteSamples + "/" + BITES);
        DevVerdict.check("caps", "morsure-reelle/pas-de-one-shot", biteSamples > 0 && hits >= 3,
                "pire morsure=" + DevVerdict.fmt(worstBite) + " PV sur 20 -> " + hits + " coups");
        for (Zombie z : spawned) {
            z.discard();
        }
        for (Cow c : victims) {
            c.discard();
        }
        spawned.clear();
        victims.clear();
    }

    /**
     * Hits a full-netherite player (armour 20, toughness 12, no Protection) survives at {@code raw} damage.
     *
     * <p>Mirrors {@code CombatRules.getDamageAfterAbsorb} rather than trusting a remembered formula:
     * {@code f1 = clamp(armour - raw/(2 + toughness/4), 0.2*raw, 20)}, then {@code raw * (1 - f1/25)}.
     */
    private static int hitsToKillInNetherite(double raw) {
        if (raw <= 0.0) {
            return Integer.MAX_VALUE;
        }
        double f1 = Math.clamp(20.0 - raw / (2.0 + 12.0 / 4.0), 0.2 * raw, 20.0);
        double taken = raw * (1.0 - f1 / 25.0);
        return taken <= 0.0 ? Integer.MAX_VALUE : (int) Math.ceil(20.0 / taken);
    }
}
