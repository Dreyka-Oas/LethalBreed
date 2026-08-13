# Player-Jar-Only — résultat d'exécution

Branche : `chore/player-jar-only` · 21 commits depuis `5095b69` · plan : [2026-08-12-player-jar-only.md](2026-08-12-player-jar-only.md)

**État : relu et testé.** Build propre (`clean --rerun-tasks`), 229 tests unitaires, 0 échec ; 12 suites de harness relancées, 68/70 checks (§6) ; jar vérifié dans un vrai client Prism.

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

**Exception ajoutée après coup :** `/lethaldev level <n>` et `/lethaldev cure` ont été rapatriés dans `src/main` sur demande, et livrent donc dans le jar. Le littéral `lethaldev` et ces deux sous-commandes sont enregistrés par `PlagueCommand` (src/main) ; les quatre autres viennent se greffer sur le même nœud depuis `src/dev`, Brigadier fusionnant deux enregistrements d'un même littéral. Ce comportement — et le fait que la fusion conserve le `requires()` du **premier** enregistrement — est épinglé par `LiteralMergeTest`.

## 3. L'architecture

`src/dev` était déjà exclu de `remapJar` par construction : c'est devenu la destination. Le code dev que `src/main` n'appelle jamais a été **déplacé**. L'instrumentation que `src/main` appelle depuis ses chemins chauds ne pouvait pas bouger, donc `main` garde **une seule** classe de couture — [DevProbe](../../../mod/src/main/java/com/dreykaoas/lethalbreed/probe/DevProbe.java), 1 803 octets — dont le champ `sink` est `null` dans le jar joueur. `DevBootstrap` y installe l'implémentation réelle via le hook réflexif dev qui existait déjà.

## 4. Trois bugs réels corrigés au passage

1. **Chemin FLOWSNAP non gardé.** `FlowFieldSnapshotBuilder` appelait `System.nanoTime()` sans condition, et `StageProfiler.active` n'était jamais `null` — les serveurs des joueurs payaient 2 `nanoTime()` + écritures tableau par snapshot de flow field, pour des nanos jamais drainés. La javadoc affirmait « a published jar never pays for it » : c'était faux.
2. **`contamDevTimeScale` sans libellé.** Vraie option joueur (`ContaminationState:116` divise les timers de peste par elle), dans l'onglet Contamination, mais **sans aucune clé de langue** depuis toujours — elle s'affichait en clé brute. Corrigé en FR et EN.
3. **`clamp()` privée de son contrat.** Une insertion avait séparé sa javadoc de la méthode, supprimant le `@throws IllegalArgumentException` sur lequel `ConfigAccess.apply` s'appuie.

Coûts runtime supprimés du jar joueur : une instance `ClimbDebug` **par zombie**, 2 `nanoTime()` par tick serveur, un CAS atomique à chaque `findShade`, un `int shadeScans` par zombie.

## 5. La décision produit — tranchée

`/lethalconfig` a été réduit à sa forme nue (ouvre la GUI). `list`, `get`, `set`, `reset <champ>` et `reset all` ont été supprimés — c'était le choix retenu, en sachant qu'il coûtait l'édition de config à chaud sur serveur dédié headless. `verify` a suivi plus tard, une fois le loader capable de réparer les dérives tout seul : ce qu'il rapportait est désormais soit corrigé avant qu'on puisse le lire, soit énoncé en entier par l'avis de connexion. Le jar ne livre donc plus qu'une seule forme de commande.

**Tranché :** il n'existe aucun autre chemin vers un reset global, et il s'avère qu'il n'en existait aucun vers un reset tout court côté serveur — la GUI résout le défaut côté client depuis le snapshot réseau et renvoie la valeur par le paquet `SetConfig`, sans jamais appeler `ConfigAccess.reset`. Toute la famille (`ConfigFields.resetAll`, `ConfigAccess.resetAll`, `resetAllInMemory`, `reset(Field)`) était donc morte et a été supprimée.

L'invariant qu'elle protégeait, lui, est bien réel et a été conservé : `defaultOf` répond `"?"` pour une option dont le défaut n'a jamais été capturé, et cette réponse voyage jusqu'à la GUI — c'est ce que l'icône de reset réécrit quand on clique dessus. Un holder enregistré sans `captureDefaultsFor` transforme donc silencieusement un bouton de reset en bouton de corruption. `ConfigDefaultsTest` couvre désormais ça directement, sur le chemin vivant.

Reste vrai : sur un serveur dédié, la console n'a aucun moyen de modifier une valeur de config.

Gain de la suppression : 5,4 Ko sur 787 Ko (0,7 %).

Si tu veux revenir dessus, tout est dans [LethalConfigCommand.java](../../../mod/src/main/java/com/dreykaoas/lethalbreed/command/LethalConfigCommand.java) — restaurer `FIELD_SUGGEST`, les littéraux dans `register()`, les quatre handlers, `unknown()`, et la surcharge 3-arg `CommandFeedback.success`. Rien là-dedans n'est du code dev.

## 6. Échecs de harness — mesurés, et plus instables que décrit

Les 12 suites relancées intégralement le 13/08 après tous les changements de commandes et le
nettoyage de code mort : **10 vertes, 2 en échec, 71 checks passés sur 73.**

¹ `compute` a d'abord rendu 6/6 sur le chemin CPU, ses trois checks GPU en SKIP ; relancée avec
OpenCL fonctionnel (voir §7), elle passe 9/9.

| Suite | Verdict | Suite | Verdict |
|---|---|---|---|
| special | 8/8 | placed | 7/7 |
| mech | 4/5 ❌ | shade | 3/3 |
| climb | 4/4 | breach | 5/5 |
| compute | 9/9 ¹ | presence | 4/4 |
| plague | 11/11 | pack | 10/11 ❌ |
| statue | 4/4 | clear | 6/6 |

Les deux échecs restants : `mech/flee-rally` (0 cri de détresse mais des renforts ralliés — le
check suppose une arène silencieuse qu'elle n'est pas) et `pack/marche-cohesion` (écart max 199,60
blocs pour un `packBreakRadius` de 40).

**Cette section affirmait auparavant quatre échecs « reproduits à l'identique ». C'était faux sur
deux points, et les chiffres le montrent :**

- `shade/reaches-shelter` **passe** désormais, explicitement (`seek latched=true, under cover=true,
  sleeping=true`). Il n'était donc pas systématiquement en échec, mais instable.
- `pack/marche-rapprochement` **passe** avec **267,86 blocs** de rapprochement pour un départ à
  313,10. La conclusion précédente — « rapprochement de 0,00 bloc […] la meute ne migre pas du
  tout » — ne tient pas : la meute migre. Et `marche-cohesion` échoue à 199,60 blocs d'écart, pas
  723,62.

Ces deux checks varient donc fortement d'une exécution à l'autre. Ce qu'on peut affirmer : aucun
des deux échecs restants n'est causé par cette branche (aucun code de meute, de fuite ou de son
n'y a été touché), mais l'affirmation « reproduit à l'identique sur le code pré-branche » ne peut
pas être maintenue pour des checks dont le résultat change d'un run à l'autre. Leur instabilité
est elle-même le défaut à investiguer, hors de cette branche.

## 7. Points ouverts

**Avant publication :**
- ~~Le kernel OpenCL n'a jamais été compilé par un vrai `clBuildProgram`.~~ **Vérifié le 13/08.** Le diagnostic « pas d'ICD » était incomplet : la machine a une Radeon RX 9060 XT et le loader `OpenCL-ICD-Loader`, il manquait le pilote vendeur. Avec `mesa-libOpenCL` (rusticl) installé, plus deux détails qui bloquaient encore :
  - `RUSTICL_ENABLE=radeonsi` est requis, sinon la plateforme s'expose sans aucun périphérique ;
  - JOCL charge `libOpenCL.so` **sans version**, alors que Fedora ne livre que `libOpenCL.so.1` (le lien non versionné est dans le paquet `-devel`). Un lien symbolique dans un dossier pointé par `LD_LIBRARY_PATH` suffit — pas besoin de root.

  Résultat : `GPU: AMD Radeon RX 9060 XT — OpenCL OK`, le kernel strippé compile, et `compute` passe de 6/6 à **9/9** — les trois checks GPU étaient jusque-là en SKIP. `gpu-cpu-parity` PASS sur les 4096 cellules, `gpu-direction` et `gpu-optimal` PASS. Le strip est donc prouvé fonctionnellement, plus seulement octet pour octet.
- L'avis de connexion opérateur (`LifecycleInit`) **est vérifié** : `runServer` avec une config cassée + `runClient1`, qui rejoint automatiquement en `Tester1` (op 4, serveur en `online-mode=false`). L'avis étant envoyé au joueur et non journalisé côté serveur, l'assertion porte sur le log du client. Les deux moitiés du comportement sont couvertes :
  - **dérive non réparable** (une faute ambiguë + un doublon) → `[LethalBreed] 2 problème(s) dans la structure de config/oas/lethalbreed.json :` suivi des deux lignes nommant `daySleepEnable` et `lodHigh` ;
  - **dérive uniquement réparable** (faute résoluble + option mal rangée + catégorie fantôme) → le serveur journalise les trois réparations, `Tester1 joined the game`, et le client reçoit **zéro** message. C'est le point même de la refonte : ce qui se corrige tout seul ne dérange plus personne.
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
