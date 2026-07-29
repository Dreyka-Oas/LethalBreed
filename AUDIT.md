# Audit LethalBreed — 2026-07-29

Audit sécurité, mémoire, performance et robustesse. **Lecture seule** : aucun fichier du dépôt
n'a été modifié en dehors de ce rapport.

- **Cible** — `mod/` (184 fichiers Java, ~14 200 lignes) + `web/` (site statique, 271 Ko)
- **Stack** — Fabric, Minecraft 1.21.11, Java 21, Gradle 9.5.1 + Loom 1.17.12
- **Méthode** — 13 lentilles d'analyse en parallèle, puis vérification adversariale de chaque
  finding par un agent indépendant chargé de le **réfuter**
- **Contexte** — un audit précédent (2026-07-27) avait déjà traité les fuites d'état statique,
  le durcissement des commandes, la libération des buffers GPU et les bornes de config. Ce
  passage vérifie ces correctifs et cherche ce qu'ils ont manqué.

---

## Synthèse

**13 findings confirmés** · 🟠 5 hauts · 🟡 7 moyens · ⚪ 1 bas retenu

| #  | Sév | Axe         | Emplacement                              | Problème                                  |
|----|-----|-------------|------------------------------------------|-------------------------------------------|
| 1  | 🟠  | gameplay    | `mixin/MilkKeepsPlagueMixin.java:24`     | Le lait guérit la peste — mixin mort      |
| 2  | 🟠  | robustesse  | `entity/ZombieMood.java:305`             | `NoAI` persisté → zombies statues         |
| 3  | 🟠  | perf        | `block/PlacedBlockTracker.java:46`       | Force le chargement de chunks, par tick   |
| 4  | 🟠  | build       | `mod/gradle.properties:6`                | Build cassé hors de la machine d'origine  |
| 5  | 🟠  | qualité     | `ContaminationHallucination.java:49`     | Plancher en dur → option config à moitié inerte |
| 6  | 🟡  | perf        | `entity/mood/ShelterFinder.java:19`      | 8 112 sondages, aucun cooldown sur échec  |
| 7  | 🟡  | perf        | `ai/flowfield/FlowFieldSnapshotBuilder.java:63` | Snapshot monde sur le thread serveur |
| 8  | 🟡  | mémoire     | `effect/contamination/ContaminationTick.java:25` | `SNAPSHOT` retient une fournée d'entités |
| 9  | 🟡  | mémoire     | `effect/contamination/ContaminationLifecycle.java:35` | Fuite si peste coupée à chaud   |
| 10 | 🟡  | mémoire     | `client/ContaminationScreenOverlay.java:47` | Pool de render targets jamais libéré   |
| 11 | 🟡  | gameplay    | `mixin/EffectClearCuresPlagueMixin.java:25` | `@At("TAIL")` rate le cas latent       |
| 12 | 🟡  | qualité     | `ContaminationState.java:128` + 5 sites  | Tirage uniforme réimplémenté 5 fois       |
| 13 | ⚪  | perf        | `tick/TickScheduler.java:59`             | Ligne dupliquée — index reconstruit 2×    |

**Gains rapides** — #13 (1 min), #4 (5 min), #5 (15 min), #8 (15 min), #1 + #11 (ensemble, ~1 h).

---

## Détail

### 🟠 #1 — Le lait guérit la peste, et `MilkKeepsPlagueMixin` est du code mort

`mixin/MilkKeepsPlagueMixin.java:24-35` · `mixin/EffectClearCuresPlagueMixin.java:25-31`
lentille mixins · effort ~1 h · **confirmé, mécanisme vérifié sur le bytecode vanilla**

**Mécanisme** · Le javadoc du mod affirme : *« Milk is unaffected by this: it goes through
`ClearAllStatusEffectsConsumeEffect`, a different code path »*. C'est **factuellement faux**.
Dans les sources 1.21.11 décompilées, cette classe fait littéralement :

```java
public boolean apply(Level level, ItemStack itemStack, LivingEntity livingEntity) {
    return livingEntity.removeAllEffects();
}
```

`removeAllEffects()` n'a que **deux** appelants dans tout le jeu : `EffectCommands:204` et
celui-ci. L'`@Inject TAIL` de `EffectClearCuresPlagueMixin` porte sur la méthode **interne**,
il s'exécute donc **avant** l'`@Inject TAIL` de `MilkKeepsPlagueMixin` sur la méthode externe.
Séquence réelle : `cure()` efface les attachements `CONTAM`/`SYMPTOMATIC` → au retour,
`isSymptomatic()` renvoie `false` → la branche de restauration n'est jamais prise.

**Impact** · N'importe quel joueur, sans aucune permission, annule la « Super Contamination » avec
un seau de lait. C'est l'inverse exact de ce que les deux mixins documentent (*« it is a disease,
not a status effect a glass of milk can wash out »*). Toute la boucle de gameplay de la peste
devient contournable par un item trivial. Aucun log, aucune erreur : les deux injections
s'appliquent correctement, elles se neutralisent.

**Prérequis** · Aucun. Vanilla 1.21.11, victime symptomatique. Reproductible immédiatement.

**Correctif recommandé** · Cibler directement `EffectCommands` plutôt que `removeAllEffects` :
c'est le seul autre appelant, ce qui rend le chemin `/effect clear` non ambigu et rend
`MilkKeepsPlagueMixin` inutile. Solution alternative : armer un drapeau au `HEAD` de
`ClearAllStatusEffectsConsumeEffect`, consulté par le handler de `removeAllEffects`. À traiter
**conjointement avec #10**, qui touche le même mixin.

> Le dépôt se contredit lui-même en trois endroits sur ce comportement :
> `ContaminationTick.java:72-73` commente « Milk / /effect clear … CURES the plague outright »,
> `MilkKeepsPlagueMixin` jure l'inverse, et `ContaminationManager.java:22` affirme que l'icône est
> réappliquée chaque tick — ce que `ContaminationTick` ne fait pas.

---

### 🟠 #2 — Les zombies endormis sont sauvegardés avec `NoAI=true`, sans moyen de le lever

`entity/ZombieMood.java:305-308` · `init/LifecycleInit.java:40-47`
lentille robustesse · effort faible · **confirmé, déclencheur plus large qu'estimé initialement**

**Mécanisme** · `dozeInPlace()` pose `entity.setNoAi(true)` et mémorise le fait dans le champ
d'instance `noAiFrozen`. Or `NoAI` est **persisté en NBT** par vanilla (`Mob:369-370` écrit,
`:389` relit) tandis que `noAiFrozen` vit dans le `ZombieMood`, détruit à chaque
`ENTITY_UNLOAD` (`EntityEventsInit:88-99`) comme au `SERVER_STOPPED`. Au rechargement, le mod
reconstruit un `ZombieMood` neuf avec `noAiFrozen=false` : le garde-fou de `clearSleepState()`
(*« hand vanilla AI back exactly when WE were the ones holding it off »*) ne se déclenchera
jamais pour ce gel-là.

```java
if (!noAiFrozen) {
    entity.setNoAi(true);
    noAiFrozen = true;
}
```

**Impact** · `isEffectiveAi()` barre à la fois `serverAiStep()` (`LivingEntity:2918`) **et**
`travel()` (`:2966`) : le zombie rechargé est une statue sans gravité ni navigation, que le
`setPersistenceRequired` posé par le mod empêche en plus de despawn. Deux branches ne
s'auto-guérissent **jamais** : (a) les zombies éveillés par `DaySleep.staysAwake` (actif dès la
phase 10) sortent de `handleDaySleep` sans jamais dozer → statue définitive ; (b) sous la phase 5,
un zombie rechargé à ciel ouvert entre en recherche d'ombre mais ne peut pas bouger → il brûle
sur place. Si le mod est désinstallé, rien en vanilla ne remet `NoAI` à `false` : la sauvegarde
reste peuplée de statues indestructibles autrement qu'à la commande.

**Prérequis** · `daySleepEnabled` + `moodEnabled` (défauts). **Un simple aller-retour du joueur
suffit** — le déchargement de chunk détruit déjà le `ZombieMood`, aucun arrêt de serveur n'est
nécessaire.

**Correctif recommandé** · Deux verrous. (1) Au `SERVER_STOPPED` et à l'`ENTITY_UNLOAD`, lever le
gel avant de jeter le `ZombieMood`. (2) Défense en profondeur à l'`ENTITY_LOAD` : si un zombie
tracké revient avec `isNoAi()` vrai sans que le mod ait posé le gel dans cette session, le lever.

---

### 🟠 #3 — `PlacedBlockTracker` force le chargement synchrone d'un chunk par bloc suivi, à chaque tick

`block/PlacedBlockTracker.java:36-63` (ligne 46)
lentille cycle de vie · effort ~2-3 h · **confirmé au bytecode 1.21.11**

**Mécanisme** · Appelé chaque tick depuis `WorldMaintenance.drainBlockOps` → `TickScheduler:67`,
pour chaque dimension. La boucle appelle `level.getBlockState(p)` sur chaque position suivie :

```java
BlockPos p = BlockPos.of(e.getKey());
BlockState bs = level.getBlockState(p);
```

Vérifié au bytecode : `Level.getBlockState` → `getChunk(II)` → `LevelReader.getChunk(x,z,FULL,true)`
avec `requireChunk = true` (`iconst_1` explicite). Sur un `ServerLevel`, sur cache miss, cela
enchaîne `addTicket(TicketType.UNKNOWN)` + `managedBlock(...)` + `join()` — **blocage synchrone
du thread serveur**. L'échappatoire attendue (« retourne de l'air sans charger ») n'existe pas :
ce chemin-là est celui de `getChunk(…, false)`, jamais emprunté ici. Aucun hook de déchargement
de chunk dans la classe : la seule voie de suppression est ce même `tick()`, qui doit d'abord
recharger le chunk pour l'évaluer.

**Impact** · Plafond exact `blockOpsPerTick=20` × `placedBlockLifetimeTicks=600` = **12 000
positions par dimension**. L'itération d'une `HashMap<Long,State>` produit un ordre décorrélé des
chunks, donc le cache à 4 entrées de `ServerChunkCache` rate quasi systématiquement : jusqu'à
~12 000 traversées `getChunkFutureMainThread` par tick et par dimension (avec allocation `ChunkPos`
+ `Ticket` à chaque appel) **même quand tous les chunks sont déjà résidents**. Le coût
d'allocation et de lookup est permanent ; la génération bloquante s'ajoute quand un joueur se
replie après un assaut.

**Prérequis** · `blockOpsEnabled` (défaut) + des zombies qui posent des blocs. Déroulement normal
d'un assaut.

**Correctif recommandé** · Ne consulter l'état d'un bloc que si son chunk est déjà résident —
le dépôt utilise déjà `level.isLoaded(m)` dans `ai/flowfield/CellClassifier.java:31` : le garde
existe ailleurs dans la base, il est simplement absent ici. Compléter par un balayage par tranches
plutôt qu'un parcours intégral de la map à chaque tick.

---

### 🟠 #4 — Le build échoue sur toute machine autre que celle d'origine

`mod/gradle.properties:6`
lentille build · effort 5 min · **vérifié par exécution réelle**

**Mécanisme** · Le fichier versionné force le JVM du démon Gradle sur un chemin absolu :

```properties
org.gradle.java.home=/opt/liberica-nik/bellsoft-liberica-vm-openjdk21-23.1.4
```

Ce répertoire **n'existe pas** sur la machine de développement actuelle. Gradle valide ce chemin
avant tout le reste : ni le toolchain Java 21 déclaré dans `build.gradle.kts`, ni `JAVA_HOME` ne
peuvent compenser.

**Impact** · `./gradlew build` échoue immédiatement avec *« Value … given for org.gradle.java.home
Gradle property is invalid »*, sans rapport avec le code. Le fichier étant versionné, l'échec est
distribué à tout contributeur et à toute CI. Confirmé par exécution : le build ne passe qu'en
surchargeant la propriété en ligne de commande.

**Correctif recommandé** · Supprimer la ligne — le bloc `java { toolchain.languageVersion.set(21) }`
déjà présent sait résoudre un JDK 21 seul. Si Liberica NIK est voulu pour les runs de dev,
l'exprimer via `toolchain.vendor` ou déplacer la ligne dans `~/.gradle/gradle.properties`
(non versionné).

---

### 🟡 #5 — `ShelterFinder.findShade` balaie 8 112 positions sans aucun cooldown sur échec

`entity/mood/ShelterFinder.java:19-56` · appelé depuis `entity/ZombieMood.java:272`
lentille perf · effort 30 min pour l'essentiel · **mécanisme confirmé, ampleur revue à la baisse**

**Mécanisme** · `shelterSearchRadius=12` → 624 colonnes × 13 niveaux Y = **8 112 itérations** par
appel. Les deux gardes `>= bestScore` ne prunent rien tant qu'aucun abri n'est trouvé (`bestScore`
reste `MAX_VALUE`) — le pire cas est donc précisément le cas « pas d'abri », qui est aussi le seul
qui se répète. Sur échec : `sleepSeekingShade = false; return;` **sans mémoire de l'échec**, donc
rebalayage complet à l'activation suivante.

**Impact** · Fréquence réelle établie à **1 Hz** (et non 4 Hz : un zombie oisif est FROZEN, et le
skip `frozenReclassifyDivisor=4` précède `updateMood`). Environ 15 600 lectures monde par appel
raté, soit ~0,4-0,5 ms. Sur succès le pruning est en revanche très efficace et le coût s'effondre.
L'état est normalement **auto-limitant** — un zombie exposé sans abri brûle et meurt en ~20 s.
**Sauf un cas** : un zombie **dans l'eau**. `exposed` ne teste pas l'eau alors que la brûlure
solaire est bloquée par `isInWaterOrRain` : il reboucle à 1 Hz indéfiniment sans jamais mourir.
C'est le seul scénario non borné.

**Prérequis** · Jour, phase < `sunImmunePhase` (5), `daySleepEnabled`, terrain sans abri à 12 blocs.
Le chemin devient du code mort dès la phase 5, soit ~2 h 30 de jeu.

**Correctif recommandé** · Un cooldown sur retour `null` (ne pas retenter avant N activations, ou
tant que le zombie n'a pas bougé de > 4 blocs) retire l'essentiel du coût pour 30 minutes de
travail. Compléments : amorcer `bestScore` à un plafond fini pour rendre le pruning actif dès la
première colonne, et parcourir en anneaux croissants avec sortie au premier succès.

---

### 🟡 #6 — Le snapshot du flow-field lit le monde en masse sur le thread serveur

`ai/flowfield/FlowFieldSnapshotBuilder.java:63-77` · `ai/flowfield/CellClassifier.java:29-55`
lentille perf · effort ~2 h pour le cache de chunk · **mécanisme confirmé, ampleur revue**

**Mécanisme** · À distinguer du solve, qui lui est correctement déporté. Dans
`FlowFieldManager.tick`, la ligne 91 `CpuFlowField.snapshot(level, players)` s'exécute **avant**
`computing.set(true)` (:92) et `POOL.submit` (:93) : elle est bien sur le thread serveur. Elle
classe chaque colonne de la grille via `CellClassifier`, qui descend jusqu'à 9 niveaux Y en
enchaînant `isLoaded` + `getBlockState`.

**Impact** · Le cas nominal est **bien plus modeste qu'il n'y paraît** : la grille suit la boîte
englobante des joueurs + `flowMargin=24`, donc avec un seul joueur elle fait 49 × 49 = **2 401
cellules**, pas 36 864. Soit ~60 000 lectures monde ≈ 1,5-2 ms toutes les 500 ms. Le plafond
`flowMaxGrid=192` n'est atteint qu'avec des joueurs séparés de ≥ 144 blocs **sur les deux axes** —
là, le coût monte à ~21 ms concentrées dans un seul tick. Aucun throttle n'est actif par défaut :
`flowResampleOnMoveDist` vaut 0.0 (move-gate désactivé) et le garde `computing` ne mord jamais
puisque le solve (8,81 ms mesuré) termine largement dans les 500 ms.

À noter : ce snapshot coûte probablement **plus** que le solve dès que la grille grandit — et lui
est sur le thread principal.

**Correctif recommandé** · Le meilleur rapport gain/effort est un cache de chunk explicite : la
boucle interne parcourt `cz`, donc 16 colonnes consécutives partagent le même chunk — hisser le
`getChunk` hors de la boucle supprime 15/16 des résolutions. Ensuite, étaler le snapshot sur
plusieurs ticks par bandes de colonnes transforme le pic en coût amorti. Défaut annexe :
`CellClassifier:59-60` alloue deux `new BlockPos` par colonne-mur.

---

### 🟡 #7 — Le buffer statique `SNAPSHOT` retient une fournée d'entités indéfiniment

`effect/contamination/ContaminationTick.java:25-33`
lentille mémoire · effort ~15 min · **trouvé indépendamment par 2 lentilles**

**Mécanisme** · Le `clear()` est **derrière** le garde de sortie anticipée :

```java
private static final ArrayList<LivingEntity> SNAPSHOT = new ArrayList<>();
public static void tick(MinecraftServer server) {
    if (!ContaminationConfig.contaminationEnabled || ContaminationState.tracked.isEmpty()) {
        return;                       // <-- SNAPSHOT n'est PAS vidé ici
    }
    long t = server.getTickCount();
    SNAPSHOT.clear();
```

Deux chemins ordinaires laissent le contenu en place : `tracked` devient vide (dernière victime
guérie ou morte), ou la peste est coupée. `ContaminationLifecycle.onServerStopped()` purge sept
collections — jamais `SNAPSHOT`, qui vit dans une autre classe. C'est la collection oubliée par la
remédiation du 2026-07-27.

**Impact** · Ce n'est **pas** une fuite qui grandit : le buffer est borné par `|tracked|`. Mais une
seule `LivingEntity` retenue épingle `entity.level` → `ServerLevel` → chunks + `MinecraftServer`
(chaîne confirmée dans le bytecode 1.21.11). En solo, quitter au menu puis charger un autre monde
dans la même JVM garde donc le monde précédent inaccessible au GC jusqu'au premier tick non vide du
monde suivant. **Impact nul en serveur dédié**, où `SERVER_STOPPED` précède la fin du processus.

**Prérequis** · Au moins une victime au moment où `tracked` se vide — soit le fonctionnement
nominal.

**Correctif recommandé** · Déplacer le `SNAPSHOT.clear()` **avant** le garde de sortie anticipée,
et l'ajouter à la purge de `onServerStopped()`. La règle générale vaut pour tout futur buffer
scratch statique : ne jamais le nettoyer uniquement sur le chemin chaud qu'il partage avec une
sortie anticipée.

---

### 🟡 #8 — Couper la contamination à chaud transforme `tracked` en accumulation

`effect/contamination/ContaminationLifecycle.java:35-42` · `ContaminationTick.java:28`
lentille mémoire · effort petit · **mécanisme confirmé, ampleur revue à la baisse**

**Mécanisme** · `ENTITY_LOAD` appelle `onLoad()` **sans** consulter `contaminationEnabled`, et le
test `age(le) > 0` lit un attachment **persistant** (NBT), qui survit aux sauvegardes et
rechargements de chunk. Chaque rechargement recrée un objet `LivingEntity` neuf — `Entity.hashCode()`
renvoie `this.id`, incrémenté par un compteur monotone, donc chaque reconstruction produit une
**entrée distincte** dans le `HashSet`. Le seul retrait à l'exécution est le balayage de `tick()`,
court-circuité par `!contaminationEnabled`. Le handler `ENTITY_UNLOAD` existe mais ne touche pas la
contamination (il ne traite que `TargetIndex`, `ZombieRegistry`, `SpatialGrid` et
`VanillaTargetingGoals`).

**Impact** · Deux bornes limitent la casse : une fois la peste désactivée, `contaminate()` et le
hook de dégâts sont tous deux gardés, donc **aucune nouvelle victime n'apparaît** — la population
de porteurs est figée ; et `onDeath` (non gardé) retire définitivement chaque porteur qui meurt.
La population est donc monotone décroissante, et `SERVER_STOPPED` remet tout à zéro. Ordre de
grandeur réaliste : 10³-10⁴ objets obsolètes sur une semaine d'uptime continu, soit des dizaines
de Mo.

**Prérequis** · Un admin coupe la peste sur un monde qui en contient déjà, puis ne redémarre pas.

**Correctif recommandé** · Deux verrous indépendants : garder `onLoad` derrière
`contaminationEnabled`, et purger l'état une fois lors de la transition activé → désactivé plutôt
que de conditionner le balayage de nettoyage à un drapeau de gameplay.

---

### 🟡 #9 — Le pool de render targets de l'overlay client n'est jamais libéré

`client/ContaminationScreenOverlay.java:47, 77-80`
lentille cycle de vie · effort ~30 min · **mécanisme confirmé au bytecode**

**Mécanisme** · Vérifié au `javap` sur le jar 1.21.11 : `CrossFrameResourcePool.release()` se
réduit à `pool.addFirst(new ResourceEntry(...))` — il ne libère rien. `endFrame()` est le **seul**
point qui décrémente `framesToLive`, appelle `close()` et retire l'entrée ; il n'existe **aucune
expiration autonome** (le `3` du constructeur est le nombre de frames à conserver, pas un délai
d'auto-purge). Or ni `endFrame()`, ni `clear()`, ni `close()` ne sont jamais appelés sur
`RESOURCE_POOL` — le champ est `private static final` et la classe ne contient que `register()`,
`render()` et `plagueLevel()`. `PostChain.process()` ne nettoie pas non plus. À titre de contraste,
le `GameRenderer` vanilla appelle `endFrame()` à chaque frame, `clear()` au redimensionnement et
`close()` à l'arrêt.

**Impact** · Les 5 `post_effect/contam_radial_blur_N.json` déclarent chacun deux cibles internes
non persistantes → créées plein écran **avec depth**. Rétention de base ≈ **33 Mo de VRAM en 1080p**
(color RGBA8 + depth, ×2), conservés jusqu'à la fermeture du jeu, y compris après retour au menu.
L'état stationnaire ne croît pas par frame (`acquire()` réutilise l'entrée), mais chaque **taille de
fenêtre distincte** produit un `RenderTargetDescriptor` différent dont l'ancien couple ne sera jamais
réapparié ni fermé — c'est le vecteur d'accumulation que vanilla neutralise explicitement avec
`clear()` dans `resize()`.

**Prérequis** · Joueur symptomatique au moins une frame. Client uniquement — aucun impact serveur.

**Correctif recommandé** · Appeler `RESOURCE_POOL.endFrame()` une fois par frame, **y compris sur
les chemins de sortie anticipée** (lignes 67 et 72), sinon le pool ne draine jamais quand le joueur
n'est pas malade ; et `close()` au retour au menu. Alternative plus simple : réutiliser le pool du
`GameRenderer`, déjà correctement piloté.

---

### 🟡 #10 — `@At("TAIL")` rate exactement le cas que le mixin existe pour corriger

`mixin/EffectClearCuresPlagueMixin.java:25`
lentille mixins · effort inclus dans #1 · **confirmé au bytecode**

**Mécanisme** · `@At("TAIL")` ne vise que la **dernière** instruction `RETURN`, contrairement à
`@At("RETURN")` qui vise toutes les sorties — vérifié en désassemblant `BeforeFinalReturn` de
sponge-mixin. Dans `LivingEntity.removeAllEffects()`, le bytecode montre trois `IRETURN` : @11
(`false`, côté client), @25 (`false`, `activeEffects` vide), @54 (`true`). Le handler est donc
attaché au seul `return true`, atteint uniquement si au moins un effet a été retiré.

**Impact** · Une victime au stade **latent** ne porte aucun effet de peste — le ralentissement
d'infection est un `AttributeModifier` transient, pas un `MobEffectInstance`, et l'icône
`SUPER_CONTAMINATION` n'est posée qu'à l'entrée en stade symptomatique. Si elle ne porte aucun autre
effet, la méthode sort par le `return false` et le handler ne s'exécute jamais. `/effect clear` ne
guérit donc pas la peste latente — précisément le comportement que le mixin dit corriger dans son
propre javadoc (*« latent has no effect at all to go missing »*). L'échec n'est pas totalement
silencieux : la commande lève `ERROR_CLEAR_EVERYTHING_FAILED`.

**Prérequis** · `/effect clear` (réservé aux opérateurs) sur une victime latente sans autre effet.

**Correctif recommandé** · Passer `@At("RETURN")` — mais **uniquement** conjointement au correctif
de #1, sinon le lait guérirait aussi les latents. La solution qui règle les deux d'un coup est de
cibler `EffectCommands` plutôt que `removeAllEffects`.

---

### 🟠 #5 — Un plancher codé en dur rend une option de config inerte sur un quart de ses cibles

`effect/contamination/ContaminationHallucination.java:47-50` · `effect/contamination/ContaminationEpisodes.java:93-96`
lentille qualité de code · effort ~15 min · **vérifié directement**

**Mécanisme** · La même formule — inverser l'intensité pour raccourcir l'écart entre deux flares, avec
un plancher anti-division-par-zéro — est écrite deux fois, avec **deux planchers différents** :

```java
// ContaminationEpisodes.rollGap — plancher configurable
1.0 / Math.max(ExpertConfig.expertContamIntensityFloor, mult)

// ContaminationHallucination.rollHallucGap — plancher codé en dur
1.0 / Math.max(1.0e-3, mult)
```

`expertContamIntensityFloor` est une vraie option : déclarée dans `ExpertConfig.java:41`, bornée
`0.000001..1000` dans `ConfigBoundsTable.java:301`, donc exposée dans le JSON, dans `/lethalconfig`
et dans l'écran de config.

**Impact** · Régler cette option n'affecte que **3 des 4 types de flare** — l'hallucination l'ignore
et reste sur `1.0e-3`. La divergence est donc déjà matérialisée, pas hypothétique. Le commentaire de
classe de `ContaminationHallucination` affirme pourtant *« Duration scales up / gap scales down with
intensity, like episodes »* : la promesse d'équivalence est écrite et fausse.

**Prérequis** · Aucun — l'écart existe dans le code livré.

**Correctif recommandé** · Le plancher d'intensité est une règle métier unique : la déplacer dans
`ContaminationState`, à côté de `devTimeScale()` qui applique déjà exactement ce motif pour l'autre
plancher (`Math.max(expertContamTimeScaleFloor, …)`). Une méthode `intensityFactor(double mult)`
appelée par les deux `rollGap`.

---

### 🟡 #12 — Le tirage uniforme `[min,max]` est réimplémenté 5 fois à côté de son helper canonique

`effect/contamination/ContaminationState.java:128-133` (canonique) · 5 sites dans `ContaminationTick.java:51`
et `:104`, `ContaminationEvolve.java:21`, `ContaminationSymptoms.java:43`, `ContaminationState.java:79`
lentille qualité de code · effort ~45 min

**Mécanisme** · `rollScaled` est le helper de référence et applique deux protections :

```java
min = Math.max(0.0, min);
max = Math.max(min, max);          // réordonnancement
double v = (min + RNG.nextDouble() * (max - min)) * factor;
```

Les 5 copies manuelles refont le lerp **sans** le réordonnancement ni le plancher. Trois d'entre
elles enchaînent en plus le même test `RNG.nextDouble() * 100.0 < pct` — un « roll de pourcentage »
identique à trois endroits.

**Impact** · La sémantique est déjà incohérente : `rollScaled` tolère un `min > max` (il réordonne),
les 5 copies non. Sur ces sites, inverser une paire min/max en config produit un tirage sous le
minimum, voire négatif — la peste devient soignante, ou les seuils de guérison/évolution ne se
déclenchent jamais. Aucune borne ne s'y oppose : `ConfigBoundsTable` borne chaque champ
indépendamment, jamais la relation entre deux champs. ~20 lignes redondantes, et toute évolution de
la politique de tirage exige 6 modifications.

**Prérequis** · Inverser une paire `contamDamageMin`/`Max` (ou guérison, symptômes, évolution) avec
deux valeurs parfaitement dans les bornes.

**Correctif recommandé** · Ajouter à `ContaminationState`, à côté de `rollScaled`, deux primitives :
`uniform(min, max)` faisant le clamp et le réordonnancement puis le lerp, et
`rollPercent(minPct, maxPct)` renvoyant un booléen. `rollScaled` se réécrit sur `uniform()`, les
5 sites deviennent un appel d'une ligne. Aucune indirection nouvelle en pratique.

---

### ⚪ #13 — `refreshTargetIndex()` est appelé deux fois par tick

`tick/TickScheduler.java:58-59`
lentille perf · effort 1 minute · **vérifié directement, remonté par 4 lentilles**

**Mécanisme** · La ligne est dupliquée à l'identique, commentaire compris :

```java
world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
world.refreshTargetIndex(server); // must precede the bucket pass, which queries it
```

`TargetIndex.refresh()` documente lui-même *« Runs once per server tick, and costs O(prey) »* —
l'invariant est violé par un copier-coller.

**Impact** · Aucune casse fonctionnelle : `refresh()` est idempotent (`reposition()` sort tôt sur
`was == now`). Le second appel est du travail intégralement perdu — une itération complète sur
toutes les proies suivies, par dimension, à chaque tick. ~15 µs/tick à 500 proies, ~50-100 µs sur
un monde avec ferme ou élevage. Modeste en absolu, mais c'est 100 % de gaspillage sur l'un des
rares balayages `O(n)` par tick, et le correctif est à risque nul.

**Correctif recommandé** · Supprimer la ligne 59.

---

## Qualité de code — duplication et structure

Recherche de clones exhaustive (fenêtre glissante normalisée sur 184 fichiers Java, 5 JS, 19 CSS),
puis lecture manuelle des **deux côtés** de chaque candidat.

**Verdict global : le dépôt est structurellement sain**, nettement au-dessus de la moyenne pour un
mod de cette taille. Sur 14 200 lignes de Java, la recherche n'a produit qu'**un seul** doublon
inter-fichiers de plus de trois lignes. Les découpages sont réels : `CrackingBlock` extrait,
`ContaminationManager` réduit à de la pure délégation, `ConfigFields` éclaté en quatre feuilles à
responsabilité unique, `OptionEntry` factorisant tout le rendu de ligne, `MoveMath` remplissant son
rôle de feuille partagée. Trois des pistes de duplication les plus probables (`block/`,
`client/screen/`, `config/domain/`) se sont révélées **déjà correctement factorisées**.

**La dette réelle tient à un seul type de défaut** : la règle métier réimplémentée à côté de son
helper canonique, alors que le helper existe déjà. C'est le mode de défaillance le plus coûteux
parce qu'il est invisible au diff — les deux versions restent correctes séparément jusqu'au jour où
une seule est modifiée. Les deux cas les plus avancés sont remontés en findings #5 et #12 ; voici
les autres, tous en ⚪ BAS.

| Emplacement | Défaut | Effort |
|---|---|---|
| `entity/move/` — 8 sites | `s.isAir() \|\| !s.blocksMotion()` réécrit 8×, et la forme est tautologique (l'air ne bloque jamais le mouvement) — `MoveMath` est la place évidente | 20 min |
| `Descend.java:82-92` / `StairDescent.java:54-64` | ~20 lignes jumelles ; la branche 1 de `Descend.step()` se ramène à l'appel `StairDescent.build()` que sa branche 3 fait déjà | ~1 h |
| `ContaminationEpisodes.java:66-85` / `ContaminationHallucination.java:23-40` | Machine à états de flare écrite deux fois (~18 l.) — c'est ce couplage manqué qui a produit le finding #5 | ~1 h |
| `ZombieMood.java` (395 l.) | Mélange la machine d'humeur **et** celle du sommeil diurne (8 champs sur 13, 5 méthodes) ; `entity/mood/DaySleep.java` existe déjà et n'est qu'un couple de prédicats | 2-3 h |
| `config/domain/ZombieMoodConfig.java:55` | `sunShelterEnabled` déclaré, borné, traduit — **jamais lu** (vérifié : une seule occurrence dans tout le Java). Option visible et éditable sans aucun effet | 15 min |
| `ai/flowfield/Snapshot.java:41,45` | `focusY()` et `flags()` sans aucun appelant ; `walk()` est un alias strict de `passable()` — deux noms pour la même donnée entre solveur CPU et GPU | 10 min |
| `ZombieMood.java:313` / `MoodStateDispatch.java:56` | `suppressHunt` et `dropHunt` : mêmes 4 lignes, deux noms. Seul doublon inter-fichiers du mod | 10 min |
| `ZombieMood.java:103` et `:175` | Fraction de vie recalculée à la main deux fois, garde anti-division comprise | 5 min |
| `mixin/PlagueBlocksRegenMixin.java:22-25` | Deux constantes de gameplay en dur là où la convention du dépôt (`ExpertConfig` + `ConfigBoundsTable` + traduction) est appliquée partout ailleurs | 30 min |
| `web/assets/js/` — 7 sites | Test `prefers-reduced-motion` écrit 7× sous **deux formes** : 2 sites omettent le garde `window.matchMedia &&`, et certains capturent la valeur une fois quand d'autres la relisent — une bascule en cours de session est respectée par certains effets et ignorée par d'autres | 30 min |
| `web/assets/css/components/nav.css:99` et `:138` | Gabarit de bouton dupliqué (~30 l.) ; l'asymétrie s'est déjà installée — `.lang-toggle` a un `:focus-visible` que les deux autres n'ont pas | 20 min |
| `web/assets/js/sound.js` — 4 sites | Quatre synthétiseurs répètent le même squelette de rafale de bruit filtrée ; seuls six scalaires varient | ~1 h |
| `web/assets/js/` — 6 sites | Idiome `data-*-bound` (liaison idempotente imposée par le routeur pjax) réécrit 6× avec 6 noms d'attribut différents | 45 min |

**Trois cas appellent une décision d'intention avant tout refactoring** — à ne pas traiter
mécaniquement :

- `Ascent` / `PillarClimb` — classe abstraite à sous-classe unique, dont le javadoc décrit un repli
  vers *« the other ascent »* qui n'existe pas dans `MoveDispatch`. Replier la hiérarchie n'a de sens
  que si aucune seconde stratégie d'ascension n'est prévue. Si elle l'est, ne corriger que le
  commentaire.
- `TargetSelector.isAudible` / `SoundEventBus.tickPlayers` — la même règle « cette entité fait-elle
  du bruit ? » écrite deux fois, avec trois écarts réels (distance 3D vs horizontale, `acting`/`hurt`
  absents côté joueur). Le javadoc promet *« Mirrors the player-footstep rule … so hearing is
  consistent for all entities »* — c'est faux. Si les écarts sont délibérés (la position serveur d'un
  joueur est peu fiable), la bonne correction est **rédactionnelle**, surtout pas une fusion.
- `sunShelterEnabled` — l'auteur a déjà tranché en documentant l'anomalie sur le wiki. Signalé pour
  la complétude de l'axe code mort, pas comme reproche.

---

## Ce qui a été vérifié et jugé sain

Ces points ont été audités en profondeur et **ne sont pas des findings**. Ils sont listés pour
qu'ils ne soient pas ré-audités.

**Concurrence** — c'est le point fort du dépôt. Le `Snapshot` ne contient que des primitives ; il
est construit sur le thread serveur **avant** le `submit`, donc aucun accès Minecraft depuis un
thread de fond. La publication du résultat passe par un `AtomicReference` : chaîne happens-before
complète. Le garde « une seule tâche en vol » est correct. Les exceptions des tâches de fond sont
attrapées, pas avalées dans un `Future` jamais lu. Aucune collection non thread-safe n'est partagée
entre threads. Pas d'inversion d'ordre de verrous.

**Chemin GPU** — mesuré et fonctionnel. Les logs d'exécution du projet (AMD RX 9060 XT) donnent
`192² : cpu=13,00 ms / gpu=4,35 ms`, avec `gpu-cpu-parity : PASS` sur les 4096 cellules. La
libération des `cl_mem` est complète sur **tous** les chemins d'erreur (déclaration `null` avant le
`try`, `finally` avec `releaseQuietly` null-safe et `catch(Throwable)`). Le kernel `.cl` est
intégralement borné, index par index. `flowMaxGrid` est clampé à 512, donc aucune allocation géante
n'est possible. Aucun buffer direct Java à appairer manuellement.

**Validation de la config** — solide. La différence mécanique entre les 296 champs de
`config/domain/` et les 249 entrées de `ConfigBoundsTable` donne une correspondance **parfaite
1-pour-1** : 0 champ scalaire non borné, 0 borne morte. `ConfigBounds` pose un garde `isFinite`
**explicite avant** le `Math.max/min`, donc NaN ne traverse jamais le clamp. Aucun `setAccessible`,
aucun path traversal (le chemin est entièrement en dur), écriture atomique en place.

**Surface réseau et autorisation** — il n'existe qu'un seul `registerGlobalReceiver` serveur, et il
est correctement gardé (`COMMANDS_GAMEMASTER`, vérifié **avant** toute planification de travail).
La clé de config est résolue contre une allowlist réflexive stricte : une String arbitraire ne peut
pas atteindre un champ hors schéma. Désérialisation bornée (`STRING_UTF8` = 32767, plafond de trame
vanilla). Les trois commandes portent la même garde, avec arguments bornés. Aucune confusion
client/serveur : zéro référence à `net.minecraft.client` depuis le chemin commun.

**Hygiène du dépôt** — exemplaire, rien à faire. `git ls-files mod/run/` = 0 fichier ; le
`usercache.json` contenant de vrais pseudos et UUID, le `hs_err` de 1,85 Mo et les 2,75 Mo de logs
sont présents sur disque mais correctement ignorés. L'historique git est vierge de tout secret sur
toutes les branches — rien à purger. Le source set `dev` (harnais de test, `/lethalspawn`) est bien
**exclu du jar joueur** (vérifié sur l'artefact construit).

**Site web** — aucune XSS exploitable. Le routeur, principal suspect, est correctement écrit :
`sameOrigin()` et `isNavigableClick()` filtrent avant tout `fetch`, les URI `javascript:`/`mailto:`
sont explicitement exclues. Aucun `eval`, `new Function`, `document.write`. Aucun `target="_blank"`
(donc pas de tabnabbing). `<html lang>` correct sur les 22 pages. Zéro image bitmap : 271 Ko au
total.

---

## Findings écartés en vérification

**12 findings ont été réfutés** par la phase adversariale, et une vingtaine d'autres rétrogradés.
Les réfutations les plus instructives :

- **« Fuite `VanillaTargetingGoals.STRIPPED` au shutdown »** — réfuté. `MinecraftServer.stopServer()`
  draine bien tous les chunks (`while … chunkMap.hasWork()`) avant que `SERVER_STOPPED` ne soit
  émis en `@At("TAIL")` : la map est déjà vide.
- **« `SoundEventBus.lastPlayerPos` non borné »** — réfuté. `PlayerList.respawn()` fait
  explicitement `setId(serverPlayer.getId())` : le respawn **conserve** l'identifiant d'entité.
- **« Le niveau de permission 2 est trop permissif pour de la config persistante »** — réfuté.
  `/gamerule`, `/difficulty`, `/worldborder` et `/setworldspawn` sont **eux aussi** niveau 2 en
  vanilla tout en mutant de l'état persistant. La prémisse était fausse.
- **« Pas de pool de buffers GPU, 5-10 % de surcoût »** — réfuté par les mesures du projet lui-même :
  le GPU bat le CPU d'un facteur 3, allocations comprises.
- **« Le ForkJoinPool abandonné fuit des threads »** — réfuté sur les sources du JDK 21 : le trim
  fonctionne sans `shutdown()` via la cascade `tryTrim` → `deregisterWorker` → `signalWork`, et le
  constructeur ne démarre aucun thread.
- **« Un paquet de config par frappe clavier »** — obsolète : le debounce de 400 ms existe déjà.
- **« 50 Ko de JS bloquants »** — mesuré : **15,5 Ko en Brotli**, et Cloudflare compresse
  automatiquement. Le correctif proposé aurait de plus introduit une régression visuelle
  (flash de thème).

---

## Couverture

**Analysé** · 184 fichiers Java (~14 200 l.) du mod, 23 mixins, le kernel OpenCL, le source set
`dev`, les 9 classes de test, la configuration Gradle et Fabric, et le site `web/` (5 JS, 19 CSS,
22 HTML). Zones chaudes couvertes : boucle de tick et LOD, index spatial, IA de mouvement et
d'humeur, flow-field CPU et GPU, contamination, phases et variantes spéciales, config et
réflexion, surface réseau, commandes, mixins, cycle de vie serveur/dimension/client.

**Tests exécutés** · `./gradlew test` — **39 tests, 0 échec** (flowfield 14, config 15, phase 8,
move 2). `./gradlew clean build` — propre, 0 avertissement, mais **uniquement** en surchargeant
`org.gradle.java.home` (finding #4). Benchmark intégré du projet exécuté : solve 192² = 8,81 ms
hors thread principal, échantillonnage ~12 ns/zombie.

**Outils** · ✗ semgrep, ✗ gitleaks, ✗ osv-scanner, ✗ snyk — **aucun scanner de vulnérabilités
n'est installé sur cette machine**. Aucune vérification automatisée de CVE n'a donc été faite, et
aucun identifiant de CVE n'est cité dans ce rapport. `jocl:2.0.5` (mai 2023, dernière release,
projet dormant) redistribue des binaires natifs préconstruits : c'est un risque de *classe*, non une
vulnérabilité identifiée. À repasser sous `osv-scanner` sur une machine outillée.

**Non couvert** · Le comportement runtime sous charge réelle (aucun serveur à 1000 zombies n'a été
lancé) : toutes les estimations en ms/tick sont dérivées de la lecture du code et de la fréquence
d'appel, jamais mesurées — sauf celles du benchmark du projet, explicitement signalées. Les en-têtes
HTTP réellement renvoyés en production n'ont pas été vérifiés (aucune requête réseau émise). La
provenance du `gradle-wrapper.jar` n'a pas été recoupée avec les checksums officiels.

**Vérification inégale** · Les 11 premiers findings ont chacun subi une réfutation adversariale par
un agent indépendant. La lentille **qualité de code** (findings #5, #12 et le tableau BAS) a été
ajoutée en cours d'audit et n'a **pas** été soumise à cette phase — l'agent a néanmoins lu les deux
côtés de chaque duplication, et les deux findings promus (#5 et #12) ont été vérifiés directement
par l'orchestrateur. Les entrées BAS du tableau reposent sur la seule lecture de l'agent.

**Angle mort** · Une analyse statique ne peut pas voir : les interactions avec des mods tiers non
installés ici, le comportement du pilote OpenCL sur d'autres GPU, et les règles de gameplay non
documentées dont l'écart avec le code ne peut être jugé sans l'intention de l'auteur.

---

*Audit mené en lecture seule. 13 lentilles d'analyse, ~60 findings bruts, vérification adversariale
individuelle, 13 confirmés · 12 réfutés · ~20 rétrogradés.*
