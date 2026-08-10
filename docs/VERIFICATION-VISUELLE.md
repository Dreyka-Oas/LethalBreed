# Vérification visuelle — ce qui reste à faire à l'œil

Tout le reste est automatisé. Ce document ne couvre que ce qu'aucun rig headless ne peut
atteindre : des pixels à l'écran et de la VRAM.

Compte à faire : **~10 minutes**, une seule session, un seul monde.

## Lancer

```bash
cd mod
./gradlew runClient
```

Un JDK 21 doit être sélectionnable par Gradle. S'il n'est pas trouvé automatiquement, pointe
`org.gradle.java.home` dans **ton** `~/.gradle/gradle.properties` personnel — jamais dans un
fichier versionné du dépôt (voir `mod/gradle.properties`, audit #4).

Monde superflat, triche activée. Puis `/gamemode creative`.

> **Piège** : `/lethaldev` vise l'entité que tu **regardes** dans un rayon de 24 blocs, et ne
> retombe sur toi qu'à défaut. Regarde le ciel vide avant chaque commande, sinon tu contamines
> un zombie voisin sans t'en rendre compte.
>
> Les commandes du plan d'origine sont périmées : c'est `/lethalphase 1` (pas `set 1`),
> `/lethaldev contaminate` (pas `@s`), et `/lethalspawn minecraft:zombie 5` (l'entité est
> obligatoire).

---

## 1. Le flou de contamination et la VRAM (~5 min)

C'est le seul protocole vraiment bloquant qui reste.

Lance un moniteur VRAM dans un autre terminal avant de commencer :
`watch -n1 cat /sys/class/drm/card1/device/mem_info_vram_used`

> Réserve honnête : ce GPU est un iGPU Cezanne. L'essentiel des grosses allocations part en GTT
> (mémoire système), pas dans l'ouverture VRAM de 512 Mo qui est déjà à ~91 % au repos. Le signal
> de plateau risque d'être noyé dans le bruit. Si le chiffre ne dit rien de net, ce n'est pas un
> échec — c'est une mesure non concluante, et il faut le dire comme ça.

```
/gamemode survival
/lethaldev contaminate
/lethaldev symptoms
/lethaldev level 5
```

| À observer | PASS | FAIL |
|---|---|---|
| Le flou radial s'affiche | visible | écran net → le `finally` a cassé l'effet |
| Il se resserre avec le niveau | oui | non |
| **Redimensionne la fenêtre 5-6 fois** à des tailles nettement différentes | la VRAM plafonne | elle monte d'un cran par taille et ne redescend jamais |
| `/lethaldev cure` | le flou s'arrête | — |
| Retour au menu principal | la VRAM retombe (c'est `releasePool()`) | inchangée après 10 s |
| Re-rentrer dans un monde, refaire contaminate+symptoms+level 5 | le flou remarche | **écran noir ou crash → revert `61b7460`** |

**Un échec ici veut dire revert, pas petit correctif** : le pool est fermé trop tôt, il n'y a pas
de rustine sûre.

## 2. Les bras baissés des dormeurs (~3 min)

Jamais vérifié visuellement — le commit d'origine le dit lui-même.

```
/gamemode creative
/time set day
/gamerule doDaylightCycle false
/lethalphase 1
```
Construis une plateforme 5×5 avec un toit 3 blocs au-dessus, puis dessous :
```
/lethalspawn minecraft:zombie 5
```

Attends qu'ils s'assoupissent.

- **PASS** : bras le long du corps, râle d'ambiance silencieux.
- **FAIL** : bras tendus en avant comme un zombie vanilla normal.
- À vérifier aussi : réveille-en un (frappe-le). Ses bras doivent **reprendre** l'animation
  normale — la mixin met les trois axes à zéro, donc un zombie réveillé qui marche encore avec
  les bras rigides est un vrai défaut.

## 3. Le champ de navigation (~2 min, le plus intéressant)

Contexte : le champ de navigation **n'a jamais tourné**. `lastComputeTick` partait de
`Long.MIN_VALUE`, la soustraction du throttle débordait en négatif et `active()` restait `null`
en permanence. Les zombies se déplaçaient en repli vanilla depuis toujours. C'est corrigé
(`8fde16b`) et ça n'a jamais été vu en jeu.

```
/lethalphase 10
/time set night
/lethalconfig set debugLogInterval 100
/lethalspawn minecraft:zombie 60
```

Ce qui devrait changer : la horde doit converger de façon coordonnée plutôt que chaque zombie
pathfinder dans son coin. Creuse-toi un trou ou barricade-toi derrière deux blocs de terre —
ils doivent casser ou ponter pour venir (les rigs headless le prouvent déjà, mais c'est la
première fois que ça se voit avec un vrai joueur).

Surveille la ligne `PerfRecap` : le stage `flowsnap` doit maintenant afficher un coût non nul —
avant le correctif il était structurellement à zéro puisque rien n'était jamais calculé.

**À restaurer avant de quitter** : `/lethalconfig set debugLogInterval 0` et `/lethalphase 1`.

---

## Ce qui est déjà couvert — ne le refais pas

41 checks headless, 0 échec, reproductibles par
`LB_DEV_TEST=<suite> ./gradlew runServer` :

| suite | ce qu'elle prouve |
|---|---|
| `clear` | lait vs `/effect clear`, les 4 cas + le désarmement du guard + l'absence de fuite |
| `plague` | dégâts vers le bas sous config inversée, purge, non-accumulation, reprise, fuite mémoire |
| `statue` | NoAI relâché au rechargement de chunk **et** au redémarrage serveur |
| `placed` | fissuration, disparition sans drop, rétention hors-chunk, `ABANDON_FACTOR` |
| `shade` | throttle du rescan d'ombre + non-régression de l'abri atteint |
| `breach` | cassage de mur et pontage de gouffre de bout en bout |
| `climb` | escalade d'un mur de 12 en passant la fenêtre de régression |
| `compute` | champ de coût, direction, optimalité, couverture des 4 classes |
| `presence` | joueur synthétique → champ de navigation → zombies qui ciblent et approchent |
