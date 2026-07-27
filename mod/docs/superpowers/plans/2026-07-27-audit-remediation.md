# Plan de remédiation de l'audit LethalBreed

> Source : `AUDIT.md` (généré le 2026-07-27 sur `7e03e97`, branche `feat/sleeping-zombie-visuals`).
> Ce document est un **plan**, aucune ligne de code n'a été modifiée en le rédigeant.
> 29 findings : 2 🔴, 9 🟠, 12 🟡, 6 ⚪.

**Objectif :** traiter les 29 findings de l'audit dans un ordre qui met le risque réel à zéro le plus vite
possible, en gardant chaque étape vérifiable headless (compile + JUnit) plutôt qu'à l'œil.

**Contrainte structurante :** l'audit dit lui-même que **22 findings sur 29 (marqués ○) n'ont subi aucune
contre-expertise**, et que sur les 7 qui en ont subi une, **4 sévérités ont été révisées à la baisse**. Le
plan ne traite donc pas les ○ comme des faits acquis : chaque phase qui en contient commence par une étape
de re-lecture du code, et le correctif n'est écrit qu'après.

---

## Phase 0 — Environnement de build (FAIT)

La machine n'avait **aucun JDK** : `gradle.properties` pointe sur
`org.gradle.java.home=/opt/liberica-nik/bellsoft-liberica-vm-openjdk21-23.1.4`, chemin inexistant ici ;
`/usr/lib/jvm/java-25-openjdk` est un dossier vide ; `sudo` demande un mot de passe.

**Résolu sans root et sans modifier de fichier versionné** : Temurin 21.0.12+8 installé dans
`~/.jdks/jdk-21.0.12+8`, surchargé en ligne de commande.

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
JAVA_HOME="$HOME/.jdks/jdk-21.0.12+8" ./gradlew \
  -Dorg.gradle.java.home="$HOME/.jdks/jdk-21.0.12+8" <tâche> --console=plain
```

`JAVA_HOME` inline est **obligatoire** : le script `gradlew` a besoin d'un `java` pour démarrer, le
`-Dorg.gradle.java.home` seul arrive trop tard. Un lancement sans le préfixe échoue sur
`ERROR: JAVA_HOME is not set`.

**Baseline mesurée sur `7e03e97`, avant toute modification :**

| Tâche | Résultat |
|---|---|
| `compileJava` | ✅ BUILD SUCCESSFUL |
| `test` (JUnit) | ✅ BUILD SUCCESSFUL — 7 classes de test |

Les tests existants sont `ConfigBoundsTest`, `ConfigTypeTest`, `PhaseConfigTest`, `FlowFieldChecksTest`,
`FlowFieldPerfBench`, `Neighbors8Test`, `MoveMathTest`. C'est le socle de vérification du plan : trois
phases sur six sont entièrement testables headless grâce à lui.

**Décision à prendre (hors périmètre de ce plan) :** `org.gradle.java.home` codé en dur dans un fichier
versionné casse le build de toute machine qui n'a pas Liberica NIK à ce chemin exact. À retirer de
`gradle.properties` au profit d'un toolchain Gradle (`java { toolchain { languageVersion = 21 } }`) ou
d'un `gradle.properties` local non versionné.

---

## Stratégie

**Ordre :** sécurité → intégrité des données → fuites → robustesse → performance → hygiène. Ce n'est pas
l'ordre de sévérité de l'audit. La raison : les phases 1 à 3 sont des correctifs **courts, locaux et à
risque de régression quasi nul**, alors que la phase 5 (performance) demande de la mesure et touche le
cœur du mod. Livrer 1–3 d'abord met le risque exploitable à zéro sans rien déstabiliser.

**Granularité :** un commit par groupe cohérent, jamais un commit par fichier. Chaque tâche se termine par
`compileJava` + `test` verts. Aucune tâche ne dépend d'une tâche d'une phase ultérieure.

**Règle sur les findings ○ :** avant d'écrire quoi que ce soit, relire le code aux lignes citées et
confirmer les trois points que la contre-expertise vérifie ailleurs — *le chemin est-il atteignable*,
*l'entrée est-elle contrôlable*, *l'impact est-il celui décrit*. Si le finding ne tient pas, le noter dans
ce fichier et passer au suivant plutôt que d'écrire un correctif pour un problème inexistant.

**Ce que ce plan ne fait pas :** il ne traite pas les trois lentilles d'audit non abouties (correction
algorithmique, cycle de vie/persistance, mixins/compatibilité). Elles sont reprises en Phase 7 comme
travail d'audit, pas de correction.

---

## Phase 1 — Contrôle d'accès des commandes (#1, #3, #4)

🔴🔴 + 🟠 · **~40 min** · findings tous ✅ contre-expertisés · **risque de régression : nul**

C'est la phase à faire en premier et elle est courte. Les trois findings sont un seul oubli avec trois
conséquences. J'ai relu le code : l'audit est exact ligne pour ligne.

- `command/LethalPhaseCommand.java:19-24` — `dispatcher.register(Commands.literal("lethalphase")` puis
  directement `.executes(...)`. Aucun `.requires`.
- `command/LethalSpecialCommand.java:33-38` — même chose.
- `command/LethalConfigCommand.java:50-51` — porte bien
  `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`. L'idiome existe donc dans le dépôt,
  à deux fichiers de distance.
- `phase/PhaseManager.java:144-150` — `setPhase` fait `phase = Math.max(0, p)`, aucune borne haute.
- `mixin/SpawnFrequencyMixin.java:35` — `int extra = (int) Math.ceil(PhaseTable.frequency(...)) - 1`,
  puis `for (int i = 0; i < extra; i++)` autour de `NaturalSpawner.spawnCategoryForChunk`. Une boucle de
  spawn pilotée par une formule non bornée, sur le thread serveur, par chunk et par tick.

### Tâche 1.1 — Poser le gate sur les deux commandes livrées

- [ ] Ajouter `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` sur le littéral racine de
      `lethalphase` et de `lethalspecial`, copié verbatim de `LethalConfigCommand:51`.
- [ ] Vérifier qu'aucune autre commande du source set `main` n'est dans le même cas :
      `grep -rn "Commands.literal(" src/main/java --include=*.java` puis croiser avec `.requires`.
- [ ] `compileJava` + `test`.

### Tâche 1.2 — Borner la phase aux deux points de convergence

- [ ] Brigadier : `IntegerArgumentType.integer(1, 1_000_000)` — cohérent avec la borne `phaseMax`
      déjà déclarée dans `ConfigBoundsTable`, pas un chiffre inventé.
- [ ] `PhaseManager.setPhase` : clamper aussi côté modèle. C'est le point de convergence de **tous** les
      appelants (commande, auto-avance, chargement de `PhaseSavedData`), donc le seul endroit qui protège
      aussi une sauvegarde déjà corrompue par un `/lethalphase 2000000000` antérieur.
- [ ] Mettre à jour le javadoc de classe de `LethalPhaseCommand`, qui annonce aujourd'hui
      « force it to `n` (**unbounded**) » — la doc doit cesser de décrire le bug.

### Tâche 1.3 — Plafonner `extra` dans `SpawnFrequencyMixin` (défense en profondeur)

- [ ] Poser un plafond dur sur `extra` indépendamment de la provenance de la phase. Une progression
      automatique longue produit le même effet qu'une commande malveillante ; le gate de la tâche 1.1 ne
      protège pas ce chemin.
- [ ] Choisir la valeur en lisant `PhaseTable.frequency` : le plafond doit être au-dessus de ce qu'une
      partie légitime atteint, pas un chiffre rond arbitraire. **À déterminer pendant la tâche**, pas
      maintenant.

### Tâche 1.4 — `setPersistenceRequired` sur les entités de commande — **REFUSÉE**

L'audit recommande de ne pas appeler `setPersistenceRequired()` pour `EntitySpawnReason.COMMAND`, en
défense en profondeur, pour qu'un abus de `/lethalspecial` reste réversible par despawn naturel.

**Refusé, pour deux raisons dont la seconde est rédhibitoire :**

1. **Conflit d'architecture.** La persistance est délibérée et load-bearing : commit `b3bdd46`, « mark
   tracked zombies persistent so vanilla despawn can't undo the LOD system ». Le commentaire de
   `EntityEventsInit.java:64-68` l'explique déjà sur place — vanilla despawn les MONSTER non persistants
   dès 32 blocs, bien avant que le système FROZEN/LOD ait la moindre utilité, et ce système throttle le
   tick, il ne retire jamais d'entité.
2. **Inapplicable à cet endroit.** L'appel est dans `ServerEntityEvents.ENTITY_LOAD`, un callback qui **ne
   porte aucune notion de `EntitySpawnReason`** et qui refire à **chaque chargement de chunk**, où la
   raison du spawn d'origine n'existe plus depuis longtemps. Implémenter la recommandation exigerait
   d'enregistrer la raison dans un attachment persistant au spawn — soit ajouter de l'état persistant par
   entité pour affaiblir une protection délibérée.

Le gate de la tâche 1.1 supprime déjà le vecteur non privilégié. Le résidu — « un opérateur peut spammer
des entités » — est inhérent à toute commande de spawn, `/summon` vanilla comprise.

**Commit :** `fix(security): gate /lethalphase and /lethalspecial, bound phase and spawn passes`

---

## Phase 2 — Intégrité de la configuration (#5, #14, #15, #6, #26)

🟠 + 🟡🟡 + ⚪ · **~3 h** · #5/#14/#15 ✅ contre-expertisés, #6/#26 ○ · testable **intégralement headless**

C'est la phase au meilleur rapport effort/gain de l'audit, et la seule entièrement couvrable par des tests
JUnit — `ConfigBoundsTest` existe déjà et sert de modèle.

Relecture de `config/ConfigIo.java` : l'audit est exact. L'accolade ligne 65 ferme bien le `catch`, et
`save()` ligne 67 s'exécute sur **tous** les chemins, y compris après un échec de parsing où l'état
mémoire vaut les défauts du code. Le log dit « keeping defaults » à la ligne même où les réglages
utilisateur sont détruits.

### Tâche 2.1 — Ne plus écraser la config après un échec de lecture (#5)

- [ ] Ne réécrire que si la lecture a réussi. L'objectif documenté du `save()` inconditionnel — « compléter
      le fichier avec les options récemment ajoutées » — n'a de sens qu'après un parsing réussi.
- [ ] Sur échec : déplacer le fichier fautif vers `lethalbreed.json.corrupt-<timestamp>` et loguer en
      **ERROR** avec le chemin, au lieu d'un `warn` trompeur.
- [ ] Modèle à suivre dans le dépôt : `client/LethalBreedClientConfig.java:72-89` fait déjà exactement ça
      (n'écrit que dans la branche « fichier absent »). Le codebase connaît la forme sûre.

### Tâche 2.2 — Écriture atomique (#14)

- [ ] `ConfigIo.save()` : écrire dans un temporaire du **même répertoire** (contrainte de `ATOMIC_MOVE`,
      qui ne franchit pas les systèmes de fichiers), puis `Files.move(tmp, path, ATOMIC_MOVE)`, avec repli
      `REPLACE_EXISTING` si le FS refuse.
- [ ] Nettoyer le temporaire en cas d'échec, sans masquer l'exception d'origine.

### Tâche 2.3 — Isoler les champs les uns des autres à la lecture (#15)

- [ ] `ConfigIo.load()` lignes 46-58 : entourer le **corps de la boucle** d'un `try/catch`, tester
      `isJsonPrimitive()` avant `getAsString()`.
- [ ] Loguer le nombre de champs ignorés **en plus** du nombre appliqué — aujourd'hui la ligne
      `config loaded ({} options)` ne dit rien des champs perdus.
- [ ] Test JUnit : un JSON contenant `{"tickBuckets": {}, "spatialCellSize": 4}` doit appliquer
      `spatialCellSize` malgré la valeur objet qui précède.

### Tâche 2.4 — Remonter la neutralisation NaN/Infinity avant le test `r == null` (#6, partie critique)

C'est **le** correctif de fond de la phase, et il est de deux lignes.

`config/ConfigBounds.java:26-30` sort au `return value` dès que le champ n'a pas d'entrée dans la table —
**y compris pour `NaN` et `Infinity`**, puisque les tests `isFinite` des lignes 38 et 44 sont à l'intérieur
de la branche `r != null`. Un champ non borné peut donc recevoir une valeur non finie, et pour
`contamLevelJitterMin/Max` cette valeur est écrite dans l'attachment **persistant** `INTENSITY`, donc dans
le NBT de la victime : corriger la config après coup ne répare rien.

- [ ] Déplacer la neutralisation `NaN`/`Infinity` **avant** le `if (r == null)`, pour qu'aucun champ,
      borné ou non, ne puisse recevoir une valeur non finie.
- [ ] Choisir la valeur de repli pour un champ sans bornes — `r.min()` n'existe pas dans ce cas. `0.0`
      est le candidat évident mais n'est pas sûr pour tout champ ; **à trancher champ par champ** pendant
      la tâche.
- [ ] Tests JUnit dans `ConfigBoundsTest` : `NaN` sur un champ **absent** de la table doit ressortir fini.

### Tâche 2.5 — Compléter la table de bornes (#6, partie volume)

Compté sur le code : `ContaminationConfig` déclare **48 champs numériques** (`int`/`long`/`double`/`float`)
et `ConfigBoundsTable` ne contient que **21 lignes mentionnant `contam`**. L'ordre de grandeur de l'écart
annoncé par l'audit (27 champs) est cohérent avec ce comptage.

- [ ] **Écrire d'abord le test, pas les entrées.** Un test qui itère `ConfigSchema.all()` et assert que
      tout champ numérique a une entrée dans `ConfigBoundsTable` produit la liste exacte des manquants et
      empêche la régression au prochain sous-système ajouté. C'est ce qui aurait attrapé l'écart d'origine.
- [ ] Ajouter les entrées manquantes, en priorité `contamLevelStep` (l'audit trace le chemin jusqu'à
      `ContaminationTick.java:104-110` `hurtServer(Float.MAX_VALUE)`, mort instantanée) et
      `contamLevelJitterMin/Max`.
- [ ] Le finding est ○ : **vérifier la chaîne `contamLevelStep` → dégâts → branche `Float.MAX_VALUE`**
      dans le code avant de fixer les bornes, plutôt que de recopier les chiffres de l'audit.

### Tâche 2.6 — Copie défensive des `double[]` (#26)

- [ ] `ConfigAccess` : `reset()` réinjecte la **référence** stockée dans `DEFAULTS`, pas une copie. Aucun
      code ne mute ces tableaux aujourd'hui — c'est un piège latent, pas un bug actif. Copie défensive à
      la capture du snapshot et à chaque `reset`.
- [ ] Le seul tableau existant est `phaseColorThresholds`, consommé sans risque. Correctif de propreté :
      le faire en même temps que le reste de la phase config, pas dans un commit dédié.

**Commit :** `fix(config): never clobber user config, atomic writes, per-field isolation, complete bounds`

---

## Phase 3 — Fuites mémoire et cycle de vie (#2, #20, #17)

🔴 + 🟡🟡 · **~4 h** (2 h si on s'en tient au colmatage, 1 j pour la refonte par UUID) · #2 ✅, #20/#17 ○

Les trois findings ont la même racine : **`SERVER_STOPPED` est le seul point de nettoyage du mod, et il ne
nettoie que deux choses**. `init/LifecycleInit.java:33-36` :

```java
ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
    registry.clear();
    dimensions.clear();
});
```

Relecture confirmant #2 : les six collections existent bien et sont bien `static`.
`ContaminationState.java:44-52` déclare `tracked` (`HashSet<LivingEntity>`) + quatre
`HashMap<LivingEntity, Long>` ; `ContaminationEpisodes.java:59` et `ContaminationHallucination.java:19`
en ajoutent une chacune. `forgetTimers` (`ContaminationState.java:96-102`) purge bien les cinq premières —
mais **pas** `episodes` ni `hallucTimers`, qui sont `private static` dans leurs classes respectives. Le
correctif de l'audit (« appeler `forgetTimers` au lieu du `tracked.remove` isolé ») est donc **incomplet
tel qu'écrit** : il laisse deux collections sur six.

Et `ContaminationTick.java:36-39` ne retire bien que de `tracked` sur la branche entité-invalide.

### Tâche 3.1 — Un point de purge unique et complet

- [ ] Créer une méthode de purge **par victime** qui couvre les six collections, pas cinq : étendre
      `forgetTimers` en lui faisant appeler une méthode de purge exposée par `ContaminationEpisodes` et
      `ContaminationHallucination` (les maps restent `private`, l'encapsulation est préservée).
- [ ] Créer une méthode de purge **globale** (les six collections vidées) et l'appeler depuis
      `SERVER_STOPPED`.
- [ ] `ContaminationTick.java:37` : appeler la purge par victime au lieu du `tracked.remove(e)` isolé.

### Tâche 3.2 — Purger le scheduler au `SERVER_STOPPED` (#20)

- [ ] `tick/TickScheduler.java:30-31` détient `climbers` et `swimmers`, deux `HashSet<SmartZombie>`, sur
      un `SCHEDULER` statique de durée de vie JVM. La purge existante (`EveryTickPass.drive:45`) ne
      s'exécute qu'au **premier tick du serveur suivant** : tout le temps passé au menu, et toute la phase
      de chargement du monde suivant, l'ancien `ServerLevel` reste résident — c'est-à-dire au pic mémoire.
- [ ] Ajouter une méthode de réinitialisation du scheduler, appelée depuis `SERVER_STOPPED`.
- [ ] Finding ○ : confirmer d'abord que `SCHEDULER` est bien statique et survit à un retour au menu.

### Tâche 3.3 — Faire de `SERVER_STOPPED` le point de nettoyage explicite

- [ ] Après 3.1 et 3.2, `LifecycleInit` devient le lieu où **tout** état process-wide contenant des
      entités ou des niveaux est purgé. Le documenter dans le javadoc de classe, pour que le prochain
      sous-système statique s'y branche par défaut au lieu de fuir en silence.
- [ ] Recenser ce qui reste : `grep -rn "static.*\(Map\|Set\|List\)<.*\(Entity\|Level\|SmartZombie\)" src/main/java`.

### Tâche 3.4 — Libérer le contexte OpenCL (#17)

- [ ] Aucun `clReleaseKernel`/`clReleaseProgram`/`clReleaseCommandQueue`/`clReleaseContext` n'existe dans
      le dépôt. Ajouter une méthode de libération appelée depuis `SERVER_STOPPED`, sous le moniteur de
      `GpuComputeManager`, réarmant `initialized`/`available`.
- [ ] Bénéfice principal : c'est le **chemin de reprise après perte de device** (mise à jour de pilote,
      bascule hybride, eGPU débranché), aujourd'hui inexistant — et le prérequis d'une correction propre
      de #8 en phase 4.

### Tâche 3.5 — Décider de la refonte structurelle

- [ ] Indexer par `UUID` ou passer en `WeakHashMap` rendrait la fuite **structurellement impossible**,
      même si un chemin de purge est oublié plus tard. Coût annoncé : ~1 j.
- [ ] **Décision à prendre après 3.1-3.3**, pas avant : le colmatage est peut-être suffisant si la tâche
      3.3 montre que les points d'entrée sont peu nombreux et bien identifiés. Ne pas lancer la refonte
      par réflexe.

**Commit :** `fix(memory): purge every per-entity static collection on server stop`

---

## Phase 4 — Robustesse flow-field et GPU (#18, #19, #8, #9, #27)

🟠🟠 + 🟡🟡 + ⚪ · **~3 h** · **tous ○** — phase entièrement non contre-expertisée, à relire avant d'écrire

Relecture de `ai/flowfield/FlowFieldManager.java:84-92` : les deux findings tiennent.

```java
computing.set(true);
POOL.submit(() -> {
    try {
        active.set(GpuFlowField.compute(snapshot));
    } finally {
        computing.set(false);
    }
});
```

Aucun `catch` dans la lambda, et le `Future` de `submit` est jeté : avec
`ExecutorService.submit(Runnable)`, un `Throwable` échappé est **capturé dans le `Future`** et n'atteint
ni la console ni le `UncaughtExceptionHandler`. Le `finally` réarme `computing`, donc une nouvelle tâche
est soumise à l'intervalle suivant, qui lève à nouveau : boucle silencieuse, coût du snapshot payé pour
un résultat jeté, et `active` conservant un champ périmé. Cette cécité masque exactement les échecs GPU
de #8.

Et `computing.set(true)` ligne 85 précède `POOL.submit` ligne 86 sans protection : si `submit` lève, la
tâche n'existe pas, son `finally` ne s'exécute jamais, et `computing` reste `true` **définitivement** —
le flow-field de cette dimension est mort jusqu'au redémarrage, sans log.

### Tâche 4.1 — Rendre les échecs de solve visibles (#18)

- [ ] `catch (Throwable)` dans la lambda, journalisé avec limitation de débit (pas une ligne par solve).
- [ ] Invalider explicitement `active` pour basculer sur un repli connu plutôt que de laisser un champ
      périmé décrire une géométrie que les zombies ont eux-mêmes détruite.
- [ ] Exposer un compteur d'échecs consécutifs dans le récapitulatif de performance existant (`PerfRecap`).

### Tâche 4.2 — Rendre l'état `computing` inescapable (#19)

- [ ] `compareAndSet(false, true)` avec retour anticipé sur échec.
- [ ] `try`/`catch` autour de `POOL.submit` qui remet le drapeau à `false` avant de propager.
- [ ] L'audit précise qu'un double-solve **n'est pas atteignable** (un seul thread par instance) : c'est
      un correctif de robustesse, pas un bug actif. Ne pas le sur-vendre dans le message de commit.

### Tâche 4.3 — Fuite VRAM des six `cl_mem` (#8)

- [ ] `ai/flowfield/gpu/GpuFlowFieldSolver.java:54-66` : les six `clCreateBuffer` précèdent le `try`, et
      `GpuContext:46` active `setExceptionsEnabled(true)` — donc un échec au rang *k* laisse *k−1* buffers
      vivants côté device. Initialiser les six références à `null` avant un `try` unique, libérer dans le
      `finally` ce qui est non nul, **chaque libération protégée individuellement** (aujourd'hui les six
      `clReleaseMemObject` sont en séquence nue : si le premier lève, les cinq suivants sont sautés).
- [ ] Relecture confirmant l'amplificateur : `GpuComputeManager.logFallbackOnce:68-73` écrit « using CPU
      from now on » et ne touche **jamais** `available`. Le basculement promis n'a pas lieu, donc la fuite
      se répète à chaque solve. Faire tenir sa promesse à la méthode, ou corriger le message.
- [ ] Finding ○, et son chiffrage (800 Mo/h aux défauts, 5,6 Go/h au plafond) est purement analytique.
      **Ne pas le citer comme un fait** dans le commit sans l'avoir mesuré.

### Tâche 4.4 — `/lethalconfig` ne doit pas initialiser OpenCL sur le thread serveur (#9)

- [ ] `command/LethalConfigCommand.java:83-87` — relu : `gpuInfo()` appelle
      `GpuComputeManager.get().isAvailable()`, qui est `synchronized` **et** déclenche `init()` si
      `initialized` est faux. Or `LifecycleInit:22-29` ne préchauffe que si `FlowConfig.useGpu` est vrai.
      Un administrateur ayant mis `useGpu=false` — précisément parce qu'OpenCL pose problème sur sa
      machine — y est donc ramené par le menu de configuration, sur le thread de tick, pour un
      `clBuildProgram` de l'ordre de la seconde à pilote froid. Le kill-switch documenté n'en est pas un.
- [ ] `gpuInfo()` doit consulter `FlowConfig.useGpu` puis un accesseur **non bloquant** renvoyant l'état
      déjà connu, sans prendre le moniteur ni déclencher `init()`.
- [ ] Séparer le verrou de cycle de vie du verrou de sérialisation de la file GPU (aujourd'hui
      `isAvailable()` et `solve()` partagent le moniteur d'instance).

### Tâche 4.5 — `volatile` sur l'état du gestionnaire GPU (#27)

- [ ] Relu : `initialized`, `available`, `deviceName`, `fallbackLogged` (lignes 27-30) ne sont pas
      `volatile`, et `deviceName()` (43-45) comme `logFallbackOnce()` (68-73) ne sont pas `synchronized`.
      La sûreté actuelle de `deviceName()` est fortuite — elle tient à ce que son unique appelant vient
      d'exécuter `isAvailable()`.
- [ ] Marquer les quatre champs `volatile`. C'est aussi le prérequis de la lecture non bloquante de 4.4.

**Commit :** `fix(flowfield,gpu): surface solve failures, release native buffers, non-blocking gpu state`

---

## Phase 5 — Performance (#7, #22, #11, #10, #12, #13, #23, #16)

🟠×4 + 🟡×4 · **~2 j** · **tous ○ sauf #12 et #13, dont les sévérités ont justement été divisées par deux**

**C'est la phase où il faut le plus se méfier de l'audit.** Il l'admet lui-même : « aucune exécution : pas
de build, pas de tests, pas de profilage. Tous les chiffrages de performance sont analytiques, jamais
mesurés. » Et les deux seuls findings perf qui ont subi une contre-expertise (#12 et #13) ont vu leur
sévérité tomber de CRITIQUE à MOYEN, avec des chiffrages divisés par un ordre de grandeur — 5-10 ms/tick
annoncés contre 0,13-0,5 ms recalculés pour #12.

### Tâche 5.0 — Mesurer avant de toucher quoi que ce soit (bloquante)

- [ ] Rien de cette phase ne s'écrit avant d'avoir un profil réel. `FlowFieldPerfBench` existe déjà dans
      `src/test`, et `PerfRecap` instrumente déjà le tick : partir de là plutôt que de créer un harnais.
- [ ] Les trois harnais headless du dépôt (`dev-tests` / `run-all-tests`) donnent une charge reproductible.
- [ ] **Livrable de cette tâche : un classement mesuré des coûts par tick.** L'ordre des tâches 5.1-5.5
      est ensuite déterminé par ce classement, pas par la numérotation de l'audit.

### Tâche 5.1 — Borner l'amplification FROZEN → actif (#22)

Priorité *a priori* la plus haute de la phase, à confirmer par 5.0. L'audit décrit le mécanisme qui
**neutralise l'optimisation sur laquelle repose tout le budget du mod** : `SoundEventBus.rememberTarget`
arme la mémoire courte, `LODManager:59-85` fait sortir de FROZEN tout zombie ayant une mémoire valide,
et une seule vache qui bouge près d'une horde dense convertit plusieurs centaines de zombies FROZEN en
HIGH/MEDIUM pendant 200 ticks — lesquels paient alors le scan de #12 à chaque activation.

- [ ] **Borner le nombre de zombies réveillés par événement** (les K plus proches). C'est le correctif
      structurant : il plafonne mécaniquement l'amplification.
- [ ] Fusionner les événements proches avant distribution.
- [ ] `Long2ObjectOpenHashMap` pour supprimer le boxing d'un `Long` par cellule sondée (fastutil est déjà
      au classpath).

### Tâche 5.2 — Clamper le rayon dans `queryRadius` (#7)

- [ ] Relu `spatial/SpatialGrid.java:79-104` : le coût est bien `((2·radius/cell)+1)²` lookups,
      **indépendant du nombre de zombies** — la double boucle `cx`/`cz` balaie toutes les cellules de la
      zone, y compris vides. Les bornes de config sont posées champ par champ et ne contraignent jamais le
      rapport `radius/cell`.
- [ ] Clamper le rayon effectif **à l'entrée de `queryRadius`** : c'est le seul endroit qui connaît le
      coût réel. Ne pas faire confiance à l'appelant.
- [ ] Au-delà d'un certain nombre de cellules, itérer `cells.entrySet()` devient strictement moins cher
      que de sonder des cellules vides. Basculer sur ce mode plutôt que de refuser la requête.
- [ ] Resserrer `soundLoudMultiplier` de 1..16 à 1..4.

### Tâche 5.3 — Soumettre `EveryTickPass` au LOD et au budget (#11)

- [ ] Relu `tick/EveryTickPass.java:37-59` : `drive` itère l'ensemble complet à chaque tick et ne teste que
      `isValid()` et `stillActive` — **aucune re-vérification du tier LOD ni du budget**. Un zombie LOW à
      120 blocs flottant dans l'océan reçoit le même traitement qu'un zombie collé au joueur. C'est le seul
      chemin du mod où une entité distante coûte autant qu'une entité proche.
- [ ] Appliquer au drain par-tick le même filtre de tier que le bucket pass, et compter ces pas dans le
      même compteur que `sz.tick()`.
- [ ] Passer un `MutableBlockPos` scratch à `tryBreak`/`breakableSolid` ; tester le cap **avant** de
      calculer la clé dans `BreakManager` (aujourd'hui `active.containsKey(key)` boxe avant le test).

### Tâche 5.4 — Rendre les protections de charge réellement actives (#10)

- [ ] Relu `config/domain/SchedulerConfig.java` : `aiTickBudget = 0` rend la condition `budget > 0`
      fausse, `msptThrottle = false`, `autoScaleBuckets = false`. **Aucune des trois protections de charge
      n'est active dans une installation par défaut.** Un mod qui empêche le despawn a l'obligation de
      borner sa charge lui-même — choisir des défauts défensifs.
- [ ] Le délestage agit aussi au mauvais endroit : `stress` ne multiplie que `divisor`, testé **après**
      `classify`, `spatialGrid().update`, `applySunBurn` et `updateMood`. Il réduit donc uniquement
      `sz.tick()`, la seule partie déjà protégée. Déplacer la barrière **avant** `classify`.
- [ ] Remplacer `getAverageTickTimeNanos()` (moyenne glissante sur 100 ticks, donc un pic de 200 ms
      n'ajoute que 2 ms) par une moyenne courte maintenue par le mod, avec hystérésis.

### Tâche 5.5 — Ne plus jeter le résultat de l'opération la plus chère (#23, #12, #13)

- [ ] #23 : extraire de `handleDaySleep` un prédicat pur (jour, phase, alerte, dégâts, `staysAwake`,
      `canSeeSky` — toutes lectures triviales) et **sauter `classify` + `updateMood`** si le zombie va
      dormir. Aujourd'hui `classify` exécute le scan complet, pose la cible, puis onze lignes plus loin
      `dozeInPlace()` → `suppressHunt()` efface tout. Boucle permanente acquis → effacé → ré-acquis.
- [ ] #12 : quand `targetPlayersOnly = true`, itérer `level.players()` au lieu de balayer
      `LivingEntity.class` — dans ce mode `isValid` rejette 100 % des non-joueurs.
- [ ] #12 (piste signalée par le vérificateur, **non chiffrée**) : la vraie dépense par scan est
      probablement le raycast voxel LOS de `canSee`, jusqu'à ~80 `getBlockState` par candidat, sans plafond
      sur le nombre de candidats. **À mesurer en 5.0** — c'est peut-être le vrai sujet de la phase.
- [ ] #13 : mémoïser l'échec de `ShelterFinder.findShade` par un `nextShadeSearchTick`, initialiser
      `bestScore` au score maximal utile plutôt qu'à `Double.MAX_VALUE` (les deux élagages comparent
      `>= bestScore`, donc tant qu'aucune ombre n'est trouvée **aucune itération n'est sautée** : 8 112
      `canSeeSky` exactement), et balayer en spirale depuis le centre avec sortie au premier succès.
- [ ] Calculer `canSeeSky(entity.blockPosition())` **une seule fois par activation** (recalculé jusqu'à
      trois fois aujourd'hui).

### Tâche 5.6 — Découpler application et persistance de la config (#16)

- [ ] `client/screen/NumOptionEntry.java:23` : `EditBox.setResponder` notifie à chaque mutation du texte.
      Taper `36000` envoie 5 paquets `SetConfig`, chacun déclenchant sur le thread de tick une
      reconstruction réflexive de 295 `Field` **deux fois** puis un `Files.writeString` bloquant. Soit
      5 écritures disque synchrones de ~10 Ko pour une valeur saisie.
- [ ] Côté client : n'émettre qu'à la perte de focus, ou après un debounce.
- [ ] Côté serveur : marqueur « dirty » drainé au plus une fois par seconde.
- [ ] Mettre en cache `ConfigSchema.all()` — la liste des champs ne change **jamais** à l'exécution
      (traite #29 par la même occasion).

**Commits :** un par tâche, chacun avec son avant/après mesuré. Pas de commit perf sans chiffre.

---

## Phase 6 — Hygiène (#21, #24, #25, #28, #29)

⚪ + 🟡 · **~2 h** · tous ○

### Tâche 6.1 — Ne pas lever depuis un callback d'entité en session (#21)

- [ ] `util/AiConflictDetector.java:76-80`, appelé depuis `init/EntityEventsInit.java:70` : le `throw` est
      légitime au boot (`checkModList` depuis `BootstrapInit.run()`, le serveur ne démarre pas), mais la
      détection **comportementale** se déclenche au premier chargement d'un zombie, donc au milieu d'un
      tick, dans `ENTITY_LOAD`. Crash en session au lieu d'un refus propre au démarrage.
- [ ] Séparer les deux politiques : au boot conserver le `throw` ; en session loguer et basculer en mode
      dégradé, ou passer par `MinecraftServer.halt` pour que le monde soit sauvegardé.

### Tâche 6.2 — `OpenConfig` conditionnel (#24)

- [ ] `client/LethalBreedClient.java:19-21` : le récepteur est inconditionnel, aucun état client ne
      mémorise qu'un `/lethalconfig` a été demandé. Ignorer le paquet si aucune demande récente, ou si un
      `CustomConfigScreen` est déjà affiché.
- [ ] Impact borné (aucun effet serveur, aucune fuite, aucune exécution de code) — correctif de propreté.

### Tâche 6.3 — Les cinq options client mensongères (#25)

- [ ] `maxRenderedZombies`, `reduceFarDetail`, `farDetailDistance`, `instancedRendering`,
      `billboardFarZombies` ne sont consommés nulle part ; seul `effectiveCullDistanceSq()` l'est. La ligne
      de log affiche pourtant `maxRender={}` comme si l'option agissait.
- [ ] **Décision à prendre :** les implémenter ou les retirer. Les laisser en l'état est un mensonge
      documenté — un utilisateur qui baisse l'option pour gagner des FPS obtient une confirmation dans le
      log et aucun effet. Le retrait est le choix par défaut ; l'implémentation est un vrai chantier
      rendering.

### Tâche 6.4 — Documenter la contrainte JOCL globale (#28)

- [ ] `setExceptionsEnabled(true)` porte sur tout le classloader, et JOCL est en `include` donc embarqué.
      Aucun changement fonctionnel : le noter comme contrainte assumée dans le javadoc de `GpuContext`.

### Tâche 6.5 — Cache de `ConfigSchema.all()` (#29)

- [ ] Traité en 5.6. Ne rien faire de plus ici — l'audit lui-même corrige sa cartographie et note que ce
      n'est **pas** un chemin chaud (les seuls appelants sont `ConfigIo`, `ConfigAccess`,
      `LethalConfigCommand`, `LethalConfigPayloads` ; toute la config lue en boucle de tick passe par des
      lectures de champs `static`, ce qui est optimal).

**Commit :** `chore: lifecycle-safe conflict detection, honest client options, doc constraints`

---

## Phase 7 — Compléter l'audit (les trois lentilles non abouties)

**Ce n'est pas de la correction, c'est de l'audit.** L'audit note que trois lentilles sur sept n'ont pas
abouti (deux interrompues par une erreur d'API, une arrêtée manuellement). Les angles suivants n'ont donc
**jamais été examinés** :

| Lentille | Ce qui n'a pas été regardé |
|---|---|
| **Correction algorithmique** | Équivalence `bellman_ford.cl` ↔ `BellmanFordSolver.java` — une divergence ferait diverger le comportement des zombies selon la présence d'un GPU. Overflow `INF + coût`. Indices et bornes de grille. Convergence. Math de mouvement (normalisation d'un vecteur nul, `acos` hors domaine). Le raycast voxel LOS de `TargetSelector` |
| **Cycle de vie & persistance** | Codecs et `SavedData` au rechargement. `SpecialAttachment` avec un id inconnu (downgrade, save éditée). Symétrie registre/grille/maps. Réentrance de `ChildSpawner`. Ordre `ENTITY_LOAD`/`AFTER_DEATH`/`ENTITY_UNLOAD`. Multi-dimension et portails |
| **Mixins & compatibilité** | Fragilité des 24 injections. **Conflit interne suspecté : `ZombieBellyModelMixin` et `ZombieSleepArmsMixin` injectent tous deux en TAIL sur `AbstractZombieModel.setupAnim`** — directement lié au travail en cours sur cette branche. Conflit `SpawnStateMobcapMixin`/Lithium avec `defaultRequire: 1`. Les trois `@Redirect` |

- [ ] Le conflit `ZombieBellyModelMixin` / `ZombieSleepArmsMixin` est le seul point de cette phase qui
      touche du code **écrit il y a quatre jours et non encore vérifié en jeu**. À traiter en premier, et
      de toute façon avant de reprendre la tâche 4 de l'autre plan.
- [ ] Le solveur GPU a déjà un self-test de parité (`devComputeTest`, cf. skill `gpu-cpu-flowfield-cost-parity`,
      qui documente trois bugs de divergence déjà trouvés et réconciliés). La lentille algorithmique
      devrait partir de là plutôt que de repartir de zéro.
- [ ] Aucun scanner de vulnérabilités n'a pu tourner. Les CVE de `jocl:2.0.5`, Fabric API 0.141.4 et
      `gson:2.13.2` n'ont **pas** été vérifiées — un `osv-scanner` sur le lockfile coûte cinq minutes.

---

## Interaction avec le travail en cours

La branche `feat/sleeping-zombie-visuals` a un plan en cours,
`docs/superpowers/plans/2026-07-23-sleeping-zombie-eyes-and-arms.md` : tâches 1-3 livrées dans `7e03e97`,
**tâche 4 (overlay paupières) non implémentée**, et la vérification visuelle in-game de la tâche 3 est
toujours en attente.

Deux chantiers indépendants sur la même branche. Recommandation :

- [ ] **Fermer le chantier sommeil d'abord** — vérification visuelle de la tâche 3, puis tâche 4 — et
      merger, plutôt que d'empiler la remédiation d'audit par-dessus une feature à moitié vérifiée.
- [ ] Ouvrir la remédiation sur une branche dédiée depuis `master` (`fix/audit-2026-07`), phase par phase.
- [ ] `AUDIT.md` est actuellement **non versionné** (`?? AUDIT.md`). Le committer avant de commencer :
      c'est la référence de tous les messages de commit du plan.

---

## Ce qui reste à décider

Cinq points que ce plan pose sans trancher, parce qu'ils demandent un arbitrage qui n'est pas technique :

1. **Tâche 1.4** — ne pas rendre persistantes les entités de `/lethalspecial` entre potentiellement en
   conflit direct avec la décision d'architecture du commit `b3bdd46` (la persistance est le socle du LOD).
2. **Tâche 2.4** — valeur de repli pour un `NaN` sur un champ **sans** bornes déclarées.
3. **Tâche 3.5** — colmatage (2 h) ou refonte par `UUID`/`WeakHashMap` (1 j).
4. **Tâche 5.4** — quels défauts défensifs pour les trois protections de charge, sachant que les activer
   change le comportement de toutes les installations existantes.
5. **Tâche 6.3** — implémenter ou retirer les cinq options client non câblées.

---

## Vérification

Chaque tâche se termine par :

```bash
cd /run/media/dreykaoas/O.A.S/projects/mods/LethalBreed/mod
JAVA_HOME="$HOME/.jdks/jdk-21.0.12+8" ./gradlew \
  -Dorg.gradle.java.home="$HOME/.jdks/jdk-21.0.12+8" compileJava test --console=plain
```

Les phases 1 à 3 sont **intégralement vérifiables headless**. La phase 4 l'est pour le code, pas pour le
chemin GPU (aucun device OpenCL garanti sur cette machine — à confirmer). La phase 5 exige en plus les
harnais `dev-tests` et un avant/après chiffré. La phase 6 est cosmétique côté vérification.

Un `runClient` n'est nécessaire nulle part dans ce plan, contrairement au plan sommeil — c'est ce qui rend
la remédiation d'audit exécutable en autonomie là où la tâche 4 du plan sommeil ne l'est pas.
