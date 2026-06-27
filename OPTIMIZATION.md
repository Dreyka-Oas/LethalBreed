# LethalBreed — Optimisation serveur / réseau

Le client est déjà optimisé (Sodium + Iris + culling maison, voir `config/lethalbreed-client.json`).
Ce document couvre le **serveur** et le **réseau**, et la coexistence avec les mods d'optimisation.

## Mods compagnons (présents en dev dans `run/mods/`, déclarés en `suggests`)

| Mod | Rôle | 1.21.11 |
|---|---|---|
| **Krypton** | réseau (Netty, flush, entity tracker) | ✅ |
| **VMP** | entity tracking scalable (multi-joueurs) | ✅ |
| **Lithium** | logique serveur (AI selection, pathfinding cache, physics) — *no behavior change* | ✅ |
| **ServerCore** | activation range, sim distance dynamique | ✅ |
| **FerriteCore** | mémoire (-40% heap → moins de pauses GC) | ✅ |
| **C2ME** | chunks gen/IO multi-thread (libère le main thread) | ✅ |
| **ScalableLux** | light engine parallèle (utile car on casse/pose) | ✅ |
| **Immersive Optimization** | tick scaling graduel par distance | ✅ |
| **Spark** | profilage (`/spark profiler`) | ✅ |
| **Carpet** | dev (`/tick warp`, logs) | ✅ |

> **Observable** : pas de build 1.21.11 → non inclus (Spark couvre le profilage).

## Réseau
On **s'appuie sur Krypton + VMP** pour les packets de tracking vanilla (gain ~40% CPU/packet,
gratuit). Pas de bulk-packet custom : il ferait doublon / conflit (double-send) avec VMP. Si un jour
nécessaire, borrow : delta-encoding (skip pos inchangée), stagger par distance, range de tracking par
type. `InstalledMods` logue si Krypton/VMP sont là.

## Compat Lithium — verdict : **probablement OK, à tester**
On remplace déjà l'IA vanilla (events Fabric + flow-field maison), donc pas de collision de logique.
Si comportement zombie diverge : couper les patches `mixin.ai.*` dans `config/lithium.properties`,
garder physics/pathfinding/block-ticking. Notre mixin de rendu est client-only → zéro intersection.

## Incompatibilité IA (mods qui changent la logique zombie)
On pilote déjà les zombies → tout autre mod d'IA zombie **entre en conflit**. Détection automatique :
1. **Auto-detect comportemental** (`AiConflictDetector.scanZombie`) : au 1er zombie, scanne ses goals ;
   tout goal hors `net.minecraft.*` / `com.dreykaoas.lethalbreed` = un autre mod a injecté de l'IA →
   conflit. Marche pour **n'importe quel** mod, sans connaître son id.
2. **Liste connue** (`AiConflictDetector` + `breaks` dans `fabric.mod.json`) : ids d'IA-mobs connus →
   le loader refuse de démarrer (incompatibilité dure).
3. **Politique** : `failOnAiConflict=true` (défaut) → arrêt net avec message. Mettre `false` pour
   seulement avertir.

## Optimisations dans notre code (borrowed)
- **Throttle par distance-tier** (Immersive Optimization) : `LethalBreedConfig.throttleByLod`,
  diviseurs `lodMediumTickDivisor`/`lodLowTickDivisor` — les zombies distants tournent moins souvent.
- **Re-path throttlé** (`navReissueInterval`) : on ne relance `moveTo` que si le chemin est fini ou
  l'intervalle écoulé → moins de churn pathfinder.
- **LOD + stagger + flow field off-thread** : déjà en place (cœur du scaling).

## Profilage (test de charge 1000 zombies)
`/spark profiler` pendant un `/summon` massif → breakdown MSPT (flow-field vs tick zombie vs entity
tracker vs packets vs chunk IO). `/spark health` pour TPS/MSPT. Comparer avec/sans `throttleByLod`.
Note Windows : async-profiler indispo → moteur Java intégré (suffisant).

## Fichiers config
- `config/lethalbreed.json` — serveur (throttle, GPU `useGpu`, `failOnAiConflict`, `suppressVanillaWander`).
- `config/lethalbreed-client.json` — rendu client (culling, Sodium-aware).
- `config/lithium.properties` — si besoin de couper un patch AI Lithium.
