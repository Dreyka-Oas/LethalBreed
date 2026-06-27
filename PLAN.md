# Plan — LethalBreed

## Context

Fabric mod 1.21.11 — Zombies vanilla améliorés avec IA environnementale.  
(Note : 1.21.11 sortie 9 déc 2025, "Mounts of Mayhem". DERNIÈRE version Java obfusquée — après, MC passe en non-obfusqué + numérotation 26.x. Fabric API `0.139.5+1.21.11` dispo, Mojang mappings OK.)  
Auteur : Dreyka Oas. Chemin : `F:\projects\LethalBreed`.  
Objectif : zombies construisent (blocs de terre), détruisent (avec drop), perçoivent les sons.  
Scale cible : « le plus de zombies possible sans lag » (objectif ~1000) → architecture flow field obligatoire.  
Compute : GPU optionnel **benchmark-gated** — cible **GPU AMD (tout modèle, OpenCL/JOCL)**, fallback CPU multi-thread (le maître par défaut). RX 9060 XT 4GB = machine dev de référence, pas une dépendance codée en dur.  
Serveur peut tourner sur même machine que GPU ou machine séparée sans GPU — `GpuComputeManager.isGpuAvailable()` détecte automatiquement → fallback `CpuFlowField` (ForkJoinPool) si OpenCL absent.  
Dimensions : toutes (Overworld, Nether, End, custom) — flow field par dimension.  
Rendu client : compatible Sodium + Iris Shaders.

### Réalité perf (à garder en tête)
Le facteur limitant de ~1000 zombies n'est **PAS** le pathfinding (flow field 128×128 ≈ 16k cases = µs en CPU). C'est, dans l'ordre :
1. **Collisions entité↔entité** vanilla (coût qui explose avec le nombre) → à neutraliser/atténuer.
2. **AI/navigation vanilla** par entité → remplacée par flow field + goals custom.
3. **LOD + budgets + threading** corrects.

Conséquence GPU : le flow field est petit + Bellman-Ford itératif (overhead lancement kernel × itérations + aller-retour PCIe). Gain GPU **incertain, possiblement négatif** pour cette taille → on construit le CPU d'abord et on **active le GPU seulement si un benchmark prouve le gain**. Le code GPU reste présent, mais désactivable.

### Cible
Serveur **dédié** (avec ou sans GPU) → détection/fallback justifiés. Le rendu client custom ne compte que pour les joueurs connectés moddés.

### État d'avancement (toutes phases buildées vertes)
- ✅ **Phase 0** — Gradle/Loom/Fabric setup, JVM GraalVM+G1GC. Client dev charge avec Sodium+Iris (RX 9060 XT détectée), serveur dev boot clean.
- ✅ **Phase 1** — `ZombieRegistry`, `TickScheduler` (buckets), `SpatialGrid`, `LODManager`, `DimensionManager` + **spawn control** (no baby/drowned/équipement, config-toggle).
- ✅ **Phase 2** — flow field CPU multi-source Dijkstra (`ai/flowfield/`), nav par gradient.
- ✅ **Phase 3** — block ops réactifs casse/pont (`block/`), budget ops/tick, `PlacedBlockTracker` (terre retirée 600 ticks sans drop).
- ✅ **Phase 4** — perception sonore (`sound/`) : pas des joueurs + casse de bloc → zombies investiguent. Priorité cible vanilla > flow > son.
- ✅ **Phase 5** — flow field calculé **hors thread principal** : snapshot main-thread → pool daemon → swap `AtomicReference`. (Tick zombie reste main-thread.)
- ✅ **Phase 6** — couche GPU OpenCL/JOCL (`gpu/`) : `GpuComputeManager` (init + détection AMD + build kernel `bellman_ford.cl` + solve) **benchmark-gated** (`useGpu=false` défaut, CPU maître, fallback CPU auto sur toute erreur).
- ✅ **Phase 7 (client opti)** — config client Sodium-aware (`LethalBreedClientConfig`, `config/lethalbreed-client.json`) + mixin de **culling distance** des zombies coopératif avec Sodium. Instancing/billboards = flags présents, implémentation lourde laissée en option future.
- Notes : mappings Mojang. Tick zombie sur thread serveur (le gros calcul = flow field, lui, est off-thread). Activer GPU = passer `useGpu=true` après benchmark.

## Environnement Dev

| Outil | Chemin | Note |
|-------|--------|------|
| JDK cible build | `C:\Program Files\BellSoft\LibericaNIK-23-OpenJDK-21` | Java 21 + GraalVM JIT — vérifié top pratique pour serveur MC (JIT GraalVM ~10-20% CPU-lourd). G1GC only (OK). Azul Prime + rapide mais Linux/incompat mods → exclu |
| JDK alternatif | `C:\Program Files\BellSoft\LibericaNIK-25-OpenJDK-25` | Java 25, pas pour ce mod |
| JAVA_HOME à configurer | `LibericaNIK-23` | Fabric 1.21.11 requiert Java 21 |
| GPU (dev) | GPU AMD (OpenCL) — réf dev : Radeon RX 9060 XT 4GB | OpenCL via driver AMD ; code générique, sélectionne 1er GPU AMD/OpenCL dispo |
| OpenCL runtime | `C:\Windows\System32\OpenCL.dll` | Présent, natif Windows/AMD |
| Pas de CUDA | — | GPU AMD, pas NVIDIA |
| Pas de Vulkan serveur | — | LWJGL absent du serveur MC |

`gradle.properties` doit pointer explicitement vers le JDK 21 :
```properties
org.gradle.java.home=C:\\Program Files\\BellSoft\\LibericaNIK-23-OpenJDK-21
```

---

## Project Setup

```
F:\projects\LethalBreed\
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/main/java/com/dreykaoas/lethalbreed/
│   ├── LethalBreedMod.java
│   ├── ai/
│   │   ├── flowfield/
│   │   │   ├── FlowFieldManager.java      ← orchestrateur principal
│   │   │   ├── FlowField.java             ← grille de coûts + directions
│   │   │   └── DijkstraComputer.java      ← calcul async
│   │   ├── goals/
│   │   │   ├── FlowFieldNavigateGoal.java ← déplacement via champ
│   │   │   ├── BreakBlockGoal.java        ← destruction de blocs
│   │   │   ├── BuildGoal.java             ← construction (terre)
│   │   │   └── InvestigateSoundGoal.java  ← suivi sonore
│   │   ├── StateMachine.java
│   │   └── LODManager.java
│   ├── entity/
│   │   ├── SmartZombie.java               ← wrapper par zombie
│   │   └── ZombieRegistry.java
│   ├── block/
│   │   ├── BlockOperationQueue.java       ← budget ops/tick
│   │   ├── BuildCoordinator.java          ← anti-doublon placement
│   │   ├── BreakCoordinator.java          ← partage progression casse
│   │   └── PlacedBlockTracker.java        ← suppression auto 30s, sans drop
│   ├── sound/
│   │   ├── SoundEventBus.java
│   │   └── SoundPropagator.java
│   ├── spatial/
│   │   └── SpatialGrid.java               ← hash grid XZ, cell 8 blocs
│   ├── tick/
│   │   └── TickScheduler.java             ← stagger buckets
│   └── config/
│       └── LethalBreedConfig.java
├── src/main/java/com/dreykaoas/lethalbreed/mixin/
│   ├── ZombieEntityMixin.java             ← inject goals + spawn control
│   └── ZombieEntitySpawnMixin.java        ← block babies + drowned
└── src/main/resources/
    ├── fabric.mod.json
    ├── lethalbreed.mixins.json
    └── config/lethalbreed.json
```

### build.gradle.kts (Fabric Loom + JOCL)

```kotlin
plugins {
    java
    id("fabric-loom") version "1.11-SNAPSHOT"  // ⚠️ 1.7 trop vieux pour 1.21.11 — vérifier dernière version Loom sur fabricmc.net au build
    id("com.github.johnrengelman.shadow") version "8.1.1"  // bundle JOCL natif
}
group = "com.dreykaoas.lethalbreed"
version = "1.0.0"
java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
dependencies {
    minecraft("com.mojang:minecraft:1.21.11")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.x")   // vérifier dernière version 1.21.11
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.139.5+1.21.11")  // vérifié dispo — épingler (ou + récent compat 1.21.11)
    // Rendu — compatibilité Sodium + Iris
    modCompileOnly("maven.modrinth:sodium:mc1.21.11-latest")
    // GPU compute — OpenCL bindings (AMD natif via driver Windows)
    include(implementation("org.jocl:jocl:2.0.5"))
}
```

**Note JOCL** : `include()` = Fabric Loom bundle JOCL dans le JAR final. Pas de dep externe nécessaire sur le serveur. Les natives OpenCL (.dll) viennent du driver GPU AMD déjà installé.

Ajout dans `fabric.mod.json` :
```json
{
  "id": "lethalbreed",
  "version": "1.0.0",
  "authors": ["Dreyka Oas"],
  "environment": "*"
}
```

Arbre complet avec GPU + client :
```
├── src/main/java/com/dreykaoas/lethalbreed/
│   ├── ...
│   ├── dimension/
│   │   └── DimensionManager.java      ← FlowField+SpatialGrid par dimension
│   ├── gpu/
│   │   ├── GpuComputeManager.java     ← détection + init OpenCL, fallback CPU
│   │   ├── GpuFlowField.java          ← kernel Bellman-Ford sur GPU (serveur)
│   │   └── CpuFlowField.java          ← fallback ForkJoinPool (serveur)
│   ├── network/
│   │   ├── ZombieBulkPositionPacket.java  ← packet bulk server→client
│   │   └── PacketRegistry.java
│   └── client/ (@Environment CLIENT)
│       ├── ZombieInstanceRenderer.java    ← instanced rendering OpenGL
│       ├── InstanceBuffer.java            ← VBO mat4 par zombie
│       ├── FrustumCuller.java             ← culling CPU-side
│       └── ZombieRenderState.java         ← state interpolation
└── src/main/resources/
    ├── ...
    └── kernels/
        └── bellman_ford.cl             ← kernel OpenCL (Bellman-Ford parallèle)
```

---

## Architecture

### SmartZombie.java
Wrapper autour du `ZombieEntity` NMS. Contient :
- `ZombieState state` (enum : IDLE / PURSUING_PLAYER / PURSUING_SOUND / BUILDING / BREAKING / DESCENDING)
- `Vec3d soundTarget`
- `LODLevel lod`
- `int bucketIndex`
- `void tick()` — appelé par TickScheduler

### TickScheduler.java
Stagger sur N buckets (défaut 5). 1000 zombies × N=5 = 200 updates/tick.
- Utilise `ServerTickEvents.END_SERVER_TICK` (Fabric API)
- Chaque tick traite 1 bucket, avance l'index

### SpatialGrid.java
Hash grid 2D XZ, cellule 8 blocs. `HashMap<Long, List<SmartZombie>>`.
- `queryRadius(x, z, r)` → liste candidats + filtre distance
- Mise à jour lazy : zombie vérifie changement de cellule lors de son tick
- **Par dimension** : `DimensionManager` tient un `SpatialGrid` + `FlowField` par `RegistryKey<World>`

### DimensionManager.java
```java
// Une instance FlowField + SpatialGrid par dimension
Map<RegistryKey<World>, WorldAIContext> contexts;
// WorldAIContext = { FlowField, SpatialGrid, FlowFieldManager }
// Créé on-demand quand zombie spawne dans une dimension
```

---

## Flow Field / Dijkstra Map

Calcul unique pour tous les joueurs → chaque zombie sample son vecteur en O(1).

### FlowField.java
```java
// Grille 2.5D sur rayon 128 blocs autour des joueurs
short[]  costGrid;    // [x * W + z]
byte[]   flowDirX;    // -1 / 0 / +1
byte[]   flowDirZ;
byte[]   flags;       // bitmask: BUILD_NEEDED, BREAK_NEEDED, JUMP_NEEDED
long     computedAtTick;
AtomicReference<FlowField> active;  // lock-free read par zombie ticks
```

### DijkstraComputer.java (async)
- Thread pool dédié : 2 threads daemon, priorité NORM-1
- Input : `WorldSnapshot` (chunk snapshots capturés sur le main thread avant dispatch)
- Coûts : déplacement=1, saut=10, placement=100, casse=50×hardness
- Blocs non-cassables (bedrock, obsidian, barrière) → cellule IMPASSABLE
- Output → `FlowFieldManager.publish()` → `activeField.set(newField)` sur main thread

### FlowFieldManager.java
- Recompute toutes les 10 ticks OU joueur bouge > 8 blocs
- `BlockBreakEvent` / `BlockPlaceEvent` → marque cellules dirty → recompute local
- Capture snapshots chunks dans rayon via `Chunk.getChunkSnapshot(true, false, false)`

---

## Contrôle des Spawns

Via `ZombieEntitySpawnMixin` (Mixin sur `ZombieEntity.initialize()`).

```java
// Fabric event alternatif : ServerLivingEntityEvents.MOB_FINALIZE_SPAWN
// Bloquer babies : type == EntityType.ZOMBIE && isBaby() → cancel spawn
// Bloquer drowned : type == EntityType.DROWNED → cancel spawn
// Bloquer zombies équipés : stripEquipment() après spawn
```

Injection des Goals via `ZombieEntityMixin` sur `initGoals()` :
```java
// goalSelector est accessible directement en Mojang mappings
goalSelector.addGoal(1, new BreakBlockGoal(entity));
goalSelector.addGoal(2, new BuildGoal(entity));
goalSelector.addGoal(3, new InvestigateSoundGoal(entity));
goalSelector.addGoal(4, new FlowFieldNavigateGoal(entity));
// Suppression wander vanilla : priorité plus haute avec canUse()=false quand nécessaire
```

---

## Block Manipulation

### BuildGoal.java
- Trigger : flow field marque BUILD_NEEDED en avant, gap ≤ 4 blocs
- `BuildCoordinator.tryReserve(pos, zombieId)` → si OK, enqueue `PlaceOp(Blocks.DIRT, pos)`
- Pilier pour monter : place bloc à ses pieds puis avance
- Pont : place blocs de l'autre côté du gap

### BreakGoal.java
- Trigger : bloc solide en avant, dans `MaterialRegistry` (cassable)
- `BreakCoordinator` partage la progression si plusieurs zombies sur même bloc
- `world.destroyBlock(pos, true)` → drop item + son (vanilla behaviour)
- Animation craquelure via `ServerWorld.sendEntityStatus()` sur chunk viewers

### PlacedBlockTracker.java

Tracks tous les blocs de terre placés par zombies.  
Suppression automatique après 30s (600 ticks), **sans drop**.

```java
// Structure : dimension → Map<BlockPos, long tickPlaced>
Map<RegistryKey<World>, Map<BlockPos, Long>> tracker;

// Chaque tick : scan les entrées où currentTick - tickPlaced >= 600
// level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)  // flag 3 = update + notify, no drop
// Retirer de tracker
```

Performance : si 1000 zombies buildent → max 1000 entries. Scan linéaire OK.

### BlockOperationQueue.java
- Budget : 20 ops/tick (configurable)
- Priorité : casses > placements
- Cap 500 opérations en attente → drop si dépassé

### MaterialRegistry.java
- Set pré-calculé au startup : `hardness >= 0 && hardness < maxHardness`
- Exclusions config : BEDROCK, OBSIDIAN, BARRIER, CRYING_OBSIDIAN, END_PORTAL_FRAME

---

## Sound Perception

### SoundEventBus.java
Écoute events Fabric :
- `PlayerMoveEvent` (throttle 5 ticks, skip si sneak + pas bougé > 0.5 blocs)
- `ServerLivingEntityEvents.AFTER_DAMAGE` (target = joueur)
- `PlayerBlockBreakEvents.AFTER` (bruit fort, rayon × 2)

Volume → rayon : `radius = BASE_RADIUS * volume` (BASE_RADIUS = 24 blocs).

### SoundPropagator.java
- Query `SpatialGrid.queryRadius(origin, radius)`
- Atténuation murs : compte blocs solides sur chemin Manhattan → filtre
- Zombie entend → `smartZombie.soundTarget = origin` → transition PURSUING_SOUND

### InvestigateSoundGoal.java
- `canUse()` : soundTarget != null
- Navigation : `entity.getNavigation().startMovingTo(target, 1.0)`
- Arrivée à ±2 blocs → clear soundTarget → IDLE
- Priorité joueur direct > son si joueur entre dans range vanilla

---

## LOD System

| Level  | Distance       | Comportement                                       |
|--------|----------------|----------------------------------------------------|
| HIGH   | < 32 blocs     | Flow field + goals + son + construction complète   |
| MEDIUM | 32–64 blocs    | Flow field seulement, pas de block ops             |
| LOW    | 64–128 blocs   | Wander vanilla, 1 tick sur 8                       |
| FROZEN | > 128 blocs    | Aucun tick plugin                                  |

Reclassification toutes les 20 ticks via LODManager + SpatialGrid.

---

## State Machine

```
IDLE → PURSUING_PLAYER (joueur en range)
     → PURSUING_SOUND  (soundTarget assigné)

PURSUING_PLAYER → BUILDING  (BUILD_NEEDED devant)
               → BREAKING  (bloc cassable bloque chemin)
               → IDLE       (joueur mort / parti)

PURSUING_SOUND → PURSUING_PLAYER (joueur détecté)
              → BREAKING        (obstacle vers source son)
              → IDLE            (arrivé à destination)

BUILDING → PURSUING_PLAYER (bloc posé, chemin libre)
         → IDLE            (timeout 5s)

BREAKING → PURSUING_PLAYER (bloc cassé)
         → IDLE            (timeout ou non-cassable)
```

Garde : min ticks par état (BUILDING=10, BREAKING=5) → pas de spam transitions.

---

## Résumé architecture Server ↔ Client

| Côté | Avec mod | Sans mod (vanilla) |
|------|----------|--------------------|
| **Serveur** | GPU compute (JOCL OpenCL) + ForkJoinPool | — |
| **Client** | Instanced rendering, bulk packets | Rendu MC vanilla, packets normaux |

**Dégradation gracieuse** : si client pas installé → serveur détecte canal packet absent → envoie entités normalement via MC vanilla. Aucune erreur.

```
fabric.mod.json → "environment": "*"
Chaque feature client = @Environment(EnvType.CLIENT) → pas chargé sur serveur pur
```

---

## Protocole Packets Custom (Server → Client)

Uniquement envoyé aux clients **qui ont le mod** installé (canal enregistré).

### Détection mod côté serveur

```java
// ServerPlayConnectionEvents.JOIN
ServerPlayNetworking.registerGlobalReceiver(
    ZombieBulkPacket.ID,
    (payload, ctx) -> { /* client capable */ }
);
// Si client enregistre le canal → ZombiePlayerContext.setModded(player, true)
// Si non → vanilla fallback pour ce joueur
```

### ZombieBulkPositionPacket

Envoyé toutes les 2 ticks (100ms) aux clients moddés.  
Contient : `short[] ids` + `float[] x, y, z, yaw` pour tous zombies dans rayon 128 blocs.

```java
// Fabric PacketCodec
record ZombieBulkPositionPacket(short[] ids, float[] x, float[] y, float[] z, float[] yaw)
    implements FabricPacket {
    static final PacketType<...> TYPE = PacketType.create(
        new Identifier("lethalbreed", "zombie_bulk"), ZombieBulkPositionPacket::new
    );
}
```

Compression : ~14 bytes/zombie × 1000 = 14KB/packet. Acceptable (UDP MC 1.21.1 compresse).

---

## Client-Side Rendering Optimization (FPS)

> Scope : côté client uniquement (`@Environment(EnvType.CLIENT)`). Ne s'exécute pas sur serveur dédié.

### Problème

1000 zombies = 1000 draw calls MC → CPU bottleneck côté client.

### Solution choisie : OpenGL Instanced Rendering

Render tous les zombies visibles en **1 draw call** via `glDrawArraysInstanced`.  
Aucun conflit avec MC (reste OpenGL). LWJGL déjà disponible côté client.

```
src/main/java/com/dreykaoas/lethalbreed/client/
├── ZombieInstanceRenderer.java   ← Fabric EntityRenderer override
├── InstanceBuffer.java           ← VBO des matrices modèle par zombie
├── FrustumCuller.java            ← Culling GPU-side (shader)
└── ZombieLODModel.java           ← Modèles LOW/MED/HIGH poly
```

**ZombieInstanceRenderer.java** remplace `ZombieRenderer` via :
```java
// Fabric EntityRendererFactories.register
EntityRendererRegistry.register(EntityType.ZOMBIE, ZombieInstanceRenderer::new);
```

**Compatibilité Sodium + Iris** :
- Sodium : override `EntityRenderer` respecté. Mais le Render Layer custom doit utiliser `RenderLayer` Fabric (pas GL direct) sinon Sodium l'ignore.
- Iris : notre instancing ne reçoit pas les effets shader Iris (acceptable — priorité perf).
- Utilise `WorldRenderEvents.AFTER_ENTITIES` pour le pass instancié.
- `modCompileOnly` Sodium dans build.gradle → compile contre l'API Sodium mais pas requis au runtime.

Chaque frame :
1. Collecte position/rotation de tous zombies dans frustum → `InstanceBuffer` (mat4[])
2. Upload VBO une fois → `glBindBuffer` + `glBufferData`
3. 1 seul `glDrawArraysInstanced(GL_TRIANGLES, 0, vertexCount, instanceCount)`
4. LOD : zombies > 32 blocs → modèle basse poly (50% triangles)

### Option Vulkan Compute (headless) — Phase bonus

**Uniquement si** OpenGL instancing ne suffit pas. Risque modéré.

- Init contexte Vulkan **sans surface** (`VkInstance` headless, `VK_KHR_surface` absent)
- Coexiste avec contexte OpenGL MC (deux APIs GPU indépendantes)
- Compute shader SPIR-V → frustum culling + tri par distance sur GPU
- Résultat (liste indices zombies visibles) → upload dans InstanceBuffer OpenGL
- Gain estimé : libère ~2ms CPU/frame sur 1000 zombies

```java
// VulkanComputeClient.java — init guard
if (!isVulkanSupported()) {
    log.warn("Vulkan unavailable — frustum culling reste CPU");
    return;
}
// VkInstance sans surface → headless compute seulement
```

**Priorité** : implémente OpenGL instancing d'abord. Vulkan compute = Phase 7 (optionnel).

---

## Debug Performance Overlay

Pas de commandes. Monitoring passif, toujours actif en dev.

**Serveur** — log console toutes les 5 secondes :
```
[LethalBreed] tick=48ms | zombies=1024 (HIGH:312 MED:501 LOW:211) | gpu=0.4ms | blockOps=18/tick | packets=14KB/s
```

**Client** (si mod installé) — extension du F3 debug screen via `DebugHudCallback` (Fabric API) :
```
[LethalBreed] GPU instancing: 847 instances | 1 draw call | FPS saved: ~12
```

```java
// Serveur : ServerTickEvents.END_SERVER_TICK, log toutes les 100 ticks
// Client  : ClientLifecycleEvents + DebugHud extension → @Environment(CLIENT)
```

---

## Minecraft Touch Points (Mojang Mappings, Fabric 1.21.11)

> Note : "NMS" (`net.minecraft.server`) est un terme Bukkit/Spigot. En Fabric on modifie les classes MC via mappings — ici Mojang mappings. Les noms de classes ci-dessous sont corrects pour Mojmap.

| Besoin | Classe Minecraft (Mojmap) |
|--------|-----------|
| Entité zombie | `net.minecraft.world.entity.monster.Zombie` |
| Base goal | `net.minecraft.world.entity.ai.goal.Goal` |
| GoalSelector | `net.minecraft.world.entity.ai.goal.GoalSelector` |
| Navigation sol | `net.minecraft.world.entity.ai.navigation.GroundPathNavigation` |
| Contrôle mouvement | `net.minecraft.world.entity.ai.control.MoveControl` |
| Monde serveur | `net.minecraft.server.level.ServerLevel` |
| Position bloc | `net.minecraft.core.BlockPos` |
| État bloc | `net.minecraft.world.level.block.state.BlockState` |

Accès via Mojang mappings (Loom remapping transparent au build).

---

## GPU Compute + Multi-Threading

### Stratégie globale

```
┌─────────────────────────────────────────────────────┐
│  Main Thread (MC tick)                              │
│  - World writes (BlockOperationQueue budget)        │
│  - SmartZombie tick dispatch → ForkJoinPool         │
│  - Flow field field swap (AtomicReference)          │
└────────────┬────────────────────────────────────────┘
             │
    ┌────────▼────────────┐    ┌───────────────────────┐
    │  ForkJoinPool       │    │  GPU Thread (JOCL)    │
    │  (zombie ticks)     │    │  Bellman-Ford kernel  │
    │  Virtual Threads    │    │  sur GPU AMD (OpenCL) │
    │  (sound propagation)│    │  async, résultat →    │
    └─────────────────────┘    │  AtomicReference swap │
                               └───────────────────────┘
```

### GpuComputeManager.java

Singleton initialisé au startup du mod. Détecte OpenCL disponible, sélectionne AMD GPU, compile kernel.

```java
// Séquence init
1. CL.create()  // init JOCL
2. clGetPlatformIDs() → cherche platform AMD ou default
3. clGetDeviceIDs(CL_DEVICE_TYPE_GPU) → liste GPUs
4. Sélectionne AMD (préférence) ou first available
5. clCreateContext(), clCreateCommandQueue()
6. Charge kernels/bellman_ford.cl depuis resources
7. clBuildProgram() → si échec → log warning → mode CPU
8. Expose: boolean isGpuAvailable(), executeFlowField(...)

// Si aucun GPU : GpuComputeManager.isGpuAvailable() = false
// DijkstraComputer utilise CpuFlowField à la place
```

### GpuFlowField.java — Kernel OpenCL (bellman_ford.cl)

Algorithme : Bellman-Ford parallèle (relaxation par couches). Chaque work-item = une cellule de la grille.

```c
// kernels/bellman_ford.cl
__kernel void relax_step(
    __global short* cost,      // grille coûts [W * H]
    __global char*  blockType, // 0=air, 1=solide, 2=cassable, 3=buildable
    __global char*  dirX,      // output direction X
    __global char*  dirZ,      // output direction Z
    __global char*  flags,     // output flags (BUILD/BREAK/JUMP needed)
    int W, int H,
    __global int* changed      // flag: 1 si au moins une relax effectuée
) {
    int idx = get_global_id(0);
    int x = idx / H, z = idx % H;
    if (x >= W || z >= H) return;

    short cur = cost[idx];
    short best = cur;
    int bestDir = 0;

    // Check 4 voisins + diagonales (8)
    // Pour chaque voisin : calcul coût traversée selon blockType
    // Si coût < best → update best, bestDir
    // ...

    if (best < cur) {
        cost[idx] = best;
        dirX[idx] = (char)(bestDirX);
        dirZ[idx] = (char)(bestDirZ);
        atomic_or(changed, 1);
    }
}
```

**Exécution** : boucle jusqu'à `changed == 0` (convergence) ou max iterations.  
Pour grille 128×128 = 16 384 work-items, convergence en ~128 iterations.  
Temps estimé : < 1ms sur GPU AMD moderne (à confirmer par benchmark — gain GPU non garanti vu la petite taille, cf. Réalité perf).

### CpuFlowField.java — Fallback CPU

```java
// Dijkstra classique sur ForkJoinPool
ForkJoinPool pool = new ForkJoinPool(
    Runtime.getRuntime().availableProcessors() - 2
);
pool.submit(() ->
    IntStream.range(0, grid.length)
        .parallel()
        .forEach(this::relaxCell)
).get();
```

Virtual Threads (Java 21) pour les tâches I/O-bound (sound propagation, LOD updates) :
```java
// Sound propagation off main thread
Thread.ofVirtual().start(() -> soundPropagator.process(event));
```

### DijkstraComputer.java — Dispatcher GPU/CPU

```java
public void compute(WorldSnapshot snapshot, FlowField target) {
    if (gpuManager.isGpuAvailable()) {
        gpuFlowField.compute(snapshot, target);  // async GPU
    } else {
        cpuFlowField.compute(snapshot, target);  // ForkJoinPool
    }
}
```

### Multi-Threading : Zombie Ticks

`TickScheduler` dispatch le bucket courant sur ForkJoinPool :

```java
void onServerTick(MinecraftServer server) {
    int b = tickCount++ % BUCKETS;
    List<SmartZombie> bucket = buckets.get(b);

    // Zombies en HIGH/MEDIUM LOD → parallel
    List<SmartZombie> heavy = bucket.stream()
        .filter(z -> z.lod != LODLevel.FROZEN)
        .toList();

    ForkJoinPool.commonPool().submit(() ->
        heavy.parallelStream().forEach(SmartZombie::tick)
    );
    // Note : SmartZombie.tick() ne touche PAS au monde MC directement
    // Il enqueue des ops dans BlockOperationQueue (thread-safe)
    // Les world writes restent sur le main thread
}
```

**Thread safety** : SmartZombie.tick() → lecture flow field (AtomicReference, lock-free) + écriture dans BlockOperationQueue (ConcurrentLinkedQueue). Aucun write direct au monde MC. Main thread seul écrit les blocs.

---

## Thread-Safety (RÈGLE CRITIQUE)

Règle d'or : **un seul thread écrit/lit le monde MC = le main thread (server tick)**. Toute lecture du monde
depuis un worker = crashs aléatoires (ConcurrentModification, chunk en cours d'unload).

- Lecture du monde pour décisions zombie → via **snapshots capturés sur le main thread**
  (`Chunk.getChunkSnapshot(...)` pour blocs ; copie des données d'entités voisines nécessaires) AVANT dispatch worker.
- `SmartZombie.tick()` sur worker (ForkJoinPool) : **lecture seule** snapshot + flow field (`AtomicReference`, lock-free) ;
  **écriture uniquement** dans structures thread-safe (`BlockOperationQueue` = `ConcurrentLinkedQueue`, `soundTarget` volatile).
- Application des effets (poser/casser blocs, déplacer entité, MoveControl) → **toujours main thread**, en vidant
  les queues sous budget (`BlockOperationQueue` : 20 ops/tick, casses > placements).
- Le worker calcule le vecteur désiré ; le mouvement réel reste main thread.

---

## Phases d'implémentation

> Principe : jouable le plus tôt possible. CPU/serveur d'abord → benchmark scale → **GPU après** (si prouvé) → **rendu client en dernier** (le plus risqué).

**Phase 0 — Bootstrap**
- `gradle.properties` → `org.gradle.java.home` = LibericaNIK-23 (Java 21), `JAVA_HOME` idem
- `build.gradle.kts` : Loom (version 1.21.11), `officialMojangMappings()`, fabric-loader/api épinglés 1.21.11, Shadow (JOCL plus tard)
- `fabric.mod.json`, `lethalbreed.mixins.json`, config vide
- **Livrable** : `./gradlew build` → JAR ; serveur Fabric 1.21.11 démarre avec le mod (vide)

**Phase 1 — Squelette entité + spawn control**
- `ZombieRegistry`, `SmartZombie`, `TickScheduler` (buckets, no-op), `SpatialGrid`, `DimensionManager`
- Mixin injection goal **no-op** → vérifier injection OK
- Spawn control : bloquer babies + drowned + équipement
- **Livrable** : zombies suivis par le mod (compteur log), spawns filtrés, 0 IA custom

**Phase 2 — Flow Field CPU + navigation** ← *premier "ça bouge intelligemment"*
- `WorldSnapshot`, `CpuFlowField` (Dijkstra/Bellman-Ford ForkJoinPool)
- `FlowFieldManager` + async dispatch + AtomicReference swap
- `FlowFieldNavigateGoal` → zombies suivent joueurs, contournent obstacles, sans pathfinder vanilla
- Tuning coûts (déplacement=1, saut=10, placement=100, casse=50×hardness, IMPASSABLE)

**Phase 3 — Block Ops (casser/construire)** ← *gameplay "lethal"*
- `MaterialRegistry`, `BlockOperationQueue` (ConcurrentLinkedQueue)
- `BuildCoordinator`, `BreakCoordinator` (ConcurrentHashMap), `PlacedBlockTracker` (terre supprimée 600 ticks, sans drop)
- `BreakBlockGoal` (`world.destroyBlock(pos, true)` = drop + son), `BuildGoal` (pilier + pont terre)
- Tests thread-safety : beaucoup de zombies build/break en même temps, application main-thread only
- **Livrable** : zombies cassent un mur de verre, comblent un fossé de 3 blocs

**Phase 4 — Son + State Machine + LOD**
- `SoundEventBus` (Virtual Threads pour propagation), `SoundPropagator`, `InvestigateSoundGoal`
- `StateMachine` complet + gardes min-ticks, `LODManager` (HIGH/MED/LOW/FROZEN)
- **Livrable** : zombies se dirigent vers les bruits hors ligne de vue ; LOD réduit le coût à distance

**Phase 5 — Montée en charge CPU + benchmark** ← *prouver le scale AVANT le GPU*
- Threading ticks zombie (bucket courant → ForkJoinPool, lecture snapshot/flow field seulement)
- **Neutraliser/atténuer collisions entité↔entité vanilla** + couper goals vanilla coûteux
- Overlay debug serveur (log 5s : tick ms, counts LOD, blockOps/tick)
- **Benchmark TPS** à 100 / 300 / 500 / 1000 zombies → trouver le mur réel
- **Livrable** : chiffre honnête de « combien de zombies sans lag » en CPU pur

**Phase 6 — GPU Compute (benchmark-gated)**
- `GpuComputeManager` : init JOCL, détection AMD GPU, fallback gracieux
- Kernel `bellman_ford.cl` : Bellman-Ford parallèle ; `GpuFlowField` (upload → kernel → download)
- `DijkstraComputer` : dispatcher GPU/CPU
- **Benchmark GPU vs CPU** sur 128×128, 256×256, multi-flow-fields → **garder GPU activé seulement si gain net mesuré**, sinon CPU maître (code GPU reste, désactivé)
- **Livrable** : décision data-driven GPU + fallback gracieux vérifié (GPU absent → CPU, aucun crash)

**Phase 7 — Client Rendering (le plus dur, en dernier)**
- `ZombieBulkPositionPacket` + `PacketRegistry`, détection mod client (canal) → sinon fallback vanilla total
- Optimisations dans l'ordre **sûr → risqué** :
  1. **LOD modèle + frustum/occlusion culling** (gains faciles)
  2. **Billboards** zombies lointains (sprite plat au lieu de modèle 3D)
  3. **Instancing OpenGL** (`glDrawArraysInstanced`, 1 draw call/LOD) — compat Sodium (`RenderLayer` Fabric, `WorldRenderEvents.AFTER_ENTITIES`) ; Iris : pas d'effet shader sur instancing (accepté)
- Overlay F3 client (`DebugHudCallback`)
- **Livrable** : FPS tenable, mod ON vs OFF testé

**Phase 8 — Perf finale**
- Profiling async-profiler : GPU kernel, ForkJoinPool, SpatialGrid, packets bulk
- Flow field chunk cache + dirty-marking ; audit mémoire (SmartZombie ~200B × 1000 ≈ 200KB ✓)
- Vulkan compute headless (optionnel, dernier recours si FPS encore insuffisant)

---

## Vérification

1. `./gradlew build` → JAR dans `build/libs/`
2. Serveur Fabric 1.21.11 local → drop JAR dans `mods/`
3. Tests manuels :
   - Spawner 10 zombies face à un mur de verre → doivent casser
   - Fossé 3 blocs devant joueur → zombies doivent poser terre et passer
   - Joueur marche loin (sans ligne de vue) → zombies se dirigent vers le son
   - Tuer zombie builder → vérifie drop item bloc
   - Spawn 1000 zombies (`/summon` loop) → surveiller TPS avec F3
4. Vérifier : pas de baby zombies, pas de drowned dans spawns naturels
5. Vérifier : zombies n'ont aucun équipement
6. **Client mod ON** : F3 → GPU instancing actif, 1 draw call pour zombies
7. **Client mod OFF** (vanilla) : connexion fonctionne, aucune erreur, zombies visibles normalement
8. GPU serveur : log startup → `[LethalBreed] GPU: <nom GPU AMD détecté> — OpenCL OK`
9. GPU absent : log → `[LethalBreed] GPU: unavailable — CPU fallback activé`
