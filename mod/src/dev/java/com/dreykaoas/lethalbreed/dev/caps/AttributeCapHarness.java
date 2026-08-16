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

    private final List<Zombie> spawned = new ArrayList<>();
    private int phaseForStage;

    private AttributeCapHarness() {
        super("caps",
                new Stage("phase-15", 20, 40),
                new Stage("phase-100", 60, 80),
                new Stage("phase-1000", 100, 120));
    }

    @Override
    protected boolean enabled() {
        return DevTestConfig.devCapsTest;
    }

    @Override
    protected void build(int stage, ServerLevel ow, MinecraftServer server, ConfigOverride cfg) {
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

    @Override
    protected void observe(int stage, ServerLevel ow, int tick) {
        // Nothing to watch: every attribute this harness measures is stamped once at finalizeSpawn and never
        // moves again. The observation window exists only so the zombies are fully in the world when read.
    }

    @Override
    protected void evaluate(int stage, ServerLevel ow, MinecraftServer server) {
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

        for (Zombie z : spawned) {
            z.discard();
        }
        spawned.clear();
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
