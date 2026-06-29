# LethalConfig — guide des réglages

Chaque tableau ci-dessous = un **onglet** du menu `/lethalconfig`.
Colonne ☐ : case à cocher (remplace `☐` par `☑` quand c'est fait). Colonne 2 : nom du réglage. Colonne 3 : ce qu'il fait, expliqué simplement.

> Les onglets sont rangés dans le même ordre que dans le jeu (17 onglets, 114 réglages).

---

## Perf (vitesse du jeu)

Réglages qui aident l'ordinateur à ne pas ramer quand il y a beaucoup de zombies.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `tickBuckets` | Partage les zombies en petits groupes pour ne pas tous les bouger en même temps. Évite que le jeu rame. |
| ☐ | `spatialCellSize` | Taille des cases invisibles qui servent à trouver vite les zombies proches. |
| ☐ | `lodHigh` | Jusqu'à cette distance, les zombies réfléchissent à fond (proches du joueur). |
| ☐ | `lodMedium` | Un peu plus loin, les zombies réfléchissent moyennement. |
| ☐ | `lodLow` | Encore plus loin, les zombies réfléchissent très peu ; au-delà de cette distance ils se figent et ne bougent plus du tout. |
| ☐ | `throttleByLod` | Faire réfléchir les zombies lointains moins souvent pour gagner de la vitesse. |
| ☐ | `lodMediumTickDivisor` | Les zombies un peu loin agissent 1 fois sur ce nombre. |
| ☐ | `lodLowTickDivisor` | Les zombies très loin agissent 1 fois sur ce nombre (encore moins souvent). |

---

## Pathing (trouver le chemin)

Comment les zombies calculent la route pour venir vers toi.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `navReissueInterval` | Temps d'attente avant de recalculer le chemin (pour ne pas le refaire trop souvent). |
| ☐ | `flowRecomputeInterval` | Tous les combien de temps la grande carte des chemins est refaite. |
| ☐ | `flowMargin` | Marge de carte ajoutée autour des joueurs pour voir un peu plus loin. |
| ☐ | `flowMaxGrid` | Taille maximum de la carte des chemins, pour ne pas trop charger l'ordinateur. |
| ☐ | `flowVerticalTolerance` | Combien de blocs en haut/bas le zombie cherche un sol où marcher. |
| ☐ | `flowWaypointStep` | À quelle distance le prochain point de passage est posé sur la route. |
| ☐ | `navSpeed` | Vitesse de marche des zombies quand ils suivent le chemin. |
| ☐ | `flowBuildCost` | Effort « coûteux » de construire un pont en terre : plus c'est haut, moins ils en font. |

---

## Dev (outils des créateurs)

Boutons de test pour les développeurs. Tu peux les laisser éteints.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `debugLogInterval` | Tous les combien de temps écrire des infos de vitesse dans le journal. 0 = jamais. |
| ☐ | `devSpecialTest` | Lance un test automatique des zombies spéciaux au démarrage. |
| ☐ | `devMechTest` | Lance un test automatique des mécaniques (soleil, équipement, infection). |
| ☐ | `devSpawnRadius` | Distance autour de toi où la commande de test fait apparaître des zombies. |

---

## Compute (qui fait les calculs)

Choisir si c'est la carte graphique (GPU) ou le processeur (CPU) qui calcule les chemins.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `useGpu` | Utiliser la carte graphique pour calculer plus vite. S'il n'y en a pas, le jeu utilise le processeur tout seul. |
| ☐ | `flowCpuThreads` | Nombre de « cerveaux » du processeur utilisés s'il n'y a pas de carte graphique. 0 = automatique. |

---

## Breaking (casser et poser des blocs)

Les zombies peuvent creuser dans les murs et poser de la terre pour t'atteindre.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `flowBreakCost` | Effort de passer EN CASSANT un bloc. Plus bas = ils cassent plus volontiers. |
| ☐ | `maxBreakHeight` | Hauteur maximum de blocs qu'un zombie casse pour passer un obstacle. |
| ☐ | `breakProgressPerTick` | Vitesse à laquelle ils cassent un bloc. Plus bas = ils creusent lentement. |
| ☐ | `breakGraceTicks` | Combien de temps une cassure reste avant d'être abandonnée si on ne continue pas. |
| ☐ | `blockOpsPerTick` | Nombre de blocs cassés ou posés par instant (pour ne pas tout faire d'un coup). |
| ☐ | `blockOpsQueueCap` | Nombre maximum de blocs en attente d'être cassés/posés. |
| ☐ | `breakMaxHardness` | Au-dessus de cette dureté, le bloc est incassable (comme l'obsidienne). |
| ☐ | `placedBlockLifetimeTicks` | Combien de temps la terre posée par un zombie reste avant de disparaître. |

---

## Targeting (choisir sa cible)

Comment les zombies repèrent qui poursuivre.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `targetCreativePlayers` | Poursuivre aussi les joueurs en mode créatif/spectateur. Normalement non. |
| ☐ | `targetDetectRadius` | Distance à laquelle un zombie repère une cible. Plus grand = il voit de plus loin. |
| ☐ | `requireLineOfSight` | Le zombie doit VOIR la cible (un mur opaque la cache ; le verre non). |
| ☐ | `attackAllTargets` | Les zombies frappent vraiment leur cible, pas seulement la suivre. |
| ☐ | `forceNearestTarget` | Toujours viser la créature la plus proche au lieu de s'accrocher au joueur. |
| ☐ | `targetMemoryTicks` | Combien de temps un zombie continue vers le dernier endroit vu d'une cible perdue. |

---

## Climb (grimper, descendre, sauter)

Tout ce qui sert au zombie à monter sur les murs et descendre sans se blesser.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `climbThreshold` | Hauteur au-dessus de lui à partir de laquelle le zombie construit un escalier. |
| ☐ | `climbHorizRadius` | Distance d'un mur à laquelle il commence à grimper vers une cible en haut. |
| ☐ | `maxClimbHeight` | Hauteur maximum qu'il grimpe avant d'abandonner un mur. |
| ☐ | `wallClimbSpeed` | Vitesse de montée quand il escalade un mur (comme une échelle). |
| ☐ | `climbGiveUpCooldown` | Temps d'attente avant de réessayer un mur qu'il n'a pas réussi à franchir. |
| ☐ | `pillarMaxHeight` | Hauteur maximum d'une tour qu'il construit sous lui pour monter. |
| ☐ | `pillarJumpPower` | Force du saut pour poser un bloc et monter d'un étage. |
| ☐ | `climbCooldown` | Temps d'attente entre deux blocs posés en montant (plus = montée plus lente). |
| ☐ | `stuckActivations` | Combien de fois il faut être bloqué avant de casser/construire/sauter. |
| ☐ | `climbJumpMaxAge` | Durée maximum d'un saut pour prendre de la hauteur avant d'abandonner. |
| ☐ | `climbJumpVelocity` | Force du saut quand il monte une tour (≈ saut normal d'un joueur). |
| ☐ | `descendThreshold` | Profondeur sous lui à partir de laquelle il creuse pour descendre. |
| ☐ | `safeDropBlocks` | Hauteur de chute sans danger : il saute au lieu de creuser un escalier. |
| ☐ | `preventFallDamage` | Empêcher les zombies de se blesser en tombant. Normalement non. |
| ☐ | `meleeStopRange` | Distance à laquelle il est « arrivé » et arrête de creuser pour frapper. |
| ☐ | `meleeStopHeight` | Écart de hauteur maximum pour considérer qu'il peut frapper la cible. |
| ☐ | `devClimbTest` | Test des créateurs : construit un mur + cible en haut pour voir s'ils grimpent. |
| ☐ | `debugClimb` | Écrire dans le journal ce que chaque zombie fait pour grimper. |

---

## World (règles du monde)

Réglages sur le jour, la météo et la cohabitation avec d'autres mods.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `suppressVanillaWander` | Enlever la balade au hasard des zombies. Risqué : à tester avant. |
| ☐ | `failOnAiConflict` | Arrêter le jeu si un autre mod essaie aussi de commander les zombies. |
| ☐ | `forceDayTime` | Forcer le monde à rester en plein jour. |
| ☐ | `forcedDayTime` | Quelle heure garder (6000 = midi). |
| ☐ | `clearWeather` | Garder le ciel dégagé, sans pluie ni orage. |

---

## Sound (entendre)

Les zombies peuvent t'entendre même sans te voir.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `soundEnabled` | Grand bouton pour activer l'ouïe des zombies. |
| ☐ | `soundBaseRadius` | Distance à laquelle ils entendent un bruit normal. |
| ☐ | `soundLoudMultiplier` | Les gros bruits (casser un bloc) s'entendent ce nombre de fois plus loin. |
| ☐ | `soundMoveThreshold` | À partir de quelle vitesse tes pas font du bruit. |
| ☐ | `soundArriveDistance` | Distance à laquelle un zombie pense être arrivé à la source du bruit. |

---

## Variation (chaque zombie est différent)

Donne à chaque zombie une taille et une force un peu différentes.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `enableVariation` | Activer les petites différences entre zombies (taille, vitesse, force, saut). |
| ☐ | `varScaleMin` | Taille minimum d'un zombie (petit). |
| ☐ | `varScaleMax` | Taille maximum d'un zombie (grand). |
| ☐ | `varSpeedMin` | Vitesse minimum d'un zombie (lent). |
| ☐ | `varSpeedMax` | Vitesse maximum d'un zombie (rapide). |
| ☐ | `varDamageMin` | Force de frappe minimum. |
| ☐ | `varDamageMax` | Force de frappe maximum. |
| ☐ | `varLeapMin` | Puissance de saut minimum. |
| ☐ | `varLeapMax` | Puissance de saut maximum. |

---

## Effects (pouvoirs spéciaux)

Certains zombies naissent avec un petit pouvoir bonus pour toute leur vie.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `randomEffectEnabled` | Activer les pouvoirs bonus au hasard sur certains zombies. |
| ☐ | `randomEffectChance` | Part des zombies qui ont un pouvoir (0.25 = environ 1 sur 4). |
| ☐ | `randomEffectMaxAmplifier` | Force maximum du pouvoir (2 = jusqu'au niveau III). |
| ☐ | `leapEffectPerLevel` | Pouvoir de saut : distance de bond gagnée par niveau (0.35 = +35%). |

---

## Spawn (qui apparaît)

Filtre quels zombies apparaissent et comment.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `blockBabyZombies` | Supprimer les bébés zombies. |
| ☐ | `blockDrowned` | Supprimer les zombies noyés (garder seulement les zombies normaux). |
| ☐ | `stripZombieEquipment` | Enlever les armes et armures des zombies. Si non : ils gardent leur matériel. |
| ☐ | `forceAllZombiesSunBurn` | Faire brûler tous les zombies au soleil (sauf protégés par casque/eau/résistance au feu). |

---

## Leap (bondir)

Les zombies sautent parfois sur toi pour t'attraper.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `leapEnabled` | Activer les bonds des zombies vers leur cible. |
| ☐ | `leapCooldownActivations` | Temps d'attente entre deux bonds possibles. |
| ☐ | `leapChance` | Chance de bondir quand c'est possible. |
| ☐ | `leapMinRange` | Distance minimum de la cible pour bondir (pas trop près). |
| ☐ | `leapMaxRange` | Distance maximum de la cible pour bondir (pas trop loin). |
| ☐ | `leapHorizontalSpeed` | Vitesse du bond vers l'avant. |
| ☐ | `leapUpward` | Hauteur du bond vers le haut. |

---

## Water (dans l'eau)

Comment les zombies se débrouillent dans l'eau.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `floatInWater` | Faire flotter et nager les zombies au lieu de couler, pour traverser l'eau. |
| ☐ | `waterRiseSpeed` | Vitesse pour remonter à la surface quand ils sont sous l'eau. |
| ☐ | `waterDiveSpeed` | Vitesse pour plonger vers une cible qui est en-dessous. |
| ☐ | `waterSwimSpeed` | Vitesse de nage vers la cible dans l'eau. |

---

## Phases (difficulté qui monte)

Le danger augmente avec le temps, en 15 phases de plus en plus dures.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `phaseSystemEnabled` | Activer les 15 phases qui rendent les zombies plus forts avec le temps. |
| ☐ | `phaseIntervalTicks` | Temps entre chaque montée de phase (12000 = 10 minutes). |
| ☐ | `phaseJitterTicks` | Petit hasard ajouté ou enlevé au temps de chaque phase. |
| ☐ | `phaseGearDropChance` | Chance qu'un objet équipé tombe quand le zombie meurt. |

---

## Specials (zombies spéciaux)

Des zombies rares avec des capacités spéciales (sprinteur, cracheur, nécromancien…).

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `specialEnabled` | Activer les zombies spéciaux. |
| ☐ | `specialBaseChance` | Chance de base qu'un zombie soit spécial. |
| ☐ | `specialPhaseScale` | Chance en plus à chaque phase (+1.5% par phase). |
| ☐ | `specialMaxChance` | Chance maximum qu'un zombie soit spécial. |
| ☐ | `specialShowName` | Afficher le nom du type spécial au-dessus du zombie. |
| ☐ | `specialActionInterval` | Temps entre deux actions spéciales d'un zombie spécial. |

---

## Contamination (la maladie)

Se faire toucher peut t'infecter ; la maladie fait mal de plus en plus jusqu'à te transformer.

| ☐ | Réglage | Ce que ça fait |
|---|---|---|
| ☐ | `contaminationEnabled` | Activer la maladie qui se transmet quand un zombie te touche. |
| ☐ | `contamBaseChance` | Chance de base d'être infecté quand on est touché. |
| ☐ | `contamPhaseScale` | Chance d'infection en plus à chaque phase. |
| ☐ | `contamMaxChance` | Chance maximum d'être infecté. |
| ☐ | `contamDamageBase` | Dégâts au tout début de l'infection (1.0 = un demi-cœur). |
| ☐ | `contamDamageRamp` | Les dégâts augmentent un peu à chaque instant qui passe. |
| ☐ | `contamDamageCap` | Dégâts maximum par coup de maladie. |
| ☐ | `contamDamageInterval` | Temps entre deux coups de maladie. |
| ☐ | `contamHungerInterval` | Temps entre deux pertes de faim à cause de la maladie. |
| ☐ | `contamCureCheckTicks` | Temps entre deux chances de guérir (seulement accroupi). |
| ☐ | `contamCureMinPct` | Chance minimum de guérir à chaque essai (toute petite). |
| ☐ | `contamCureMaxPct` | Chance maximum de guérir à chaque essai. |
