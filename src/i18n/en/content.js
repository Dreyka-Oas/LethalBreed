export default {
  features: {
    label: 'Features',
    title: 'New breed of danger',
    items: {
      ai: { title: 'Advanced AI', desc: 'Zombies now possess sound memory. They will investigate footsteps, block breaking, and other players sounds.' },
      mutant: { title: 'Mutant Bosses', desc: 'Rare mutant variants with higher health and tentacle attacks that summon minions upon death.', tag: 'Boss' },
      movement: { title: 'Climbing & Building', desc: 'Zombies can scale walls to reach higher ground and place temporary blocks to cross gaps.', tag: 'Movement' },
      kamikaze: { title: 'Kamikaze', desc: 'Watch out for TNT-carrying zombies. They will ignite and charge when you are in range.', tag: 'Danger' },
      panic: { title: 'Panic System', desc: 'Injured zombies will scream and flee, alerting nearby allies to join the hunt.', tag: 'AI' },
      breaking: { title: 'Block Breaking', desc: 'No wall is safe. Zombies will slowly break through blocks to reach their target.' }
    }
  },
  faq: {
    label: 'FAQ',
    title: 'Common questions',
    items: {
      q1: { q: 'Is it server-side only?', a: 'Yes! LethalBreed is a 100% server-side mod. Clients do not need to install it to join your server.' },
      q2: { q: 'Can I disable specific features?', a: 'Yes, everything is configurable in the lethalbreed.json file.' },
      q3: { q: 'Does it work with other mods?', a: 'Generally yes, but any mod that also modifies zombie AI or behavior might conflict.' }
    }
  }
}
