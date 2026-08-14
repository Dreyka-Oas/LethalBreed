# Bombeur — mèche aléatoire et éclaboussure infectieuse

Branche : `chore/player-jar-only` · design validé le 2026-08-14

## 1. Le problème

Le Bombeur (`SpecialType.BOMBEUR`, débloqué phase 4, poids 7) s'amorce dès que sa cible est à 3 blocs, puis
explose en **0.83 seconde** (`charge += 0.06` par activation, détonation à 1.0). Deux défauts :

1. **Aucune fenêtre de réaction.** 0.83 s ne suffit ni à reculer, ni à se mettre à couvert, ni à poser un bloc.
2. **Fuir ne sert à rien.** Une fois `charge > 0`, `armed` reste vrai pour toujours : le Bombeur poursuit et
   détone quoi qu'il arrive. En phase 14 son multiplicateur de vitesse avoisine ×1.5, donc il court plus vite
   qu'un joueur en sprint — sortir de portée est de toute façon impossible.

Mesuré en jeu (phase 14, joueur en netherite complet, armure 20 / résistance 12) : l'explosion actuelle de
puissance 3.0 inflige **23.4 dégâts au contact** sur 20 PV — un one-shot sans avertissement utilisable.

## 2. Objectifs

- Donner une **fenêtre de réaction lisible** avant la détonation.
- Rendre la durée **aléatoire**, pour interdire le comptage mécanique des ticks.
- Garder cette plage aléatoire **identique à toutes les phases** — c'est la puissance des zombies qui monte
  avec la phase, pas le tempo de cette mécanique.
- Faire de la mèche un **pari** : attendre plus longtemps rend l'explosion plus grosse.
- Faire que **la distance compte pour autre chose que les dégâts** : reculer doit protéger les PV sans
  protéger l'état du joueur.

### Hors périmètre

- La détonation reste **inévitable** une fois amorcée. Aucun désamorçage par la fuite. Décision explicite :
  la fenêtre sert à se mettre à couvert ou à encaisser moins, pas à annuler la menace.
- `specialBombeurArmRange` (3.0 blocs) est inchangé.
- Aucune modification des autres variantes spéciales.

## 3. La mèche

Tirée **une seule fois à l'amorçage**, uniformément dans `[specialBombeurFuseMinTicks,
specialBombeurFuseMaxTicks]` = **30 à 120 ticks** (1.5 s à 6 s).

`charge` reste la progression `0 → 1` : elle est déjà synchronisée aux clients pour le gonflement du ventre,
donc le rendu existant continue de fonctionner sans y toucher. Seule la **vitesse** de remplissage change.

Conséquence gratuite et voulue : le ventre gonfle sur toute la durée de la mèche, donc **un Bombeur qui gonfle
lentement annonce visuellement une grosse explosion**. Le tell existe déjà, il devient informatif.

## 4. La puissance suit la mèche

```
ratio  = (fuseTicks - fuseMin) / (fuseMax - fuseMin)      ∈ [0, 1]
power  = powerMin + (powerMax - powerMin) × ratio          → [2.0, 5.0]
```

Mèche courte = petite explosion (peu d'avertissement, mais on survit). Mèche longue = explosion dévastatrice
(le temps de fuir existe, ne pas l'utiliser se paie plein tarif).

Dégâts au contact sur un joueur en netherite complet (20 PV) :

| Mèche | Puissance | Rayon de souffle | Dégâts bruts | Après armure |
|---|---|---|---|---|
| 1.5 s | 2.0 | 4 blocs | 29 | 12.5 |
| 3.75 s | 3.5 | 7 blocs | 50 | 30.0 |
| 6.0 s | 5.0 | 10 blocs | 71 | 54.5 |

## 5. Deux zones concentriques

C'est le cœur du design.

| Zone | Rayon | Contenu |
|---|---|---|
| **Souffle** | `power × 2` → 4 à 10 blocs | Dégâts physiques (comportement vanilla existant) |
| **Éclaboussure** | souffle × `specialBombeurSplatterMul` (1.5) → **6 à 15 blocs** | Effets de statut uniquement |

Reculer sort du souffle mais **pas de l'éclaboussure**. À 12 blocs d'un Bombeur à mèche longue on prend
0 dégât et on attrape quand même les effets. Fuir protège les PV, pas l'état.

### Intensité

```
prox      = max(0, 1 - distance / splatterRadius)          ∈ [0, 1]
intensity = prox × (0.4 + 0.6 × ratio)                     ∈ [0, 1]
```

La proximité compte toujours (facteur dominant) ; la mèche amplifie sans jamais annuler l'effet de la
distance. Une mèche courte au contact vaut 0.40 ; une mèche longue au contact vaut 1.00.

| Mèche | Distance | Puissance | Souffle | Éclaboussure | Dégâts | Intensité |
|---|---|---|---|---|---|---|
| 1.5 s | contact | 2.0 | 4 | 6 | 12.5 | 0.40 |
| 1.5 s | 3 b | 2.0 | 4 | 6 | 1.2 | 0.20 |
| 3.75 s | contact | 3.5 | 7 | 10.5 | 30.0 | 0.70 |
| 3.75 s | 4 b | 3.5 | 7 | 10.5 | 5.2 | 0.43 |
| 3.75 s | 8 b | 3.5 | 7 | 10.5 | **0** | 0.17 |
| 6.0 s | contact | 5.0 | 10 | 15 | 54.5 | 1.00 |
| 6.0 s | 6 b | 5.0 | 10 | 15 | 7.2 | 0.60 |
| 6.0 s | 12 b | 5.0 | 10 | 15 | **0** | 0.20 |
| 6.0 s | 15 b | 5.0 | 10 | 15 | 0 | 0.00 |

## 6. Les effets — « éclaboussure infectieuse »

Un Bombeur est un cadavre gonflé qui éclate : la charge utile est de la chair infectée, pas du shrapnel.

| Effet | Amplificateur | Durée | Condition |
|---|---|---|---|
| **Nausée** | 0 | `4 + 11 × intensity` s | toujours |
| **Poison** | `intensity < 0.6 ? 0 : 1` | `3 + 9 × intensity` s | toujours |
| **Lenteur** | `min(2, ⌊intensity × 3⌋)` | `3 + 9 × intensity` s | toujours |
| **Cécité** | 0 | `1 + 4 × (intensity − seuil) / (1 − seuil)` s | `intensity ≥ specialBombeurBlindThreshold` (0.75) |
| **Contamination** | — | persistante | tirage : `intensity × specialBombeurInfectChance` (0.5) |

Rendu concret :

| Intensité | Nausée | Poison | Lenteur | Cécité | Contamination |
|---|---|---|---|---|---|
| 0.15 | 5.7 s | 4.3 s A0 | 4.3 s A0 | — | 8 % |
| 0.30 | 7.3 s | 5.7 s A0 | 5.7 s A0 | — | 15 % |
| 0.50 | 9.5 s | 7.5 s A0 | 7.5 s A1 | — | 25 % |
| 0.70 | 11.7 s | 9.3 s A1 | 9.3 s A2 | — | 35 % |
| 0.85 | 13.3 s | 10.6 s A1 | 10.6 s A2 | 2.6 s | 42 % |
| 1.00 | 15.0 s | 12.0 s A1 | 12.0 s A2 | 5.0 s | 50 % |

Justification de chaque pièce :

- **Lenteur** est la vraie punition. Dans un mod de horde, être ralenti est plus dangereux que perdre des PV :
  on ne distance plus ce qui arrive derrière.
- **Poison** ne tue jamais (plancher vanilla à 1 PV) — pression sans double peine après un souffle déjà encaissé.
- **Nausée** est l'habillage : elle signale sans rien retirer au joueur mécaniquement.
- **Cécité** est réservée au haut du spectre (`intensity ≥ 0.75`, donc contact + mèche longue) : c'est la
  sanction du « tu as vraiment merdé », pas un effet de routine.
- **Contamination** réutilise `ContaminationManager.contaminate()` — la peste maison. C'est le lien thématique
  et le vrai coût à long terme d'une éclaboussure.

**Les zombies sont exclus des effets.** Ce sont les vecteurs, pas les victimes ; et `contaminate()` refuse
déjà les zombies. Ils continuent en revanche de subir les dégâts du souffle (comportement vanilla inchangé).

**Constantes de code, volontairement pas des options.** Les seuils internes de la table — le `0.6` du palier
de Poison, le `min(2, ⌊intensity × 3⌋)` de Lenteur, les coefficients de durée `4 + 11 ×`, `3 + 9 ×`,
`1 + 4 ×` — restent des constantes nommées dans `BombeurBlast`. Seuls les cinq leviers du §8 sont exposés :
la surface de config du mod compte déjà 339 options, et exposer chaque coefficient d'une courbe rendrait la
mécanique impossible à équilibrer pour un joueur. Le seuil de cécité est configurable parce qu'il décide
*si* l'effet le plus punitif apparaît ; les coefficients de forme ne décident que du *dosage*.

Si `specialBombeurBlindThreshold` vaut 1.0, `(1 − seuil)` s'annule : la cécité est alors simplement désactivée
(aucune intensité ne peut dépasser strictement 1.0), et le code doit traiter ce cas sans division par zéro.

## 7. Découpage du code

| Fichier | Changement |
|---|---|
| `special/SpecialAttachment.java` | nouveau `BOMBEUR_RATE` (`Float`, transitoire comme `BOMBEUR_CHARGE`) |
| `special/SpecialBehavior.java` | tire la mèche à l'amorçage, accumule `charge += rate` |
| `special/runtime/BombeurBlast.java` | **nouveau** — calcul pur : `rate → ratio`, `ratio → power`, `(ratio, dist) → intensity`, `intensity → effets` |
| `special/runtime/SpecialAbilities.java` | `bomb()` calcule la puissance et déclenche l'éclaboussure |
| `config/domain/SpecialVariantConfig.java` | champs de config (voir §8) |
| `config/bounds/ProgressionBounds.java` | bornes des nouveaux champs |
| `assets/lethalbreed/lang/fr_fr.json` + `en_us.json` | libellés des nouveaux champs |

`BombeurBlast` isole **tout le calcul** de l'entité et du monde : il ne prend que des nombres et rend des
nombres. C'est ce qui rend le modèle testable sans démarrer de serveur, et c'est la seule partie du design
qui contient de la logique non triviale.

L'enregistrement dans le schéma de config est **automatique** : `ConfigFields` expose par réflexion tout champ
`public static` non-`final` de la classe de domaine. Aucun câblage par option.

## 8. Config

| Retiré | Ajouté | Défaut | Bornes |
|---|---|---|---|
| `specialBombeurPower` | `specialBombeurPowerMin` | 2.0 | 0 – 100 |
| | `specialBombeurPowerMax` | 5.0 | 0 – 100 |
| `specialBombeurFusePerTick` | `specialBombeurFuseMinTicks` | 30 | 1 – 6000 |
| | `specialBombeurFuseMaxTicks` | 120 | 1 – 6000 |
| — | `specialBombeurSplatterMul` | 1.5 | 0 – 10 |
| — | `specialBombeurInfectChance` | 0.5 | 0 – 1 |
| — | `specialBombeurBlindThreshold` | 0.75 | 0 – 1 |

`specialBombeurArmRange` (3.0) est conservé inchangé.

Les deux champs retirés disparaissent aussi des deux fichiers de langue. Les fichiers de config existants qui
les portent encore verront ces clés signalées comme inconnues par le rapport de dérive — comportement normal
et déjà en place, aucune migration nécessaire.

**Invariant à faire respecter :** `powerMin ≤ powerMax` et `fuseMinTicks ≤ fuseMaxTicks`. Si un joueur inverse
les bornes, le code doit les réordonner à la lecture plutôt que produire un `ratio` négatif ou une plage vide.

## 9. Tests

**Unitaires** (`BombeurBlast`, sans serveur) :

- `ratio` vaut 0 à `fuseMin`, 1 à `fuseMax`, et croît de façon monotone entre les deux.
- `power` reste dans `[powerMin, powerMax]` pour tout `ratio` de `[0, 1]`.
- `intensity` vaut 0 exactement au bord de l'éclaboussure, et 1 seulement au contact avec `ratio = 1`.
- `intensity` décroît de façon monotone avec la distance, à mèche fixée.
- Bornes inversées (`powerMin > powerMax`, `fuseMin > fuseMax`) : réordonnées, aucune plage vide ni valeur
  hors bornes produite.
- `blindThreshold = 1.0` : aucune cécité appliquée, et aucune division par zéro.
- Les durées et amplificateurs d'effet correspondent au tableau du §6 aux points d'intensité tabulés.

**Intégration** (suite dev `special`, via `SpecialTestCase` / `SpecialTestArena` / `SpecialTestEvaluator`) :

- La mèche mesurée tombe dans `[30, 120]` ticks et **varie d'un Bombeur à l'autre** (une valeur constante sur
  N zombies est un échec — c'est précisément le bug que le design corrige).
- Une victime placée **hors du souffle mais dans l'éclaboussure** prend 0 dégât et porte bien les effets.
  C'est le check central : il vérifie les deux rayons d'un coup.
- Une victime **hors de l'éclaboussure** ne porte aucun effet.
- La contamination se déclenche sur un échantillon suffisant à intensité élevée.
- Un zombie dans l'éclaboussure ne porte aucun effet de statut issu de celle-ci.
