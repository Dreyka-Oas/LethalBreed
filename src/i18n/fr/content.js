export default {
  features: {
    label: 'Fonctionnalités',
    title: 'Une nouvelle race de danger',
    items: {
      ai: { title: 'IA Avancée', desc: 'Les zombies possèdent désormais une mémoire sonore. Ils enquêteront sur les bruits de pas, la destruction de blocs et d\'autres bruits de joueurs.' },
      mutant: { title: 'Boss Mutants', desc: 'Variantes mutantes rares avec plus de santé et des attaques de tentacules qui invoquent des sbires à la mort.', tag: 'Boss' },
      movement: { title: 'Escalade & Construction', desc: 'Les zombies peuvent escalader les murs pour atteindre les hauteurs et placer des blocs temporaires pour franchir des fossés.', tag: 'Mouvement' },
      kamikaze: { title: 'Kamikaze', desc: 'Attention aux zombies portant de la TNT. Ils s\'allument et chargent quand vous êtes à portée.', tag: 'Danger' },
      panic: { title: 'Système de Panique', desc: 'Les zombies blessés hurlent et fuient, alertant les alliés proches pour rejoindre la chasse.', tag: 'IA' },
      breaking: { title: 'Destruction de Blocs', desc: 'Aucun mur n\'est sûr. Les zombies détruiront lentement les blocs pour atteindre leur cible.' }
    }
  },
  faq: {
    label: 'FAQ',
    title: 'Questions fréquentes',
    items: {
      q1: { q: 'Est-ce uniquement côté serveur ?', a: 'Oui ! LethalBreed est un mod 100% côté serveur. Les clients n\'ont pas besoin de l\'installer pour rejoindre.' },
      q2: { q: 'Puis-je désactiver certaines fonctionnalités ?', a: 'Oui, tout est configurable dans le fichier lethalbreed.json.' },
      q3: { q: 'Est-ce compatible avec d\'autres mods ?', a: 'Généralement oui, mais tout mod qui modifie également l\'IA ou le comportement des zombies peut entrer en conflit.' }
    }
  }
}
