# Player-Jar-Only — résultat d'exécution

Branche : `chore/player-jar-only` · 21 commits depuis `5095b69` · plan : [2026-08-12-player-jar-only.md](2026-08-12-player-jar-only.md)

**État : relu et testé.** Build propre (`clean --rerun-tasks`), 229 tests unitaires, 0 échec ; les 12 suites de harness vertes après correction de deux défauts d'arène (§6) ; kernel OpenCL validé sur GPU réel (§7) ; jar vérifié dans un vrai client Prism.

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

## 6. Le harness — six défauts d'arène, aucun dans le code livré

Au départ : 10 suites vertes, 2 en échec. Mais en relançant, **une suite différente échouait à chaque
campagne** — `mech`, puis `statue` et `breach`, puis `plague`, puis `presence`, puis `special`… alors
que chacun de ces checks passait 5/5 exécuté seul. Douze checks capricieux, c'est une explication qui
n'en est pas une. La vraie : des arènes qui **supposent un état du monde qu'elles n'établissent pas**,
dans une sauvegarde que les douze partagent.

### Les six défauts

| Arène | Ce qui n'allait pas |
|---|---|
| `pack` | prenait « la première » meute de `packManager().all()` — un `Long2ObjectOpenHashMap`, donc l'ordre de hachage. Elle mesurait une meute sauvage prise au hasard. |
| `mech` | ne forçait aucune option, et la config porte `fleeEnabled=false` : le zombie n'entrait jamais en FLEEING, le cri de détresse était **impossible**. |
| `mech` (2) | vanilla efface `lastHurtByMob` après ~100 ticks pour une fenêtre de 400 ; ensuite le repli est « le joueur le plus proche », et un serveur headless n'en a aucun. |
| `plague` | exige que le compteur `tracked` tombe à zéro, mais `CONTAM` est une attache **persistante** : une victime d'un run passé revient suivie au chargement du chunk. |
| toutes | **le tirage bébé.** `EntityType.ZOMBIE.spawn` passe par `finalizeSpawn`, ~5 % des zombies naissent bébés, et `blockBabyZombies` les jette à l'ENTITY_LOAD — qui se déclenche **à l'intérieur** de `spawn()`. L'appel rend une entité non-nulle déjà supprimée. |
| `shade` | l'aire A a besoin de **pluie** (c'est son mécanisme) et la force pour 24000 ticks ; l'aire B enchaîne dans la même fenêtre avec le **soleil** pour menace. Personne ne coupait la pluie. |

Le tirage bébé est le plus instructif. Une rangée de cinq zombies en perd un environ une fois sur
quatre — exactement la fréquence à laquelle `presence/zombies-approach` signalait « jamais les 5
vivants » et `statue` ne trouvait « aucun probe dans l'arène ». C'est aussi ce que la sonde `pack`
décrit depuis toujours comme « ce monde perd par intermittence un ou deux zombies d'une rangée » :
pas un monde capricieux, un dé à 5 % rencontrant une option activée par défaut. `ArenaBuilder` expose
désormais un `spawnZombie` qui retente, et les onze sites de spawn l'utilisent.

### Deux expériences pour établir la cause commune

- **Monde vierge** : pire, pas mieux. `special` tombe à 1/8 — les spéciaux exigent une phase minimale
  (`specialSplitterPhase = 11`) et un monde neuf démarre à 0. Le harness dépend de l'état accumulé de
  Greenfield, pas seulement de sa géographie.
- **Greenfield nettoyé, restauré à l'identique avant chaque suite** (les reflinks btrfs rendent la
  copie de 2,2 Go quasi gratuite : 0,4 s) : `special`, `statue`, `breach` et `presence` repassent.

### Où on en est

**11 suites sur 12, systématiquement**, et 12/12 quand `shade` tombe du bon côté de son tirage. Les
onze autres sont désormais stables — c'était l'objectif, et il tient campagne après campagne.

### `shade/reaches-shelter` — ouvert, mesuré à ~2/3 de réussite

**Deux tentatives de correction, toutes deux révoquées après mesure.** Elles valent d'être racontées :

- **Couper la pluie de l'aire A avant l'aire B.** Raisonnement : B a le soleil pour menace, la pluie
  n'a rien à y faire. Faux. Le log montre que sous la pluie le zombie **ne brûle pas**
  (`onFire=false`) et cherche son abri quand même — la recherche est pilotée par l'exposition, pas par
  les dégâts. Ciel dégagé, il prend feu : −9 PV en 120 ticks, il lâche sa cible et erre. Le check est
  passé de « passe » à 1/5 sur ce seul changement.
- **Élargir la fenêtre B de 400 à 900 ticks.** Il a fini **au-delà** de l'abri (x=157 alors que le
  toit couvre z 458-462). Plus de temps ne le fait pas converger.

**Une erreur de protocole de ma part, corrigée :** j'ai d'abord mesuré un taux de 0/4 avec un script
qui ne restaurait pas le monde entre les runs. Ce n'était pas le check, c'était mon monde qui se
dégradait. Avec la restauration, la mesure honnête est **2 réussites sur 3**.

Ce qu'on sait donc : la recherche s'engage à chaque fois (`seek latched=true`), le zombie marche, et
environ une fois sur trois il n'entre pas sous le toit — position finale différente à chaque échec
(x=151, 153, 155, 157). Le diagnostic le montre en état `PURSUING_PLAYER`, occupé à poursuivre plutôt
qu'à se mettre à l'abri.

Aller plus loin demande d'entrer dans le comportement de la recherche d'ombre du mod, pas dans
l'arène. Continuer à ajuster des constantes jusqu'au vert reviendrait à régler le test plutôt que le
problème — et rien ne permet d'exclure que ce check signale quelque chose de réel.

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
