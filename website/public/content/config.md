# Configuration du Mod

Le mod **Lethal Breed** est entièrement personnalisable via un fichier JSON situé dans le dossier de configuration de votre instance Minecraft.

**Chemin du fichier :** `.minecraft/config/o.a.s/lethalbreed.json`

---

## 📊 Attributs des Zombies

Ces paramètres contrôlent les statistiques physiques de base générées aléatoirement pour chaque zombie.

- **`zombieFollowRange`** (Défaut : `18.0`) : Distance maximale à laquelle un zombie peut verrouiller une cible visuelle.
- **`minScale` / `maxScale`** (Défaut : `0.85` / `1.35`) : Plage de taille des zombies. Influence également la santé et les dégâts.
- **`minSpeed` / `maxSpeed`** (Défaut : `0.18` / `0.28`) : Vitesse de déplacement au sol.
- **`healthBonusMin` / `healthBonusMax`** (Défaut : `0.8` / `1.2`) : Multiplicateur de santé supplémentaire appliqué après le calcul de la taille.

---

## 🧬 Système de Mutation

Paramètres relatifs aux variantes spéciales et aux Mutants.

- **`mutantChance`** (Défaut : `0.05`) : Probabilité (0.0 à 1.0) qu'un zombie devienne un Mutant lors de son apparition.
- **`mutantMinionCount`** (Défaut : `8`) : Nombre de serviteurs qui apparaissent à la mort d'un Mutant.
- **`mutantTentacleTickRate`** (Défaut : `5`) : Fréquence de mise à jour de l'aura de particules du Mutant.

---

## 🔊 IA et Système d'Ouïe

Contrôle la sensibilité auditive et les capacités de traque.

- **`hearingRange`** (Défaut : `16.0`) : Rayon en blocs dans lequel un zombie peut entendre du bruit (pas, blocs, etc.).
- **`soundLockTicks`** (Défaut : `300`) : Durée pendant laquelle un zombie se souvient de la position d'un son avant de reprendre sa patrouille.

---

## 💣 Spécialistes Kamikazes

- **`kamikazeChance`** (Défaut : `0.05`) : Chance qu'un zombie apparaisse avec de la TNT sur la tête.
- **`kamikazeFuseTicks`** (Défaut : `40`) : Temps avant l'explosion une fois amorcé (20 ticks = 1 seconde).
- **`kamikazeExplosionPower`** (Défaut : `3.0`) : Puissance de l'explosion.

---

## 🏃 Panique et Survie

- **`panicHealthThreshold`** (Défaut : `0.25`) : Seuil de santé (25%) déclenchant l'état de panique.
- **`fleeExplosionRange`** (Défaut : `8.0`) : Distance à laquelle les zombies s'éloignent d'un allié sur le point d'exploser.

---

## 🏗️ Construction et Minage

- **`climbVerticalSpeed`** (Défaut : `0.25`) : Vitesse d'ascension lors de l'escalade ou de la construction de tours.
- **`breakSpeedMultiplier`** (Défaut : `4.0`) : Multiplicateur de vitesse de minage des blocs (par rapport à un joueur non armé).

---
Dernière mise à jour : 12 février 2026
