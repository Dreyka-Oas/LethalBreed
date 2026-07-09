# LethalBreed — Optimisation perf (cible : 5000+ zombies IA custom)

> Bilan issu de : bench headless de l'API flow-field, analyse du hot-path, et 2 deep-research web (GameAIPro, Shipilev, docs des mods d'optim). Statut de chaque item : ✅ confirmé (source primaire / bench), ⚠️ plausible (non re-vérifié), ❌ réfuté.

---

## 0. Résultats du bench API (machine 16 cœurs, CPU solver, headless)

Fichier : `src/test/java/com/dreykaoas/lethalbreed/ai/flowfield/FlowFieldPerfBench.java`.

- **SOLVE** (Bellman-Ford CPU, partagé par TOUS les zombies d'une dimension) :
  - 128² = 10,0 ms · 192² (cap in-game) = 17,9 ms · 256² = 27,0 ms
  - Dépend de la **taille de grille**, PAS du nombre de zombies. ns/cellule baisse quand la grille grossit (parallélisme rentable).
- **SAMPLE** (lecture O(1) par zombie du champ résolu) : **~15 ns/zombie**, linéaire parfait.
  - 5000 zombies = 0,077 ms · 10000 = 0,15 ms → **négligeable**.
- **Verdict** : côté flow, 5000 zombies = solve 17,9 ms (off-thread + caché) + sample 0,055 ms (server thread) = ~35,8 % d'un tick 50 ms. **Optimisé côté flow.**

---

## 1. ✅ FAIT — le flow field est DÉBRANCHÉ (corrigé)

- `FlowFieldManager.tick()` (`FlowFieldManager.java:41`) **n'a aucun appelant** (grep = 0).
- Conséquence : `active()` reste `null` en permanence → `BrainNavigator.java:74` `field == null` → **chaque zombie retombe sur l'A* vanilla individuel**.
- L'optim centrale du mod (le flow field benché à 17,9 ms partagé) **ne tourne jamais**. Tout le coût actuel à 5000 zombies = 5000 A* vanilla.
- ✅ **Fix #1, priorité absolue** : appeler `ctx.flowFieldManager().tick(level, tickCounter)` 1×/dimension/tick dans `WorldMaintenance` (throttlé par `flowRecomputeInterval`, la logique de throttle + move-gate existe déjà dans `tick()`).
- Recherche web (GameAIPro Ch.23 + Ch.17, sources primaires, vote 3-0) : **flow-field partagé = LA méthode pour 1000+ agents vers cible commune**. Coût amorti sur tous les agents, lookup O(1) constant. A*-par-entité = pire cas exact (beaucoup d'unités, une cible).

---

## 2. Pathfinding — flow field (une fois #1 branché)

| Item | Fichier:ligne | Action | Statut |
|---|---|---|---|
| Recompute dirty-cells only | `FlowFieldSnapshotBuilder.java:64-77` | Ne reclassifier que les cellules modifiées, pas toute la grille à chaque solve | ⚠️ |
| Réutiliser les buffers snapshot | `FlowFieldSnapshotBuilder.java:57-77` (alloc L57-59) | Pooler `boolean[n]/int[n]/byte[n]` entre snapshots (mais voir §5 : le GC bat le pool sur petits objets — ici gros tableaux longue vie, pool OK) | ✅ |
| Hierarchical A* + flow-tiles | (nouveau) | Ne solve que les tuiles sur le chemin (ex. 50×50) au lieu de toute la map. jdxdev : 50² = 0,3 ms | ⚠️ |
| Eikonal vs Dijkstra | `BellmanFordSolver.java` | Eikonal (Fast Marching) = champ plus lisse, sans artefact diamant. Bellman-Ford actuel marche mais géométrique | ✅ (GameAIPro) |
| GPU solver | `ai/flowfield/gpu/*` | Déjà présent (OpenCL). Auto-calibre le crossover CPU/GPU. Vérifier qu'il s'active sur grandes grilles | — |
| Boxing des seeds | `FlowFieldSnapshotBuilder.java:79-95` | `List<Integer>` → `int[]` direct / fastutil `IntArrayList` | ✅ |
| BlockPos dans classif | `CellClassifier.java:59-60` | `new BlockPos` 2×/cellule BREAKABLE → passer la `MutableBlockPos` existante | ✅ |

---

## 3. Ciblage / Targeting (plus gros coût CPU récurrent ACTUEL)

| Item | Fichier:ligne | Action | Statut |
|---|---|---|---|
| Scan entités par zombie | `util/TargetSelector.java:83` | `getEntitiesOfClass` (scan AABB + alloc List) par zombie → requêter la **SpatialGrid** déjà maintenue, pas le monde vanilla | ✅ |
| Throttle selon distance | `util/TargetSelector.java` / `LODManager.java` | Cibler moins souvent quand le zombie est loin du joueur (pattern Mobtimizations). ⚠️ leurs **chiffres de gain (22-25%) sont RÉFUTÉS** — le principe tient, pas les nombres | ⚠️ / ❌(chiffres) |
| Sort → min en 1 passe | `util/TargetSelector.java:86-106` | Supprimer shuffle + sort O(k log k) allouant ; garder le min en une passe. `distanceToSqr` déjà bien utilisé (pas de sqrt) | ✅ |
| distSq recalculé 2× | `util/TargetSelector.java:98-99 / 111` | Pré-calc une fois par candidat | ✅ |
| Reclassify LOD trop fréquent | `LODManager.java:21-27` | strip/restore goals + gros scan à CHAQUE activation → ne reclassifier le target que tous les K ticks sauf perte | ✅ |

---

## 4. Line-of-sight (raycast)

| Item | Fichier:ligne | Action | Statut |
|---|---|---|---|
| Raycast voxel par candidat | `util/TargetSelector.java:137-160` | `getBlockState` pas-de-0.5 + `new MutableBlockPos`/candidat/zombie → **grille de visibilité partagée + Bresenham** au lieu de raycast par agent | ✅ |
| Mutualiser MutableBlockPos | `util/TargetSelector.java:137-160` | Un seul `MutableBlockPos` réutilisé, pas un `new` par appel | ✅ |
| Cache LOS court terme | (nouveau) | Cacher le résultat LOS quelques ticks (la visibilité change peu) | ⚠️ |

---

## 5. Allocations hot-path

**Règle (Shipilev, ✅ confirmé) :** HotSpot élimine les objets **non-échappants** via scalar replacement (escape analysis) → ~0 B/op, ~1,9 ns/op.
- **Piège** : ça casse sur les merges de control-flow (16 B/op) et les méthodes non-inlinées. Là → fastutil / pool encore utiles.
- **NE PAS pooler les petits objets courts** (BlockPos/Vec3) : le GC moderne bat le pool. Viser scalar-replacement = **capturer en locale, éviter que l'objet échappe**.
- **Pool OK uniquement** pour gros objets longue vie / très alloués (buffers snapshot, tableaux de path).

| Item | Fichier:ligne | Action | Statut |
|---|---|---|---|
| BlockPos rebuild 2× | `LodBucketPass.java:96` | `blockPosition()` appelé 2× → capturer en locale (aide le JIT à scalar-replace) | ✅ |
| Double update SpatialGrid | `ZombieBrain.java:54-56` + `LodBucketPass.java:96` | La grille est update 2× la même activation → une seule passe | ✅ |
| SpatialGrid remove O(n) | `SpatialGrid.java:68` (`new ArrayList(4)` L53) | `list.remove(z)` linéaire + `new ArrayList(4)` churn → swap-remove O(1) | ✅ |
| Sound query alloc | `SoundEventBus.java:98` / `SpatialGrid.java:80` | `queryRadius` alloue une List/event → passer un consumer réutilisé | ✅ |
| Registry boxing | `ZombieRegistry.java:16` | `ConcurrentHashMap<Integer>` autoboxing + overhead concurrent (hot path mono-thread) → fastutil `Int2ObjectMap` | ✅ |
| HashSet churn | `TickScheduler.java:30-31` | climbers/swimmers add/remove/itère chaque tick → liste + flag | ⚠️ |
| Celebrate scan | `ZombieMood.java:287` | `getEntitiesOfClass` à chaque kill → SpatialGrid / throttle | ✅ |
| findShade quadratique | `ZombieMood.java:233-265` | double boucle (2r+1)² + canSeeSky + 3 getBlockState/case, par zombie exposé au soleil (tous, de jour) → cache par zone, itération row-major, 1×/épisode | ✅ **(HAUT le jour)** |

---

## 6. Tick scheduling / bucketing

| Item | Fichier:ligne | Action | Statut |
|---|---|---|---|
| Scan registry entier/tick | `LodBucketPass.java:47-48` | Itère les 5000 + modulo pour jeter (buckets-1)/buckets → pré-bucketer en N listes maintenues à l'insert/remove | ✅ |
| Auto-scale buckets | `TickScheduler.java:46-52` | Déjà présent (`autoScaleBucketLoad`) — vérifier le tuning à 5000 | — |
| PerfRecap | `PerfRecap.java:67-70` | `String.format` + boucle 5000, mais gardé derrière dev-env → OK en prod | ✅ (RAS) |

---

## 7. Mods d'optim compagnons (Lithium, Krypton, VMP, C2ME, FerriteCore, ServerCore, ScalableLux, Immersive Optimization, Spark)

Détectés dans `InstalledMods.java` (booleans), **non branchés**.

**Fait clé (deep-research, ✅) : AUCUN de ces mods n'expose d'API pour s'y "brancher".** Ce sont des optimiseurs serveur (mixins sur le vanilla). LethalBreed en profite **passivement** s'il reste compatible vanilla.

| Mod | Rôle | API ? | Coopération |
|---|---|---|---|
| **Lithium** | optim AI/physique/ticking | ❌ (config `lithium.properties` togglable par mixin) | Escape hatch : `mixin.ai.task/sensor/pathing=false` si conflit avec l'IA custom. ⚠️ `mixin.ai.pathing` défaut **OFF** → ne pas supposer qu'il accélère ton pathfinding |
| **Immersive Optimization** | entity tick scheduler (ralentit selon distance/viewport + bucketing) | ❌ | passif |
| **VMP** | entity tracking area-map, many-players | ❌ | passif |
| **Krypton** | networking protocol | ❌ | passif |
| **C2ME** | chunk I/O parallèle | ❌ | passif |
| **FerriteCore** | RAM (dédup) | ❌ | passif |
| **ServerCore** | ticking divers | ❌ | passif |
| **ScalableLux** | moteur lumière async | ❌ | passif |
| **Spark** | profiler | API de profiling (pas d'optim) | optionnel pour mesurer |

### Dépendance : `recommends`, PAS `depends`

- ❌ **NE PAS mettre en `depends`** (obligatoire) : aucun n'a d'API → aucun besoin technique. `depends` = le mod refuse de charger sans eux, pour zéro gain de code. Casse le solo / les modpacks sans eux.
- ✅ **Utiliser `recommends`** dans `fabric.mod.json` (dépendance douce : Fabric suggère, ne bloque pas).
- ⚠️ **Conflit connu** : IA custom (strip/restore goals, targeting) vs Lithium `mixin.ai.task/sensor`. Coopération = **documenter** de désactiver ces mixins si comportement bizarre, pas s'y brancher.

---

## 8. Ordre d'attaque recommandé

1. 🔴 **Brancher `FlowFieldManager.tick()`** (§1) — sans ça tout le reste est secondaire (A* vanilla par zombie tourne).
2. **TargetSelector** (§3-4) : scan via SpatialGrid, supprimer sort, LOS Bresenham + cache. Plus gros coût CPU actuel.
3. **findShade** (§5, HAUT le jour).
4. **Double update SpatialGrid + allocs BlockPos** (§5).
5. **fastutil** sur registry + seeds (§5-2).
6. **Dirty-cell recompute + buffers snapshot** (§2) une fois le flow branché.
7. **`recommends`** des 9 mods dans fabric.mod.json (§7).

---

## Ce qui est DÉJÀ bon (ne pas toucher)

- SpatialGrid évite le O(n²) inter-zombies. ✅
- Pas de lock sur le hot path (grille mono-thread, flow field lock-free via AtomicReference). ✅
- `distanceToSqr` partout, pas de `distanceTo`/sqrt en boucle chaude (sauf recalcul redondant dans le comparateur §3). ✅
- Solve flow field off-thread (pool daemon) + caché. ✅
- GPU solver présent + auto-calibré. ✅

## Sources
- Bench : `FlowFieldPerfBench.java` (min-of-50, headless).
- [GameAIPro Ch.23 — Crowd Pathfinding Flow Field Tiles](https://www.gameaipro.com/GameAIPro/GameAIPro_Chapter23_Crowd_Pathfinding_and_Steering_Using_Flow_Field_Tiles.pdf) (primaire, vote 3-0)
- [GameAIPro2 Ch.17 — Robust Efficient Crowds](https://www.gameaipro.com/GameAIPro2/GameAIPro2_Chapter17_Advanced_Techniques_for_Robust_Efficient_Crowds.pdf) (primaire)
- [Shipilev — JVM Scalar Replacement (quark 18)](https://shipilev.net/jvm/anatomy-quarks/18-scalar-replacement/) (primaire)
- [jdxdev — RTS flow fields](https://www.jdxdev.com/blog/2020/05/03/flowfields/)
- [Lithium mixin config](https://github.com/CaffeineMC/lithium/blob/develop/lithium-mixin-config.md)
- [ImmersiveOptimization](https://github.com/Luke100000/ImmersiveOptimization)
- [Mobtimizations](https://modrinth.com/mod/mobtimizations) (⚠️ chiffres de gain réfutés, principe OK)
- [fastutil / Java perf](https://javapro.io/2025/04/07/hitchhikers-guide-to-java-performance/)
