package com.dreykaoas.lethalbreed.dev.special;

import com.dreykaoas.lethalbreed.GameState;
import com.dreykaoas.lethalbreed.LethalBreed;
import com.dreykaoas.lethalbreed.effect.LethalBreedEffects;
import com.dreykaoas.lethalbreed.special.SpecialBehavior;
import com.dreykaoas.lethalbreed.special.SpecialType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Logs PASS/FAIL per ability and kills splitters mid-run so their DEATH special fires. */
public final class SpecialTestEvaluator {
    private SpecialTestEvaluator() {}

    public static void killSplitters(ServerLevel ow, List<SpecialTestCase> cases) {
        for (SpecialTestCase c : cases) {
            if (c.type() == SpecialType.SPLITTER && c.z().isAlive()) {
                c.z().hurtServer(ow, c.z().damageSources().magic(), 1000f);
            }
        }
    }

    public static void evaluate(ServerLevel ow, List<SpecialTestCase> cases) {
        for (SpecialTestCase c : cases) {
            Zombie z = c.z();
            Cow cow = c.cow();
            boolean pass;
            String detail;
            switch (c.type()) {
                case SPRINTEUR -> { pass = z.getEffect(MobEffects.SPEED) != null; detail = "speed effect"; }
                case BONDISSEUR -> { pass = z.getEffect(LethalBreedEffects.LEAP) != null; detail = "LEAP effect"; }
                case JUGGERNAUT -> { pass = !z.getItemBySlot(EquipmentSlot.HEAD).isEmpty(); detail = "iron armor"; }
                case FOUISSEUR -> { pass = z.getEffect(MobEffects.HASTE) != null; detail = "haste effect"; }
                case TOXIQUE -> { pass = cow != null && cow.getEffect(MobEffects.POISON) != null; detail = "cow poisoned"; }
                case GIVRE -> { pass = cow != null && cow.getEffect(MobEffects.SLOWNESS) != null; detail = "cow slowed"; }
                case CRACHEUR -> { pass = SpecialBehavior.SPIT_COUNT.get() > 0; detail = "spit x" + SpecialBehavior.SPIT_COUNT.get(); }
                case BOMBEUR -> { pass = z.isRemoved(); detail = "exploded (removed)"; }
                case HURLEUR -> {
                    pass = SpecialBehavior.HURL_COUNT.get() > 0;
                    boolean hasTgt = z.getTarget() != null;
                    var esz = c.extra() == null ? null : GameState.REGISTRY.get(c.extra().getId());
                    int near = ow.getEntitiesOfClass(Zombie.class, new AABB(z.blockPosition()).inflate(24)).size();
                    detail = "retargets x" + SpecialBehavior.HURL_COUNT.get()
                            + " hurlTgt=" + hasTgt + " extraTgt=" + (esz != null && esz.hasTarget())
                            + " near=" + near;
                }
                case SOIGNEUR -> {
                    pass = SpecialBehavior.HEAL_COUNT.get() > 0;
                    boolean extraRegen = c.extra() != null && c.extra().getEffect(MobEffects.REGENERATION) != null;
                    detail = "heals x" + SpecialBehavior.HEAL_COUNT.get() + " extraRegen=" + extraRegen;
                }
                case NECROMANCIEN -> { pass = SpecialBehavior.SUMMON_COUNT.get() > 0; detail = "summons x" + SpecialBehavior.SUMMON_COUNT.get(); }
                case SPLITTER -> {
                    int kids = ow.getEntitiesOfClass(Zombie.class, new AABB(c.pos()).inflate(5)).size();
                    pass = kids >= 1; detail = "children=" + kids;
                }
                default -> { pass = false; detail = "n/a"; }
            }
            LethalBreed.LOGGER.info("[SpecialTest] {} : {} ({})", c.type().id(), pass ? "PASS" : "FAIL", detail);
        }
    }
}
