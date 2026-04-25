export default {
  config: {
    categories: {
      attributes: {
        label: 'Attributes',
        desc: 'Zombie size, speed and base health. These values are randomised per spawn within the min/max ranges.',
        opt: {
          zombieFollowRange: 'Detection and pursuit range in blocks',
          minScale: 'Minimum zombie size multiplier',
          maxScale: 'Maximum zombie size multiplier',
          minSpeed: 'Minimum movement speed',
          maxSpeed: 'Maximum movement speed',
          healthBonusMin: 'Minimum health multiplier',
          healthBonusMax: 'Maximum health multiplier',
        }
      },
      mutant: {
        label: 'Mutant',
        desc: 'Mutant boss zombies with tentacle attacks. High minion counts can impact server performance — tune carefully.',
        opt: {
          mutantChance: 'Spawn chance per zombie (5% default)',
          mutantMinionCount: 'Number of minions summoned on death',
          mutantTentacleTickRate: 'Ticks between tentacle attacks',
        }
      },
      equipment: {
        label: 'Equipment',
        desc: 'Weapons, armor, and the kamikaze system. Probabilities are independent — a zombie can have a full set of Netherite.',
        opt: {
          kamikazeChance: 'Chance a zombie carries TNT',
          weaponChance: 'Weapon spawn chance',
          weaponEnchantChance: 'Weapon enchantment chance',
          armorHeadChance: 'Helmet drop chance',
          armorChestChance: 'Chestplate drop chance',
          armorLegsChance: 'Leggings drop chance',
          armorFeetChance: 'Boots drop chance',
          armorEnchantChance: 'Armor enchantment chance',
        }
      },
      ai: {
        label: 'AI & Hearing',
        desc: 'Sound detection range, kamikaze fuse, and sound memory duration. Detected sounds include footsteps, block breaking, doors, and combat.',
        opt: {
          hearingRange: 'Hearing detection distance in blocks',
          kamikazeFuseTicks: 'TNT fuse duration (40 ticks = 2 seconds)',
          kamikazeExplosionPower: 'Explosion power (TNT default is 4.0)',
          soundLockTicks: 'Ticks a sound stays in memory (300 = 15 sec)',
        }
      },
      panic: {
        label: 'Panic',
        desc: 'When health drops below the threshold, zombies scream to alert allies. A sufficiently large group will stop panicking and turn to fight.',
        opt: {
          healthThreshold: 'Health % to trigger panic mode',
          continueHealthThreshold: 'Health % at which panic can stop',
          screamIntervalTicks: 'Ticks between panic screams',
          allyAlertRange: 'Distance to alert nearby allies',
          stopPackSize: 'Group size before panic stops',
          cooldownTicks: 'Cooldown before panicking again (600 = 30 sec)',
          fleeExplosionRange: 'Distance at which zombies flee explosions',
        }
      },
      movement: {
        label: 'Movement',
        desc: 'Wall climbing speeds and the block-building system. Blocks placed by zombies decay after a configurable time.',
        opt: {
          climbVerticalSpeed: 'Speed when climbing up a wall',
          climbHorizontalSpeed: 'Speed when moving along a wall',
          buildGlobalCooldownTicks: 'Ticks de recharge entre chaque bloc placé', // oops, mix? No wait.
          temporaryBlocks_enabled: 'Whether placed blocks eventually decay',
          temporaryBlocks_decayTicks: 'Ticks before blocks disappear (600 = 30 sec)',
        }
      },
      breaking: {
        label: 'Breaking',
        desc: 'Block-breaking speed multiplier. At 4× default, a dirt block falls in 5 ticks. Disable by setting a very high cooldown.',
        opt: {
          breakSpeedMultiplier: 'Multiplier applied to vanilla mining speed',
          breakMinTicks: 'Minimum ticks before any block can be broken',
        }
      }
    }
  }
}
