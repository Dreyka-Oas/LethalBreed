# Configuration du Mod

Le mod **Lethal Breed** est entièrement personnalisable via un fichier JSON situé dans le dossier de configuration.

**Chemin :** `.minecraft/config/o.a.s/lethalbreed.json`

---

### 📊 attributes : Statistiques Physiques
- **`zombieFollowRange`** (18.0) : Distance de détection et de poursuite.
- **`minScale` / `maxScale`** (0.85 / 1.35) : Plage de taille aléatoire.
- **`minSpeed` / `maxSpeed`** (0.18 / 0.28) : Vitesse de déplacement au sol.
- **`healthBonusMin` / `healthBonusMax`** (0.8 / 1.2) : Multiplicateur de santé aléatoire.

---

### 🧬 mutant : Paramètres du Boss
- **`mutantChance`** (0.05) : Probabilité qu'un zombie apparaisse en tant que Mutant.
- **`mutantMinionCount`** (8) : Nombre de zombies invoqués à la mort du Mutant.
- **`mutantTentacleTickRate`** (5) : Fréquence d'apparition des particules d'aura.

---

### ⚔️ equipment : Chances d'Équipement
- **`kamikazeChance`** (0.05) : Probabilité qu'un zombie soit un kamikaze.
- **`weaponChance`** (0.7) : Chance d'avoir une arme en main.
- **`weaponEnchantChance`** (0.4) : Chance que l'arme soit enchantée.
- **`armorHeadChance`** (0.5) : Chance d'avoir un casque.
- **`armorChestChance`** (0.4) : Chance d'avoir un plastron.
- **`armorLegsChance`** (0.4) : Chance d'avoir des jambières.
- **`armorFeetChance`** (0.4) : Chance d'avoir des bottes.
- **`armorEnchantChance`** (0.3) : Chance que l'armure soit enchantée.

---

### 🔊 ai : Intelligence & Explosions
- **`hearingRange`** (16.0) : Rayon d'écoute des bruits (pas, blocs, chutes).
- **`soundLockTicks`** (300) : Durée pendant laquelle un zombie se souvient d'un son.
- **`kamikazeFuseTicks`** (40) : Temps avant l'explosion du kamikaze.
- **`kamikazeExplosionPower`** (3.0) : Puissance de base de la détonation.

---

### 📢 panic : Comportement de Meute
- **`healthThreshold`** (0.25) : PV restants pour déclencher la panique (25%).
- **`continueHealthThreshold`** (0.5) : PV pour arrêter de paniquer.
- **`screamIntervalTicks`** (40) : Fréquence des cris d'alerte.
- **`allyAlertRange`** (12.0) : Rayon d'appel des alliés.
- **`stopPackSize`** (5) : Nombre de zombies pour stopper la fuite et contre-attaquer.
- **`cooldownTicks`** (600) : Temps avant de pouvoir paniquer à nouveau.
- **`fleeExplosionRange`** (8.0) : Distance de fuite face aux explosions.

---

### 🏃 movement : Escalade & Pose de blocs
- **`climbVerticalSpeed`** (0.25) : Vitesse de montée aux murs.
- **`climbHorizontalSpeed`** (0.15) : Vitesse de déplacement sur les parois.
- **`buildGlobalCooldownTicks`** (4) : Temps entre chaque pose de bloc par le groupe.

---

### 🔨 breaking : Destruction de Blocs
- **`breakSpeedMultiplier`** (4.0) : Multiplicateur de vitesse de minage.
- **`breakMinTicks`** (5) : Temps minimum pour casser un bloc.
