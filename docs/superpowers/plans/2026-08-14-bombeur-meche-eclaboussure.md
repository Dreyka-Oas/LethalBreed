# Bombeur — mèche aléatoire et éclaboussure infectieuse — plan d'implémentation

> **Pour les agents :** SOUS-COMPÉTENCE REQUISE — utiliser `superpowers:subagent-driven-development`
> (recommandé) ou `superpowers:executing-plans` pour dérouler ce plan tâche par tâche. Les étapes utilisent
> la syntaxe case à cocher (`- [ ]`).

**But :** remplacer la mèche fixe du Bombeur par une mèche aléatoire découplée de `tickBuckets`, lier la
puissance d'explosion à la durée tirée, et ajouter une zone d'éclaboussure qui applique des effets de statut
au-delà du rayon de souffle.

**Architecture :** tout le calcul (mèche → ratio → puissance → intensité → effets) vit dans une classe pure
sans type Minecraft, `BombeurBlast`, testable en unitaire sans serveur. Le code qui touche aux entités reste
dans `SpecialBehavior` (l'échéance) et `SpecialAbilities` (l'explosion et l'éclaboussure).

**Stack :** Java 21, Minecraft 1.21.11, Fabric Loom, JUnit 5. Le schéma de config est réflexif — un champ
`public static` non-`final` dans la classe de domaine s'expose automatiquement au JSON, au GUI et à
`/lethalconfig`.

**Spec :** [2026-08-14-bombeur-meche-eclaboussure-design.md](../specs/2026-08-14-bombeur-meche-eclaboussure-design.md)

## Contraintes globales

- Fichiers Java du mod : encodage UTF-8, fins de ligne **LF** (vérifier avant commit — une conversion CRLF
  accidentelle transforme une retouche de 3 lignes en diff de 300 lignes).
- `BombeurBlast` ne doit importer **aucun** type `net.minecraft.*`. C'est ce qui rend ses tests exécutables
  sans démarrer de serveur.
- Aucune option de config nouvelle hors des cinq du §8 du spec. Les coefficients de forme des courbes restent
  des constantes nommées dans `BombeurBlast`.
- Toute lecture de paire min/max doit **réordonner** si l'utilisateur a inversé les bornes.
- Ne jamais se mettre en co-auteur ni ajouter de ligne `Co-Authored-By:` aux commits.

---

### Tâche 1 : `BombeurBlast` — le calcul pur

**Fichiers :**
- Créer : `mod/src/main/java/com/dreykaoas/lethalbreed/special/runtime/BombeurBlast.java`
- Créer : `mod/src/test/java/com/dreykaoas/lethalbreed/special/BombeurBlastTest.java`

**Interfaces :**
- Consomme : `SpecialVariantConfig` (champs de la tâche 2 — écrire la tâche 2 d'abord si l'ordre est libre).
- Produit : l'API statique ci-dessous, utilisée par les tâches 3 et 4.

```java
static int    fuseTicksFor(double rand01)                       // durée tirée, ticks
static double ratioOf(int fuseTicks)                            // [0,1]
static double powerFor(double ratio)                            // [powerMin, powerMax]
static double blastRadius(double power)                         // power * 2
static double splatterRadius(double power)                      // blastRadius * splatterMul
static double intensity(double ratio, double dist, double splatR)
static int    nauseaTicks(double i)
static int    poisonTicks(double i)
static int    poisonAmp(double i)
static int    slowTicks(double i)
static int    slowAmp(double i)
static int    blindTicks(double i)                              // 0 = pas de cécité
static double infectChance(double i)
```

- [ ] **Étape 1 : écrire les tests qui échouent**

```java
package com.dreykaoas.lethalbreed.special;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;
import com.dreykaoas.lethalbreed.special.runtime.BombeurBlast;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BombeurBlastTest {

    @BeforeEach
    void resetConfig() {
        SpecialVariantConfig.specialBombeurFuseMinTicks = 30;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 120;
        SpecialVariantConfig.specialBombeurPowerMin = 2.0;
        SpecialVariantConfig.specialBombeurPowerMax = 5.0;
        SpecialVariantConfig.specialBombeurSplatterMul = 1.5;
        SpecialVariantConfig.specialBombeurInfectChance = 0.5;
        SpecialVariantConfig.specialBombeurBlindThreshold = 0.75;
    }

    @Test
    void fuseSpansTheConfiguredRange() {
        assertEquals(30, BombeurBlast.fuseTicksFor(0.0));
        assertEquals(120, BombeurBlast.fuseTicksFor(1.0));
        assertEquals(75, BombeurBlast.fuseTicksFor(0.5));
    }

    @Test
    void fuseRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBombeurFuseMinTicks = 120;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 30;
        assertEquals(30, BombeurBlast.fuseTicksFor(0.0));
        assertEquals(120, BombeurBlast.fuseTicksFor(1.0));
    }

    @Test
    void ratioIsZeroAtMinAndOneAtMax() {
        assertEquals(0.0, BombeurBlast.ratioOf(30), 1e-9);
        assertEquals(1.0, BombeurBlast.ratioOf(120), 1e-9);
        assertTrue(BombeurBlast.ratioOf(60) > BombeurBlast.ratioOf(45));
    }

    @Test
    void ratioIsZeroWhenTheRangeIsDegenerate() {
        SpecialVariantConfig.specialBombeurFuseMinTicks = 60;
        SpecialVariantConfig.specialBombeurFuseMaxTicks = 60;
        assertEquals(0.0, BombeurBlast.ratioOf(60), 1e-9);
    }

    @Test
    void powerFollowsTheFuse() {
        assertEquals(2.0, BombeurBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BombeurBlast.powerFor(1.0), 1e-9);
        assertEquals(3.5, BombeurBlast.powerFor(0.5), 1e-9);
    }

    @Test
    void powerRangeIsReorderedWhenInverted() {
        SpecialVariantConfig.specialBombeurPowerMin = 5.0;
        SpecialVariantConfig.specialBombeurPowerMax = 2.0;
        assertEquals(2.0, BombeurBlast.powerFor(0.0), 1e-9);
        assertEquals(5.0, BombeurBlast.powerFor(1.0), 1e-9);
    }

    @Test
    void splatterReachesFurtherThanTheBlast() {
        double power = 3.0;
        assertEquals(6.0, BombeurBlast.blastRadius(power), 1e-9);
        assertEquals(9.0, BombeurBlast.splatterRadius(power), 1e-9);
    }

    @Test
    void intensityIsOneOnlyAtContactWithTheLongestFuse() {
        double splatR = BombeurBlast.splatterRadius(BombeurBlast.powerFor(1.0));
        assertEquals(1.0, BombeurBlast.intensity(1.0, 0.0, splatR), 1e-9);
        assertEquals(0.4, BombeurBlast.intensity(0.0, 0.0, splatR), 1e-9);
    }

    @Test
    void intensityIsZeroAtTheSplatterEdgeAndBeyond() {
        double splatR = 15.0;
        assertEquals(0.0, BombeurBlast.intensity(1.0, 15.0, splatR), 1e-9);
        assertEquals(0.0, BombeurBlast.intensity(1.0, 40.0, splatR), 1e-9);
    }

    @Test
    void intensityDecreasesWithDistance() {
        double splatR = 15.0;
        double near = BombeurBlast.intensity(1.0, 2.0, splatR);
        double far = BombeurBlast.intensity(1.0, 10.0, splatR);
        assertTrue(near > far, "near=" + near + " far=" + far);
    }

    @Test
    void effectShapesMatchTheSpecTable() {
        // Spec §6 : intensité 1.0 → Nausée 15 s, Poison 12 s A1, Lenteur 12 s A2, Cécité 5 s, 50 %.
        assertEquals(300, BombeurBlast.nauseaTicks(1.0));
        assertEquals(240, BombeurBlast.poisonTicks(1.0));
        assertEquals(1, BombeurBlast.poisonAmp(1.0));
        assertEquals(240, BombeurBlast.slowTicks(1.0));
        assertEquals(2, BombeurBlast.slowAmp(1.0));
        assertEquals(100, BombeurBlast.blindTicks(1.0));
        assertEquals(0.5, BombeurBlast.infectChance(1.0), 1e-9);
    }

    @Test
    void poisonAmplifierStepsUpAtItsThreshold() {
        assertEquals(0, BombeurBlast.poisonAmp(0.59));
        assertEquals(1, BombeurBlast.poisonAmp(0.61));
    }

    @Test
    void blindnessOnlyAppearsAboveTheThreshold() {
        assertEquals(0, BombeurBlast.blindTicks(0.74));
        assertTrue(BombeurBlast.blindTicks(0.76) > 0);
    }

    @Test
    void blindnessIsDisabledWhenTheThresholdIsOne() {
        SpecialVariantConfig.specialBombeurBlindThreshold = 1.0;
        assertEquals(0, BombeurBlast.blindTicks(1.0));
        assertEquals(0, BombeurBlast.blindTicks(0.99));
    }
}
```

- [ ] **Étape 2 : lancer les tests, vérifier qu'ils échouent**

Commande : `mod/gradlew -p mod test --tests '*BombeurBlastTest*'`
Attendu : ÉCHEC de compilation — `BombeurBlast` n'existe pas.

- [ ] **Étape 3 : écrire `BombeurBlast`**

```java
package com.dreykaoas.lethalbreed.special.runtime;

import com.dreykaoas.lethalbreed.config.domain.SpecialVariantConfig;

/**
 * Pure maths behind a Bombeur detonation: how long its fuse burns, how hard it blows, how far the gore
 * reaches, and what that gore does to whoever it lands on.
 *
 * <p>Deliberately free of every {@code net.minecraft} type. The entity-facing half lives in
 * {@link SpecialAbilities}; keeping the numbers here is what lets them be unit-tested without booting a
 * server, and this is the only part of the Bombeur that carries non-trivial logic.
 *
 * <p>The shaping constants below are NOT config options. Only the five levers in
 * {@link SpecialVariantConfig} are exposed: the mod already ships 339 options, and surfacing every
 * coefficient of a curve would make the variant impossible for a player to balance. The blindness
 * THRESHOLD is configurable because it decides whether the harshest effect appears at all; the shape
 * coefficients only decide dosage.
 */
public final class BombeurBlast {
    private BombeurBlast() {}

    /** Intensity at or above which Poison steps from amplifier 0 to 1. */
    static final double POISON_AMP_STEP = 0.6;
    /** Slowness amplifier ceiling (Slowness III would make escape hopeless rather than hard). */
    static final int SLOW_AMP_MAX = 2;

    private static final double NAUSEA_BASE_S = 4.0, NAUSEA_SPAN_S = 11.0;
    private static final double DOT_BASE_S = 3.0, DOT_SPAN_S = 9.0;
    private static final double BLIND_BASE_S = 1.0, BLIND_SPAN_S = 4.0;
    /** Weight of the fuse in the intensity blend; proximity always keeps the remaining share. */
    private static final double FUSE_WEIGHT = 0.6;

    private static final int TPS = 20;

    private static double lo(double a, double b) { return Math.min(a, b); }
    private static double hi(double a, double b) { return Math.max(a, b); }

    /** Fuse length in GAME TICKS for a uniform roll in [0,1]. Ticks, not activations: the caller turns this
     *  into an absolute deadline, so the duration cannot drift with {@code tickBuckets}. */
    public static int fuseTicksFor(double rand01) {
        double a = SpecialVariantConfig.specialBombeurFuseMinTicks;
        double b = SpecialVariantConfig.specialBombeurFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        double r = Math.clamp(rand01, 0.0, 1.0);
        return (int) Math.round(min + (max - min) * r);
    }

    /** Where a fuse length sits in its configured range. A degenerate range yields 0 — the mildest blast,
     *  which is the safe way to fail. */
    public static double ratioOf(int fuseTicks) {
        double a = SpecialVariantConfig.specialBombeurFuseMinTicks;
        double b = SpecialVariantConfig.specialBombeurFuseMaxTicks;
        double min = lo(a, b), max = hi(a, b);
        if (max - min <= 0.0) {
            return 0.0;
        }
        return Math.clamp((fuseTicks - min) / (max - min), 0.0, 1.0);
    }

    /** Explosion power for a fuse ratio: the longer it swelled, the bigger it bursts. */
    public static double powerFor(double ratio) {
        double a = SpecialVariantConfig.specialBombeurPowerMin;
        double b = SpecialVariantConfig.specialBombeurPowerMax;
        double min = lo(a, b), max = hi(a, b);
        return min + (max - min) * Math.clamp(ratio, 0.0, 1.0);
    }

    /** Vanilla explosions reach twice their power. */
    public static double blastRadius(double power) {
        return power * 2.0;
    }

    /** The gore ring — wider than the blast, so backing out of lethal range still gets you splattered. */
    public static double splatterRadius(double power) {
        return blastRadius(power) * Math.max(0.0, SpecialVariantConfig.specialBombeurSplatterMul);
    }

    /** How hard the splatter lands: proximity dominates, fuse length amplifies. 0 at the ring's edge. */
    public static double intensity(double ratio, double dist, double splatterRadius) {
        if (splatterRadius <= 0.0) {
            return 0.0;
        }
        double prox = Math.max(0.0, 1.0 - dist / splatterRadius);
        return Math.clamp(prox * ((1.0 - FUSE_WEIGHT) + FUSE_WEIGHT * Math.clamp(ratio, 0.0, 1.0)), 0.0, 1.0);
    }

    private static int seconds(double base, double span, double intensity) {
        return (int) Math.round((base + span * Math.clamp(intensity, 0.0, 1.0)) * TPS);
    }

    public static int nauseaTicks(double i) { return seconds(NAUSEA_BASE_S, NAUSEA_SPAN_S, i); }

    public static int poisonTicks(double i) { return seconds(DOT_BASE_S, DOT_SPAN_S, i); }

    public static int poisonAmp(double i) { return i < POISON_AMP_STEP ? 0 : 1; }

    public static int slowTicks(double i) { return seconds(DOT_BASE_S, DOT_SPAN_S, i); }

    public static int slowAmp(double i) {
        return Math.min(SLOW_AMP_MAX, (int) Math.floor(Math.clamp(i, 0.0, 1.0) * 3.0));
    }

    /** Blindness ticks, or 0 below the configured threshold. A threshold of 1.0 disables it outright —
     *  no intensity can exceed 1, and the guard also keeps the span division safe. */
    public static int blindTicks(double i) {
        double t = Math.clamp(SpecialVariantConfig.specialBombeurBlindThreshold, 0.0, 1.0);
        if (t >= 1.0 || i < t) {
            return 0;
        }
        return seconds(BLIND_BASE_S, BLIND_SPAN_S, (i - t) / (1.0 - t));
    }

    public static double infectChance(double i) {
        return Math.clamp(i, 0.0, 1.0) * Math.clamp(SpecialVariantConfig.specialBombeurInfectChance, 0.0, 1.0);
    }
}
```

- [ ] **Étape 4 : lancer les tests, vérifier qu'ils passent**

Commande : `mod/gradlew -p mod test --tests '*BombeurBlastTest*'`
Attendu : `BUILD SUCCESSFUL`, 14 tests, 0 échec.

- [ ] **Étape 5 : commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/special/runtime/BombeurBlast.java \
        mod/src/test/java/com/dreykaoas/lethalbreed/special/BombeurBlastTest.java
git commit -m "feat: le calcul pur du souffle et de l'éclaboussure du Bombeur"
```

---

### Tâche 2 : config, bornes et libellés

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/config/domain/SpecialVariantConfig.java:50-56`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ProgressionBounds.java:58-61`
- Modifier : `mod/src/main/resources/assets/lethalbreed/lang/fr_fr.json:135-137`
- Modifier : `mod/src/main/resources/assets/lethalbreed/lang/en_us.json:135-137`

**Interfaces :**
- Produit : les sept champs `public static` lus par `BombeurBlast` (tâche 1).

- [ ] **Étape 1 : remplacer les champs de domaine**

Dans `SpecialVariantConfig.java`, remplacer le bloc Bombeur existant :

```java
    // -- Bombeur (ACTIVE): timed fuse + explosion + infectious splatter.
    /** Distance (blocks) to the target at which a Bombeur arms and commits to detonating. */
    public static double specialBombeurArmRange = 3.0;
    /** Shortest fuse, in GAME TICKS (not activations): the floor of the random roll made when it arms. */
    public static int specialBombeurFuseMinTicks = 30;
    /** Longest fuse, in GAME TICKS. A long fuse gives the victim time, and detonates harder for it. */
    public static int specialBombeurFuseMaxTicks = 120;
    /** Explosion power of the shortest fuse (vanilla TNT ≈ 4.0). */
    public static double specialBombeurPowerMin = 2.0;
    /** Explosion power of the longest fuse — the payoff for having swelled the whole time. */
    public static double specialBombeurPowerMax = 5.0;
    /** Splatter radius as a multiple of the blast radius. Above 1.0, backing out of lethal range still
     *  leaves you inside the gore — which is the whole point of the ring. */
    public static double specialBombeurSplatterMul = 1.5;
    /** Contamination chance at intensity 1.0; scales down linearly with intensity. */
    public static double specialBombeurInfectChance = 0.5;
    /** Splatter intensity from which Blindness is applied. 1.0 disables Blindness entirely. */
    public static double specialBombeurBlindThreshold = 0.75;
```

- [ ] **Étape 2 : mettre à jour les bornes**

Dans `ProgressionBounds.java`, remplacer les deux lignes `specialBombeurPower` et
`specialBombeurFusePerTick` par :

```java
        r.b("specialBombeurArmRange", 0, 64);
        r.b("specialBombeurFuseMinTicks", 1, 6_000);
        r.b("specialBombeurFuseMaxTicks", 1, 6_000);
        r.b("specialBombeurPowerMin", 0, 100);
        r.b("specialBombeurPowerMax", 0, 100);
        r.b("specialBombeurSplatterMul", 0, 10);
        r.b("specialBombeurInfectChance", 0, 1);
        r.b("specialBombeurBlindThreshold", 0, 1);
```

(la ligne `specialBombeurArmRange` existante est conservée — la supprimer si elle se retrouve en double)

- [ ] **Étape 3 : mettre à jour les deux fichiers de langue**

`fr_fr.json` — remplacer les lignes `specialBombeurPower` et `specialBombeurFusePerTick` par :

```json
  "lethalbreed.option.specialBombeurFuseMinTicks": "Bombeur — Mèche minimale (ticks)",
  "lethalbreed.option.specialBombeurFuseMaxTicks": "Bombeur — Mèche maximale (ticks)",
  "lethalbreed.option.specialBombeurPowerMin": "Bombeur — Puissance à mèche courte",
  "lethalbreed.option.specialBombeurPowerMax": "Bombeur — Puissance à mèche longue",
  "lethalbreed.option.specialBombeurSplatterMul": "Bombeur — Rayon d'éclaboussure (× souffle)",
  "lethalbreed.option.specialBombeurInfectChance": "Bombeur — Chance de contamination",
  "lethalbreed.option.specialBombeurBlindThreshold": "Bombeur — Seuil de cécité",
```

`en_us.json` — idem avec :

```json
  "lethalbreed.option.specialBombeurFuseMinTicks": "Bomber — Minimum Fuse (ticks)",
  "lethalbreed.option.specialBombeurFuseMaxTicks": "Bomber — Maximum Fuse (ticks)",
  "lethalbreed.option.specialBombeurPowerMin": "Bomber — Short-Fuse Power",
  "lethalbreed.option.specialBombeurPowerMax": "Bomber — Long-Fuse Power",
  "lethalbreed.option.specialBombeurSplatterMul": "Bomber — Splatter Radius (× blast)",
  "lethalbreed.option.specialBombeurInfectChance": "Bomber — Contamination Chance",
  "lethalbreed.option.specialBombeurBlindThreshold": "Bomber — Blindness Threshold",
```

- [ ] **Étape 4 : vérifier que le mod compile et que les langues restent cohérentes**

Commande : `mod/gradlew -p mod test`
Attendu : `BUILD SUCCESSFUL`. Le test de parité des langues (paquet `lang`) doit passer — il compare les
jeux de clés de `fr_fr` et `en_us`, donc une clé oubliée d'un côté le fait échouer.

- [ ] **Étape 5 : commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/config/domain/SpecialVariantConfig.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/config/bounds/ProgressionBounds.java \
        mod/src/main/resources/assets/lethalbreed/lang/fr_fr.json \
        mod/src/main/resources/assets/lethalbreed/lang/en_us.json
git commit -m "feat: les options de mèche, de puissance et d'éclaboussure du Bombeur"
```

---

### Tâche 3 : l'échéance — attachments et `SpecialBehavior`

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialAttachment.java`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialBehavior.java:35-50`

**Interfaces :**
- Consomme : `BombeurBlast.fuseTicksFor(double)` (tâche 1), les champs de config (tâche 2).
- Produit : `SpecialAttachment.BOMBEUR_FUSE` (`Integer`) et `BOMBEUR_ARMED_AT` (`Long`), lus par la tâche 4.

- [ ] **Étape 1 : ajouter les deux attachments**

Dans `SpecialAttachment.java`, après `BOMBEUR_CHARGE` :

```java
    /**
     * BOMBEUR fuse length in GAME TICKS, rolled once when it arms; 0 means not armed yet. Transient and
     * NOT synced — only the derived {@link #BOMBEUR_CHARGE} needs to reach clients.
     */
    public static final AttachmentType<Integer> BOMBEUR_FUSE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "bombeur_fuse"),
            builder -> builder.initializer(() -> 0));

    /**
     * Game time at which the BOMBEUR armed. With {@link #BOMBEUR_FUSE} this makes the detonation an absolute
     * deadline rather than a per-activation accumulation — so the fuse lasts the same real time whatever
     * {@code tickBuckets} is set to, and a skipped activation cannot stretch it.
     */
    public static final AttachmentType<Long> BOMBEUR_ARMED_AT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("lethalbreed", "bombeur_armed_at"),
            builder -> builder.initializer(() -> 0L));
```

- [ ] **Étape 2 : réécrire la branche BOMBEUR**

Dans `SpecialBehavior.tick`, remplacer intégralement le `case BOMBEUR -> { … }` par :

```java
            case BOMBEUR -> {
                // Absolute deadline, not per-activation accumulation: this method runs once every
                // `tickBuckets` ticks, so counting activations tied the fuse to a performance knob.
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
                }
                long armedAt = z.getAttachedOrElse(SpecialAttachment.BOMBEUR_ARMED_AT, now);
                long elapsed = now - armedAt;
                if (elapsed >= fuse) {
                    SpecialAbilities.bomb(level, z, fuse);
                } else {
                    // Derived, not accumulated — the belly swells linearly in real time, so a slowly
                    // inflating Bombeur reads as "long fuse", which is exactly "big explosion".
                    z.setAttached(SpecialAttachment.BOMBEUR_CHARGE, (float) elapsed / fuse);
                }
            }
```

Ajouter l'import `com.dreykaoas.lethalbreed.special.runtime.BombeurBlast;`.

- [ ] **Étape 3 : compiler**

Commande : `mod/gradlew -p mod compileJava`
Attendu : ÉCHEC — `SpecialAbilities.bomb` ne prend pas encore trois arguments. C'est normal, la tâche 4
corrige la signature.

- [ ] **Étape 4 : commit après la tâche 4**

Cette tâche et la tâche 4 forment une seule unité compilable ; les commiter ensemble à la fin de la tâche 4.

---

### Tâche 4 : l'éclaboussure — `SpecialAbilities.bomb`

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/special/runtime/SpecialAbilities.java:23-28`

**Interfaces :**
- Consomme : `BombeurBlast.*` (tâche 1), les attachments (tâche 3).
- Produit : `bomb(ServerLevel, Zombie, int fuseTicks)`.

- [ ] **Étape 1 : réécrire `bomb`**

```java
    /** BOMBEUR: burst, then splatter everything in the wider gore ring with infectious status effects. */
    public static void bomb(ServerLevel level, Zombie z, int fuseTicks) {
        double ratio = BombeurBlast.ratioOf(fuseTicks);
        double power = BombeurBlast.powerFor(ratio);
        double splatR = BombeurBlast.splatterRadius(power);
        double cx = z.getX(), cy = z.getY() + 0.5, cz = z.getZ();

        // Gather BEFORE the explosion: it can kill or fling victims, and a corpse must still catch the gore.
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - splatR, cy - splatR, cz - splatR, cx + splatR, cy + splatR, cz + splatR),
                e -> e != z && e.isAlive() && !(e instanceof Zombie));

        level.explode(z, cx, cy, cz, (float) power, Level.ExplosionInteraction.NONE);
        z.discard();

        for (LivingEntity victim : caught) {
            double dist = Math.sqrt(victim.distanceToSqr(cx, cy, cz));
            double i = BombeurBlast.intensity(ratio, dist, splatR);
            if (i <= 0.0) {
                continue;
            }
            splatter(victim, i, z.getRandom());
        }
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
```

Imports à ajouter : `com.dreykaoas.lethalbreed.effect.ContaminationManager`, `net.minecraft.util.RandomSource`,
`net.minecraft.world.phys.AABB`, `java.util.List`.

- [ ] **Étape 2 : compiler et lancer toute la suite unitaire**

Commande : `mod/gradlew -p mod test`
Attendu : `BUILD SUCCESSFUL`, aucun échec.

- [ ] **Étape 3 : commit (tâches 3 + 4)**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialAttachment.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialBehavior.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/special/runtime/SpecialAbilities.java
git commit -m "feat: mèche à échéance absolue et éclaboussure infectieuse du Bombeur"
```

---

### Tâche 5 : vérifications dans la suite dev `special`

**Fichiers :**
- Modifier : `mod/src/dev/java/com/dreykaoas/lethalbreed/dev/special/SpecialTestEvaluator.java:40`

**Interfaces :**
- Consomme : `SpecialAttachment.BOMBEUR_FUSE`, `BombeurBlast`.

- [ ] **Étape 1 : enrichir le cas BOMBEUR**

Remplacer `case BOMBEUR -> { pass = z.isRemoved(); detail = "exploded (removed)"; }` par une vérification
qui contrôle aussi la mèche et l'éclaboussure. La vache témoin (`c.cow()`) sert de victime : elle doit porter
les effets si elle est dans l'anneau.

```java
                case BOMBEUR -> {
                    boolean gone = z.isRemoved();
                    var cow = c.cow();
                    boolean splattered = cow != null && cow.isAlive()
                            && cow.getEffect(MobEffects.NAUSEA) != null
                            && cow.getEffect(MobEffects.SLOWNESS) != null;
                    // A dead cow means the blast reached it, which tells us nothing about the wider ring —
                    // the check only asserts the splatter when the witness survived to carry it.
                    pass = gone && (cow == null || !cow.isAlive() || splattered);
                    detail = "exploded=" + gone + " cowAlive=" + (cow != null && cow.isAlive())
                            + " splattered=" + splattered;
                }
```

Import à ajouter si absent : `net.minecraft.world.effect.MobEffects`.

- [ ] **Étape 2 : lancer la suite `special`**

Commande :
```bash
LB_DEV_TEST=special mod/gradlew -p mod runServer --console=plain
```
Attendu : `[LB-Verify] ALL DONE` présent, et aucune ligne `: FAIL`. L'absence de `ALL DONE` signifie que la
suite a planté — à traiter comme un échec même sans `FAIL` explicite.

- [ ] **Étape 3 : commit**

```bash
git add mod/src/dev/java/com/dreykaoas/lethalbreed/dev/special/SpecialTestEvaluator.java
git commit -m "test: la suite special vérifie la mèche et l'éclaboussure du Bombeur"
```

---

### Tâche 6 : vérification sur serveur réel et restauration de la config

**Fichiers :**
- Modifier : `mod/run/server/config/oas/lethalbreed.json` (hors dépôt — fichier de run)

- [ ] **Étape 1 : mesurer les mèches réellement tirées**

Démarrer `runServer`, se placer en phase 14, puis faire apparaître plusieurs Bombeurs avec
`/lethalspecial bombeur 8` et observer les durées entre amorçage et détonation.

Attendu : les durées tombent dans `[1.5 s, 6 s]` et **diffèrent entre elles**. Des durées identiques
signifient que le tirage n'est pas branché.

- [ ] **Étape 2 : vérifier les deux rayons**

Placer une victime hors du rayon de souffle mais dans l'anneau d'éclaboussure et confirmer qu'elle prend
0 dégât tout en portant Nausée / Poison / Lenteur.

- [ ] **Étape 3 : restaurer la config de run**

Remettre les valeurs d'origine modifiées pendant l'enquête :

```json
    "phaseMaxEnabled": false,
    "phaseMax": 50,
```

Attendu : `grep -n "phaseMaxEnabled\|phaseMax\"" mod/run/server/config/oas/lethalbreed.json` montre
`false` et `50`.

- [ ] **Étape 4 : suite complète et commit final**

Commande : `mod/gradlew -p mod build`
Attendu : `BUILD SUCCESSFUL`, tous les tests unitaires verts.

---

## Auto-relecture

**Couverture du spec.** §3 mèche → tâches 1 et 3. §4 puissance → tâches 1 et 4. §5 deux zones et intensité →
tâches 1 et 4. §6 effets → tâches 1 et 4. §7 découpage → tâches 1, 3, 4. §8 config → tâche 2. §9 tests →
tâches 1 (unitaires) et 5 (intégration), plus la tâche 6 pour la vérification manuelle en jeu.

**Cohérence des types.** `bomb(ServerLevel, Zombie, int)` est déclarée en tâche 4 et appelée en tâche 3 avec
exactement ces trois arguments. `BombeurBlast.ratioOf(int)` prend des ticks, ce que `BOMBEUR_FUSE` stocke.
`fuseTicksFor(double)` prend un tirage `[0,1]`, fourni par `z.getRandom().nextDouble()`.

**Point d'attention connu.** Les tâches 3 et 4 ne compilent pas séparément : la tâche 3 appelle la signature
que la tâche 4 introduit. C'est assumé et signalé dans les deux tâches — elles forment une unité de commit.
