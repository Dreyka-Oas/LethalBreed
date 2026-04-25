export const CATEGORIES = [
  {
    id: 'attributes',
    icon: '📏',
    json: `{
  "attributes": {
    "zombieFollowRange": 18.0,
    "minScale": 0.85,
    "maxScale": 1.35,
    "minSpeed": 0.18,
    "maxSpeed": 0.28,
    "healthBonusMin": 0.8,
    "healthBonusMax": 1.2
  }
}`,
    options: [
      { key: 'zombieFollowRange', type: 'Double', def: '18.0', min: '4.0',  max: '128.0' },
      { key: 'minScale',          type: 'Double', def: '0.85', min: '0.1',  max: '5.0' },
      { key: 'maxScale',          type: 'Double', def: '1.35', min: '0.1',  max: '5.0' },
      { key: 'minSpeed',          type: 'Double', def: '0.18', min: '0.05', max: '1.0' },
      { key: 'maxSpeed',          type: 'Double', def: '0.28', min: '0.05', max: '1.0' },
      { key: 'healthBonusMin',    type: 'Double', def: '0.8',  min: '0.1',  max: '10.0' },
      { key: 'healthBonusMax',    type: 'Double', def: '1.2',  min: '0.1',  max: '10.0' },
    ],
  },
  {
    id: 'mutant',
    icon: '👹',
    json: `{
  "mutant": {
    "mutantChance": 0.05,
    "mutantMinionCount": 8,
    "mutantTentacleTickRate": 5
  }
}`,
    options: [
      { key: 'mutantChance',           type: 'Double', def: '0.05', min: '0.0', max: '1.0' },
      { key: 'mutantMinionCount',      type: 'Int',    def: '8',    min: '1',   max: '64' },
      { key: 'mutantTentacleTickRate', type: 'Int',    def: '5',    min: '1',   max: '20' },
    ],
  },
  {
    id: 'equipment',
    icon: '⚔️',
    json: `{
  "equipment": {
    "kamikazeChance": 0.05,
    "weaponChance": 0.7,
    "weaponEnchantChance": 0.4,
    "armorHeadChance": 0.5,
    "armorChestChance": 0.4,
    "armorLegsChance": 0.4,
    "armorFeetChance": 0.4,
    "armorEnchantChance": 0.3
  }
}`,
    options: [
      { key: 'kamikazeChance',     type: 'Double', def: '0.05', min: '0.0', max: '1.0' },
      { key: 'weaponChance',       type: 'Double', def: '0.7',  min: '0.0', max: '1.0' },
      { key: 'weaponEnchantChance',type: 'Double', def: '0.4',  min: '0.0', max: '1.0' },
      { key: 'armorHeadChance',    type: 'Double', def: '0.5',  min: '0.0', max: '1.0' },
      { key: 'armorChestChance',   type: 'Double', def: '0.4',  min: '0.0', max: '1.0' },
      { key: 'armorLegsChance',    type: 'Double', def: '0.4',  min: '0.0', max: '1.0' },
      { key: 'armorFeetChance',    type: 'Double', def: '0.4',  min: '0.0', max: '1.0' },
      { key: 'armorEnchantChance', type: 'Double', def: '0.3',  min: '0.0', max: '1.0' },
    ],
  },
  {
    id: 'ai',
    icon: '👂',
    json: `{
  "ai": {
    "hearingRange": 16.0,
    "kamikazeFuseTicks": 40,
    "kamikazeExplosionPower": 3.0,
    "soundLockTicks": 300
  }
}`,
    options: [
      { key: 'hearingRange',           type: 'Double', def: '16.0', min: '0.0', max: '64.0' },
      { key: 'kamikazeFuseTicks',      type: 'Int',    def: '40',   min: '10',  max: '200' },
      { key: 'kamikazeExplosionPower', type: 'Float',  def: '3.0',  min: '0.1', max: '10.0' },
      { key: 'soundLockTicks',         type: 'Int',    def: '300',  min: '60',  max: '1200' },
    ],
  },
  {
    id: 'panic',
    icon: '😱',
    json: `{
  "panic": {
    "healthThreshold": 0.25,
    "continueHealthThreshold": 0.5,
    "screamIntervalTicks": 40,
    "allyAlertRange": 12.0,
    "stopPackSize": 5,
    "cooldownTicks": 600,
    "fleeExplosionRange": 8.0
  }
}`,
    options: [
      { key: 'healthThreshold',          type: 'Double', def: '0.25', min: '0.0', max: '1.0' },
      { key: 'continueHealthThreshold',  type: 'Double', def: '0.5',  min: '0.0', max: '1.0' },
      { key: 'screamIntervalTicks',      type: 'Int',    def: '40',   min: '10',  max: '200' },
      { key: 'allyAlertRange',           type: 'Double', def: '12.0', min: '0.0', max: '32.0' },
      { key: 'stopPackSize',             type: 'Int',    def: '5',    min: '1',   max: '32' },
      { key: 'cooldownTicks',            type: 'Int',    def: '600',  min: '100', max: '3600' },
      { key: 'fleeExplosionRange',       type: 'Double', def: '8.0',  min: '0.0', max: '16.0' },
    ],
  },
  {
    id: 'movement',
    icon: '🧗',
    json: `{
  "movement": {
    "climbVerticalSpeed": 0.25,
    "climbHorizontalSpeed": 0.15,
    "buildGlobalCooldownTicks": 4,
    "temporaryBlocks": {
      "enabled": true,
      "decayTicks": 600
    }
  }
}`,
    options: [
      { key: 'climbVerticalSpeed',       type: 'Double',  def: '0.25', min: '0.01', max: '1.0' },
      { key: 'climbHorizontalSpeed',     type: 'Double',  def: '0.15', min: '0.01', max: '1.0' },
      { key: 'buildGlobalCooldownTicks', type: 'Int',     def: '4',    min: '1',    max: '40' },
      { key: 'temporaryBlocks.enabled',  type: 'Boolean', def: 'true', min: '—',    max: '—' },
      { key: 'temporaryBlocks.decayTicks',type:'Int',     def: '600',  min: '60',   max: '7200' },
    ],
  },
  {
    id: 'breaking',
    icon: '🧱',
    json: `{
  "breaking": {
    "breakSpeedMultiplier": 4.0,
    "breakMinTicks": 5
  }
}`,
    options: [
      { key: 'breakSpeedMultiplier', type: 'Double', def: '4.0', min: '0.1', max: '100.0' },
      { key: 'breakMinTicks',        type: 'Int',    def: '5',   min: '1',   max: '100' },
    ],
  },
]
