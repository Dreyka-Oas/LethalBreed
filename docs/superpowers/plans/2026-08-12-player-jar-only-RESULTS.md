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

## 6. Les deux échecs de harness — trouvés et corrigés

Les 12 suites relancées le 13/08 donnaient 10 vertes et 2 en échec, `mech/flee-rally` et
`pack/marche-cohesion`. Ils sont corrigés. **Aucun code livré n'est en cause : les deux défauts
étaient dans les arènes de test elles-mêmes**, et les deux correctifs vivent dans `src/dev`.

### `pack` — la sonde suivait la mauvaise meute

`PackMarchProbe` prenait sa meute avec `packManager().all()` puis « la première ». Le stockage est un
`Long2ObjectOpenHashMap` : « première » veut dire ordre de hachage. Or l'overworld contient des meutes
que l'arène n'a pas construites — 41 zombies suivis au stage 0 pour 12 générés, formation activée. La
sonde attrapait donc une meute sauvage au hasard, lui forçait sa destination à travers la carte, puis
mesurait les membres de l'arène contre le centre de cette meute-là.

C'est toute l'explication des chiffres absurdes. La distance de départ devrait être constante, environ
120 blocs — la destination est à `CX + 120` et la meute se forme en `CX`. Les runs rapportaient 313,
646 et 745. Et un « écart max » de 728 blocs n'a jamais été une meute qui se disperse : c'était la
distance entre cette arène et la meute de quelqu'un d'autre.

La sonde résout maintenant sa meute par le `packId` d'un de ses propres membres, via
`PackManager.get(long)`.

### `mech` — le scénario était impossible, pas instable

`buildFleeRally` ne forçait aucune option, contrairement à `buildContamination` qui en épingle dix. Or
la config du run porte `fleeEnabled: false`, et `ZombieMood` fait
`fleeThreat = fleeEnabled ? flightThreat(...) : null`. Le zombie n'entrait donc **jamais** en FLEEING
et le cri de détresse ne pouvait pas partir. `distressScreams=0` n'était pas un aléa mais une
certitude — le message du check disait déjà que sa prémisse était fausse.

Un second défaut se cachait derrière : vanilla efface `lastHurtByMob` après ~100 ticks alors que la
fenêtre du test en fait 400. Passé ce délai `currentThreat` renvoie null, et le repli est « le joueur
le plus proche » — un serveur headless n'en a aucun. Même avec `fleeEnabled`, le cri n'aurait été
possible que dans les 100 premiers ticks.

L'arène épingle désormais les cinq options dont elle dépend, et rafraîchit la mémoire d'agresseur à
chaque tick de la fenêtre.

### Vérification

Cinq exécutions de chaque suite après correction : **10/10 PASS**.

| | avant | après (5 runs) |
|---|---|---|
| `flee-rally` cris de détresse | 0 (toujours) | 1 à 2 |
| `pack` distance de départ | 313 · 646 · 745 | **127,50 constant** |
| `pack` écart max au centre | 199 · 561 · 728 | 20,9 à 27,8 (seuil 40) |

La distance de départ enfin constante est la preuve directe que la sonde verrouille la bonne meute.

**Ce que ça corrige aussi dans ce document :** la section précédente parlait de checks « instables ».
C'était encore inexact. `flee-rally` échouait de façon déterministe ; seul `pack` variait, et sa
variation était le tirage aléatoire d'une meute, pas du bruit de mesure.

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
