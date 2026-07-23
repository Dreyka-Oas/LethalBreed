# LethalBreed — Analyse d'architecture & optimisation

> Revue de l'architecture runtime du mod (`mod/src/main`) sous l'angle **perf / fluidité**.
> Cible du mod : ~1000 zombies « intelligents » (flow-field, casse/pose de blocs, ouïe), MC 1.21.11 / Java 21 / Fabric.
> Verdict court : **l'architecture est proche du meilleur possible pour ce type de mod.** Décisions structurantes justes, chemins chauds sans allocation, dégradation gracieuse sous charge. Les écarts restants sont **algorithmiques** (pas structurels) et pour la plupart déjà commentés dans le code.

---

## 1. Verdict global

| Axe | Note | Commentaire |
|-----|------|-------------|
| Décisions structurantes | ★★★★★ | Champ partagé/dimension, solve off-thread, LOD+bucketing, config statique |
| Chemin de mouvement | ★★★★★ | Zéro-alloc, lock-free, budget adaptatif au MSPT |
| Acquisition de cible | ★★☆☆☆ | Scan 80³ + tri + LOS raycast **non throttlé par LOD ni budget** — point chaud n°1 |
| Efficacité algorithmique | ★★★☆☆ | Bellman-Ford O(n·(W+D)) vs Dijkstra ; snapshot 100% main-thread |
| Robustesse long-run | ★★★☆☆ | Fuite mémoire contamination ; risque conflit Lithium (spawn) |
| Infra / build / mesure | ★★★★★ | GPU réel, double build player/dev, PerfRecap, tuning G1GC |

**Ce n'est pas de la complaisance** : le code montre une compréhension réelle des contraintes du tick serveur MC (accès monde non thread-safe, budget MSPT, garbage par tick). Ce que la plupart des mods ratent, celui-ci le fait bien.

---

## 2. Cœur d'architecture (vérifié directement)

### Points forts

1. **Flow-field partagé par dimension, pas par entité** — [`FlowFieldManager`](mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldManager.java)
   Un seul champ multi-sources (tous les joueurs = seeds coût 0) par dimension ; chaque zombie échantillonne sa cellule + l'aval en O(1). **Aucun pathfind par mob** — c'est LA décision qui fait tenir la perf à 1000 entités.

2. **Solve off-thread + publication lock-free**
   Snapshot sur le thread serveur → solve sur un pool daemon 2 threads basse priorité → swap atomique (`AtomicReference<FlowField>`, `FlowField` immuable) → lecture `active()` sans verrou. Garde anti-pile-up (`AtomicBoolean computing`) : on skippe le cycle si le solve précédent tourne encore.

3. **Bucketing + LOD adaptatif** — [`TickScheduler`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/TickScheduler.java) / [`LodBucketPass`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java) / [`LODManager`](mod/src/main/java/com/dreykaoas/lethalbreed/ai/LODManager.java)
   - Travail étalé sur N ticks (auto-scale pour viser ~200 zombies/tick).
   - 4 tiers LOD (HIGH/MEDIUM/LOW/FROZEN) avec diviseurs de fréquence par tier.
   - **Hystérésis** anti-flip-flop de tier (`lodHysteresis`).
   - **Budget dur par tick** (`aiTickBudget`) contre les pics de population.
   - **Throttle adaptatif au MSPT** : si le serveur rame (`mspt > seuil`), tous les diviseurs doublent pour délester la charge → dégradation gracieuse. Rare et excellent.
   - `frozenReclassifyDivisor` : les zombies gelés ne se reclassent que 1 activation sur N.

4. **Zéro-allocation dans le chemin chaud**
   - Config lue via champs `static` (ex. `SchedulerConfig.lodHigh`, `TargetingConfig.targetDetectRadius`) — pas de lookup de map, JIT-friendly.
   - `registry.all()` renvoie la vue live du `ConcurrentHashMap` (pas de copie par tick).
   - `Scalars.sq` pour comparer les distances au carré (évite `sqrt`).
   - Nav zombie : scratch `int[2]` réutilisé.

5. **Infra pro**
   - GPU OpenCL **réellement implémenté** (JOCL 2.0.5 bundlé via `include`, kernel Bellman-Ford `bellman_ford.cl` → `.clx`).
   - **Double build** : jar player (zéro code test/dev) vs jar dev (harnesses + commandes de load-test).
   - [`PerfRecap`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/PerfRecap.java) : instrumentation dédiée (ai ms/tick, MSPT, histogramme LOD, block ops, mémoire) — **on mesure ce qu'on optimise**.
   - Tuning G1GC Aikar-style, `breaks`/`suggests` déclarés (conscient des conflits avec Lithium/c2me/ferritecore/…).

### Point structurel discutable
- [`LodBucketPass:47`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java) itère **tout** le registre à chaque tick puis skip par modulo → O(N) chaque tick même si seul 1/N travaille. À 1000 zombies c'est un scan léger (modulo+continue), et le commentaire justifie le choix (re-spread runtime quand `tickBuckets` change). Tradeoff raisonnable ; des buckets pré-partitionnés éviteraient de toucher chaque entrée, au prix de re-partitionner sur changement de config.

---

## 3. Sous-système flow-field / GPU

**Bonne architecture, 2 écarts algorithmiques réels (conscients).**

### Confirmé bon
- Champ partagé/dimension multi-sources, échantillonnage O(1), solve off-thread, publication lock-free/immuable, garde anti-pile-up.
- Chemin de nav zombie **zéro-alloc** ([`BrainNavigator:24`](mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/BrainNavigator.java)).
- GPU réel + fallback CPU propre vérifié par self-test de parité cellule-à-cellule.

### Faiblesses (par impact)
1. **Snapshot 100 % sur le thread serveur** — [`FlowFieldManager:84`](mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldManager.java) → [`CellClassifier`](mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/CellClassifier.java)
   Jusqu'à ~15–30 `getBlockState`/cellule → **~550k lectures sur le main thread** à la grille max 192², toutes les 10 ticks. **Principal risque de micro-stutter.** Inhérent (accès monde MC non thread-safe) mais atténuable : copier les sections de chunk sur le main thread puis **classifier off-thread**.

2. **Bellman-Ford O(n·(W+D)) au lieu d'un Dijkstra/BFS à buckets O(n)** — [`BellmanFordSolver`](mod/src/main/java/com/dreykaoas/lethalbreed/ai/flowfield/BellmanFordSolver.java)
   Jusqu'à ~114M relaxations/solve au pire cas 192². Coûts quasi-entiers bornés (10/14/…) → *Dial's algorithm* ~2 ordres de grandeur plus rapide sur les grilles courantes. Le **fork-join par itération coûte plus que le calcul** sur la petite grille solo (~2400 cellules).

3. **Détails** : `move-gate` désactivé par défaut (resolve à 2 Hz même joueurs immobiles) ; 6 buffers GPU réalloués/libérés à chaque appel ; file GPU `synchronized` globale (pas de recouvrement multi-dimension).

---

## 4. Sous-système IA / mouvement / spatial / targeting

**Squelette LOD correct, mais le throttle ne couvre PAS l'opération la plus chère.** C'est le principal écart perf du mod.

**Contexte** : un `SmartZombie` est un *wrapper* autour d'une `Zombie` vanilla ([`SmartZombie:22`](mod/src/main/java/com/dreykaoas/lethalbreed/entity/SmartZombie.java)) — le brain du mod pilote l'IA **en parallèle** de l'entité vanilla qui continue de tourner.

### ⚠️ Point n°1 : le scan de cible n'est ni throttlé par LOD ni plafonné
Pour chaque zombie du bucket, l'ordre est ([`LodBucketPass:90-101` vs `117`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java)) : `classify` (scan cible) → `grid.update` → `applySunBurn` → `updateMood` → **PUIS** la barrière `dueThisActivation` + `aiTickBudget`. Donc :
- La barrière LOD et le budget dur ne gouvernent **que `sz.tick()`** (le mouvement). **Le scan d'acquisition de cible tourne à chaque activation pour HIGH, MEDIUM et LOW indistinctement.** Un zombie LOW à 120 blocs paie le scan complet aussi souvent qu'un HIGH.
- [`TargetSelector.findNearest`](mod/src/main/java/com/dreykaoas/lethalbreed/util/TargetSelector.java) (radius 40) = `AABB.inflate(40)` → boîte **80×80×80**, `getEntitiesOfClass` (alloue une List, balaye les sections), shuffle + `sort` par distance², puis **LOS par raycast voxel manuel** : `getBlockState` pas par pas (~0.5 bloc), jusqu'à ~80 lectures/candidat, sur *tous* les candidats jusqu'au premier visible. Pas de `level.clip`, pas de cache, pas de limite de candidats.

### ⚠️ Point n°2 : IA additive (double pilotage de navigation)
`forceNearestTarget` ne strippe **que** le `targetSelector` vanilla, pas les goals de mouvement/attaque. `ZombieGoalSuppressMixin` est gated par `suppressVanillaWander=false` **par défaut**. Résultat : les goals vanilla (MeleeAttack, MoveTowardsTarget, RandomStroll…) **et** `navigation.tick()` vanilla continuent de tourner et d'appeler `navigation.moveTo(...)` pendant que le brain du mod pilote aussi la navigation → **contention + calcul redondant** sur la même entité.

### Spatial grid : bien fait, mais sous-exploité
- [`SpatialGrid`](mod/src/main/java/com/dreykaoas/lethalbreed/spatial/SpatialGrid.java) : hash XZ (cellules 8 blocs), maintenu incrémentalement (re-bucket au franchissement de cellule, pas de rebuild), limite verticale `spatialVerticalLimit=24`. Correct.
- `queryRadius` **alloue une `ArrayList` neuve par appel** (mineur).
- **Limite de conception** : la grille ne contient **que des zombies**, pas les joueurs/proies → le targeting ne peut pas s'en servir et retombe sur `getEntitiesOfClass` 80³. **Opportunité d'optim manquée.**

### Mouvement : chemin nav principal zéro-alloc, chemin block-ops alloue
- Nav « normale » : scratch `flowDir` réutilisé → **allocation-free**. Très bien.
- Chemin block-ops (stuck/breaking/leaping) : nombreux `new BlockPos` par tick ([`Obstacle`](mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/Obstacle.java), [`Descend`](mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/Descend.java), `MoveMath.fallDistanceInto`, `Leap.hasLanding`). Borné aux HIGH/MEDIUM actifs, mais un assaut de mur collectif = des dizaines de zombies allouant chacun par tick.

### Charge non bornée par défaut
`autoScaleBuckets=false` + `tickBuckets=5` fixe → charge classify/tick = **pop/5, croissance linéaire non bornée**, et les zombies **ne despawnent jamais** (`setPersistenceRequired()`). À 5000 zombies = 1000 scans 80³/tick. `ShelterFinder.findShade` (triple boucle `canSeeSky`+`getBlockState`) appelée depuis `updateMood` non throttlé → pic à l'aube quand toute la horde cherche l'ombre.

---

## 5. Sous-système blocs / contamination / mixins / tick pipeline

**Nettement au-dessus de la moyenne : budgeting réel, travail amorti, 0 `@Overwrite`.** Les faiblesses sont ciblées (1 fuite mémoire, 1 zone de conflit Lithium, 1 scan global), pas structurelles.

### Blocs — casse/pose : solide, budgété des deux côtés
- **Pose** ([`BlockOperationQueue`](mod/src/main/java/com/dreykaoas/lethalbreed/block/BlockOperationQueue.java)) : file `ArrayDeque` + dédup, drain sous budget dur `blockOpsPerTick`/dimension, cap de file. **Aucune pose synchrone illimitée.**
- **Casse** ([`BreakManager`](mod/src/main/java/com/dreykaoas/lethalbreed/block/BreakManager.java)) : progressive/amortie dans le temps, cap `maxConcurrentBreaks`, overlay de crack poussé seulement au changement d'étage (pas de spam réseau).
- Seul point de vigilance : cumul des micro-`getBlockState` côté [`Obstacle`](mod/src/main/java/com/dreykaoas/lethalbreed/entity/move/Obstacle.java) sous grosse horde — mais amorti par le LOD.

### Contamination : scale sur les infectés seulement — mais fuit
- **Bien** : le sweep n'itère que le set `tracked` (early-out si vide), timers randomisés par victime (pas de flare par tick), buffer `SNAPSHOT` statique réutilisé (anti-CME sans alloc).
- **⚠️ FUITE MÉMOIRE CONCRÈTE** — [`ContaminationTick.java:37`](mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java) retire l'entité disparue **uniquement de `tracked`**, jamais des 6 maps keyées fortement par `LivingEntity` (`nextPulse`, `nextSymptomRoll`, `latentSlowUntil`, `nextEvolveRoll` dans [`ContaminationState:46-52`](mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationState.java) + `episodes` + `hallucTimers`). Or [`ENTITY_UNLOAD`](mod/src/main/java/com/dreykaoas/lethalbreed/init/EntityEventsInit.java) ne gère **que les zombies**. Une victime **non-zombie** (joueur déco, villageois dont le chunk se décharge) qui disparaît sans mourir n'est jamais purgée → références fortes retenues à vie, y compris à travers les reload. Fuite lente mais réelle sur serveur long-running.
- Garbage évitable : boxing `Integer`/`Long` par victime par tick sur plusieurs `HashMap` ([`ContaminationTick.java:57-58,97`](mod/src/main/java/com/dreykaoas/lethalbreed/effect/contamination/ContaminationTick.java)).

### Mixins : surface de conflit minimale par construction
- **23 mixins** (15 serveur + 8 client) : **18 `@Inject`, 4 `@Redirect`, 0 `@Overwrite`**. Injections chaudes toutes en **early-return** (ex. `EntityRendererMixin.shouldRender` sort si `type != ZOMBIE`). Aucun mixin sur `LivingEntity.tick`.
- **⚠️ Risque de conflit Lithium — spawn** : `SpawnStateMobcapMixin` (`@Shadow @Final` sur champs privés `spawnableChunkCount`/`mobCategoryCounts`) + `SpawnStateInvoker` (`@Invoker`) ciblent `NaturalSpawner.SpawnState` — **exactement ce que Lithium réimplémente**. Avec `defaultRequire: 1`, un inject raté = **crash de chargement**. C'est le seul risque de crash identifié ; à tester Lithium activé.
- Sodium : seulement `shouldRender` coopératif (risque faible). C2ME/FerriteCore : nul (threadent la génération/dédup mémoire, pas le tick entité).

### Tick pipeline : amortissement multi-niveaux (la vraie force)
- Bucketing auto-scalé + LOD par distance + budget dur `aiTickBudget` + **load-shedding adaptatif MSPT** + `frozenReclassifyDivisor`.
- **Limite à connaître** : `aiTickBudget` ne couvre **que** `sz.tick()`. La reclass LOD, l'update `spatialGrid`, `applySunBurn` et `updateMood` tournent pour **tous** les membres du bucket, hors budget ([`LodBucketPass:90-101`](mod/src/main/java/com/dreykaoas/lethalbreed/tick/LodBucketPass.java)). Voulu (grid frais) mais le « budget » n'est pas un plafond total.
- **Scan global** : [`SoundEventBus.tickEntities`](mod/src/main/java/com/dreykaoas/lethalbreed/sound/SoundEventBus.java) fait `level.getAllEntities()` (O(toutes entités)), throttlé 1/4 ticks — domine le coût son sur dimension chargée en items/projectiles.
- `PerfRecap` = **dev-only** → aucune télémétrie perf en production (seul `msptThrottle` réagit).

---

## 6. Recommandations d'optimisation (consolidées, priorisées)

### 🔴 Impact fort — les vrais leviers de fluidité
1. **Throttler / cacher le scan de cible par LOD.** C'est le gain le plus élevé : aujourd'hui `classify` → `TargetSelector.findNearest` (80³ + tri + LOS) tourne à **chaque** activation pour tous les tiers non-frozen. Le déplacer **derrière** la barrière `dueThisActivation`, ou mettre la cible en cache K ticks pour MEDIUM/LOW, et étendre `aiTickBudget` pour couvrir `classify` (pas seulement `tick()`).
2. **Remplacer le raycast LOS manuel par `level.clip` et ne tester la LOS que sur le candidat le plus proche** (ou capper le nombre de raycasts). Aujourd'hui : walk voxel `getBlockState` par pas sur *tous* les candidats. Alternative : réutiliser le cache `Sensing` vanilla.
3. **Alimenter le `SpatialGrid` avec les joueurs/proies** pour que le targeting requête la grille au lieu de `getEntitiesOfClass` 80³ par zombie. Corrige la cause du point 1 à la racine.
4. **Sortir le snapshot flow-field du main thread** : copier les sections de chunk (ou lire via heightmap) puis classifier off-thread ; sinon découper la classification sur plusieurs ticks. Pic périodique jusqu'à 36 864 cellules **indépendant du nombre de zombies**.

### 🟠 Impact moyen — robustesse & scaling
5. **Corriger la fuite mémoire contamination** : purger les 6 maps de timers au déchargement (`forgetTimers` dans la branche `isRemoved` + hook `ENTITY_UNLOAD` pour les non-zombies), ou passer ces maps en `WeakHashMap`. Bonus : fusionner les timers en un seul état attaché élimine mécaniquement la fuite ET le boxing par tick.
6. **Sécuriser les mixins de spawn contre Lithium** : passer les injects spawn en `require = 0` pour dégrader proprement au lieu de crasher au chargement, et tester la chaîne `SpawnState` avec Lithium activé.
7. **Réduire l'IA additive** : passer `suppressVanillaWander` à ON par défaut (ou stripper aussi les goals de mouvement/attaque + gérer le melee manuellement) pour supprimer le double pilotage goals+nav vanilla sous le brain.
8. **Borner la population traitée/tick** : activer `autoScaleBuckets` par défaut pour figer la charge à ~`autoScaleBucketLoad` quel que soit le pic (les zombies ne despawnent jamais).
9. **Remplacer Bellman-Ford par un flood Dijkstra/BFS à buckets (Dial)** pour les grilles courantes ; garder le parallèle au-delà d'un seuil de taille.
10. **`SoundEventBus`** : remplacer `getAllEntities()` par une requête bornée via le `SpatialGrid` déjà maintenu.

### 🟡 Impact faible — polish
11. Activer le `move-gate` flow-field par défaut + invalidation événementielle à la casse/pose de bloc.
12. Réutiliser les buffers GPU ; hisser `pool()`/`AtomicBoolean`/`new int[]` hors des boucles chaudes ; relever `gpuMinCells` ou activer `gpuAutoCalibrate`.
13. Exposer un compteur d'overrun de budget **même en production** (`PerfRecap` est dev-only → aucune télémétrie perf chez les joueurs).

---

## 7. Conclusion

**Est-ce « la meilleure architecture possible » ? Structurellement, oui — à ~85 %.** Les décisions de fond sont celles qu'un ingé perf expérimenté prendrait : champ partagé/dimension au lieu de pathfind par mob, solve off-thread lock-free, LOD + bucketing + load-shedding adaptatif au MSPT, config statique, chemins nav zéro-alloc, GPU réel avec fallback vérifié. Le mod **mesure** ce qu'il optimise et **documente** ses tradeoffs. C'est rare et sérieux.

**Les 15 % restants sont des écarts algorithmiques identifiables, pas des refontes** :
- Le throttle LOD protège le mouvement mais **laisse passer l'acquisition de cible** (l'op la plus chère) → recos 1-3, le gros du gain.
- Le solveur privilégie le parallélisme/parité GPU à l'efficacité pure (Bellman-Ford vs Dijkstra) → reco 9.
- Le snapshot monde reste sur le main thread → reco 4.
- Une fuite mémoire et un risque de conflit Lithium à corriger → recos 5-6.

Aucun de ces points n'exige de retoucher le squelette. En traitant les recos 🔴, le mod tiendrait ses ~1000 zombies avec une marge nettement plus confortable — et scalerait au-delà.
