# Suivi de la campagne de tests — 24 août 2026

Règle d'or de cette campagne : **on ne corrige rien**. Les agents écrivent et exécutent
uniquement des tests. Tout défaut découvert dans le mod est consigné plus bas, avec ses
preuves, et attend ta décision.

Point de départ : 519 vérifications vertes sur 519 (52 suites serveur + 300 tests unitaires),
matrice exhaustive d'hier exécutée à ~90 %. Ce qui manque : les vérifications visuelles côté
client (~75 cas jamais écrits), 10 tests unitaires d'interface, 3 ajouts aux suites serveur,
et le registre des cas volontairement non couverts.

## Phases

- [x] Phase 0 — suppression de `.runners` (1,1 Go) et de `RAPPORT-VERIFICATION.md`
- [x] Phase 0 — point de sauvegarde git `39f55f0` de l'état vert d'aujourd'hui
- [x] Phase 1 — vague 1 : 5 agents en parallèle — terminée
- [ ] Phase 2 — vague 2 : classes gametest par domaine — en cours (6 agents)
- [ ] Phase 3 — intégration : enregistrement des classes, première exécution client headless
- [ ] Phase 4 — étalonnage des seuils de pixels sur la 9060 XT
- [ ] Phase 5 — campagne complète : `./gradlew test` puis `scripts/verify-all.sh`
- [ ] Phase 6 — registre `00-uncovered.md`, bilan final

## Agents

| Agent | Domaine | État |
|---|---|---|
| A1 | 10 tests unitaires interface (annexe 13) → `mod/src/test/.../client/` | terminé : 18 verts / 1 rouge voulu |
| A2 | suite statue + pièce jointe sommeil | terminé, compilation verte |
| A3 | suite special + montée du ventre Bomber | terminé, compilation verte |
| A4 | suite plague + pas d'icône au stade latent | terminé, compilation verte |
| A5 | aides communes PixelMetrics + PoolProbe | terminé, compilation verte |
| B3 | HUD teinté (3 cas) + enderman (2 cas) | fichiers créés, compilés |
| B4 | culling (3 cas) + config client (2 cas) | fichiers créés, compilés |
| B2 | flou de peste (10 cas) | relancé |
| B5 | écran de configuration (11 cas) | relancé |
| B6 | annexe 12 : inventaire GUI, porte SetConfig, préavis d'arrivée | lancé |
| B7 | cas dispersés annexes 01+02+03+04 (8 cas) | lancé |
| B8a | cas dispersés annexes 06+07+09+14 (8 cas) | lancé |
| B8b | cas dispersés annexes 08+10+11 (10 cas) | lancé |

État des tests unitaires après vague 1 : 321 tests, 320 verts, 1 rouge volontaire
(`ClientMixinDocRefTest`), 1 ignoré préexistant (banc de performance).

## Défauts trouvés dans le mod (consignés, non corrigés)

1. `PresentationalMixinNotes` citée 8 fois dans les commentaires des mixins client
   (EndermanParticleMixin, GuiContaminationHudMixin, PlayerModelZombieArmsMixin,
   ZombieBellyModelMixin, ZombieSleepArmsMixin, AvatarRendererHallucinationMixin,
   LivingEntityRendererMixin, EntityRendererMixin) et n'existe nulle part.
2. `LethalBreedClientConfig.sanitize()` (ligne 86) laisse passer `-0.0` :
   `-0.0 < 0` est faux. Sans gravité (rayon nul, comme un 0 légitime), mais l'annexe
   voulait un clamp à 96.0 pour `-0.0`.
3. `ContaminationManager.plagueLevel()` renvoie 0 tant que la victime n'est pas
   symptomatique alors que le niveau interne vaut déjà 1 — sémantique piégeuse,
   exactement ce que le nouveau check verrouille.
4. `SymptomEffects.apply` soigne silencieusement toute victime symptomatique dont
   l'icône a disparu : tout chemin qui retire les effets autrement que lait ou
   `/effect clear` termine la peste sans aucun journal.
5. Suite `special` : la fusée Bomber épinglée min=max rend `BomberBlast.ratioOf`
   dégénéré (puissance minimale garantie) — effet de bord du pin, le check splatter
   existant mesure désormais le minimum, plus le hasard.
6. Suite `special` n'épingle pas `tickBuckets` (défaut 5) : la charge du Bomber
   n'est écrite qu'une activation sur cinq, valeurs répétées entre écritures.
7. `SpecialTestEvaluator.RAMP_SAMPLES` champ statique non remis à zéro : un second
   lancement de la suite dans la même JVM accumulerait les échantillons.
8. Caractères non ASCII préexistants dans `SpecialTestArena.java` lignes 114 et 147.

## Décisions en attente

- Défaux n° 1 : recréer le fichier documentaire ou corriger les huit commentaires ?
  Le test reste rouge jusqu'à trancher.
- Défauts n° 3, 4, 5, 6, 7 : comportements observés, aucune correction demandée.

## 30 août 2026 : corrections d'organisation, et première exécution complète de la matrice

Branche `audit-corrections`, sept commits. Aucun changement de comportement du jeu : cinq imports
qui ne servaient qu'un lien de documentation supprimés, la comptabilité de la montée sortie de
`PillarColumn` dans une classe testable sans Minecraft, les verdicts des deux plus longs harnais
sortis dans `SymptomVerdicts` et `EpisodeVerdicts`, et quatre nouveaux garde-fous : `PackageEdgeTest`
(les liens entre paquets ne peuvent plus qu'être retirés), `ConfigWriteDisciplineTest` (le code livré
ne peut plus écrire un réglage à la main), `DevBudgetTest` (les harnais plafonnés à 300 lignes avec
une liste d'exceptions qui se vide), `ClimbProgressTest` et `PackLifecycleTest`. Tests unitaires :
338, 1 rouge, celui voulu, 1 ignoré.

Nouveau : `mod/scripts/verify-parallel.sh` lance la matrice sur N serveurs à la fois, chacun dans sa
copie du dossier de jeu et sur son propre port. La matrice complète, 50 suites plus les quatre
démarrages `phasesave` plus les tests client, passe de l'heure et demie séquentielle à 19 minutes.

**Mesure à connaître avant de lire un résultat parallèle.** Deux suites échouent sous huit serveurs
et réussissent seules : `descend` donne 13/15 puis 15/15, `sunshelter` 9/10 puis 10/10. Les arènes
sont cadencées au tick mais leurs fixtures courent encore après le chargeur de terrain, et huit
serveurs sur seize cœurs perdent cette course. Le script fait donc deux passes : la passe parallèle
ne désigne que des suspects, et le verdict vient de la reprise en solitaire.

### Défauts trouvés dans le mod (consignés, non corrigés)

9. Suite `pillar` : instable, pas fausse. Sept lancements ont donné 11/14, 11/14, 12/14, 12/14,
   13/14, 14/14 et 14/14, les checks rouges changeant d'un run à l'autre (`support-under-feet`,
   `single-column`, `reach-zone-refuses`, `only-when-stuck`). Le compte de cellules de terre varie
   aussi, ce qui pointe vers l'arène et non vers le code de grimpe. Vérifié deux fois en remettant
   l'arbre entier à `master` : elle échoue pareil, la refonte du 30 août n'y est pour rien. Seule au
   calme, la dernière exécution donne 14/14. À stabiliser avant de pouvoir s'en servir comme preuve.
10. `packstore/dat-survives-a-restart` : 2/2 meutes reviennent, 4/4 fantômes lisibles et appariés,
    mais 22 champs sur 23 seulement sont identiques après le redémarrage. Un champ ne survit pas.
11. `mood/celebrate-expires-after-celebrateTicks` et `mood/celebrate-rolls-into-flee` : la durée de
    célébration mesurée vaut -1 tick pour un budget de 40 à 42. Un début et une fin lus au même
    instant, donc probablement la mesure et non la mécanique, mais les deux checks sont rouges.
12. `spawn/phase-zero-no-natural-monsters` : en phase 0, une arrivée hostile est comptée alors que la
    règle veut zéro. Aucun survivant, donc la culture attrape le cas, mais l'arrivée existe.
13. `spawn/variation-survives-reload-without-compounding` : zéro aller-retour réel sur trois demandés,
    un abandon après 1300 ticks à attendre que le témoin quitte la mémoire.
14. `doze/wake-delay-is-honoured` : le somnolent se réveille seul à +103 ticks avant le moindre bruit,
    un délai de réaction était déjà armé, la fenêtre de silence de 140 ticks n'est jamais observée.
15. `doze/alert-window-before-redoze` : réveillé à +23, plus aucun échantillon endormi sur 190, et
    jamais rendormi dans les 280 ticks.
16. `doze/silent-player-never-wakes` : un joueur ciblable à 6 blocs est jugé inaudible sur seulement
    51 échantillons sur 400. Ce contrôle-là n'est rouge qu'une fois sur deux.

### Décisions en attente

- Défauts n° 10 à 16 : huit contrôles rouges dans quatre suites, confirmés en reprise solitaire,
  aucun ne touche le code modifié le 30 août. Ils sont apparus parce que la matrice complète tourne
  pour la première fois, pas parce que quelque chose a régressé.
- Défaut n° 9 : la suite `pillar` est à stabiliser. Tant qu'elle varie, elle ne prouve rien, ni dans
  un sens ni dans l'autre.

Dernière exécution complète, 30 août 20h00 à 20h48 : 50 suites, quatre démarrages `phasesave`,
tests client. Tout vert sauf `packstore`, `mood`, `spawn` et `doze`, soit huit contrôles.
