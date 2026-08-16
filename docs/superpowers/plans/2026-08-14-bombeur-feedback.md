# Bombeur — retour de test : immobilisation, gonflement fluide, diagnostic — plan d'implémentation

> **Pour les agents :** SOUS-COMPÉTENCE REQUISE — utiliser `superpowers:subagent-driven-development`
> (recommandé) ou `superpowers:executing-plans` pour dérouler ce plan tâche par tâche. Les étapes utilisent
> la syntaxe case à cocher (`- [ ]`).

**But :** trois correctifs sur le Bombeur suite à un test en jeu — il s'immobilise à l'amorçage au lieu de
continuer à poursuivre, son ventre gonfle visuellement de façon fluide au lieu de sauter par paliers, et un
amorçage inattendu (sans cible visible du joueur) laisse une trace de diagnostic exploitable au lieu de
disparaître sans preuve.

**Architecture :** l'arrêt de mouvement suit le patron déjà en place dans `ZombieBrain` (une méthode
`handleXxx()` qui renvoie `true` pour couper la suite du tick) ; le lissage visuel est purement client, ajouté
aux hooks de rendu existants sans toucher au serveur ; le diagnostic est une ligne de log conditionnelle au
seul point où l'amorçage est décidé.

**Tech Stack :** Java 21, Minecraft 1.21.11, Fabric Loom, JUnit 5.

## Contraintes globales

- Fichiers Java du mod : encodage UTF-8, fins de ligne LF — vérifier avant commit.
- Le lissage visuel est un détail cosmétique client-only : pas de nouvelle option de config, une constante
  nommée suffit (cohérent avec `GIRTH_XZ_SCALE`/`GIRTH_Y_SCALE` déjà en place dans `ZombieBellyModelMixin`).
- `ZombieState.ARMED` doit être ajouté **en dernier** dans l'enum, comme le commentaire de `SLEEPING` l'exige
  déjà — l'ordinal est stocké dans `ZombieStateAttachment.STATE`, le réordonner casserait les sauvegardes.
- Ne jamais se mettre en co-auteur ni ajouter de ligne `Co-Authored-By:` aux commits.

---

### Tâche 1 : diagnostic à l'amorçage

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialBehavior.java:49-58`

**Interfaces :**
- Aucune nouvelle API — ajout d'une ligne de log au point d'amorçage existant.

**Contexte :** en jeu (créatif), un Bombeur s'est mis à gonfler sans cible apparente pour le joueur. La
lecture complète du code (`LODManager.classify()` tourne avant `tick()`, et `TargetSelector.isValid` exclut
le créatif via `Players.isTargetable`) montre que l'amorçage ne devrait être atteignable qu'avec une cible
valide trouvée par le mod — donc soit une autre créature était présente sans que le joueur la remarque, soit
un chemin non identifié existe. Plutôt que de corriger à l'aveugle, cette tâche ajoute la preuve qui manquait
pour trancher au prochain repro.

- [ ] **Étape 1 : ajouter le log**

Dans `SpecialBehavior.java`, au moment où le fusible est tiré (juste avant `fuse =
BombeurBlast.fuseTicksFor(...)`), ajouter :

```java
                    fuse = BombeurBlast.fuseTicksFor(z.getRandom().nextDouble());
                    z.setAttached(SpecialAttachment.BOMBEUR_FUSE, fuse);
                    z.setAttached(SpecialAttachment.BOMBEUR_ARMED_AT, now);
                    if (LethalBreed.LOGGER.isDebugEnabled()) {
                        LethalBreed.LOGGER.debug("[Bombeur] armé sur {} à {} blocs (fuse={} ticks)",
                                tgt instanceof net.minecraft.world.entity.player.Player pl ? pl.getGameProfile().getName()
                                        : tgt.getClass().getSimpleName(),
                                Math.sqrt(z.distanceToSqr(tgt)), fuse);
                    }
```

Import à ajouter si absent : `com.dreykaoas.lethalbreed.LethalBreed`.

- [ ] **Étape 2 : compiler**

Commande : `mod/gradlew -p mod compileJava`
Attendu : `BUILD SUCCESSFUL`.

- [ ] **Étape 3 : commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/special/SpecialBehavior.java
git commit -m "debug: logger la cible d'amorçage du Bombeur"
```

---

### Tâche 2 : immobilisation pendant la mèche

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieState.java`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java`
- Modifier : `mod/src/dev/java/com/dreykaoas/lethalbreed/dev/special/SpecialTestEvaluator.java`

**Interfaces :**
- Consomme : `SpecialBehavior.fuseIsLit(Zombie)` (déjà public, déjà utilisé par `LODManager`).
- Produit : `ZombieState.ARMED`, comportement observable (immobilité totale) vérifié par la suite dev.

**Contexte :** décision explicite du joueur — le Bombeur doit s'arrêter net dès l'amorçage, comme un
Creeper, et gonfler sur place jusqu'à l'explosion. Ça revient sur la décision initiale du design
(« compte à rebours inévitable » sans arrêt) : la fuite ne désamorce toujours pas, mais elle redevient
efficace puisque le Bombeur ne peut plus rattraper personne une fois amorcé.

- [ ] **Étape 1 : ajouter l'état**

Dans `ZombieState.java`, ajouter après `SLEEPING` (garder l'ordre d'ajout, jamais réordonner) :

```java
    SLEEPING,
    /** Bombeur fuse lit — frozen in place until detonation, like a Creeper. Appended LAST, same reason as
     *  SLEEPING: the ordinal is persisted. */
    ARMED
```

- [ ] **Étape 2 : couper le mouvement dans `ZombieBrain.tick()`**

Dans `ZombieBrain.java`, juste après le `if (owner.lod() == LODLevel.FROZEN) return;` (avant
`handleSleeping()`), insérer :

```java
        if (owner.lod() == LODLevel.FROZEN) return;
        if (handleArmed()) return;
        if (handleSleeping()) return;
```

Placé avant `handleSheltering`/`handleFleeing` à dessein : un Bombeur amorcé ne cherche plus l'ombre ni ne
fuit à bas PV, il est déjà committed — comme un Creeper qui siffle, rien ne l'interrompt plus que
l'explosion elle-même.

Puis ajouter la méthode, près de `handleSleeping()` :

```java
    /** BOMBEUR with a lit fuse: frozen in place, gonflant jusqu'à la détonation. Distinct from FROZEN (which
     *  means "no target, not simulated") — an armed Bombeur is very much simulated, just deliberately not
     *  moving, exactly like a Creeper mid-hiss. */
    private boolean handleArmed() {
        if (!SpecialBehavior.fuseIsLit(entity)) {
            return false;
        }
        pillar.cancel();
        entity.getNavigation().stop();
        // Kill horizontal momentum only — falling still falls, so an armed Bombeur mid-leap lands normally
        // rather than freezing in the air.
        entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
        owner.setState(ZombieState.ARMED);
        return true;
    }
```

Import à ajouter si absent : `com.dreykaoas.lethalbreed.special.SpecialBehavior`.

- [ ] **Étape 3 : compiler**

Commande : `mod/gradlew -p mod compileJava`
Attendu : `BUILD SUCCESSFUL`.

- [ ] **Étape 4 : vérifier l'immobilité dans la suite dev**

Dans `SpecialTestEvaluator.java`, ajouter un suivi de dérive de position pendant que la mèche brûle. Près des
champs `bombeurSplattered`/`BEFORE_SPLIT` :

```java
    /** Position du Bombeur au premier tick où sa mèche est allumée. Null tant qu'il n'a pas encore armé. */
    private static net.minecraft.world.phys.Vec3 bombeurArmedPos;
    /** Plus grande distance mesurée à cette position pendant que la mèche brûle. */
    private static double bombeurMaxDrift;
```

Dans `sample()`, ajouter au corps de la boucle (à côté du bloc qui latch `bombeurSplattered`) :

```java
            if (c.type() == SpecialType.BOMBEUR && !c.z().isRemoved()
                    && com.dreykaoas.lethalbreed.special.SpecialBehavior.fuseIsLit(c.z())) {
                if (bombeurArmedPos == null) {
                    bombeurArmedPos = c.z().position();
                } else {
                    bombeurMaxDrift = Math.max(bombeurMaxDrift, bombeurArmedPos.distanceTo(c.z().position()));
                }
            }
```

Dans `evaluate()`, le cas `BOMBEUR` devient :

```java
                case BOMBEUR -> {
                    boolean gone = z.isRemoved();
                    boolean alive = c.cow() != null && c.cow().isAlive();
                    // 0.25 bloc de marge : nudges de collision, pas un vrai déplacement. Un Bombeur qui
                    // recommence à courir bougerait de plusieurs blocs sur les 1.5-6 s de la mèche.
                    boolean immobile = bombeurMaxDrift < 0.25;
                    pass = gone && (!alive || bombeurSplattered) && immobile;
                    detail = "explosé=" + gone + " témoinVivant=" + alive + " éclaboussé=" + bombeurSplattered
                            + " dérive=" + String.format("%.2f", bombeurMaxDrift);
                }
```

- [ ] **Étape 5 : lancer la suite `special`**

Commande :
```bash
LB_DEV_TEST=special mod/gradlew -p mod runServer --console=plain
```
Attendu : `special/bombeur : PASS` avec `dérive=` proche de `0.00`, et `[LB-Verify] ALL DONE`.

- [ ] **Étape 6 : commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/entity/ZombieState.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/ZombieBrain.java \
        mod/src/dev/java/com/dreykaoas/lethalbreed/dev/special/SpecialTestEvaluator.java
git commit -m "feat: le Bombeur s'immobilise pendant que sa mèche brûle"
```

---

### Tâche 3 : gonflement lissé côté client

**Fichiers :**
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/client/ZombieRenderFlags.java`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRenderStateMixin.java`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRendererMixin.java`
- Modifier : `mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java`

**Interfaces :**
- Produit : `ZombieRenderFlags.lethalbreed$bellyChargeDisplayed()` (float, lissé), consommé par
  `ZombieBellyModelMixin`.

**Contexte :** `BOMBEUR_CHARGE` n'est réécrit côté serveur qu'une fois par activation du zombie (~4×/s en
LOD HIGH), et le modèle applique cette valeur brute directement — pour une mèche courte (1.5 s), ça donne
~6 sauts visibles de ~17 % de grossissement chacun, perçu comme un gonflement d'un coup plutôt que
progressif. Le correctif est purement côté client : lisser la valeur affichée vers la valeur synchronisée par
un easing dépendant du temps réel écoulé, indépendant du taux de rafraîchissement de la valeur serveur.

- [ ] **Étape 1 : ajouter l'accesseur lissé à l'interface**

Dans `ZombieRenderFlags.java`, ajouter après les accesseurs `lethalbreed$bellyCharge` :

```java
    /** {@link #lethalbreed$bellyCharge()} lissé en temps réel côté client — c'est CETTE valeur que le modèle
     *  doit lire. La valeur brute n'est réécrite par le serveur qu'une fois par activation du zombie (à peu
     *  près 4×/s en LOD HIGH), ce qui produisait des sauts de grossissement visibles sur une mèche courte ;
     *  celle-ci comble l'écart entre deux mises à jour. */
    float lethalbreed$bellyChargeDisplayed();

    void lethalbreed$bellyChargeDisplayed(float charge);

    /** Horodatage ({@code System.nanoTime()}) du dernier calcul de lissage — 0 tant qu'aucun calcul n'a eu
     *  lieu pour ce render state. */
    long lethalbreed$bellyChargeLastNanos();

    void lethalbreed$bellyChargeLastNanos(long nanos);
```

- [ ] **Étape 2 : stocker les champs dans le mixin de render state**

Dans `LivingEntityRenderStateMixin.java`, ajouter :

```java
    @Unique
    private float lethalbreed$bellyChargeDisplayed;

    @Unique
    private long lethalbreed$bellyChargeLastNanos;

    @Override
    public float lethalbreed$bellyChargeDisplayed() {
        return lethalbreed$bellyChargeDisplayed;
    }

    @Override
    public void lethalbreed$bellyChargeDisplayed(float charge) {
        this.lethalbreed$bellyChargeDisplayed = charge;
    }

    @Override
    public long lethalbreed$bellyChargeLastNanos() {
        return lethalbreed$bellyChargeLastNanos;
    }

    @Override
    public void lethalbreed$bellyChargeLastNanos(long nanos) {
        this.lethalbreed$bellyChargeLastNanos = nanos;
    }
```

- [ ] **Étape 3 : calculer le lissage à l'extraction**

Dans `LivingEntityRendererMixin.java`, remplacer :

```java
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ((ZombieRenderFlags) state).lethalbreed$bellyCharge(charge);
```

par :

```java
        float charge = entity instanceof Zombie
                ? entity.getAttachedOrElse(SpecialAttachment.BOMBEUR_CHARGE, 0.0f)
                : 0.0f;
        ZombieRenderFlags flags = (ZombieRenderFlags) state;
        flags.lethalbreed$bellyCharge(charge);
        flags.lethalbreed$bellyChargeDisplayed(smoothedBellyCharge(flags, charge));
```

Puis ajouter la méthode privée dans la même classe :

```java
    /** Temps caractéristique du lissage (secondes) : plus petit = rattrape plus vite. 0.15 comble un saut de
     *  mise à jour serveur (~0.25 s à LOD HIGH) en restant imperceptible sur une mèche de 1.5 s. */
    private static final float SMOOTH_TIME_CONSTANT = 0.15f;

    /** Rapproche la valeur affichée de la cible synchronisée par un facteur dépendant du temps réel écoulé
     *  depuis le dernier appel — indépendant du taux de rafraîchissement (framerate) et du taux de mise à
     *  jour serveur, donc fluide quels que soient les deux. Redescend instantanément si la cible chute (le
     *  ventre ne doit jamais rester gonflé sur un zombie qui vient de renaître). */
    private static float smoothedBellyCharge(ZombieRenderFlags flags, float target) {
        long now = System.nanoTime();
        long last = flags.lethalbreed$bellyChargeLastNanos();
        float displayed = flags.lethalbreed$bellyChargeDisplayed();
        flags.lethalbreed$bellyChargeLastNanos(now);
        if (target <= 0.0f) {
            return 0.0f; // pas de traînée résiduelle sur un zombie neuf / non armé
        }
        if (last == 0L || target < displayed) {
            return target; // premier appel, ou la cible a chuté (nouveau fusible) : pas de traînée à l'envers
        }
        float dt = (now - last) / 1_000_000_000.0f;
        float t = Math.min(1.0f, dt / SMOOTH_TIME_CONSTANT);
        return displayed + (target - displayed) * t;
    }
```

- [ ] **Étape 4 : lire la valeur lissée dans le modèle**

Dans `ZombieBellyModelMixin.java`, remplacer :

```java
        float charge = ((ZombieRenderFlags) state).lethalbreed$bellyCharge();
```

par :

```java
        float charge = ((ZombieRenderFlags) state).lethalbreed$bellyChargeDisplayed();
```

- [ ] **Étape 5 : compiler**

Commande : `mod/gradlew -p mod compileJava`
Attendu : `BUILD SUCCESSFUL`.

- [ ] **Étape 6 : vérification manuelle**

Pas de test automatisé possible (rendu client). Lancer le client dev, `/lethalspecial bombeur 1`, observer le
ventre gonfler en continu sans à-coups visibles jusqu'à la détonation — à comparer avec le comportement actuel
(par paliers) en repassant temporairement `lethalbreed$bellyChargeDisplayed` par `lethalbreed$bellyCharge`
dans `ZombieBellyModelMixin` si une comparaison côte-à-côte est utile.

- [ ] **Étape 7 : commit**

```bash
git add mod/src/main/java/com/dreykaoas/lethalbreed/client/ZombieRenderFlags.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRenderStateMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/render/LivingEntityRendererMixin.java \
        mod/src/main/java/com/dreykaoas/lethalbreed/mixin/client/model/ZombieBellyModelMixin.java
git commit -m "feat: lisser le gonflement du ventre du Bombeur côté client"
```

---

## Auto-relecture

**Couverture.** Les trois retours du joueur sont couverts : immobilisation (tâche 2), gonflement fluide
(tâche 3), amorçage sans cible apparente (tâche 1, diagnostic plutôt que correctif puisque la cause exacte
n'a pas pu être établie par lecture de code — le chemin d'amorçage examiné exclut déjà le créatif).

**Cohérence des types.** `SpecialBehavior.fuseIsLit(Zombie)` existe déjà (ajouté lors du correctif de la
régression FROZEN) et est réutilisé tel quel par les tâches 2 et 3 sans changement de signature.
`ZombieRenderFlags` gagne deux accesseurs, implémentés à l'identique dans `LivingEntityRenderStateMixin` ; le
seul consommateur de `lethalbreed$bellyCharge()` (brut) reste la ligne d'extraction elle-même, tout le reste
lit désormais la valeur lissée.

**Point d'attention.** La tâche 1 ne corrige rien — elle instrumente. Si le diagnostic ne se redéclenche
jamais, considérer l'observation initiale comme probablement une cible non-joueur non remarquée plutôt que
comme un bug non résolu.
