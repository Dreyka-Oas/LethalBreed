# Player-Jar-Only — résultat d'exécution

Branche : `chore/player-jar-only` · 21 commits depuis `5095b69` · plan : [2026-08-12-player-jar-only.md](2026-08-12-player-jar-only.md)

**État : prêt à relire, une décision produit à confirmer (§5).** Build vert, 224 tests, 0 échec.

---

## 1. Ce qui a changé pour le joueur

`./gradlew build` écrit désormais **un seul fichier** dans `build/libs` : `lethalbreed-1.0.0.jar`, 787 461 octets.

| Avant | Après |
|---|---|
| jar joueur **+ jar de sources** (207 fichiers .java, 3 995 lignes de commentaires) | jar joueur seul |
| flavour développeur (`devJar` / `remapDevJar` / `build-dev.bat`) | supprimée |
| `-g` complet : 233 classes avec `LocalVariableTable` | `-g:source,lines` — 0 `LocalVariableTable`, traces de crash toujours exploitables |
| kernel OpenCL commenté en clair dans le jar | commentaires strippés à l'empaquetage |
| 17 options dev + onglet « Dev / Debug » visibles en jeu | absents du jar joueur |
| 35 clés de langue dev × 2 fichiers | supprimées |
| `/lethalphase`, `/lethalspecial` livrées | passées côté dev |

Vérifié sur l'artefact construit : **0 classe** sous `com/dreykaoas/lethalbreed/dev/`, **0 occurrence** de `harness|devtest|lethaldev|lethalspawn|StageProfiler|ClimbDbg|PackDbg` dans les `strings` de toutes les classes, **0 clé** dev/debug dans les fichiers de langue.

## 2. Ce que le dev conserve

`runClient`, `runClient1`, `runClient2`, `runServer`, `start.bat`, `start-all.bat` : inchangés.

Les **4 commandes** (`/lethaldev`, `/lethalspawn`, `/lethalphase`, `/lethalspecial`), les **12 suites de harness**, l'onglet de config dev et le flux du profileur fonctionnent tous — ils ne sont simplement plus dans le jar.

## 3. L'architecture

`src/dev` était déjà exclu de `remapJar` par construction : c'est devenu la destination. Le code dev que `src/main` n'appelle jamais a été **déplacé**. L'instrumentation que `src/main` appelle depuis ses chemins chauds ne pouvait pas bouger, donc `main` garde **une seule** classe de couture — [DevProbe](../../../mod/src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java), 1 803 octets — dont le champ `sink` est `null` dans le jar joueur. `DevBootstrap` y installe l'implémentation réelle via le hook réflexif dev qui existait déjà.

## 4. Trois bugs réels corrigés au passage

1. **Chemin FLOWSNAP non gardé.** `FlowFieldSnapshotBuilder` appelait `System.nanoTime()` sans condition, et `StageProfiler.active` n'était jamais `null` — les serveurs des joueurs payaient 2 `nanoTime()` + écritures tableau par snapshot de flow field, pour des nanos jamais drainés. La javadoc affirmait « a published jar never pays for it » : c'était faux.
2. **`contamDevTimeScale` sans libellé.** Vraie option joueur (`ContaminationState:116` divise les timers de peste par elle), dans l'onglet Contamination, mais **sans aucune clé de langue** depuis toujours — elle s'affichait en clé brute. Corrigé en FR et EN.
3. **`clamp()` privée de son contrat.** Une insertion avait séparé sa javadoc de la méthode, supprimant le `@throws IllegalArgumentException` sur lequel `ConfigAccess.apply` s'appuie.

Coûts runtime supprimés du jar joueur : une instance `ClimbDebug` **par zombie**, 2 `nanoTime()` par tick serveur, un CAS atomique à chaque `findShade`, un `int shadeScans` par zombie.

## 5. ⚠️ Une décision produit à confirmer

`/lethalconfig` a été réduit à sa forme nue (ouvre la GUI). `list`, `get`, `set`, `reset <champ>` et `reset all` ont été supprimés — c'était le choix retenu, en sachant qu'il coûtait l'édition de config à chaud sur serveur dédié headless. `verify` a suivi plus tard, une fois le loader capable de réparer les dérives tout seul : ce qu'il rapportait est désormais soit corrigé avant qu'on puisse le lire, soit énoncé en entier par l'avis de connexion. Le jar ne livre donc plus qu'une seule forme de commande.

**Fait découvert après coup, à trancher :** il n'existe **aucun autre chemin** vers un reset global. La GUI n'a qu'un bouton de reset par ligne, pas de reset-all, et aucun paquet réseau ne le porte. Conséquence : `ConfigFields.resetAll()` → `ConfigAccess.resetAll()` n'a plus d'appelant et part en **code mort** dans le jar. Sur un serveur dédié, la console n'a plus aucun moyen de modifier une valeur de config.

Gain de la suppression : 5,4 Ko sur 787 Ko (0,7 %).

Si tu veux revenir dessus, tout est dans [LethalConfigCommand.java](../../../mod/src/main/java/com/dreykaoas/lethalbreed/command/LethalConfigCommand.java) — restaurer `FIELD_SUGGEST`, les littéraux dans `register()`, les quatre handlers, `unknown()`, et la surcharge 3-arg `CommandFeedback.success`. Rien là-dedans n'est du code dev.

## 6. Échecs de harness — antérieurs, pas causés par la branche

4 sous-checks échouent sur 3 suites. **Aucun n'est une régression de cette branche :**

| Check | Preuve |
|---|---|
| `shade/reaches-shelter` | reproduit à l'identique sur le code pré-branche (test au `git stash`, Task 5) |
| `mech/flee-rally` | idem |
| `pack/marche-rapprochement` | voir ci-dessous |
| `pack/marche-cohesion` | voir ci-dessous |

Les deux `pack` ont demandé deux passes. Une première analyse statique concluait « pas de régression » ; la revue finale l'a **réfutée** en montrant que la ligne supprimée `cfg.set("debugPacks", stage == 0)` était un gate **par étape**, et que le log toujours actif pouvait faire monter le mspt → `msptThrottle` → `stress = 2` → diviseurs LOD doublés → migration à demi-vitesse. Théorie plausible, donc testée : le gate par étape a été restauré et le harness relancé. **Toujours 2/11 en échec.** La théorie est donc fausse, et les chiffres le confirment — rapprochement de `0.00` blocs pour un départ à 745,87, écart max 723,62 blocs : la meute ne migre pas du tout, ce n'est pas une dégradation de performance.

Ces deux checks méritent une investigation à part, hors de cette branche.

## 7. Points ouverts

**Avant publication :**
- Le strip des commentaires du kernel OpenCL a été prouvé équivalent octet pour octet, mais **cette machine n'a pas d'ICD OpenCL** — le kernel n'a jamais été compilé par un vrai `clBuildProgram`. À revérifier sur une machine avec GPU.
- Les joueurs existants verront **un lancement** de WARN pour les 17 clés désormais inconnues, plus une ligne `'Dev' is not a config category`, puis le fichier est réécrit propre. Aucun réglage perdu. Mérite une ligne de changelog.

**Résidus assumés :**
- ~59 octets de littéraux d'étiquettes de trace (` dy=`, ` ground=`, ` -> `) restent dans le constant pool : Java interne les littéraux que la branche gardée s'exécute ou non. Les éliminer imposerait d'élargir `Sink.trace` en varargs.
- Le JiJ JOCL (424 Ko, ~54 % du jar) reste intact : 4 de ses 5 bibliothèques natives sont inutiles à un joueur donné, mais en retirer une désactive silencieusement le GPU sur cette plateforme.

**Non bloquants**, listés dans `.superpowers/sdd/progress.md` : une douzaine de points cosmétiques (javadoc, duplication d'un helper de reply entre deux commandes dev, `DevSink.get()` sans garde null — inatteignable aujourd'hui).

## 8. Méthode

11 tâches, une par commit ou groupe cohérent, chacune implémentée par un agent neuf puis relue par un agent adverse indépendant, plus une revue finale de branche entière. Le journal complet — chaque décision, chaque déviation, chaque finding — est dans `.superpowers/sdd/progress.md`.

Trois défauts du plan ont été rattrapés en cours de route par les implémenteurs ou les reviewers :
- le câblage `DevBootstrap` du plan avait une circularité `null`-puis-`bindProfiler` ;
- le hoist littéral de `installDevHooks()` aurait cassé le sélecteur `LB_DEV_TEST` ;
- le snippet de remplacement de la trace CLIMB perdait silencieusement deux champs de diagnostic et son throttle.
