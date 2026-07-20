package com.dreykaoas.lethalbreed.dev.mechanics;

import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.effect.ContaminationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

/** Logs PASS/FAIL for the sun-burn, phase-scaling, and contamination mechanics. */
public final class MechTestEvaluator {
    private MechTestEvaluator() {}

    public static void evaluate(ServerLevel ow, MechTestState s) {
        // Sun-burn (the key check: Husk burns now too). Use the latched "was on fire" flag so a mob that
        // ignited then burned to death before this tick still counts as a PASS (instantaneous state is flaky).
        boolean huskBurn = s.husk != null && (s.huskWasOnFire || s.husk.getRemainingFireTicks() > 0);
        boolean zBurn = s.sunZombie != null && (s.sunZombieWasOnFire || s.sunZombie.getRemainingFireTicks() > 0);
        boolean bright = ow.isBrightOutside();
        boolean sky = s.husk != null && ow.canSeeSky(s.husk.blockPosition());
        LethalBreed.LOGGER.info("[MechTest] sunburn : {} (husk={} zombie={} | bright={} skyAtHusk={} huskPos={} removed={})",
                huskBurn && zBurn ? "PASS" : "FAIL", huskBurn, zBurn, bright, sky,
                s.husk == null ? "null" : s.husk.blockPosition(), s.husk != null && s.husk.isRemoved());

        // Phase-15 scaling: zombies are tanky (HP scaled up) and their health varies (per-zombie variation).
        // Zombies carry no gear, so there is nothing armor-related to check.
        int tanky = 0;
        double minHp = Double.MAX_VALUE, maxHp = 0;
        for (Zombie z : ow.getEntitiesOfClass(Zombie.class, new AABB(s.gearPos).inflate(12))) {
            double hp = z.getAttributeValue(Attributes.MAX_HEALTH);
            if (hp > 25.0) {
                tanky++;
            }
            minHp = Math.min(minHp, hp);
            maxHp = Math.max(maxHp, hp);
        }
        boolean scalePass = tanky > 0 && maxHp > minHp;
        LethalBreed.LOGGER.info("[MechTest] phasescale : {} (tanky={} hp={}–{})",
                scalePass ? "PASS" : "FAIL", tanky, String.format("%.1f", minHp), String.format("%.1f", maxHp));

        // Contamination: infected at least one + the ramping DoT killed it.
        int infect = ContaminationManager.INFECT_COUNT.get();
        int died = ContaminationManager.DEATH_COUNT.get();
        LethalBreed.LOGGER.info("[MechTest] contamination : {} (infect={} died={})",
                infect > 0 && died > 0 ? "PASS" : "FAIL", infect, died);

        // Flee + distress-rally: the wounded zombie must have screamed for help (distress emit) AND at least
        // one idle helper must have picked up sound-memory of the fleer (rallied to come help).
        int distress = com.dreykaoas.lethalbreed.entity.ZombieMood.DISTRESS_COUNT.get();
        boolean rallyPass = distress > 0 && s.rallyHelped;
        LethalBreed.LOGGER.info("[MechTest] flee-rally : {} (distressScreams={} helpersRallied={})",
                rallyPass ? "PASS" : "FAIL", distress, s.rallyHelped);
    }
}
