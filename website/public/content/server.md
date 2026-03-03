# Optimisation Serveur

**Lethal Breed** est conçu pour maintenir des performances optimales même sur des serveurs avec une forte densité de joueurs et d'entités. Pour y parvenir, le mod délègue les calculs d'IA les plus lourds à des threads séparés.

---

## 🧵 Architecture Multi-Thread

Contrairement au comportement standard de Minecraft où l'IA s'exécute entièrement sur le thread principal (ce qui peut causer des ralentissements ou des "chutes de TPS"), Lethal Breed utilise un système de gestion de tâches asynchrones appelé **LethalThreads**.

### Le Moteur : `LethalThreads.java`
Ce composant gère une file d'attente intelligente et adapte sa puissance en fonction du processeur du serveur.

```java
public class LethalThreads {
    private static final int CORES = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
        CORES, CORES, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(256),
        new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public static void execute(Runnable task) {
        POOL.execute(task);
    }
}
```

---

## 🧠 Qu'est-ce qui est "Threadé" et pourquoi ?

Le calcul le plus coûteux pour le serveur est l'**Analyse Dynamique d'Obstruction**. Lorsqu'un zombie est bloqué, il doit scanner l'environnement sous plusieurs angles et hauteurs pour décider s'il doit miner ou construire.

### Processus de Pensée Asynchrone
Le `BrainProcessor` demande aux threads d'arrière-plan de calculer la solution pendant que le jeu continue de s'exécuter.

```java
LethalThreads.execute(() -> {
    try {
        // Balayage environnemental lourd (Raycasting)
        BlockPos res = ObstructionAnalyzer.getHorizontal(world, zombie, target);
        resultSlot.set(res);
    } finally {
        thinking.set(false); // Libère la "pensée" du zombie
    }
});
```

### Avantages pour le serveur :
- **Stabilité du TPS** : Le serveur ne s'arrête pas pour attendre qu'un zombie trouve son chemin.
- **Réduction du Lag** : Même avec 100 zombies essayant de percer vos murs, le moteur physique de Minecraft reste fluide.
- **Utilisation du CPU** : Le mod exploite enfin les processeurs multi-cœurs modernes, là où Minecraft Vanilla est souvent limité à un seul cœur.

---
