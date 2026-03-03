# Lethal Breed - L'Évolution du Prédateur

<p align="left">
  <img src="https://img.shields.io/badge/Status-BETA-orange?style=for-the-badge&logo=minecraft" alt="Status Beta">
  <img src="https://img.shields.io/badge/Version-1.21.11-brightgreen?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/Loader-Fabric-blue?style=for-the-badge" alt="Fabric">
</p>

> [!IMPORTANT]
> **Ce mod est actuellement en phase de test actif.** Des bugs mineurs peuvent survenir. Vos retours sont essentiels pour affiner l'IA !

---

> "Les zombies ne sont plus des sacs de chair sans cervelle. Ils sont maintenant des chasseurs intelligents, adaptatifs et implacables."

---

## ![Capacités](https://img.shields.io/badge/Capacit%C3%A9s-Avanc%C3%A9es-darkgreen?style=flat-square)

<details>
<summary><b>Détails des mécaniques d'IA</b></summary>

### Système d'Ouïe Dynamique
Les zombies ne se contentent plus de vous voir. Ils vous **entendent**.
*   **Réaction aux sons :** Vos bruits de pas, les blocs cassés ou les explosions attirent leur attention.
*   **Traque persistante :** Une fois un son localisé, ils enquêteront sur la zone même s'ils perdent le contact visuel.

### Ingénierie de Meute
Aucune base n'est sûre. Les zombies s'adaptent dynamiquement au terrain :
*   **Ponts Improvisés (Bridging) :** S'ils font face à un vide, ils poseront des blocs de **terre** pour traverser.
*   **Échafaudage (Scaffolding) :** Ils peuvent s'empiler et poser de la **terre** sous leurs pieds pour monter.
*   **Minage Tactique :** Ils analysent les obstructions et creusent des tunnels intelligents vers vous.
*   **Adultes Rampants (Crawling) :** Les bébés zombies ont disparu. À la place, vous ferez face à des adultes capables de ramper dans les passages de 1 bloc de haut.

### États Comportementaux (FSM)
Chaque zombie suit une logique de survie et de traque :
*   **Chase :** Poursuite agressive et escalade agile.
*   **Appel de Détresse (Panic) :** Un zombie blessé hurlera, envoyant sa position exacte à tous les alliés dans un rayon de 12 blocs. Ces derniers convergeront vers lui pour former une meute punitive.
*   **Pillage :** Chaque zombie peut ramasser et utiliser votre équipement. Mourir face à eux signifie souvent qu'ils porteront votre propre armure contre vous.

### Performance & Multithreading
Lethal Breed déporte les calculs lourds (scans de blocs, IA de construction) sur des threads séparés. Des centaines de zombies intelligents peuvent coexister sans impacter la fluidité (TPS) du serveur.
</details>

---

## ![Variantes](https://img.shields.io/badge/Variantes-Sp%C3%A9ciales-red?style=flat-square)

<details>
<summary><b>Détails des Mutants et Kamikazes</b></summary>

### Variabilité Biologique
Le système de statistiques rend chaque individu unique et imprévisible :
*   **Corrélation Taille/Puissance :** La taille d'un zombie (Scale) multiplie directement sa **Santé Maximale** et ses **Dégâts d'Attaque**. Plus ils sont grands, plus ils sont résistants et mortels.
*   **Agilité variable :** Les petits spécimens sont souvent plus rapides, compensant leur fragilité par une vitesse frénétique.

### Le Mutant (Boss Rare)
Il y a 5% de chance (configurable) qu'un zombie devienne un **Mutant**.
*   **Aura Sombre :** Reconnaissable à son aura de particules d'encre.
*   **Dernier Souffle :** À sa mort, le Mutant explose en libérant un essaim de serviteurs (minions) pour venger sa chute.
*   **Stats Boostées :** Plus grand, plus fort et beaucoup plus résistant.

### Le Kamikaze
Certains zombies portent une charge explosive instable (reconnaissables au bloc de **TNT** sur leur tête).
*   **Détonation Scalable :** La puissance de l'explosion dépend de la taille du zombie. Plus il est gros, plus le cratère sera immense.
*   **Avertissement Visuel :** Des étincelles électriques et des flammes apparaissent juste avant l'explosion. Fuyez.
</details>

---

## ![Configuration](https://img.shields.io/badge/Guide-Configuration-blue?style=flat-square)

<details>
<summary><b>Paramètres complets (lethalbreed.json)</b></summary>

Le fichier se trouve dans `config/o.a.s/lethalbreed.json`.

### attributes : Statistiques Physiques
*   `zombieFollowRange` (Standard: 18.0) : Distance à laquelle un zombie vous poursuit.
*   `minScale` / `maxScale` (0.85 / 1.35) : Plage de taille aléatoire des zombies.
*   `minSpeed` / `maxSpeed` (0.18 / 0.28) : Vitesse de déplacement au sol.
*   `healthBonusMin` / `healthBonusMax` (0.8 / 1.2) : Multiplicateur de santé aléatoire.

### mutant : Paramètres du Boss
*   `mutantChance` (0.05) : Probabilité qu'un zombie apparaisse en tant que Mutant.
*   `mutantMinionCount` (8) : Nombre de zombies invoqués à la mort du Mutant.
*   `mutantTentacleTickRate` (5) : Fréquence d'apparition des particules d'aura.

### equipment : Chances d'Équipement
*   `kamikazeChance` (0.05) : Probabilité qu'un zombie soit un kamikaze.
*   `weaponChance` (0.7) : Chance d'avoir une arme en main.
*   `weaponEnchantChance` (0.4) : Chance que l'arme soit enchantée.
*   `armor[Head/Chest/Legs/Feet]Chance` : Probabilités pour chaque pièce d'armure.
*   `armorEnchantChance` (0.3) : Chance que l'armure soit enchantée.

### ai : Intelligence & Explosions
*   `hearingRange` (16.0) : Rayon d'écoute des bruits.
*   `soundLockTicks` (300) : Durée pendant laquelle un zombie se souvient d'un son.
*   `kamikazeFuseTicks` (40) : Temps avant l'explosion du kamikaze.
*   `kamikazeExplosionPower` (3.0) : Puissance de base de la détonation.

### panic : Comportement de Meute
*   `healthThreshold` (0.25) : PV restants pour déclencher la panique (25%).
*   `continueHealthThreshold` (0.5) : PV pour arrêter de paniquer (après régénération).
*   `screamIntervalTicks` (40) : Fréquence des cris d'alerte.
*   `allyAlertRange` (12.0) : Rayon d'appel des alliés.
*   `stopPackSize` (5) : Nombre de zombies pour stopper la fuite et contre-attaquer.
*   `cooldownTicks` (600) : Temps avant de pouvoir paniquer à nouveau.
*   `fleeExplosionRange` (8.0) : Distance de fuite face aux explosions.

### movement : Escalade & Pose de blocs
*   `climbVerticalSpeed` (0.25) : Vitesse de montée aux murs.
*   `climbHorizontalSpeed` (0.15) : Vitesse de déplacement sur les parois.
*   `buildGlobalCooldownTicks` (4) : Temps entre chaque pose de bloc par le groupe.

### breaking : Destruction de Blocs
*   `breakSpeedMultiplier` (4.0) : Multiplicateur de vitesse de minage.
*   `breakMinTicks` (5) : Temps minimum pour casser un bloc (même très fragile).

> *Conseil : Toutes ces options sont modifiables en jeu avec **Mod Menu** !*
</details>

---

## ![Recommandations](https://img.shields.io/badge/D%C3%A9pendances-Recommandations-purple?style=flat-square)

### Obligatoires
*   [Fabric API](https://modrinth.com/mod/fabric-api)
*   Java 25 (GraalVM recommandé)

### Recommandations O.A.S
*   **Mod Menu :** Pour accéder à la configuration facilement.
*   **Sodium :** Pour des performances optimales.
*   **Sounds :** Complète parfaitement notre système d'ouïe.

---

## Licence & Crédits
*   **Développeur :** O.A.S (Optimization & Quality)
*   **Licence :** O.A.S - MS-RSL (Microsoft Reference Source License)
*   **Modpacks :** Vous êtes **libre** d'inclure Lethal Breed dans n'importe quel modpack, tant que le crédit est maintenu.
*   **Inspiration :** Les films de zombies classiques et les jeux de survie tactiques.

---
![O.A.S Badge](https://img.shields.io/badge/O.A.S-Certified-008800?style=for-the-badge)
