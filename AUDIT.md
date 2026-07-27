# Audit — LethalBreed

> Généré le 2026-07-27 · commit `7e03e97` · branche `feat/sleeping-zombie-visuals`
> Analyse en lecture seule. **Aucun fichier source n'a été modifié.** Ce document est le seul fichier créé.
> Méthode : recon déterministe → cartographie → 7 lentilles en parallèle → vérification adversariale.

## Résumé

Le mod est bien architecturé sur le plan de la performance — le flow-field partagé, le LOD à 4 tiers,
le bucketing et la publication lock-free du solveur sont corrects, et le document
`ANALYSE-PERF-ARCHITECTURE.md` de l'auteur est lucide sur ses propres faiblesses. Le risque dominant
n'est pas là où ce document regarde.

**Deux défauts critiques, tous deux hors du champ de l'analyse perf existante.** Le premier est un
oubli de contrôle d'accès : `/lethalphase` et `/lethalspecial` sont enregistrées sans `.requires(...)`
alors que les deux autres commandes du mod l'ont. N'importe quel joueur connecté, sans aucun
privilège, peut figer définitivement un serveur — et l'état est persisté dans la sauvegarde, donc le
redémarrage rejoue le gel. Le second est une fuite mémoire par six collections statiques clés par
entité, qui épingle le graphe monde complet ; l'auteur la documente déjà, elle est confirmée telle
qu'il la décrit.

**Le second axe de risque est la perte de données de configuration.** `ConfigIo.load()` réécrit le
fichier utilisateur après tout échec de lecture, sans sauvegarde préalable, avec un message de log
qui dit « keeping defaults » au moment précis où il détruit les réglages. La correction est de
quinze minutes et c'est le meilleur rapport effort/gain de l'audit.

**Ce qui n'est pas un problème.** Le handler réseau `SetConfig` est correctement gardé, au bon
endroit et sur le bon thread. Le solveur off-thread ne touche jamais le monde : le `Snapshot` est
une vraie copie, vérifié champ par champ. Les chaînes happens-before de publication du flow-field
sont correctes. Aucun secret, aucune injection, aucune surface web.

| Sévérité | Nombre | À traiter |
|---|---:|---|
| 🔴 Critique | 2 | Immédiatement |
| 🟠 Haut | 9 | Ce sprint |
| 🟡 Moyen | 12 | Backlog priorisé |
| ⚪ Bas | 6 | Opportuniste |

**29 findings bruts → 29 retenus, 0 réfutés, 4 sévérités révisées à la baisse.**
7 findings ont subi une vérification adversariale dédiée (marqués ✅) ; les 22 autres sont retenus
sans contre-expertise (marqués ○) et doivent être relus avant action.

---

## Synthèse

| # | Sév | Vérif | Axe | Emplacement | Problème |
|---|---|---|---|---|---|
| 1 | 🔴 | ✅ | authz | `command/LethalPhaseCommand.java:20` | `/lethalphase` sans `.requires()` → gel persistant |
| 2 | 🔴 | ✅ | fuite | `effect/contamination/ContaminationState.java:44` | 6 maps statiques clés-entité jamais purgées |
| 3 | 🟠 | ✅ | authz | `command/LethalSpecialCommand.java:33` | `/lethalspecial` sans `.requires()` → spawn illimité |
| 4 | 🟠 | ✅ | authz | `command/LethalPhaseCommand.java:22` | `n` non borné → `Integer.MAX_VALUE` |
| 5 | 🟠 | ✅ | données | `config/ConfigIo.java:63-67` | Config écrasée après échec de lecture |
| 6 | 🟠 | ○ | config | `config/ConfigBoundsTable.java:210` | 27 champs contamination sans borne |
| 7 | 🟠 | ○ | perf | `spatial/SpatialGrid.java:81` | Rayon × cellule → 16,8 M lookups/événement |
| 8 | 🟠 | ○ | natif | `ai/flowfield/gpu/GpuFlowFieldSolver.java:54` | 6 `cl_mem` hors `try` → fuite VRAM |
| 9 | 🟠 | ○ | freeze | `command/LethalConfigCommand.java:82` | OpenCL initialisé sur le thread serveur |
| 10 | 🟠 | ○ | perf | `config/domain/SchedulerConfig.java:15,21,68` | Aucune protection de charge active par défaut |
| 11 | 🟠 | ○ | perf | `tick/EveryTickPass.java:26` | Nageurs/grimpeurs à 20 Hz hors budget et LOD |
| 12 | 🟡 | ✅ | perf | `util/TargetSelector.java:82` | Scan de cible visite les zombies avant rejet |
| 13 | 🟡 | ✅ | perf | `entity/mood/ShelterFinder.java:19` | 8 112 `canSeeSky`/appel, sans cooldown |
| 14 | 🟡 | ✅ | données | `config/ConfigIo.java:96` | Écriture non atomique (troncature avant écriture) |
| 15 | 🟡 | ✅ | données | `config/ConfigIo.java:50-52` | Valeur objet/null → 250 champs suivants perdus |
| 16 | 🟡 | ○ | perf | `client/screen/NumOptionEntry.java:23` | Une écriture disque complète par frappe clavier |
| 17 | 🟡 | ○ | natif | `ai/flowfield/gpu/GpuContext.java:44` | Contexte OpenCL jamais libéré, pas de reprise |
| 18 | 🟡 | ○ | robust. | `ai/flowfield/FlowFieldManager.java:86` | Exception de solve avalée → flowfield figé |
| 19 | 🟡 | ○ | robust. | `ai/flowfield/FlowFieldManager.java:85` | `computing` armé avant `submit()` |
| 20 | 🟡 | ○ | fuite | `tick/TickScheduler.java:30-31` | Grimpeurs/nageurs non purgés au `SERVER_STOPPED` |
| 21 | 🟡 | ○ | robust. | `util/AiConflictDetector.java:76` | Exception levée depuis un callback d'entité |
| 22 | 🟡 | ○ | perf | `sound/SoundEventBus.java:108` | Le bus de son dégèle massivement le LOD |
| 23 | 🟡 | ○ | perf | `entity/ZombieMood.java:272` | Le scan de cible est exécuté puis jeté |
| 24 | ⚪ | ○ | robust. | `client/LethalBreedClient.java:19` | Écran de config ouvert sur tout paquet reçu |
| 25 | ⚪ | ○ | hygiène | `client/LethalBreedClientConfig.java:21` | 5 options sur 9 ne sont jamais lues |
| 26 | ⚪ | ○ | config | `config/ConfigBounds.java:26` | `clamp` no-op pour `double[]`, `DEFAULTS` aliasé |
| 27 | ⚪ | ○ | concur. | `ai/flowfield/gpu/GpuComputeManager.java:27` | État GPU lu hors moniteur sans `volatile` |
| 28 | ⚪ | ○ | concur. | `ai/flowfield/gpu/GpuContext.java:46` | `setExceptionsEnabled` est global à la JVM |
| 29 | ⚪ | ○ | perf | `config/ConfigSchema.java:59` | `find()` reconstruit la liste réflexive à chaque appel |

---

## 🔴 Critique

### #1 — `/lethalphase` sans contrôle d'accès : gel de serveur persistant par un joueur ordinaire
`command/LethalPhaseCommand.java:20-23` · lentille autorisation · effort ~5 min · ✅ vérifié, **confirmé et aggravé**

**Mécanisme** · La commande est enregistrée sans aucun `.requires(...)`. Le prédicat Brigadier par
défaut est `s -> true`, et ni vanilla ni `CommandRegistrationCallback` de Fabric n'imposent de niveau
de permission aux commandes de mods. `PhaseManager.setPhase` (`phase/PhaseManager.java:144`) ne fait
qu'un `Math.max(0, p)` — `applyCeiling` n'est appelé que depuis `tick()`, et `phaseMaxEnabled` vaut
`false` par défaut. La phase est ensuite persistée dans `PhaseSavedData`, puis lue par
`mixin/SpawnFrequencyMixin.java:35` qui en dérive un compteur de boucle de spawn.

**Preuve**
```java
dispatcher.register(Commands.literal("lethalphase")
        .executes(LethalPhaseCommand::show)
        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "n")))));
```

**Chiffrage du vérificateur** · `/lethalphase 2000000000` → `PhaseTable.frequency` = 2 133 400 000,0
(défauts `phaseFrequencyGrowth=1.0667`, `phaseFrequencyExponent=1.0`) → `extra` = 2 133 399 999 passes
de `NaturalSpawner.spawnCategoryForChunk` **par chunk et par tick**. À 1 µs par appel — l'ordre
réaliste — cela fait ~36 minutes de calcul pour un seul chunk d'un seul tick, sur le thread serveur.
La saturation `double → int` plafonne à 2,147e9 sans rien changer à l'ordre de grandeur. Même
`/lethalphase 100000` produit déjà 106 669 passes par chunk et par tick.

**Impact** · Le thread principal ne revient jamais du tick. Sur serveur dédié, le watchdog
(`max-tick-time=60000`) tue le processus ; en solo il n'y a pas de watchdog, donc gel permanent. La
phase ayant été écrite dans la sauvegarde, le redémarrage rejoue le gel au premier tick de spawn :
**déni de service persistant**, non réparable sans édition manuelle de `lethalbreed_phase.dat`.
Effets de bord du même geste : multiplicateurs d'attributs de l'ordre de 1e13 sur les zombies,
35 % de spéciaux, immunité solaire générale, et un message chat diffusé à tous les joueurs.

**Prérequis** · Être connecté. Aucun opérateur, aucun client modifié, aucun objet.

**Aggravations relevées par la vérification** · `SpawnStateMobcapMixin:46` sature lui aussi
`(int) Math.floor(base * factor)` à `Integer.MAX_VALUE`, donc le plafond global de mobs cesse de
freiner quoi que ce soit. Et l'auteur maîtrise l'idiome : `.requires` est présent sur
`LethalConfigCommand:51` et sur les deux commandes du source set `dev`. C'est un oubli, pas un choix.

**Correctif recommandé** · Poser `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` sur
le littéral racine, comme sur `lethalconfig`. Indépendamment, plafonner `extra` dans
`SpawnFrequencyMixin` : une boucle de spawn pilotée par une formule non bornée reste fragile quelle
que soit la provenance de la phase, y compris après une longue progression automatique.

---

### #2 — Six collections statiques clés par entité, jamais purgées : le graphe monde reste épinglé
`effect/contamination/ContaminationState.java:44-52`, `ContaminationTick.java:36-39` · lentille
concurrence/mémoire · effort ~30 min (2 lignes) à 1 j (refonte par UUID) · ✅ vérifié, **confirmé**

**Mécanisme** · `ContaminationState` déclare un `HashSet<LivingEntity>` et quatre
`HashMap<LivingEntity, Long>` `static final` ; `ContaminationEpisodes.episodes` et
`ContaminationHallucination.hallucTimers` suivent le même motif, soit **six collections fuyantes**.
Le seul purgeur complet, `forgetTimers(e)`, n'est appelé que depuis `cure()` et `onDeath()`. Or la
branche entité-invalide du balayage par tick ne retire que du `Set` :

**Preuve**
```java
if (e == null || e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
    ContaminationState.tracked.remove(e);   // ContaminationTick.java:37 — les maps ne sont pas touchées
    continue;
}
```

**Chemins d'oubli non couverts** · Déchargement de chunk, despawn, changement de dimension, arrêt du
serveur. `ServerEntityEvents.ENTITY_UNLOAD` est **structurellement incapable** de couvrir le cas :
son corps est entièrement gardé par `if (entity instanceof Zombie)`, alors que `contaminate()`
exclut explicitement les zombies — aucune victime n'est jamais un zombie. `SERVER_STOPPED`
(`init/LifecycleInit.java:33`) ne vide que `registry` et `dimensions`, et c'est le seul handler
d'arrêt du dépôt.

**Impact** · Une clé `LivingEntity` retient `Entity.level` → `ServerLevel` → `MinecraftServer` →
`RegistryAccess`, `ReloadableServerResources`, `PlayerList`. Les collections étant `static final`
dans le classloader Knot, un retour au menu ne les touche pas. La comptabilité correcte n'est pas
« N objets fuités » mais **un graphe monde complet retenu par session jouée, jamais relâché**.
Le vérificateur estime quelques dizaines à ~150 Mo par session retenue — `ServerLevel.close()`
décharge les chunks, donc le niveau retenu est une coquille partiellement vidée, pas le tas complet.

**Correction apportée au finding par la vérification** · L'aggravant « ids d'entités recyclés à
chaque serveur, donc les `put` ne remplacent pas la clé morte » est **faux** :
`Entity.ENTITY_COUNTER` est un `AtomicInteger` de durée de vie JVM, jamais réinitialisé. Il n'y a
donc aucune collision — ce qui **aggrave** la fuite plutôt que de l'atténuer, puisque chaque entité
morte occupe une clé distincte et que rien ne plafonne la croissance.

**Statut vis-à-vis de l'existant** · `ANALYSE-PERF-ARCHITECTURE.md` documente déjà ce défaut, avec
le décompte exact de six maps. Il est retenu ici parce qu'il est confirmé et non corrigé, pas parce
qu'il serait inédit.

**Correctif recommandé** · Appeler `forgetTimers(e)` au lieu du `tracked.remove(e)` isolé sur la
branche entité-invalide, et ajouter la purge des six collections au handler `SERVER_STOPPED`
existant. À terme, indexer par `UUID` ou passer en `WeakHashMap`, ce qui rendrait la fuite
structurellement impossible même si un chemin de purge est oublié.

---

## 🟠 Haut

### #3 — `/lethalspecial` sans contrôle d'accès : accumulation permanente d'entités
`command/LethalSpecialCommand.java:33-38` · effort ~5 min · ✅ confirmé en marge de la vérification #1

Même oubli que #1. Le contraste est explicite dans le dépôt : `dev/LethalSpawnCommand.java:34` porte
le gate avec le commentaire de l'auteur « must not be reachable by non-operators on a server ». La
commande **livrée** qui spawne des entités ne l'a pas ; la commande de dev, absente du jar joueur,
l'a. Chaîne : `/lethalspecial juggernaut 200` → 200 `EntityType.ZOMBIE.spawn` → `ENTITY_LOAD` →
`EntityEventsInit.java:69` `setPersistenceRequired()`. Le plafond de 200 est **par invocation** ;
aucun compteur global, aucun cooldown, seul l'anti-spam chat vanilla freine. Environ 200 zombies
jamais despawnés par seconde et par joueur, tickés indéfiniment, à nettoyer au `/kill`.
En variante `bombeur`, chaque entité déclenche une explosion à portée des autres joueurs — arme PvP.
Prérequis : phase ≥ 1, obtenue via `/lethalphase 1`, elle aussi sans gate — **les deux trous se
composent**.

**Correctif** · `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`. En défense en
profondeur, ne pas appeler `setPersistenceRequired()` sur les entités issues de
`EntitySpawnReason.COMMAND`, pour qu'un abus reste réversible par le despawn naturel.

### #4 — `n` de `/lethalphase` non borné supérieurement
`command/LethalPhaseCommand.java:22` + `phase/PhaseManager.java:144` · effort ~15 min · ✅ vérifié

`IntegerArgumentType.integer(1)` ne pose qu'une borne basse ; le maximum est `Integer.MAX_VALUE`.
Aucun clamp en aval : `setPhase` ne fait qu'un `Math.max(0, p)`, et `ConfigBoundsTable` borne bien
`phaseMax` à `[1, 1_000_000]` mais cette table n'est consultée que depuis `ConfigAccess.apply`,
jamais depuis ce chemin. Ce finding est distinct de #1 **parce que son correctif l'est** : même
après avoir posé le gate, un opérateur légitime qui teste `/lethalphase 100000` tue le serveur et
laisse la sauvegarde dans un état qui rejoue le crash.

**Correctif** · Borner dans Brigadier (`integer(0, 1_000_000)`, cohérent avec la borne `phaseMax`
existante) **et** clamper défensivement dans `setPhase`, qui est le point de convergence de tous les
appelants.

### #5 — `ConfigIo.load()` écrase le fichier de configuration après tout échec de lecture
`config/ConfigIo.java:63-67` · effort ~15 min · ✅ vérifié, **confirmé, seul maillon inentamé**

```java
} catch (Exception e) {
    LethalBreed.LOGGER.warn("[LethalBreed] config load failed ({}): keeping defaults", e.toString());
}
// Always (re)write so the file is complete and reflects newly-added options.
save();
```

L'accolade ligne 65 ferme le `catch` : `save()` est au niveau du corps de méthode, exécuté sur
**tous** les chemins. Après un échec de parsing, l'état mémoire vaut les défauts du code, et
`Files.writeString` écrase en `CREATE + TRUNCATE_EXISTING`. Aucun `.bak`, aucune copie, aucun
versionnage — recherche exhaustive sur `\.bak|backup|ATOMIC_MOVE|createTempFile|StandardCopyOption|Files\.copy|Files\.move|FileChannel|force\(` : zéro occurrence fonctionnelle dans tout le dépôt.
Le message de log dit « keeping defaults » au moment exact où les réglages sont détruits.

**Corroboration décisive relevée par la vérification** · `client/LethalBreedClientConfig.java:72-89`
fait l'inverse : il n'écrit que dans la branche « fichier absent » et laisse le fichier utilisateur
intact en cas d'échec. Le codebase connaît donc la forme sûre ; `ConfigIo` en diverge.

**Correctif** · Ne pas réécrire quand le parsing a échoué — l'objectif « compléter le fichier avec
les nouvelles options » n'a de sens qu'après une lecture réussie. À défaut, déplacer le fichier
fautif vers `lethalbreed.json.corrupt-<timestamp>` et le signaler en ERROR avec le chemin.

### #6 — 27 champs de `ContaminationConfig` sans borne, dont trois atteignent une branche de mort instantanée
`config/ConfigBoundsTable.java:210-231` · effort ~45 min · ○ non vérifié

Croisement exhaustif réflexion ↔ table : 295 champs exposés par `ConfigSchema.all()`, 221 entrées
dans la table, **zéro entrée morte**, 46 booléens hors périmètre — l'écart est de 27 champs
numériques + 1 tableau, tous dans le sous-système « niveaux / évolution / épisodes » de la
contamination, ajouté après la rédaction de la table.

Trois d'entre eux sont dangereux. `contamLevelStep` multiplie les dégâts de pulsation ; à 500, le
niveau 5 donne `mult ≈ 2001` donc `dmg ≈ 200-1000`, ce qui bascule sur la branche
`hurtServer(Float.MAX_VALUE)` de `ContaminationTick.java:104-110` — mort instantanée de toute
victime. La borne posée sur `contamDamageMin/Max` (0..1000) est donc décorative.
`contamLevelJitterMin/Max` ouvrent une seconde voie : `ConfigBounds.clamp` ne neutralise `NaN` et
`Infinity` **que dans la branche `Range != null`**, donc une valeur non finie traverse intacte,
produit une intensité `NaN`, et cette valeur est **écrite dans l'attachment persistant `INTENSITY`**,
donc dans le NBT de la victime. Corriger la config après coup ne répare rien.

Les 24 autres champs sont protégés en aval par `rollScaled` (`Math.max(0,min)`, `Math.max(min,max)`,
`Math.max(1L, Math.round(...))`) — dégradation à 1 tick, bruyante mais non fatale.

**Correctif** · Ajouter les 27 entrées manquantes. Surtout, remonter la neutralisation
`NaN`/`Infinity` **avant** le test `r == null` dans `ConfigBounds.clamp`, pour qu'aucun champ non
borné ne puisse recevoir une valeur non finie. Un test unitaire assertant « tout champ numérique a
une entrée dans la table » aurait attrapé l'écart à l'ajout du sous-système.

### #7 — Deux champs individuellement dans leurs bornes produisent 16,8 M de lookups par événement sonore
`spatial/SpatialGrid.java:81-90`, `sound/SoundEventBus.java:86-87` · effort ~30 min · ○ non vérifié

Le coût de `queryRadius` est `((2·radius/cell)+1)²` lookups, **indépendant du nombre de zombies** :
la boucle balaie toutes les cellules de la zone, même vides. Les bornes sont posées champ par champ
et ne contraignent jamais le rapport `radius/cell`. Avec `soundBaseRadius = 128` et
`soundLoudMultiplier = 16` — les deux maxima que la table déclare sains — le rayon d'un son fort
atteint 2048 blocs ; à `spatialCellSize = 1` (minimum autorisé), cela fait 4097² ≈ 16,8 millions de
lookups par événement, et `tickEntities` en émet un par entité audible toutes les 4 ticks.

**Correctif** · Clamper le rayon effectif à l'entrée de `queryRadius` — c'est le seul endroit qui
connaît le coût réel — plutôt que de faire confiance à l'appelant. Resserrer `soundLoudMultiplier`
à 1..4. Au-delà d'un certain nombre de cellules, itérer `cells.entrySet()` devient strictement moins
cher que de sonder des cellules vides.

### #8 — Six `cl_mem` alloués hors du `try` : fuite de mémoire native GPU, relancée à chaque solve
`ai/flowfield/gpu/GpuFlowFieldSolver.java:54-66` et `:116-123` · effort faible · ○ non vérifié

Les six `clCreateBuffer` précèdent l'ouverture du bloc protégé, et `GpuContext:46` appelle
`setExceptionsEnabled(true)` — donc tout code de retour OpenCL non nul lève. Une allocation qui
échoue au rang *k* laisse *k−1* buffers vivants côté device, sans aucun `finally` pour les couvrir.
Symétriquement, dans le `finally` les six `clReleaseMemObject` sont en séquence nue : si le premier
lève, les cinq suivants sont sautés.

Amplificateur · `GpuComputeManager.logFallbackOnce` écrit « using CPU from now on » mais ne remet
**jamais** `available` à `false` ; `GpuFlowField:30` réinterroge `isAvailable()` à chaque solve. Le
basculement promis n'a pas lieu, donc la fuite se répète. Chiffrage : à `flowMaxGrid` par défaut,
~110 Ko par tentative × 2 solves/s ≈ 800 Mo/heure ; au plafond de configuration (512), ≈ 5,6 Go/h.
Invisible dans le heap Java et dans tout profileur Java. Se termine par une réinitialisation du
pilote ou la mort du processus.

**Correctif** · Initialiser les six références à `null` avant un `try` unique et libérer dans le
`finally` ce qui est non nul, chaque libération protégée individuellement. Faire tenir à
`logFallbackOnce` sa promesse. Le correctif de fond déjà identifié par l'auteur — buffers persistants
dimensionnés au plus grand champ — résout aussi celui-ci.

### #9 — `/lethalconfig` initialise OpenCL sur le thread serveur, sous le verrou global, et ignore le kill-switch
`command/LethalConfigCommand.java:82-87`, `ai/flowfield/gpu/GpuComputeManager.java:36,64` · ○ non vérifié

`gpuInfo()` est appelé depuis un `.executes(...)` Brigadier, donc sur le thread serveur, et invoque
`isAvailable()` qui est `synchronized` sur le même moniteur que `solve()`. Deux scénarios.
**(A)** Un solve GPU est en cours, bloqué dans `clEnqueueReadBuffer` puis `clFinish` : le thread
serveur attend le moniteur jusqu'à la fin du solve. **(B)** Plus grave — un administrateur a mis
`useGpu=false` ; `LifecycleInit:23` saute alors le préchauffage, donc `initialized` est faux, et
`/lethalconfig` déclenche `init()` **sur le thread serveur** : chargement de la bibliothèque native
JOCL, énumération de tous les ICD, `clCreateContext`, puis `clBuildProgram` — une compilation de
noyau, de l'ordre de la seconde à pilote froid.

Le kill-switch documenté (« set false to force CPU ») n'empêche donc pas l'initialisation OpenCL :
un administrateur qui a désactivé le GPU précisément parce qu'OpenCL pose problème sur sa machine y
est ramené par le menu de configuration.

**Correctif** · `gpuInfo()` doit consulter `FlowConfig.useGpu` puis un accesseur non bloquant
renvoyant l'état déjà connu, sans prendre le moniteur. Séparer le verrou de cycle de vie du verrou
de sérialisation de la file GPU. Si `init()` doit rester paresseux, il ne doit jamais s'exécuter
depuis un chemin de commande.

### #10 — Aucune des trois protections de charge n'est active par défaut, et le délestage agit au mauvais endroit
`config/domain/SchedulerConfig.java:15,21,68` · ○ non vérifié

`aiTickBudget = 0` rend la condition `budget > 0` fausse : le « budget dur contre les pics de
population » n'existe pas dans une installation par défaut. `msptThrottle = false` : `stress` vaut
toujours 1. `autoScaleBuckets = false` : la charge reste `pop/5`, non bornée.

Même activé, le délestage est mal câblé sur deux points. D'abord `getAverageTickTimeNanos()` est une
moyenne glissante sur 100 ticks : un pic de 200 ms n'ajoute que 2 ms à la moyenne, donc le seuil de
45 ms n'est franchi qu'après plusieurs secondes de dépassement soutenu, et le throttle oscille par
paliers de 5 secondes au lieu de réguler. Ensuite `stress` ne multiplie que `divisor`, testé
**après** `classify`, `spatialGrid().update`, `applySunBurn` et `updateMood` : le délestage réduit
donc uniquement `sz.tick()`, la seule partie déjà protégée, et laisse au taux plein les coûts
dominants (#12, #13, #22).

**Correctif** · Mesurer une moyenne courte maintenue par le mod (il a déjà `System.nanoTime()`
autour du tick) avec hystérésis. Déplacer la barrière de budget **avant** `classify` en la faisant
porter sur un coût pondéré. Choisir des défauts défensifs : un mod qui empêche le despawn a
l'obligation de borner sa charge lui-même.

### #11 — `EveryTickPass` court-circuite bucketing, LOD et budget : 20 Hz quel que soit le tier
`tick/EveryTickPass.java:26-58` · ○ non vérifié

Un zombie n'entre dans `climbers`/`swimmers` que via `sz.tick()`, donc sous budget — mais une fois
dedans il est piloté **à chaque tick** jusqu'à ce qu'il cesse de nager ou grimper, sans aucune
re-vérification du LOD ni du budget. Un zombie LOW à 120 blocs flottant dans l'océan reçoit le même
traitement complet qu'un zombie collé au joueur. C'est le seul endroit du mod où une entité distante
coûte autant qu'une entité proche — un troisième chemin que le document de l'auteur ne mentionne pas.

Par nageur et par tick : `getNavigation().stop()`, 2 `getBlockState` de sonde, puis jusqu'à
3 `tryBreak` allouant chacun un `BlockPos` et boxant 3 objets dans `BreakManager` — **y compris sur
le chemin de rejet**, puisque `active.containsKey(key)` boxe avant le test de cap. Soit ~5-6
`getBlockState` et ~9-12 allocations par nageur et par tick. À 300 nageurs simultanés : ~3 000
allocations/tick, 20 fois par seconde ; ~93 % des requêtes de casse sont rejetées après avoir boxé.

**Correctif** · Appliquer au drain par-tick le même filtre de tier que le bucket pass, et compter ces
pas dans le même compteur que `sz.tick()`. Passer un `MutableBlockPos` scratch à
`tryBreak`/`breakableSolid`. Remplacer les maps boxées de `BreakManager` par des maps primitives
(fastutil est déjà au classpath), et tester le cap avant de calculer la clé.

---

## 🟡 Moyen

### #12 — Le scan de cible visite les zombies avant de les rejeter
`util/TargetSelector.java:82-83` · ✅ vérifié — **sévérité révisée CRITIQUE → MOYEN**

`getEntitiesOfClass(LivingEntity.class, box, isValid)` sur une boîte de 80,6 blocs de côté retourne
bien une liste pré-indexée incluant les zombies du mod, et `classify` appelle `findNearest`
**inconditionnellement** : aucun cache, aucune mémoïsation (grep `lastScan|cachedTarget|nextScan` :
zéro résultat). `dueThisActivation` et `aiTickBudget` sont postérieurs à `classify`, donc ne le
gardent pas. Le mécanisme est réel et le dépôt le documente déjà comme sa faiblesse perf n°1.

**Ce que la vérification a cassé** · Trois piliers du chiffrage initial. (a) `isValid` rejette
`instanceof Zombie` en **deuxième** test (`:33`), pas en dernier — le coût par visite annoncé était
surestimé. (b) `frozenReclassifyDivisor = 4` est une garde réelle entièrement omise : un zombie
FROZEN saute `classify` 3 activations sur 4. (c) Le terme N² supposait toute la horde dans une seule
boîte de 80 blocs, alors que les zombies FROZEN ne tickent pas donc **ne bougent pas** — il n'y a pas
de convergence globale — et que le balayage est borné aux sections locales. Recalcul du vérificateur
aux défauts lus : **0,13 à 0,5 ms/tick à 1000 zombies**, contre 5 à 10 ms annoncés. Le régime
CRITIQUE n'est atteint que dans un agglomérat de fin de partie où les coûts vanilla (collision,
pathfinding, mêlée) dominent déjà.

**Correctif** · Quand `targetPlayersOnly = true`, itérer `level.players()` au lieu de balayer
`LivingEntity.class` — dans ce mode `isValid` rejette 100 % des non-joueurs, le balayage est du
gaspillage pur. Sinon, deux requêtes typées étroites plutôt qu'une requête large. Déplacer `classify`
derrière la barrière `dueThisActivation`. Le vérificateur note que la vraie dépense par scan est
probablement ailleurs : le raycast voxel LOS de `canSee`, jusqu'à ~80 `getBlockState` par candidat,
sans plafond sur le nombre de candidats.

### #13 — `ShelterFinder.findShade` : 8 112 itérations par appel, sans cooldown sur l'échec
`entity/mood/ShelterFinder.java:19-56` · ✅ vérifié — **sévérité révisée CRITIQUE → MOYEN**

Les quatre affirmations factuelles sont exactes, vérifiées jusqu'au bytecode. Les deux élagages
comparent `>= bestScore` initialisé à `Double.MAX_VALUE` : tant qu'aucune ombre n'est trouvée,
**aucune itération n'est sautée** — 25 × 25 colonnes × 13 `dy` = 8 112 `canSeeSky` exactement, plus
~7 488 `getBlockState`. `m.below()` alloue bien un `BlockPos` par appel (~3 744 par recherche, ~90 Ko),
mais par un mécanisme différent de celui annoncé : c'est `BlockPos.relative(Direction)` qui alloue,
la surcharge `MutableBlockPos` n'étant pas sur ce chemin. `updateMood` (`LodBucketPass:101`) précède
bien le `continue` FROZEN (`:103`). Aucun cooldown sur le retour `null`.

**Ce que la vérification a cassé** · Le chiffrage. Le chemin n'est vivant que si
`burnsInSun(phase)`, c'est-à-dire `phase < sunImmunePhase` dont le **défaut réel est 5** ; avec
`phaseIntervalTicks = 36000`, cela borne la fenêtre aux ~2,5 premières heures de monde — précisément
les phases où le multiplicateur de mob-cap vaut 0 à 4,21, plafonnant la population à ~100-450 zombies
en solo. Les 1000 zombies du chiffrage **ne peuvent pas coexister** avec la condition qui déclenche
l'appel : passé la phase 5, `findShade` n'est plus jamais appelé depuis `handleDaySleep`. Réalité
mesurée : 2,5 à 4 ms/tick en solo à l'aube, 7 à 9 ms sur un serveur à 4 joueurs encore en phase ≤ 4,
transitoire et auto-limité (les zombies exposés brûlent en 25-30 s).

**Correctif** · Mémoïser l'échec par un `nextShadeSearchTick`, initialiser `bestScore` au score
maximal utile plutôt qu'à `MAX_VALUE`, et balayer en spirale depuis le centre avec sortie au premier
succès — la recherche est un « plus proche » et l'ordre actuel part du coin le plus lointain.

### #14 — Écriture de configuration non atomique
`config/ConfigIo.java:96-101` · ✅ vérifié — **sévérité révisée HAUT → MOYEN**

`Files.writeString` sans `OpenOption` équivaut à `CREATE + TRUNCATE_EXISTING + WRITE` : le fichier
est vidé **avant** que le nouveau contenu soit écrit, inconditionnellement à chaque `save()`.

**Ce que la vérification a nuancé** · « Toute interruption laisse un JSON tronqué » est surestimé :
la fenêtre pour 10 Ko est de l'ordre de la microseconde, et `auto_da_alloc` (actif par défaut sur
ext4) cible précisément le motif `O_TRUNC` + write + close. Le seul déclencheur **sans chance de
timing** est `ENOSPC` : troncature réussie, écriture partielle, `IOException` loguée mais fichier
tronqué laissé sur disque — et le démarrage suivant enchaîne sur #5. Facteur aggravant relevé au
passage : `NumOptionEntry.java:23` déclenche un `save()` complet par frappe clavier (voir #16), donc
le nombre de tirages est bien supérieur au « une fois au démarrage » supposé.

**Correctif** · Écrire dans un fichier temporaire du même répertoire puis `Files.move` avec
`ATOMIC_MOVE`, repli sur `REPLACE_EXISTING` si le système de fichiers refuse.

### #15 — Une valeur objet ou nulle dans le JSON annule l'application de tous les champs suivants
`config/ConfigIo.java:50-54` · ✅ vérifié — **sévérité révisée HAUT → MOYEN**

`getAsString()` est appelé **hors** de tout `try` par champ ; le seul garde-fou, dans
`ConfigAccess.apply`, n'intervient qu'à la ligne suivante. Le point Gson est confirmé par lecture du
bytecode de `gson-2.13.2.jar` : ni `JsonNull` ni `JsonObject` n'overrident `getAsString()`, tous deux
héritent de `JsonElement.getAsString()` qui lève `UnsupportedOperationException`. L'exception sort de
la boucle, les champs suivants restent aux défauts, puis `save()` persiste le mélange.

**Ce que la vérification a nuancé** · « Un seul champ mal typé » est trop large. Un mauvais scalaire
(`"abc"`, `true`, `12.5` pour un `int`) passe `getAsString()` sans erreur, échoue dans
`ConfigType.parse`, est rattrapé par `ConfigAccess.apply` et la boucle **continue**. Les tableaux
sont exclus par le test `isJsonArray()`. Seules deux formes avortent réellement : une valeur objet
`{}` ou `null`. Comme `save()` n'émet que des primitifs et des tableaux, ces formes ne peuvent venir
que d'une édition manuelle — déclencheur réel, mais étroit.

**Correctif** · Entourer le corps de la boucle d'un `try/catch` par champ, tester `isJsonPrimitive()`
avant `getAsString()`, et loguer le nombre de champs ignorés en plus du nombre appliqué.

### #16 — Une écriture disque complète de la configuration par frappe clavier, sur le thread serveur
`client/screen/NumOptionEntry.java:23-28` → `config/ConfigAccess.java:58-60` · ○ non vérifié

`EditBox.setResponder` notifie à chaque mutation du texte, pas à la validation. Taper `36000` envoie
5 paquets `SetConfig`, tous valides. Chacun s'exécute via `context.server().execute(...)` — donc sur
le thread de tick — et déclenche `ConfigSchema.all()` (reconstruction réflexive de 295 `Field`) puis
`ConfigIo.save()` qui refait un second `all()`, sérialise 295 entrées et **bloque** sur
`Files.writeString`. Soit 5 écritures disque synchrones de ~10 Ko pour une seule valeur saisie, et
5× la fenêtre d'exposition de #14.

**Correctif** · Découpler application et persistance : marqueur « dirty » drainé au plus une fois par
seconde, ou exécuteur mono-thread dédié. Côté client, n'émettre qu'à la perte de focus ou après un
debounce. Mettre en cache `ConfigSchema.all()` — la liste des champs ne change jamais à l'exécution.

### #17 — Contexte, file, programme et noyau OpenCL jamais libérés
`ai/flowfield/gpu/GpuContext.java:44-114` · ○ non vérifié

Aucun `clReleaseKernel`, `clReleaseProgram`, `clReleaseCommandQueue` ni `clReleaseContext` n'existe
dans le dépôt, et `LifecycleInit` n'enregistre aucun traitement GPU sur `SERVER_STOPPED`. Ce n'est
pas une fuite répétée — le singleton n'initialise qu'une fois par JVM — mais c'est le maillon
manquant : si le device disparaît (mise à jour de pilote, bascule hybride, eGPU débranché),
`available` reste `true`, le contexte reste invalide à vie, et rien ne peut le reconstruire sans
redémarrer le jeu.

**Correctif** · Une méthode de libération appelée depuis `SERVER_STOPPED`, sous le moniteur du
gestionnaire, réarmant `initialized`/`available`. Cela offre du même coup le chemin de reprise après
perte de device, prérequis d'une correction propre de #8.

### #18 — L'exception de la tâche de solve est avalée : le flowfield peut se figer sans une ligne de log
`ai/flowfield/FlowFieldManager.java:86-92` · ○ non vérifié

Le `Future` retourné par `POOL.submit` est jeté et la lambda ne comporte aucun `catch` : avec
`ExecutorService.submit(Runnable)`, un `Throwable` échappé est capturé dans le `Future` et n'atteint
ni le `UncaughtExceptionHandler` ni la console. Le `finally` remet `computing` à `false`, donc une
nouvelle tâche est soumise dix ticks plus tard, qui lève à nouveau : boucle silencieuse. Pendant ce
temps `active` conserve un `FlowField` périmé décrivant une géométrie que les zombies ont eux-mêmes
détruite depuis. Le coût du snapshot (~550 k `getBlockState` selon le document de l'auteur) continue
d'être payé toutes les 10 ticks pour un résultat jeté. Cette cécité masque exactement les échecs GPU
de #8.

**Correctif** · `catch (Throwable)` journalisé avec limitation de débit, invalidation explicite de
`active` pour basculer sur un repli connu, et compteur d'échecs consécutifs exposé au récapitulatif
de performance existant.

### #19 — `computing` armé avant `submit()` sans protection
`ai/flowfield/FlowFieldManager.java:46,85-86` · ○ non vérifié

Sur la question du garde `get`-puis-`set` : un seul thread exécute ce chemin pour une instance donnée
(chaque dimension a son propre `FlowFieldManager`, et `tick()` vient de `END_SERVER_TICK`), donc **un
double-solve n'est pas atteignable** — le `compareAndSet` manquant est une faiblesse de robustesse,
pas un bug actif. Le vrai défaut : la ligne 85 arme le drapeau avant la soumission et rien ne protège
la ligne 86. Si `POOL.submit()` lève, la tâche n'existe pas, son `finally` ne s'exécute jamais, et
`computing` reste `true` **définitivement** : le flowfield de cette dimension est mort jusqu'au
redémarrage, sans log. État absorbant sans issue.

**Correctif** · `compareAndSet(false, true)` avec retour anticipé sur échec, et `try`/`catch` autour
de la soumission qui remet le drapeau à `false` avant de propager.

### #20 — Grimpeurs et nageurs non purgés au `SERVER_STOPPED`
`tick/TickScheduler.java:30-31`, `init/LifecycleInit.java:33-36` · ○ non vérifié

`SCHEDULER` est un champ statique de `LethalBreedMod`, créé une fois par JVM, et détient deux
`HashSet<SmartZombie>`. `LifecycleInit` vide `registry` et `dimensions` mais pas ces ensembles. La
purge existe (`EveryTickPass.drive:45` retire sur `!sz.isValid()`) mais ne s'exécute qu'au **premier
tick du serveur suivant** : tant que le joueur reste au menu, et pendant toute la phase de chargement
du monde suivant, l'ancien `ServerLevel` est intégralement résident — c'est-à-dire au pic de
consommation mémoire. Aggrave directement #2, qui rend cette rétention permanente.

**Correctif** · Une méthode de réinitialisation du planificateur appelée depuis `SERVER_STOPPED`.
Plus généralement, ce handler est le seul point de nettoyage du mod et gagnerait à devenir
explicitement le lieu où *tout* état process-wide contenant des entités ou des niveaux est purgé.

### #21 — Exception levée depuis un callback d'entité en cours de partie
`util/AiConflictDetector.java:76-80`, appelé depuis `init/EntityEventsInit.java:70` · ○ non vérifié

Le refus est légitime au démarrage (`checkModList` depuis `BootstrapInit.run()` : le serveur ne
démarre pas). Mais la détection **comportementale** se déclenche au premier chargement d'un zombie,
donc au milieu d'un tick, dans le handler `ENTITY_LOAD`. L'`IllegalStateException` remonte alors dans
le pipeline de chargement d'entité d'un monde en cours d'exécution : crash en session plutôt que
refus propre au démarrage, avec les risques habituels sur une sauvegarde interrompue. Un mod dont le
goal zombie n'est injecté qu'à l'exécution, donc invisible pour la liste d'ids curée, tombe
précisément dans ce cas. `failOnAiConflict` vaut `true` par défaut.

**Correctif** · Séparer les deux politiques : au boot, conserver le `throw` ; en session, ne jamais
lever depuis un callback d'entité — loguer, basculer en mode dégradé, et si l'arrêt est nécessaire,
passer par `MinecraftServer.halt` pour que le monde soit sauvegardé. Ou déplacer le scan
comportemental vers un zombie synthétique instancié au démarrage.

### #22 — Le bus de son annule en pratique l'optimisation FROZEN
`sound/SoundEventBus.java:103-127`, `spatial/SpatialGrid.java:79-104` · ○ non vérifié

Coût direct modeste — `cells` est un `Map<Long, …>` sondé avec un `long` primitif, donc **un `Long`
alloué par cellule sondée** (49 par événement aux défauts, 169 sur un son fort), plus une `ArrayList`
neuve par appel, plus des écritures mortes quand plusieurs événements touchent le même zombie dans le
même tick. Mais l'effet dominant est ailleurs : `rememberTarget` arme la mémoire courte, et
`LODManager:59-85` fait sortir de FROZEN tout zombie ayant une mémoire valide. **Une seule vache qui
bouge près d'une horde dense convertit plusieurs centaines de zombies FROZEN en HIGH/MEDIUM pendant
200 ticks**, lesquels paient alors le scan de #12 à chaque activation — et `tickEntities` ré-arme
l'état toutes les 4 ticks. C'est le mécanisme qui neutralise l'optimisation sur laquelle repose tout
le budget du mod ; le document de l'auteur ne le mentionne pas.

**Correctif** · `Long2ObjectOpenHashMap` pour supprimer le boxing ; fusionner les événements proches
avant distribution ; fournir une liste de sortie réutilisable à `queryRadius` ; et surtout **borner
le nombre de zombies réveillés par événement** (les K plus proches), ce qui plafonne mécaniquement
l'amplification FROZEN → actif.

### #23 — L'opération la plus chère du tick est exécutée puis son résultat est jeté
`ai/LODManager.java:30` puis `entity/ZombieMood.java:249-262,285-318` · ○ non vérifié

`classify` (`LodBucketPass:90`) exécute le scan complet, pose la cible et arme la mémoire. Onze
lignes plus loin (`:101`), `updateMood` → `handleDaySleep` → `dozeInPlace()` → `suppressHunt()`
**efface tout** et repasse le zombie en FROZEN. Le test `investigatingNoise` exige
`targetEntity() == null`, donc un zombie qui vient d'acquérir une cible vivante n'échappe pas à
l'effacement. Boucle permanente : acquis → effacé → ré-acquis.

Deux redondances mineures du même chemin : `canSeeSky(entity.blockPosition())` est recalculé jusqu'à
trois fois par zombie et par activation ; et `findNearest(level, self, radius, current)` appelle
**inconditionnellement** la variante sans `current` en première instruction, donc `targetSwitchMargin`
change la réponse mais n'économise jamais un scan, contrairement à ce que suggère son commentaire.

**Correctif** · Évaluer la décision de sommeil **avant** `classify` : extraire de `handleDaySleep` un
prédicat pur (jour, phase, alerte, dégâts, `staysAwake`, `canSeeSky` — toutes des lectures triviales)
et sauter `classify` + `updateMood` si la réponse est oui. Calculer `canSeeSky` une seule fois par
activation.

---

## ⚪ Bas

### #24 — L'écran de configuration s'ouvre sur tout paquet `OpenConfig` reçu
`client/LethalBreedClient.java:19-21` · ○ non vérifié

Aucun état client ne mémorise qu'un `/lethalconfig` a été demandé : le récepteur est inconditionnel.
Un serveur hostile émettant `OpenConfig` à chaque tick remplace l'écran courant — y compris le menu
pause — rendant le client inutilisable sans tuer le processus. Impact borné : aucun effet serveur,
aucune fuite, aucune exécution de code, et un serveur hostile dispose déjà de primitives comparables
en vanilla.

**Point vérifié sans finding** · Le parsing S2C a été spécifiquement audité et il est **sûr** :
`ConfigScreenData.parse` fait `split(SEP, -1)` puis teste `p.length == 5`, donc toute ligne malformée
est ignorée ; le préambule `@gpu=` est protégé par `startsWith` qui garantit les index de `substring` ;
`` n'est pas un métacaractère de regex ; `ConfigType.isValidNumber` enveloppe tout dans un
`catch (NumberFormatException)`.

**Correctif** · Ne traiter `OpenConfig` que si le client a émis une demande dans les secondes
précédentes, ou à défaut ignorer le paquet si un `CustomConfigScreen` est déjà affiché.

### #25 — Cinq options client sur neuf ne sont jamais lues
`client/LethalBreedClientConfig.java:21-45` · ○ non vérifié

`maxRenderedZombies`, `reduceFarDetail`, `farDetailDistance`, `instancedRendering` et
`billboardFarZombies` ne sont consommés nulle part — seul `effectiveCullDistanceSq()` l'est, depuis
`mixin/client/EntityRendererMixin.java:35`. La ligne de log affiche pourtant `maxRender={}` comme si
l'option agissait : un utilisateur qui l'abaisse pour gagner des FPS obtient une confirmation dans le
log et aucun effet. Aucune borne non plus (Gson désérialise directement), mais l'impact réel est nul
— une valeur négative est neutralisée par l'élévation au carré, `NaN` désactive simplement le culling.

**Correctif** · Implémenter les cinq options ou les retirer du fichier et du log ; les laisser en
l'état est un mensonge documenté. Ajouter une validation post-désérialisation.

### #26 — `clamp` est un no-op silencieux pour `double[]`, et `reset` fait aliaser le tableau des défauts
`config/ConfigBounds.java:26-50`, `config/ConfigAccess.java:64-69` · ○ non vérifié

Les booléens traversent `clamp` inchangés — correct et documenté. Un champ absent de la table sort au
`return value` **y compris pour `NaN`/`Infinity`**, ce qui est le vecteur exploité en #6. Les
`double[]` ne sont ni bornés en longueur ni validés en contenu. Le seul tableau existant,
`phaseColorThresholds`, est consommé sans risque (`COLOR_PALETTE[tier % length]`, tableau vide →
`tier = 0`) — vérifié, pas de faille aujourd'hui.

Second point, latent : `reset()` réinjecte **la référence** stockée dans `DEFAULTS`, pas une copie.
Aucun code ne mute ces tableaux en place aujourd'hui ; la première mutation jamais écrite détruirait
silencieusement le snapshot des défauts.

**Correctif** · Copie défensive à la capture du snapshot et à chaque `reset`, pour que `DEFAULTS` soit
immuable par construction plutôt que par convention.

### #27 — Champs d'état du gestionnaire GPU lus hors moniteur, sans `volatile`
`ai/flowfield/gpu/GpuComputeManager.java:27-30,43-45,68-73` · ○ non vérifié

`initialized`, `available`, `deviceName`, `fallbackLogged` ne sont pas `volatile`, et `deviceName()`
comme `logFallbackOnce()` ne sont pas `synchronized`. En pratique `deviceName()` est sauvé par son
unique appelant qui vient d'exécuter `isAvailable()`, donc d'acquérir et relâcher le moniteur — c'est
une sûreté fortuite, pas une garantie de conception.

**Correctif** · Marquer les quatre champs `volatile`, ce qui prépare aussi la lecture d'état non
bloquante requise par #9.

### #28 — `setExceptionsEnabled(true)` est un réglage JOCL global à la JVM
`ai/flowfield/gpu/GpuContext.java:46` · ○ non vérifié

`org.jocl.CL.setExceptionsEnabled` porte sur tout le classloader. JOCL étant déclaré en `include`
dans `build.gradle.kts` donc embarqué et partagé, un autre mod utilisant JOCL verrait ses appels lever
là où son code teste des codes de retour. Très peu probable dans l'écosystème actuel.

**Correctif** · Aucun changement fonctionnel nécessaire ; le noter comme contrainte assumée dans le
commentaire de classe.

### #29 — `ConfigSchema.find()` reconstruit la liste réflexive à chaque appel
`config/ConfigSchema.java:59-66` · ○ non vérifié

`all()` refait `getDeclaredFields()` sur 9 classes et reconstruit une `ArrayList` de 295 `Field` à
chaque recherche, suivie d'un `equalsIgnoreCase` linéaire. **Correction apportée à la cartographie** :
ce n'est **pas** un chemin chaud — les seuls appelants sont `ConfigIo`, `ConfigAccess`,
`LethalConfigCommand` et `LethalConfigPayloads`. Toute la config lue en boucle de tick passe par des
lectures de champs `static`, ce qui est optimal. Retenu en BAS uniquement parce que #16 le place dans
un chemin déclenché par frappe clavier.

---

## Findings écartés en vérification

**Aucun finding n'a été réfuté.** Quatre ont vu leur sévérité révisée à la baisse (#12, #13, #14,
#15), documentées ci-dessus avec le raisonnement du réfuteur.

Les points suivants ont été **spécifiquement suspectés puis innocentés**. Ils sont listés pour qu'un
audit ultérieur ne les re-signale pas.

| Point suspecté | Verdict | Raison |
|---|---|---|
| Handler C2S `SetConfig` sans gate | Innocenté | `Permissions.COMMANDS_GAMEMASTER` vérifié sur le thread réseau, **avant** tout effet de bord ; `player == null` traité par le même `if` |
| Accès au monde depuis le thread solveur | Innocenté | `Snapshot` est une copie complète — chaque champ remonté : 5 `int`, `boolean[]`, `int[]`, `byte[]`, `int[]`. Aucun import Minecraft dans `BellmanFordSolver`, `Snapshot`, `Neighbors8`, ni dans `gpu/` |
| Course sur la publication du flowfield | Innocenté | Chaîne happens-before correcte : workers → `join()` → `active.set()` (volatile) → thread serveur. Tableaux jamais mutés après construction |
| Déchirure 64 bits sur la config lue hors thread | Innocenté | Les seuls champs lus hors thread serveur sont `int` ou `boolean`, donc atomiques (JLS). Le seul `double` du chemin est lu avant le `submit` |
| Division ou modulo par zéro depuis la config | Innocenté | `Math.max(1, …)` au point d'usage sur tous les diviseurs vérifiés : `tickBuckets`, `frozenReclassifyDivisor`, `spatialCellSize`, `contamCureCheckTicks`, `flowRecomputeInterval`, coûts du solveur |
| Surface de la réflexion depuis une chaîne réseau | Innocenté | `ConfigSchema.find` ne parcourt que les champs `public static` non-`final` de 9 classes ; aucun `Class.forName`, aucun `setAccessible`. `equalsIgnoreCase` élargit l'écriture du nom, pas l'ensemble atteignable — aucune collision de casse (vérifié par extraction) |
| Plantage du client par un paquet S2C malformé | Innocenté | Voir #24 — toutes les gardes de parsing sont présentes |
| Fuite de threads au rechargement solo | Innocenté | `FlowFieldManager.POOL` et `solvePool` sont `static final`, créés une fois par JVM |
| `ZombieRegistry` / `SpatialGrid` multi-thread | Innocenté | Aucun appelant hors thread serveur. L'itération avec `remove()` est sûre (itérateur faiblement cohérent de `ConcurrentHashMap`) |
| Interversion d'ordre de verrous GPU | Innocenté | `ComputeCalibration` prend le moniteur `GpuComputeManager`, jamais l'inverse |
| `WorldSoundEventMixin` dans un chemin très chaud | Innocenté | Gardé dès la première instruction par une lecture de `static boolean` ; aucune allocation ni accès monde avant le filtre. ~7 µs/tick estimés |
| Stagger cassé par `autoScaleBuckets` | Innocenté | La charge totale reste bornée ; l'intervalle individuel devient irrégulier (gigue bornée), pas de famine. Impact MSPT nul |
| Débordement du multiplicateur de mob-cap | Innocenté | La conversion `double → int` sature à `Integer.MAX_VALUE` en Java, elle ne boucle pas vers le négatif |
| `nextInt` à borne négative | Innocenté | `Math.max(min, …)` explicite dans `SpecialAbilities`, `SpecialRoller`, `ZombieVariation`, `PhaseManager` |
| Cascade de spawn récursif via `SpecialAbilities.summon` | Innocenté | La garde de densité (`specialNecromancienDensityCap = 40` dans un rayon de 12) empêche l'amplification |
| Allocations pilotées par la config | Innocenté | `flowMaxGrid` borné 1..512 → 262 144 cellules maximum, pas de `NegativeArraySizeException` |

---

## Méthode

Recon déterministe → cartographie des zones chaudes → lentilles en parallèle → vérification
adversariale → déduplication et priorisation.

**Lentilles exécutées (4 sur 7)** : autorisation & entrées non fiables · config, bornes et I/O ·
concurrence, threading & mémoire native · performance du chemin critique serveur.

**Vérification** : 7 findings ont subi une contre-expertise dédiée, confiée à un agent indépendant
n'ayant pas vu le raisonnement d'origine et dont la consigne était de **réfuter**. Protocole en six
questions : le code existe-t-il à ces lignes · le chemin est-il atteignable · l'entrée est-elle
contrôlable · une protection existe-t-elle en amont · l'impact est-il celui de cette application ·
la sévérité tient-elle. Règle de tranchage : en cas de doute, réfuté.

Deux vérifications sont allées jusqu'au **désassemblage de bytecode** pour trancher un point
factuel : `gson-2.13.2.jar` pour le comportement de `getAsString()` sur `JsonNull`/`JsonObject`,
et le jar Minecraft 1.21.11 de la loom-cache pour l'allocation de `BlockPos.relative(Direction)`.

**Outils exécutés** : `recon.sh` (collecte déterministe). Aucun scanner tiers n'était installé sur la
machine — ni `semgrep`, ni `gitleaks`, ni `trivy`, ni `osv-scanner`, ni `hadolint`. La suite de tests
JUnit n'a pas été lancée.

---

## Couverture et angles morts

**Analysé** · 208 fichiers retenus, 181 fichiers Java, ~13 000 lignes. Zones couvertes : réseau,
commandes, config et I/O, boucle de tick, LOD et scheduler, flowfield et solveurs, GPU/JOCL,
contamination, spatial, son, blocs, phase, cycle de vie.

**Non couvert — trois lentilles sur sept n'ont pas abouti.** Deux ont été interrompues par une erreur
d'API en fin d'exécution, une a été arrêtée manuellement. Les angles suivants n'ont donc **pas** été
audités :

| Lentille manquante | Ce qui n'a pas été examiné |
|---|---|
| **Correction algorithmique** | Équivalence entre `bellman_ford.cl` (kernel OpenCL) et `BellmanFordSolver.java` — une divergence ferait diverger le comportement des zombies selon la présence d'un GPU. Overflow `INF + coût` dans Bellman-Ford. Indices et bornes de la grille. Convergence. Math de mouvement (normalisation d'un vecteur nul, `acos` hors domaine). Le raycast voxel LOS de `TargetSelector`, que la vérification de #12 signale comme la dépense probablement dominante du scan |
| **Cycle de vie & persistance** | Codecs et `SavedData` au rechargement. `SpecialAttachment` avec un id inconnu (downgrade de version, save éditée). Symétrie registre / grille / maps. Réentrance de `ChildSpawner`. Ordre `ENTITY_LOAD` / `AFTER_DEATH` / `ENTITY_UNLOAD`. Multi-dimension et portails. Bornes de `PlacedBlockTracker` et `BlockOperationQueue` |
| **Mixins, compatibilité & build** | Fragilité des 24 injections face à une mise à jour de Minecraft. Conflit interne suspecté : `ZombieBellyModelMixin` et `ZombieSleepArmsMixin` injectent tous deux en TAIL sur `AbstractZombieModel.setupAnim`. Conflit `SpawnStateMobcapMixin` / Lithium, alors que Lithium est listé en `suggests` et que `defaultRequire: 1` transforme tout échec d'injection en crash de chargement. Les trois `@Redirect`. Séparation client/common du `mixins.json`. Couverture des binaires natifs JOCL par plateforme. Fabric API en `modImplementation` mais seulement en `suggests` |

**Autres angles morts**

- **22 findings sur 29 n'ont pas subi de contre-expertise** (marqués ○). Le taux de révision observé
  sur l'échantillon vérifié est élevé — 4 sévérités révisées sur 7 findings testés — donc ces 22
  findings doivent être relus avant toute action, particulièrement leurs chiffrages.
- Aucun scanner de vulnérabilités n'a pu tourner : les CVE des dépendances (`jocl:2.0.5`,
  Fabric API 0.141.4, `gson:2.13.2`) n'ont **pas** été vérifiées.
- Aucune exécution : pas de build, pas de tests, pas de profilage. Tous les chiffrages de performance
  sont analytiques, jamais mesurés. Les coûts unitaires en nanosecondes sont des estimations.
- Les sources de Minecraft n'étaient pas décompilées sur la machine ; deux vérifications ont contourné
  la limite par désassemblage de bytecode, mais les autres raisonnements sur le comportement vanilla
  sont des **inférences**, signalées comme telles par les agents.
- Comportement en production, configuration serveur hors dépôt, interactions avec d'autres mods en
  conditions réelles : hors périmètre.

---

## Ce que cet audit ne couvre pas

- Comportement à l'exécution : aucune session de jeu, aucun profilage, aucun test de charge
- Interactions réelles avec les mods listés en `breaks` et `suggests`
- Sécurité physique, organisationnelle, ou de la chaîne de distribution du jar
- Tests d'intrusion actifs — aucune commande, aucun paquet n'a été réellement envoyé à un serveur
- Les trois lentilles non abouties détaillées ci-dessus
