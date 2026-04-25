export default {
  config: {
    label: 'Configuration',
    title: 'Full config reference',
    subtitle_1: 'All settings live in',
    subtitle_2: 'Reload live with',
    reload_title: 'Reload without restarting',
    reload_desc: 'Updates all living zombies with new stats, re-equips them with new equipment settings, and resets AI behaviours — all without a restart.',
    compat_warning: '⚠️ Warning: Any mod that modifies zombie AI or behavior might interfere with LethalBreed. Use with caution alongside other AI mods.',
    tabs: {
      options: 'Options',
      json: 'JSON',
    },
    note_sounds: 'Detected sounds: Footsteps, block breaking, door opening/closing, combat, item use, and more.',
    note_performance: 'Server performance: High mutantMinionCount values can cause lag on servers with many players. Test before deploying.',
    note_scale: 'Higher maxScale makes zombies bigger and more intimidating, but consider lowering maxSpeed to compensate.',
    table: {
      option: 'Option',
      type: 'Type',
      default: 'Default',
      range: 'Range',
      description: 'Description',
    }
  }
}
