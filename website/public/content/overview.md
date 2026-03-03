# Lethal Breed - Aperçu Technique

## Introduction
**Lethal Breed** est un mod de survie-horreur pour Minecraft (Fabric) qui transforme radicalement le comportement des zombies. L'objectif est de faire de chaque nuit un défi stratégique où les zombies ne se contentent pas de marcher vers le joueur, mais adaptent leur approche en fonction de l'environnement.

---

## 🏗️ Ingénierie d'Obstacles (Pathfinding Avancé)

### 1. Analyse Dynamique des Obstructions
Le mod utilise une classe spécialisée, `ObstructionAnalyzer`, pour identifier précisément ce qui bloque le zombie via un balayage angulaire.

### 2. Coordination de Pontage (Bridging)
Le `BridgeCoordinator` intervient pour poser des blocs de terre et créer un passage au-dessus des vides.

### 3. Système de Pose : `BlockSetter`
Gère l'interaction physique avec le monde. Il vérifie la validité de l'emplacement avant de matérialiser un bloc.
```java
public static void placeDirt(World world, BlockPos pos) {
    if (PlacementValidator.canPlaceAt(world, pos)) {
        world.setBlockState(pos, Blocks.DIRT.getDefaultState());
    }
}
```

### 4. Destruction de Blocs : `BreakAction`
Les zombies minent les obstacles avec un multiplicateur de vitesse configurable. Un effet visuel de fissure est synchronisé avec la progression réelle.
```java
int maxTime = (int) (20 / (speed * ModConfig.INSTANCE.breakSpeedMultiplier)); 
int progress = (int) (((float)timer / maxTime) * 10);
world.setBlockBreakingInfo(zombie.getId(), target, progress);
```

### 5. Détection Critique de Vide : `MovementCoordinator`
Empêche les zombies de tomber accidentellement. Si un "Prochain Pas" mène à un vide, le zombie s'arrête net, se centre, et déclenche la construction d'un pont.

---

## 🧠 Intelligence Artificielle et Threads

### 6. Machine à États : `BuildStateMachine`
Pilote les transitions complexes entre la poursuite, le minage et la construction.

```java
public void tick() {
    if (state == 2) processMining(); // État Minage
    if (state == 1) processBuilding(); // État Construction
}
```

### 7. Traitement Asynchrone : `LethalThreads`
Pour éviter de ralentir le serveur, les calculs d'IA complexes (comme l'analyse d'obstruction) sont déportés sur des threads en arrière-plan.
```java
private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
    CORES, CORES, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256)
);
```

### 8. Processeur de Pensée : `BrainProcessor`
Fait le lien entre le tick de jeu et les threads. Il permet aux zombies de "réfléchir" à leur prochain bloc sans bloquer le moteur physique.

### 9. Conditions de Démarrage : `BuildConditions`
Définit si un zombie doit commencer à construire. Il vérifie la distance verticale (`dy`) et la présence de trous avant d'engager le mode construction.

---

## 🧗 Mouvement et Coordination de Meute

### 10. Escalade Verticale : `ClimbMover`
Ajuste les vecteurs de vélocité pour permettre aux zombies de grimper le long des structures qu'ils construisent ou des murs naturels.

### 11. Pose Intelligente en Meute : `PackPlacementLogic`
Empêche 10 zombies d'essayer de construire au même endroit. Si un zombie détecte des alliés en train de construire, il cherchera un point adjacent (Nord, Sud, Est ou Ouest) pour créer un escalier plus large.

### 12. Validation de Placement : `PlacementValidator`
Sécurité empêchant la pose de blocs hors des limites du monde ou dans des blocs non-remplaçables.

### 13. Centrage Physique : `ConstructionCoordinator`
Avant chaque action de construction, le zombie est "magnétisé" au centre de son bloc actuel pour garantir que sa tour ou son pont soit parfaitement aligné.

---

## 🔊 Écosystème Sonore et Instincts

### 14. Système d'Ouïe
Les zombies traquent les sons environnementaux (pas, blocs) via le `HearingRegistry`.

### 15. Instinct de Survie : `FleeExplosionGoal`
Les zombies normaux détectent les alliés "Amorcés" (Kamikazes sur le point d'exploser) et fuient pour éviter les dommages collatéraux.
```java
var list = world.getEntitiesByClass(ZombieEntity.class, range, z -> z.hasTag("lethal_primed"));
if (!list.isEmpty()) fleeFrom(list.get(0));
```

### 16. Surcharge Kamikaze
Détails visuels pour les kamikazes : particules de flammes et étincelles électriques (`ELECTRIC_SPARK`) si la puissance d'explosion dépasse 1,7x la normale.

---

## 🧬 Génétique et Mutation

### 17. Mise à l'Échelle Dynamique des Spécimens
Manipulation des attributs `SCALE`, `MAX_HEALTH`, et `MOVEMENT_SPEED` en fonction de la taille générée à l'apparition.

### 18. Mécaniques de Panique
Gestion des cris et de l'alerte des alliés lorsque la santé tombe sous un seuil critique.

---
Dernière mise à jour : 12 février 2026
