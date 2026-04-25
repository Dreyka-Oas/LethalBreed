export default {
  config: {
    categories: {
      attributes: {
        label: 'Attributs',
        desc: 'Taille, vitesse et santé de base des zombies. Ces valeurs sont aléatoires par apparition dans les plages min/max.',
        opt: {
          zombieFollowRange: 'Portée de détection et de poursuite en blocs',
          minScale: 'Multiplicateur de taille minimum du zombie',
          maxScale: 'Multiplicateur de taille maximum du zombie',
          minSpeed: 'Vitesse de déplacement minimum',
          maxSpeed: 'Vitesse de déplacement maximum',
          healthBonusMin: 'Multiplicateur de santé minimum',
          healthBonusMax: 'Multiplicateur de santé maximum',
        }
      },
      mutant: {
        label: 'Mutant',
        desc: 'Zombies boss mutants avec des attaques de tentacules. Un nombre élevé de sbires peut impacter les performances du serveur — réglez avec précaution.',
        opt: {
          mutantChance: 'Chance d\'apparition par zombie (5% par défaut)',
          mutantMinionCount: 'Nombre de sbires invoqués à la mort',
          mutantTentacleTickRate: 'Ticks entre les attaques de tentacules',
        }
      },
      equipment: {
        label: 'Équipement',
        desc: 'Armes, armures et système kamikaze. Les probabilités sont indépendantes — un zombie peut avoir un ensemble complet en Netherite.',
        opt: {
          kamikazeChance: 'Chance qu\'un zombie porte de la TNT',
          weaponChance: 'Chance d\'apparition d\'arme',
          weaponEnchantChance: 'Chance d\'enchantement d\'arme',
          armorHeadChance: 'Chance d\'apparition de casque',
          armorChestChance: 'Chance d\'apparition de plastron',
          armorLegsChance: 'Chance d\'apparition de jambières',
          armorFeetChance: 'Chance d\'apparition de bottes',
          armorEnchantChance: 'Chance d\'enchantement d\'armure',
        }
      },
      ai: {
        label: 'IA & Audition',
        desc: 'Portée de détection sonore, mèche kamikaze et durée de la mémoire sonore. Les sons détectés incluent les pas, la destruction de blocs, les portes et le combat.',
        opt: {
          hearingRange: 'Distance de détection auditive en blocs',
          kamikazeFuseTicks: 'Durée de la mèche TNT (40 ticks = 2 secondes)',
          kamikazeExplosionPower: 'Puissance d\'explosion (la TNT par défaut est de 4.0)',
          soundLockTicks: 'Ticks pendant lesquels un son reste en mémoire (300 = 15 sec)',
        }
      },
      panic: {
        label: 'Panique',
        desc: 'Lorsque la santé descend en dessous du seuil, les zombies hurlent pour alerter les alliés. Un groupe suffisamment grand arrêtera de paniquer et se tournera pour combattre.',
        opt: {
          healthThreshold: '% de santé pour déclencher le mode panique',
          continueHealthThreshold: '% de santé auquel la panique peut s\'arrêter',
          screamIntervalTicks: 'Ticks entre les cris de panique',
          allyAlertRange: 'Distance pour alerter les alliés proches',
          stopPackSize: 'Taille du groupe avant que la panique ne s\'arrête',
          cooldownTicks: 'Délai avant de paniquer à nouveau (600 = 30 sec)',
          fleeExplosionRange: 'Distance à laquelle les zombies fuient les explosions',
        }
      },
      movement: {
        label: 'Mouvement',
        desc: 'Vitesses d\'escalade de murs et système de construction de blocs. Les blocs placés par les zombies disparaissent après un temps configurable.',
        opt: {
          climbVerticalSpeed: 'Vitesse en grimpant verticalement',
          climbHorizontalSpeed: 'Vitesse en se déplaçant horizontalement le long d\'un mur',
          buildGlobalCooldownTicks: 'Ticks de recharge entre chaque bloc placé',
          temporaryBlocks_enabled: 'Si les blocs placés finissent par disparaître',
          temporaryBlocks_decayTicks: 'Ticks avant que les blocs ne disparaissent (600 = 30 sec)',
        }
      },
      breaking: {
        label: 'Destruction',
        desc: 'Multiplicateur de vitesse de destruction de blocs. À 4× par défaut, un bloc de terre tombe en 5 ticks. Désactivez en réglant un délai très élevé.',
        opt: {
          breakSpeedMultiplier: 'Multiplicateur appliqué à la vitesse de minage vanilla',
          breakMinTicks: 'Ticks minimum avant qu\'un bloc puisse être cassé',
        }
      }
    }
  }
}
